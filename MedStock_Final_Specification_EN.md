# MedStock — Complete Project Specification (Final)

**Hospital Inter-Branch Medicine Availability & Transfer System**

This document defines the entire project — problem, roles, architecture, entities, APIs, business logic, everything in one place. By reading it, the whole system can be built from scratch.


---

# 1. PROBLEM STATEMENT

A hospital has multiple branches. Each branch has its own medicine stock. There are two core problems:

1. **Availability Problem** — A medicine runs out at Branch A, and there is no way to know which branch has it. Currently this is found out through phone calls — it's slow, and risky in an emergency.
2. **Wastage Problem** — At some branches, stock is expiring that could have been used elsewhere.

**What MedStock does:**
- A branch raises a request when its stock runs out (instead of making phone calls)
- The system checks nearby branches itself, and whatever is expiring soonest is offered first (FEFO)
- If a branch does not respond in time, the system tries the next branch on its own (auto-escalation)
- If all branches fail, the Central Warehouse is notified
- A critical shortage never waits in the queue behind a routine request (criticality-based ordering)

---

# 2. ROLES (4 Total)

| Role | Tied to a branch? | How it is created | What it does |
|---|---|---|---|
| **ADMIN** | No (`branchId = null`) | Created by the system itself at startup (bootstrap) — nobody can register with this role | Adds/edits/deletes branches, generates Warehouse Access Codes |
| **BRANCH_STAFF** | Yes | Registers — an existing Branch ID is verified | Checks availability, raises transfer requests, approves/rejects incoming requests for their own branch |
| **INVENTORY_MANAGER** | Yes | Registers — an existing Branch ID is verified | Adds/edits/views their own branch's stock, views low-stock alerts |
| **WAREHOUSE_ADMIN** | No (`branchId = null`) | Registers — a Warehouse Access Code created by the ADMIN is verified | Only views escalated requests (view-only) |

## Registration Trust Chain (who allows whom to exist)

```
ADMIN (created by the system itself, once, at startup via bootstrap)
   │
   ├──► Creates a Branch (in branch-service)
   │        │
   │        └──► BRANCH_STAFF / INVENTORY_MANAGER can register
   │             (they must know the Branch ID that the Admin created)
   │
   └──► Creates a Warehouse Access Code (in auth-service)
            │
            └──► WAREHOUSE_ADMIN can register
                 (they must know that exact code)
```

**Core principle:** No role can exist without a trusted source above it. There is no random self-registration anywhere.

---

# 3. ARCHITECTURE — 7 Microservices

```
                              REACT FRONTEND
                    (role-based routing — 4 separate dashboards)
                                    |
                                    | axios calls (JWT header)
                                    v
                            API GATEWAY (Port 8080)
              JWT validation + role check (protects admin-only routes) +
                   forwards role/branchId headers downstream
                                    |
    ------------------------------------------------------------------
    |            |              |               |                |
 AUTH         BRANCH        INVENTORY        TRANSFER          ALERT
SERVICE       SERVICE        SERVICE          SERVICE          SERVICE
(8083)        (8084)         (8081)           (8082)           (8085)
    |            |              |               |                |
    |            <--------------+---------------+                |
    | (verify                   |  (check stock,                 |
    |  branch ID)               |   deduct stock)                |
    |                           <--------------------------------+
    |                                 (read medicine data
    |                                  for low-stock calculation)
    ------------------------------------------------------------------
                                    |
                          PostgreSQL (medstock DB)

              Eureka Server (Port 8761) — service discovery; helps all
              services find one another
```

**Service Dependency Map:**
```
auth-service      -->  branch-service     (when BRANCH_STAFF/INVENTORY_MANAGER register)
transfer-service  -->  branch-service     (city-based nearby branch lookup)
transfer-service  -->  inventory-service  (availability check + stock deduction)
alert-service     -->  inventory-service  (reads medicine data in order to create alerts)
api-gateway       -->  all 5 services     (routing + auth)
```

---

# 4. TECH STACK

