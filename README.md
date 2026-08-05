# MedStock

**Hospital Inter-Branch Medicine Availability & Transfer System**

When a branch runs out of a medicine it raises a request instead of phoning around. The system finds branches that hold the stock (earliest-expiring offered first — FEFO), nudges one branch at a time, auto-escalates if nobody responds, and falls back to a Central Warehouse queue when no branch can supply it. Critical shortages never queue behind routine ones.

Seven Spring Boot services plus a React frontend. Full design: [`MedStock_Final_Specification_EN.md`](MedStock_Final_Specification_EN.md). Architecture and behavioural details for working on the code: [`CLAUDE.md`](CLAUDE.md).

---

## 1. Prerequisites

| Need | Version | Notes |
|---|---|---|
| JDK | 17 | Services target Java 17 |
| Maven | — | Not needed globally; each service ships `mvnw.cmd` |
| PostgreSQL | 12+ | Developed against 14 |
| Node.js | 20+ | For the React frontend (Vite 8) |

---

## 2. One-time setup

### 2.1 Create the database

All services share a single database named `medstock`. Create it once:

```powershell
psql -U postgres -c "CREATE DATABASE medstock;"
```

You do **not** need to create tables. Every service runs `spring.jpa.hibernate.ddl-auto=update`, so schemas are created on first start. There is no migration tool.

### 2.2 Point the services at your Postgres

Database credentials are checked into each service's `application.properties`. If your local Postgres user/password differs from what's committed, edit these **five** files (the other two services have no database):

```
auth-service/src/main/resources/application.properties
branch-service/src/main/resources/application.properties
inventory-service/src/main/resources/application.properties
transfer-service/src/main/resources/application.properties
alert-service/src/main/resources/application.properties
```

> **Note:** these files contain a plaintext DB password, and `jwt.secret` is duplicated in `auth-service` and `api-gateway`. Those two secrets **must stay identical** or every request will 401 — the gateway validates tokens that auth-service signs. Move both to environment variables before this goes anywhere real.

### 2.3 Install frontend dependencies

```powershell
cd frontend
npm install
```

---

## 3. Running the stack

**Start order matters.** Services resolve each other through Eureka and verify ids across service boundaries, so a service started too early will fail its first calls. Each service needs **its own terminal** — `spring-boot:run` stays in the foreground.

Run each of these from the repo root, one per terminal, in this order:

```powershell
# 1. Service registry — everything else registers here
.\eureka-server\mvnw.cmd -f eureka-server\pom.xml spring-boot:run

# 2. Branches — auth and transfer both depend on it
.\branch-service\mvnw.cmd -f branch-service\pom.xml spring-boot:run

# 3. Stock (independent)
.\inventory-service\mvnw.cmd -f inventory-service\pom.xml spring-boot:run

# 4. Auth — verifies branch ids, and bootstraps the default admin
.\auth-service\mvnw.cmd -f auth-service\pom.xml spring-boot:run

# 5. Transfers — needs both branch and inventory
.\transfer-service\mvnw.cmd -f transfer-service\pom.xml spring-boot:run

# 6. Low-stock alerts — needs inventory
.\alert-service\mvnw.cmd -f alert-service\pom.xml spring-boot:run

# 7. Gateway — single entry point, must resolve all of the above
.\api-gateway\mvnw.cmd -f api-gateway\pom.xml spring-boot:run
```

In Git Bash use `./eureka-server/mvnw -f eureka-server/pom.xml spring-boot:run` instead.

Wait for `Started <Name>Application` in each terminal before starting the next.

**Then confirm registration** at <http://localhost:8761> — the Eureka dashboard should list six instances (`BRANCH-SERVICE`, `AUTH-SERVICE`, `INVENTORY-SERVICE`, `TRANSFER-SERVICE`, `ALERT-SERVICE`, `API-GATEWAY`). Eureka itself does not register. Registration can lag ~30s after startup; acting before all six appear is the most common cause of confusing first-run failures.

### Frontend

```powershell
cd frontend
npm run dev
```

Open <http://localhost:5173>. The frontend talks **only** to the gateway on `:8080` (see `frontend/src/services/api.js`) — never to a service port directly.

Other frontend commands: `npm run build` (production build to `dist/`), `npm run lint`.

### Ports

