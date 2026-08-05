package com.medstock.transfer.service.impl;

import com.medstock.transfer.client.BranchClient;
import com.medstock.transfer.client.InventoryClient;
import com.medstock.transfer.dto.BranchResponse;
import com.medstock.transfer.dto.CallerContext;
import com.medstock.transfer.dto.MedicineAvailabilityResponse;
import com.medstock.transfer.dto.TransferRequestDTO;
import com.medstock.transfer.dto.TransferResponseDTO;
import com.medstock.transfer.entity.Criticality;
import com.medstock.transfer.entity.TransferRequest;
import com.medstock.transfer.entity.TransferStatus;
import com.medstock.transfer.exception.ForbiddenException;
import com.medstock.transfer.exception.InsufficientStockException;
import com.medstock.transfer.exception.ResourceNotFoundException;
import com.medstock.transfer.exception.ServiceUnavailableException;
import com.medstock.transfer.repository.TransferRequestRepository;
import com.medstock.transfer.service.TransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferServiceImpl implements TransferService {

    private final TransferRequestRepository transferRequestRepository;
    private final InventoryClient inventoryClient;
    private final BranchClient branchClient;

    @Value("${escalation.timeout-seconds}")
    private long escalationTimeoutSeconds;

    @Override
    public TransferResponseDTO createRequest(TransferRequestDTO dto) {
        if (dto.getCriticality() == null) {
            throw new IllegalArgumentException("Criticality is required");
        }
        if (!StringUtils.hasText(dto.getMedicineName())) {
            throw new IllegalArgumentException("medicineName is required");
        }
        if (dto.getQuantity() == null || dto.getQuantity() <= 0) {
            throw new IllegalArgumentException("quantity must be a positive number");
        }
        if (dto.getRequestingBranchId() == null) {
            throw new IllegalArgumentException("requestingBranchId is required");
        }

        TransferRequest request = new TransferRequest();
        request.setMedicineName(dto.getMedicineName());
        request.setQuantity(dto.getQuantity());
        request.setRequestingBranchId(dto.getRequestingBranchId());
        request.setRequestingBranchName(dto.getRequestingBranchName());
        request.setCriticality(dto.getCriticality());
        request.setAttemptedBranchIds("");
        request.setRemarks(dto.getRemarks());

        List<MedicineAvailabilityResponse> sorted;
        try {
            String requestingCity = resolveCity(dto.getRequestingBranchId());
            List<MedicineAvailabilityResponse> availability =
                    inventoryClient.checkAvailability(dto.getMedicineName(), dto.getQuantity()).stream()
                            .filter(m -> !dto.getRequestingBranchId().equals(m.getBranchId()))
                            .collect(Collectors.toList());
            sorted = sortByCityPreference(availability, requestingCity);
        } catch (ServiceUnavailableException ex) {
            log.warn("Inventory service unavailable while creating request for medicine={}, requestingBranch={} - "
                            + "leaving request PENDING with no target; escalation scheduler will retry shortly",
                    dto.getMedicineName(), dto.getRequestingBranchId());
            request.setCurrentTargetBranchId(null);
            request.setCurrentTargetBranchName(null);
            request.setStatus(TransferStatus.PENDING);
            request.setLastAttemptedAt(LocalDateTime.now());
            return toResponse(transferRequestRepository.save(request));
        }

        if (sorted.isEmpty()) {
            request.setCurrentTargetBranchId(null);
            request.setCurrentTargetBranchName(null);
            request.setStatus(TransferStatus.ESCALATED_TO_WAREHOUSE);
            request.setLastAttemptedAt(LocalDateTime.now());
            log.warn("Request created with no available branch stock - escalated directly to Central Warehouse: "
                            + "Medicine={}, Quantity={}, Requesting Branch={}",
                    dto.getMedicineName(), dto.getQuantity(), dto.getRequestingBranchId());
        } else {
            MedicineAvailabilityResponse target;
            if (dto.getTargetBranchId() != null) {
                target = sorted.stream()
                        .filter(m -> dto.getTargetBranchId().equals(m.getBranchId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Selected target branch does not have sufficient stock of this medicine"));
            } else {
                target = sorted.get(0);
            }

            request.setCurrentTargetBranchId(target.getBranchId());
            request.setCurrentTargetBranchName(target.getBranchName());
            request.setStatus(TransferStatus.PENDING);
            request.setLastAttemptedAt(LocalDateTime.now());
        }

        return toResponse(transferRequestRepository.save(request));
    }

    @Override
    public List<TransferResponseDTO> getAllRequests() {
        return sortByQueueOrder(transferRequestRepository.findAll());
    }

    @Override
    public List<TransferResponseDTO> getRequestsByStatus(TransferStatus status) {
        return sortByQueueOrder(transferRequestRepository.findByStatus(status));
    }

    @Override
    public TransferResponseDTO getRequestById(Long id) {
        return toResponse(findOrThrow(id));
    }

    /**
     * Any branch holding the stock may approve - not only the branch escalation currently points
     * at - so the supplying branch is whoever approved, and the deduction comes out of their
     * stock. The requesting branch cannot approve its own request.
     */
    @Override
    @Transactional
    public TransferResponseDTO approveTransfer(Long id, CallerContext caller) {
        TransferRequest request = findOrThrow(id);
        Long supplyingBranchId = resolveSupplyingBranch(request, caller);

        if (request.getStatus() == TransferStatus.CONFIRMED) {
            return toResponse(request);
        }
        if (request.getStatus() != TransferStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot approve a request with status " + request.getStatus());
        }

        MedicineAvailabilityResponse batch = inventoryClient
                .getStockAtBranch(supplyingBranchId, request.getMedicineName(), request.getQuantity())
                .orElse(null);

        if (batch == null) {
            throw new InsufficientStockException(
                    "Branch " + supplyingBranchId + " does not have enough unexpired stock of "
                            + request.getMedicineName() + " in a single batch to fulfil this request");
        }

        inventoryClient.deductStock(batch.getId(), request.getQuantity());

        request.setFulfilledByBranchId(supplyingBranchId);
        request.setFulfilledByBranchName(resolveBranchName(supplyingBranchId));
        request.setStatus(TransferStatus.CONFIRMED);
        log.info("Request {} confirmed - supplied by branch {} (escalation target was {})",
                request.getId(), supplyingBranchId, request.getCurrentTargetBranchId());
        return toResponse(transferRequestRepository.save(request));
    }

    /**
     * Which branch's stock this approval draws from. ADMIN has no branch of its own, so it can
     * only rubber-stamp the branch escalation currently points at.
     */
    private Long resolveSupplyingBranch(TransferRequest request, CallerContext caller) {
        if (caller.branchId() != null) {
            if (caller.branchId().equals(request.getRequestingBranchId())) {
                throw new ForbiddenException("A branch cannot approve its own transfer request");
            }
            return caller.branchId();
        }

        if (caller.isAdmin()) {
            if (request.getCurrentTargetBranchId() == null) {
                throw new IllegalStateException(
                        "This request has no target branch assigned yet - it is waiting for the next escalation cycle");
            }
            return request.getCurrentTargetBranchId();
        }

        throw new ForbiddenException("Only staff belonging to a branch can approve a transfer request");
    }

    /**
     * Declining is per branch: the declining branch is recorded so the request is never offered
     * to it again. Only a decline from the branch escalation currently points at moves the
     * request on - an uninvolved branch opting out must not cut short another branch's response
     * window, and must not strand the request either.
     */
    @Override
    public TransferResponseDTO rejectTransfer(Long id, String remarks, CallerContext caller) {
        TransferRequest request = findOrThrow(id);
        Long decliningBranchId = resolveDecliningBranch(request, caller);

        if (request.getStatus() != TransferStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot reject a request with status " + request.getStatus());
        }
        if (StringUtils.hasText(remarks)) {
            request.setRemarks(remarks);
        }

        if (decliningBranchId.equals(request.getCurrentTargetBranchId())) {
            log.info("Request {} declined by its current target branch {} - advancing",
                    request.getId(), decliningBranchId);
            advanceToNextBranch(request);
        } else {
            Set<Long> attempted = parseAttemptedBranchIds(request.getAttemptedBranchIds());
            attempted.add(decliningBranchId);
            request.setAttemptedBranchIds(joinAttemptedBranchIds(attempted));
            log.info("Request {} declined by branch {} - recorded; escalation target {} unchanged",
                    request.getId(), decliningBranchId, request.getCurrentTargetBranchId());
        }

        return toResponse(transferRequestRepository.save(request));
    }

    private Long resolveDecliningBranch(TransferRequest request, CallerContext caller) {
        if (caller.branchId() != null) {
            if (caller.branchId().equals(request.getRequestingBranchId())) {
                throw new ForbiddenException(
                        "A branch cannot decline its own request - delete it instead");
            }
            return caller.branchId();
        }

        if (caller.isAdmin() && request.getCurrentTargetBranchId() != null) {
            return request.getCurrentTargetBranchId();
        }

        throw new ForbiddenException("Only staff belonging to a branch can decline a transfer request");
    }

    /**
     * Really removes the request rather than parking it in CANCELLED. A soft cancel left the row
     * in the requesting branch's list forever, so "Delete" looked like it had done nothing.
     * A CONFIRMED request is still refused - stock has already moved between branches and that
     * record has to survive.
     */
    @Override
    public void cancelTransfer(Long id, CallerContext caller) {
        TransferRequest request = findOrThrow(id);
        requireRequestingBranchOwnership(request, caller);

        if (request.getStatus() == TransferStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Cannot delete a CONFIRMED request - stock has already been transferred");
        }

        transferRequestRepository.delete(request);
        log.info("Request {} deleted by branch {} (was {})",
                request.getId(), request.getRequestingBranchId(), request.getStatus());
    }

    @Override
    public List<TransferResponseDTO> getRequestsByBranch(Long branchId) {
        return sortByQueueOrder(
                transferRequestRepository.findByRequestingBranchIdOrCurrentTargetBranchId(branchId, branchId));
    }

    @Override
    public List<TransferResponseDTO> getOpenRequestsForBranch(Long branchId) {
        if (branchId == null) {
            return List.of();
        }

        List<TransferRequest> open =
                transferRequestRepository.findByStatusAndRequestingBranchIdNot(TransferStatus.PENDING, branchId)
                        .stream()
                        // A branch that already declined (or was tried and timed out) should not be
                        // asked again - that is what keeps escalation from looping.
                        .filter(r -> !parseAttemptedBranchIds(r.getAttemptedBranchIds()).contains(branchId))
                        .collect(Collectors.toList());

        if (open.isEmpty()) {
            return List.of();
        }

        // One inventory call for this branch's whole stock, then match locally - far cheaper than
        // asking per request, and it keeps the listing responsive (a stated NFR).
        Map<String, Integer> stockByMedicine = usableStockByMedicine(branchId);

        List<TransferResponseDTO> responses = sortByQueueOrder(open);
        for (TransferResponseDTO response : responses) {
            Integer available = response.getMedicineName() == null
                    ? null
                    : stockByMedicine.get(response.getMedicineName().toLowerCase());
            response.setCanFulfil(
                    available != null && response.getQuantity() != null && available >= response.getQuantity());
            response.setAssignedToYou(branchId.equals(response.getCurrentTargetBranchId()));
        }
        return responses;
    }

    /**
     * Largest unexpired batch per medicine at a branch. Deliberately the max of individual
     * batches rather than their sum, because a transfer is fulfilled from a single batch - summing
     * would advertise stock that approval then refuses.
     */
    private Map<String, Integer> usableStockByMedicine(Long branchId) {
        LocalDate today = LocalDate.now();
        Map<String, Integer> largestBatch = new HashMap<>();
        for (MedicineAvailabilityResponse medicine : inventoryClient.getBranchStock(branchId)) {
            if (medicine.getMedicineName() == null || medicine.getQuantity() == null
                    || medicine.getExpiryDate() == null || !medicine.getExpiryDate().isAfter(today)) {
                continue;
            }
            largestBatch.merge(
                    medicine.getMedicineName().toLowerCase(), medicine.getQuantity(), Integer::max);
        }
        return largestBatch;
    }

    @Override
    public void escalateOverdueRequests() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(escalationTimeoutSeconds);
        List<TransferRequest> overdue =
                transferRequestRepository.findByStatusAndLastAttemptedAtBefore(TransferStatus.PENDING, cutoff);

        for (TransferRequest request : overdue) {
            escalateSingleRequest(request);
        }
    }

    private void escalateSingleRequest(TransferRequest request) {
        log.info("Request {} timed out at branch {} after {}s - advancing to next available branch",
                request.getId(), request.getCurrentTargetBranchId(), escalationTimeoutSeconds);
        try {
            advanceToNextBranch(request);
        } catch (ServiceUnavailableException ex) {
            log.warn("Inventory service unavailable while escalating request {} - will retry on next scheduled run",
                    request.getId());
            return;
        }
        transferRequestRepository.save(request);
    }

    /**
     * Moves a request on from its current target branch: records that branch as attempted, then
     * re-targets the next branch that has stock and has not been tried yet (city-preferred, FEFO
     * order), or escalates to the central warehouse once the candidates are exhausted. Shared by
     * the timeout-driven scheduler and by explicit rejections so both follow one path.
     *
     * <p>Does not persist - the caller saves, so that a failure to reach inventory-service leaves
     * the request untouched and retryable instead of wrongly escalated.
     *
     * @throws ServiceUnavailableException if inventory-service cannot be reached
     */
    private void advanceToNextBranch(TransferRequest request) {
        Set<Long> attempted = parseAttemptedBranchIds(request.getAttemptedBranchIds());
        if (request.getCurrentTargetBranchId() != null) {
            attempted.add(request.getCurrentTargetBranchId());
        }

        String requestingCity = resolveCity(request.getRequestingBranchId());
        List<MedicineAvailabilityResponse> availability =
                inventoryClient.checkAvailability(request.getMedicineName(), request.getQuantity()).stream()
                        .filter(m -> !request.getRequestingBranchId().equals(m.getBranchId()))
                        .collect(Collectors.toList());
        List<MedicineAvailabilityResponse> sorted = sortByCityPreference(availability, requestingCity);

        Optional<MedicineAvailabilityResponse> next = sorted.stream()
                .filter(m -> !attempted.contains(m.getBranchId()))
                .findFirst();

        request.setAttemptedBranchIds(joinAttemptedBranchIds(attempted));

        if (next.isPresent()) {
            MedicineAvailabilityResponse chosen = next.get();
            Long oldBranchId = request.getCurrentTargetBranchId();
            request.setCurrentTargetBranchId(chosen.getBranchId());
            request.setCurrentTargetBranchName(chosen.getBranchName());
            request.setStatus(TransferStatus.PENDING);
            request.setLastAttemptedAt(LocalDateTime.now());
            log.info("Request {} re-targeted from branch {} to branch {}",
                    request.getId(), oldBranchId, chosen.getBranchId());
        } else {
            request.setCurrentTargetBranchId(null);
            request.setCurrentTargetBranchName(null);
            request.setStatus(TransferStatus.ESCALATED_TO_WAREHOUSE);
            log.info("Request {} escalated to Central Warehouse - all branches with stock exhausted (attempted: {})",
                    request.getId(), request.getAttemptedBranchIds());
        }
    }

    private String resolveBranchName(Long branchId) {
        BranchResponse branch = branchClient.getBranch(branchId);
        return branch != null ? branch.getBranchName() : null;
    }

    private void requireRequestingBranchOwnership(TransferRequest request, CallerContext caller) {
        if (caller.isAdmin() || caller.belongsToBranch(request.getRequestingBranchId())) {
            return;
        }
        log.warn("Blocked attempt by user '{}' (role={}, branch={}) to delete request {} raised by branch {}",
                caller.username(), caller.role(), caller.branchId(), request.getId(),
                request.getRequestingBranchId());
        throw new ForbiddenException("Only the branch that raised this request can delete it");
    }

    private String resolveCity(Long branchId) {
        BranchResponse branch = branchClient.getBranch(branchId);
        return branch != null ? branch.getCity() : null;
    }

    private List<MedicineAvailabilityResponse> sortByCityPreference(
            List<MedicineAvailabilityResponse> availability, String preferredCity) {
        if (!StringUtils.hasText(preferredCity)) {
            return availability;
        }

        Set<Long> cityBranchIds = branchClient.getBranchesByCity(preferredCity).stream()
                .map(BranchResponse::getId)
                .collect(Collectors.toSet());
        if (cityBranchIds.isEmpty()) {
            return availability;
        }

        List<MedicineAvailabilityResponse> sameCity = availability.stream()
                .filter(m -> cityBranchIds.contains(m.getBranchId()))
                .collect(Collectors.toList());
        List<MedicineAvailabilityResponse> otherCity = availability.stream()
                .filter(m -> !cityBranchIds.contains(m.getBranchId()))
                .collect(Collectors.toList());

        List<MedicineAvailabilityResponse> result = new ArrayList<>(sameCity);
        result.addAll(otherCity);
        return result;
    }

    private Set<Long> parseAttemptedBranchIds(String attemptedBranchIds) {
        if (!StringUtils.hasText(attemptedBranchIds)) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(attemptedBranchIds.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(Long::parseLong)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String joinAttemptedBranchIds(Set<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private int criticalityOrder(Criticality criticality) {
        // Rows persisted before criticality became mandatory can still hold null - sort them
        // last rather than letting a switch NPE take down the whole queue listing.
        if (criticality == null) {
            return Integer.MAX_VALUE;
        }
        return switch (criticality) {
            case CRITICAL -> 1;
            case URGENT -> 2;
            case ROUTINE -> 3;
        };
    }

    private List<TransferResponseDTO> sortByQueueOrder(List<TransferRequest> requests) {
        Map<Long, BranchResponse> branchCache = new HashMap<>();
        return requests.stream()
                .sorted(Comparator
                        .comparing((TransferRequest r) -> criticalityOrder(r.getCriticality()))
                        .thenComparing(TransferRequest::getRequestedAt))
                .map(r -> toResponse(r, branchCache))
                .collect(Collectors.toList());
    }

    private TransferRequest findOrThrow(Long id) {
        return transferRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer request not found with id: " + id));
    }

    private TransferResponseDTO toResponse(TransferRequest request) {
        return toResponse(request, new HashMap<>());
    }

    private TransferResponseDTO toResponse(TransferRequest request, Map<Long, BranchResponse> branchCache) {
        return new TransferResponseDTO(
                request.getId(),
                request.getMedicineName(),
                request.getQuantity(),
                request.getRequestingBranchId(),
                request.getRequestingBranchName(),
                resolveContact(request.getRequestingBranchId(), branchCache),
                request.getCurrentTargetBranchId(),
                request.getCurrentTargetBranchName(),
                resolveContact(request.getCurrentTargetBranchId(), branchCache),
                request.getCriticality(),
                request.getStatus(),
                request.getAttemptedBranchIds(),
                request.getLastAttemptedAt(),
                request.getRemarks(),
                request.getRequestedAt(),
                request.getUpdatedAt(),
                request.getFulfilledByBranchId(),
                request.getFulfilledByBranchName(),
                // per-viewer hints; only the per-branch listing knows who is asking
                null,
                null);
    }

    private String resolveContact(Long branchId, Map<Long, BranchResponse> branchCache) {
        if (branchId == null) {
            return null;
        }
        BranchResponse branch = branchCache.computeIfAbsent(branchId, branchClient::getBranch);
        return branch != null ? branch.getContactNumber() : null;
    }
}