| Layer | Choice |
|---|---|
| Backend | Java 17, Spring Boot 3.x, Maven |
| Service Discovery | Netflix Eureka |
| Gateway | Spring Cloud Gateway |
| Database | PostgreSQL |
| ORM | Spring Data JPA / Hibernate |
| Auth | Spring Security + JWT (jjwt library, HS256) |
| Resilience | Resilience4j (circuit breaker + fallback, between the Inventory–Transfer call) |
| Boilerplate reduction | Lombok |
| Frontend | React (react-router-dom, axios, Context API) |
| Testing | JUnit 5, Mockito (service-layer unit tests, no HTTP needed) |

---

# 5. SERVICE-BY-SERVICE SPECIFICATION

## 5.1 eureka-server (Port 8761)

No business logic — just a registry. Every service registers here at startup.

```properties
server.port=8761
eureka.client.register-with-eureka=false
eureka.client.fetch-registry=false
```

---

## 5.2 branch-service (Port 8084)

**Entity: `Branch`**
| Field | Type | Notes |
|---|---|---|
| id | Long | PK, auto-generated — this is the ID that gets verified at registration time |
| branchName | String | |
| city | String | for escalation filtering |
| contactNumber | String | |

**Endpoints:**
```
POST   /api/branch/branches                 → new branch (ADMIN only)
GET    /api/branch/branches                 → list all
GET    /api/branch/branches/{id}            → find one (auth-service calls this)
GET    /api/branch/branches/city/{cityName} → city-wise list (transfer-service calls this)
PUT    /api/branch/branches/{id}            → update (ADMIN only)
DELETE /api/branch/branches/{id}            → delete (ADMIN only)
```

---

## 5.3 auth-service (Port 8083)

**Entity: `User`**
| Field | Type | Notes |
|---|---|---|
| id | Long | |
| username | String | unique |
| password | String | BCrypt hashed |
| role | Enum: ADMIN, BRANCH_STAFF, INVENTORY_MANAGER, WAREHOUSE_ADMIN | |
| branchId | Long | required for STAFF/MANAGER, null for ADMIN/WAREHOUSE_ADMIN |

**Entity: `WarehouseAccessCode`**
| Field | Type | Notes |
|---|---|---|
| id | Long | |
| code | String | unique, generated by the ADMIN |
| isUsed | Boolean | default false |
| assignedToUsername | String | set once it has been used |
| createdAt | LocalDateTime | |

**Register logic (different validation depending on the role):**
```
role == ADMIN                          → ALWAYS REJECT (cannot be created via public register)
role == BRANCH_STAFF/INVENTORY_MANAGER → branchId required, will be verified with branch-service
role == WAREHOUSE_ADMIN                → warehouseCode required, verified in the DB + isUsed check
```

**Bootstrap (AdminBootstrap — CommandLineRunner):** At app startup, if there is no ADMIN in the DB, it creates one default ADMIN (username/password from `application.properties`, e.g. `admin` / `ChangeMe@123`). Idempotent — it will not be created a second time on restart.

**Endpoints:**
```
POST /api/auth/register                → register with role-based validation
POST /api/auth/login                    → returns JWT (claims: username, role, branchId)
POST /api/auth/warehouse-codes          → generate a new code (ADMIN only)
GET  /api/auth/warehouse-codes          → list all codes (ADMIN only)
PUT  /api/auth/change-password          → change your own password (for everyone, especially the bootstrap admin)
```

**JWT Claims:** `subject=username`, `role`, `branchId`, `issuedAt`, `expiration`

---

## 5.4 inventory-service (Port 8081)

**Entity: `Medicine`**
| Field | Type | Notes |
|---|---|---|
| id | Long | |
| medicineName | String | |
| batchNumber | String | |
| branchId | Long | |
| branchName | String | |
| quantity | Integer | |
| unitPrice | Double | |
| expiryDate | LocalDate | |
| avgDailyConsumption | Double | for low-stock alerts (moving average) |
| createdAt, updatedAt | LocalDateTime | |

