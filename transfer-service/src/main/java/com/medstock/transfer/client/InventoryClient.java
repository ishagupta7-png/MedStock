package com.medstock.transfer.client;

import com.medstock.transfer.dto.MedicineAvailabilityResponse;
import com.medstock.transfer.exception.InsufficientStockException;
import com.medstock.transfer.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryClient {

    private final RestTemplate restTemplate;

    private static final String AVAILABILITY_URL =
            "http://inventory-service/api/inventory/medicines/availability?medicineName={medicineName}&requiredQuantity={requiredQuantity}";

    private static final String BRANCH_STOCK_URL =
            "http://inventory-service/api/inventory/medicines/branch/{branchId}";

    private static final String DEDUCT_URL =
            "http://inventory-service/api/inventory/medicines/{id}/deduct?quantity={quantity}";

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackAvailability")
    public List<MedicineAvailabilityResponse> checkAvailability(String medicineName, Integer requiredQuantity) {
        MedicineAvailabilityResponse[] response = restTemplate.getForObject(
                AVAILABILITY_URL, MedicineAvailabilityResponse[].class, medicineName, requiredQuantity);
        return response != null ? Arrays.asList(response) : Collections.emptyList();
    }

    /**
     * Must not swallow this into "no stock found" - callers rely on an empty list meaning
     * genuinely no branch has stock (which is a valid reason to escalate straight to the
     * warehouse). If inventory-service is merely unreachable, silently returning empty here
     * caused every request to prematurely and permanently escalate without ever reaching a
     * branch's incoming queue.
     */
    public List<MedicineAvailabilityResponse> fallbackAvailability(String medicineName, Integer requiredQuantity, Throwable t) {
        log.warn("Inventory service unavailable while checking availability for medicine {}", medicineName, t);
        throw new ServiceUnavailableException(
                "Inventory service is temporarily unavailable while checking availability for " + medicineName);
    }

    /**
     * Used at approval time - unlike checkAvailability, unavailability here must not be
     * silently swallowed into "no stock", since that misleads the caller into thinking the
     * transfer is unfulfillable when the real problem is that inventory-service is down.
     *
     * <p>Batch selection deliberately mirrors the availability search: only unexpired batches
     * that can cover the request on their own, earliest-expiring first (FEFO). Picking an
     * arbitrary batch by name alone reported INSUFFICIENT_STOCK whenever the branch also held a
     * smaller or already-expired batch of the same medicine, and could deduct from a
     * later-expiring batch than the one that was actually offered.
     */
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackStockAtBranch")
    public Optional<MedicineAvailabilityResponse> getStockAtBranch(
            Long branchId, String medicineName, Integer requiredQuantity) {
        MedicineAvailabilityResponse[] response =
                restTemplate.getForObject(BRANCH_STOCK_URL, MedicineAvailabilityResponse[].class, branchId);
        LocalDate today = LocalDate.now();
        return Arrays.stream(response != null ? response : new MedicineAvailabilityResponse[0])
                .filter(m -> medicineName.equalsIgnoreCase(m.getMedicineName()))
                .filter(m -> m.getQuantity() != null && m.getQuantity() >= requiredQuantity)
                .filter(m -> m.getExpiryDate() != null && m.getExpiryDate().isAfter(today))
                .min(Comparator.comparing(MedicineAvailabilityResponse::getExpiryDate));
    }

    public Optional<MedicineAvailabilityResponse> fallbackStockAtBranch(
            Long branchId, String medicineName, Integer requiredQuantity, Throwable t) {
        log.warn("Inventory service unavailable while reading stock for branch {}", branchId, t);
        throw new ServiceUnavailableException(
                "Inventory service is temporarily unavailable, please retry this approval shortly");
    }

    /**
     * A branch's full stock, fetched once so a list of requests can be annotated with "can this
     * branch actually supply it" locally, instead of one inventory call per request.
     */
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackBranchStock")
    public List<MedicineAvailabilityResponse> getBranchStock(Long branchId) {
        MedicineAvailabilityResponse[] response =
                restTemplate.getForObject(BRANCH_STOCK_URL, MedicineAvailabilityResponse[].class, branchId);
        return response != null ? Arrays.asList(response) : Collections.emptyList();
    }

    /**
     * Degrades to "cannot confirm any stock" rather than failing the whole listing - the request
     * list is still useful without the can-fulfil hints, and approval re-checks stock anyway.
     */
    public List<MedicineAvailabilityResponse> fallbackBranchStock(Long branchId, Throwable t) {
        log.warn("Inventory service unavailable while listing stock for branch {}, "
                + "returning no fulfilment hints", branchId, t);
        return Collections.emptyList();
    }

    public void deductStock(Long medicineId, Integer quantity) {
        try {
            restTemplate.put(DEDUCT_URL, null, medicineId, quantity);
        } catch (HttpClientErrorException ex) {
            // inventory-service reached us and actively refused the deduction (e.g. a concurrent
            // transfer drained the batch first). Retrying will not help, so this must not be
            // reported as a transient "service unavailable".
            log.warn("Inventory service rejected stock deduction for medicine {} with status {}",
                    medicineId, ex.getStatusCode(), ex);
            throw new InsufficientStockException(
                    "Inventory service rejected the stock deduction for this transfer - current stock may have "
                            + "changed, please re-check availability");
        } catch (RestClientException ex) {
            log.warn("Failed to deduct stock for medicine {}", medicineId, ex);
            throw new ServiceUnavailableException(
                    "Inventory service is temporarily unavailable, stock was not deducted - please retry this approval");
        }
    }
}