| Service | Port | |
|---|---|---|
| eureka-server | 8761 | registry + dashboard |
| api-gateway | 8080 | **the only port the frontend uses** |
| inventory-service | 8081 | |
| transfer-service | 8082 | |
| auth-service | 8083 | |
| branch-service | 8084 | |
| alert-service | 8085 | |
| frontend (Vite) | 5173 | |

---

## 4. First-time application setup

Nothing can be created without a trusted source above it, so this order is required.

1. **Log in as the bootstrap admin.** On first start, auth-service creates one ADMIN if none exists — credentials come from `admin.bootstrap.*` in `auth-service/application.properties` (default `admin` / `ChangeMe@123`). Watch the auth-service log for the creation message. It is idempotent, so restarts don't duplicate it.
2. **Change that password immediately** (`PUT /api/auth/change-password`).
3. **Create at least three branches** as ADMIN (`/admin/branches`). Use the **same city** for two of them — city matching drives which branches get offered first.
4. **Generate a Warehouse Access Code** as ADMIN (`/admin/warehouse-codes`), if you want a warehouse user.
5. **Register users**, each needing proof it may exist:
   - `BRANCH_STAFF` / `INVENTORY_MANAGER` — need a real **Branch ID** from step 3
   - `WAREHOUSE_ADMIN` — needs the **code** from step 4 (single use)
   - `ADMIN` cannot be registered at all
6. **Add stock** as an INVENTORY_MANAGER (`/stock`). Give a future expiry date — expired batches are never offered.

> **Register staff into *different* branches.** Requests are matched on branch **id**, so several accounts sharing one branch all see the same lists and none of them can supply each other. The header shows `username · role · branch (#id)` so you can always confirm which branch you're acting as.

---

## 5. Verifying the flow works

1. As **branch A staff** → *Availability* → search a medicine another branch stocks. It should appear.
2. → *Raise Request* → pick a criticality → submit.
3. As **staff of a branch holding that stock** → *Incoming Requests*. The request should be listed, marked either **"Assigned to you"** (escalation currently points at you) or **"Open"**, with a **Your Stock** column showing whether you can supply it.
4. **Approve** → status becomes `CONFIRMED` and the quantity is deducted from **the approving branch's** stock.
5. **Idempotency:** approve the same request again → it must **not** deduct twice.
6. **Escalation:** raise a request nobody can fill and wait out `escalation.timeout-seconds` (default **30s**, in `transfer-service/application.properties`). It should re-target other branches and finally land in the WAREHOUSE_ADMIN's *Escalated* queue.

Low-stock alerts are generated by a scheduled job every `alert.check-interval-ms` (default **6 hours**) when `quantity / avgDailyConsumption <= alert.days-remaining-threshold` (default 3). Lower the interval if you want to see alerts during a demo.

---

## 6. Troubleshooting

**A new request immediately shows `ESCALATED_TO_WAREHOUSE` with no target branch.**
No branch could supply it. Either genuinely nobody has enough unexpired stock in a *single batch*, or the stock that exists points at a branch that no longer exists. Check for orphaned stock:

```sql
SELECT m.id, m.branch_id, b.branch_name AS resolves_to
FROM medicine m LEFT JOIN branch b ON b.id = m.branch_id;
-- resolves_to = NULL means the stock is attached to a deleted branch
```

**A request appears in nobody's Incoming Requests.**
It is targeted at a branch id that has no staff registered, or every staff account sits in the requesting branch (you never see your own branch's requests there — those live under *My Requests*).

**Beware duplicate tables.** `ddl-auto=update` never drops anything, so renamed entities left dead tables that still contain rows. The live ones are **`branch`**, **`medicine`**, **`app_users`**. The stale ones — `branches`, `medicines`, `users` — are ignored by the app but will happily send you down a false trail.

**403 on approve/reject/delete.** transfer-service enforces per-record rules using the `X-Auth-*` headers the gateway injects. Call through `:8080` with a JWT, not `:8082` directly. Also: a branch cannot approve or decline its own request, and only the requesting branch can delete one.

**409 "Another branch just acted on this request".** Two branches approved simultaneously; the second was rejected on purpose so stock isn't double-deducted. Refresh.

**Everything 401s.** `jwt.secret` differs between `auth-service` and `api-gateway`.

**Changes not taking effect.** `spring-boot:run` does not hot-reload. Restart the service — mandatory after editing an entity, `pom.xml`, or any `application.properties`.

**Port already in use.** A previous run is still alive: `netstat -ano | findstr :8082` then `taskkill /PID <pid> /F`.