**Endpoints:**
```
POST   /api/inventory/medicines                                    → add
GET    /api/inventory/medicines                                    → list all
GET    /api/inventory/medicines/{id}                                → one
PUT    /api/inventory/medicines/{id}                                → update
DELETE /api/inventory/medicines/{id}                                → delete
GET    /api/inventory/medicines/branch/{branchId}                   → a branch's stock
GET    /api/inventory/medicines/availability?medicineName=X&requiredQuantity=Y&city=Z
                                                                      → FEFO search, city-filtered
PUT    /api/inventory/medicines/{id}/deduct?quantity=N               → deduct stock (idempotent-safe,
                                                                        transfer-service calls this)
```

**FEFO query (core logic):**
```java
List<Medicine> findByMedicineNameIgnoreCaseAndQuantityGreaterThanAndExpiryDateAfterOrderByExpiryDateAsc(
    String medicineName, Integer quantity, LocalDate today);
```

---

## 5.5 transfer-service (Port 8082)

**Enums:**
```java
public enum Criticality { CRITICAL, URGENT, ROUTINE }

public enum TransferStatus {
    PENDING, APPROVED, REJECTED, ESCALATED_TO_WAREHOUSE, CONFIRMED, CANCELLED
}
```

**Entity: `TransferRequest`**
| Field | Type | Notes |
|---|---|---|
| id | Long | |
| medicineName | String | |
| quantity | Integer | |
| requestingBranchId/Name | Long, String | who raised the request |
| currentTargetBranchId/Name | Long, String | which branch is currently being tried |
| criticality | Enum | **required — validation applies (400 if missing)** |
| status | Enum | default PENDING |
| attemptedBranchIds | String (comma-separated) | so the same branch is not tried again |
| lastAttemptedAt | LocalDateTime | for the timeout check |
| remarks | String | |
| requestedAt, updatedAt | LocalDateTime | |

**Endpoints:**
```
POST   /api/transfer/requests                    → new request (criticality required)
GET    /api/transfer/requests                    → all (sorted by criticality + FIFO order)
GET    /api/transfer/requests?status=X            → status-filtered (WAREHOUSE_ADMIN will use this)
GET    /api/transfer/requests/{id}                → one
PUT    /api/transfer/requests/{id}/approve        → approve → idempotent stock deduction → CONFIRMED
PUT    /api/transfer/requests/{id}/reject         → reject
DELETE /api/transfer/requests/{id}                → delete/cancel
GET    /api/transfer/requests/branch/{branchId}   → everything related to one branch
```

**Queue ordering (GET /api/transfer/requests):**
```sql
ORDER BY 
  CASE criticality WHEN 'CRITICAL' THEN 1 WHEN 'URGENT' THEN 2 WHEN 'ROUTINE' THEN 3 END ASC,
  requestedAt ASC
```

**Escalation logic (Scheduled job, runs every 1–2 min):**
```
1. Find all PENDING requests where more time has passed than lastAttemptedAt + TIMEOUT
2. Put currentTargetBranchId into attemptedBranchIds
3. From the availability list (city-filtered, FEFO order), find the next branch that has
   not been attempted
4. Found one → update currentTargetBranchId, reset lastAttemptedAt, LOG it
5. Not found (list exhausted) → status = ESCALATED_TO_WAREHOUSE, LOG it
```

**Resilience4j Circuit Breaker (around the Inventory Service call):**
```java
@CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackAvailability")
public List<MedicineResponse> checkAvailabilityAt(Long branchId, String medicineName) { ... }

public List<MedicineResponse> fallbackAvailability(Long branchId, String medicineName, Throwable t) {
    log.warn("Inventory service unavailable for branch {}, skipping", branchId);
    return Collections.emptyList();
}
```

**Idempotent Deduction (approve endpoint):**
```java
@Transactional
public void approveTransfer(Long requestId) {
    TransferRequest request = repo.findById(requestId)...;
    if (request.getStatus() == TransferStatus.CONFIRMED) return; // already done, safe exit
    
    Medicine medicine = inventoryClient.getMedicine(...);
    if (medicine.getQuantity() < request.getQuantity())
        throw new InsufficientStockException("INSUFFICIENT_STOCK");
    
    inventoryClient.deductStock(medicine.getId(), request.getQuantity());
    request.setStatus(TransferStatus.CONFIRMED);
    repo.save(request);
}
```

---

## 5.6 alert-service (Port 8085)

**Entity: `RestockAlert`**
| Field | Type | Notes |
|---|---|---|
| id | Long | |
| medicineId | Long | |
| medicineName | String | |
| branchId | Long | |
| daysRemaining | Double | quantity / avgDailyConsumption |
| resolved | Boolean | default false |
| createdAt | LocalDateTime | |

**Scheduled Job (every 6 hours):**
```
1. Fetch all medicine data from inventory-service
2. For each medicine: daysRemaining = quantity / avgDailyConsumption
3. If daysRemaining <= 3 (threshold) AND no unresolved alert already exists
   for the same medicine+branch → create a new RestockAlert
4. Be sure to do the duplicate check (already-unresolved-alert-exists check)
```

**Endpoints:**
```
GET /api/alert/alerts                  → all alerts
GET /api/alert/alerts/branch/{id}       → one branch's alerts (INVENTORY_MANAGER will use this)
PUT /api/alert/alerts/{id}/resolve      → mark the alert as resolved
```

---

## 5.7 api-gateway (Port 8080)

**Routes:**
```properties
spring.cloud.gateway.routes[0].id=branch-service
spring.cloud.gateway.routes[0].uri=lb://branch-service
spring.cloud.gateway.routes[0].predicates[0]=Path=/api/branch/**

spring.cloud.gateway.routes[1].id=auth-service
spring.cloud.gateway.routes[1].uri=lb://auth-service
spring.cloud.gateway.routes[1].predicates[0]=Path=/api/auth/**

spring.cloud.gateway.routes[2].id=inventory-service
spring.cloud.gateway.routes[2].uri=lb://inventory-service
spring.cloud.gateway.routes[2].predicates[0]=Path=/api/inventory/**

spring.cloud.gateway.routes[3].id=transfer-service
spring.cloud.gateway.routes[3].uri=lb://transfer-service
spring.cloud.gateway.routes[3].predicates[0]=Path=/api/transfer/**

spring.cloud.gateway.routes[4].id=alert-service
spring.cloud.gateway.routes[4].uri=lb://alert-service
spring.cloud.gateway.routes[4].predicates[0]=Path=/api/alert/**
```

**AuthenticationFilter (GlobalFilter) — it does two jobs:**
1. **Authentication:** Apart from `/api/auth/login` and `/api/auth/register`, every request needs a valid JWT (otherwise 401)
2. **Authorization:** Some specific routes are protected for ADMIN only (otherwise 403):
   ```
   POST/PUT/DELETE /api/branch/branches/**  → ADMIN only
   POST /api/auth/warehouse-codes            → ADMIN only
   ```
3. When valid, it adds the `X-Auth-Username`, `X-Auth-Role`, `X-Auth-BranchId` headers and forwards the request to the downstream service

---

# 6. FRONTEND (React)

```
frontend/
├── package.json
├── public/index.html
├── src/
│   ├── App.jsx                       ← role-based routing
│   ├── context/AuthContext.jsx       ← token, role, branchId global state
│   ├── services/
│   │   ├── api.js                    ← axios instance + JWT interceptor
│   │   ├── authService.js
│   │   ├── branchService.js
│   │   ├── inventoryService.js
│   │   ├── transferService.js
│   │   └── alertService.js
│   ├── pages/
│   │   ├── LoginPage.jsx
│   │   ├── RegisterPage.jsx          ← role dropdown, conditional fields
│   │   │                                (either a Branch dropdown or a Warehouse Code input)
│   │   ├── admin/
│   │   │   ├── BranchManagementPage.jsx
│   │   │   └── WarehouseCodeManagementPage.jsx
│   │   ├── branch-staff/
│   │   │   ├── AvailabilitySearchPage.jsx
│   │   │   ├── RaiseRequestPage.jsx
│   │   │   └── IncomingRequestsPage.jsx
│   │   ├── inventory-manager/
│   │   │   ├── StockManagementPage.jsx
│   │   │   └── AlertsPage.jsx
│   │   └── warehouse-admin/
│   │       └── EscalatedQueuePage.jsx  ← read-only
│   └── components/
│       ├── Navbar.jsx                ← links according to role
│       ├── ProtectedRoute.jsx        ← role-check + redirect
│       ├── MedicineTable.jsx
│       ├── TransferTable.jsx
│       └── BranchDropdown.jsx
```

**Redirect straight after login (according to role):**
```
ADMIN              → /admin/branches
BRANCH_STAFF       → /availability
INVENTORY_MANAGER  → /stock
WAREHOUSE_ADMIN    → /escalated
```

**Register form conditional fields:**
```
role == BRANCH_STAFF / INVENTORY_MANAGER  → a Branch dropdown is shown (required)
role == WAREHOUSE_ADMIN                    → a Warehouse Access Code input is shown
role == ADMIN                              → the option is not even present in the dropdown
```

---

# 7. NON-FUNCTIONAL REQUIREMENTS (from the PRD)

| Area | Requirement |
|---|---|
| Availability | If the Inventory Service is down/slow, the Transfer Service must not block — Resilience4j fallback |
| Performance | Availability check + queue ordering should respond within ~1–2 seconds (demo-scale data) |
| Data Integrity | Stock must never be deducted twice for one and the same confirmed transfer (idempotency) |
| Observability | Every escalation attempt must be LOGGED — which branch was tried, why it failed |
| Testability | Business logic (FEFO, criticality ordering, escalation, deduction) must be unit-testable at the Service layer, without needing HTTP or both services running |

---

# 8. BUILD SEQUENCE (The Order Matters)

```
1. eureka-server
2. branch-service          (both auth and transfer depend on this)
3. inventory-service       (independent)
4. auth-service             (depends on branch-service; the bootstrap admin lives here too)
5. transfer-service         (depends on both branch-service and inventory-service)
6. alert-service            (depends on inventory-service)
7. api-gateway              (must know everyone's names in order to route)
8. frontend (React)         (depends on all the backend APIs)
```

---

# 9. END-TO-END TEST SCENARIO (To Verify the Whole Flow)

```
1. Start auth-service → "DEFAULT ADMIN CREATED" should appear in the log
2. Log in with admin/ChangeMe@123 → change the password
3. As ADMIN, create 2 Branches (in the same city) — Branch A, Branch B
4. As ADMIN, generate 1 Warehouse Access Code
5. Register a BRANCH_STAFF using Branch A's ID
6. Register an INVENTORY_MANAGER using Branch B's ID
7. Register a WAREHOUSE_ADMIN using the generated code
8. The INVENTORY_MANAGER logs in and adds stock at Branch B
9. The BRANCH_STAFF (Branch A) logs in and searches availability —
   Branch B's stock should appear
10. The BRANCH_STAFF raises a CRITICAL request against Branch B
11. Branch B approves → CONFIRMED → the stock should be deducted
12. Try approving that same request again (duplicate call)
    → the stock must NOT be deducted a second time (idempotency test)
13. Set Branch B's stock to 0, raise a new request, wait for the timeout
    (keep a short timeout for testing) → it should become
    ESCALATED_TO_WAREHOUSE
14. The WAREHOUSE_ADMIN logs in and sees this request in their queue
15. Try registering with a wrong Branch ID → it should be rejected
    ("Invalid Branch ID")
16. Try registering again using the same Warehouse Code → it should be
    rejected ("already used")
```

If all 16 of these steps pass, the entire system is working correctly.
