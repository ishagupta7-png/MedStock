package com.medstock.inventory.service.impl;

import com.medstock.inventory.client.BranchClient;
import com.medstock.inventory.dto.BranchResponse;
import com.medstock.inventory.dto.CallerContext;
import com.medstock.inventory.dto.MedicineRequest;
import com.medstock.inventory.dto.MedicineResponse;
import com.medstock.inventory.entity.Medicine;
import com.medstock.inventory.exception.ForbiddenException;
import com.medstock.inventory.exception.InsufficientStockException;
import com.medstock.inventory.exception.ResourceNotFoundException;
import com.medstock.inventory.repository.MedicineRepository;
import com.medstock.inventory.service.MedicineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MedicineServiceImpl implements MedicineService {

    private final MedicineRepository medicineRepository;
    private final BranchClient branchClient;

    @Override
    public MedicineResponse createMedicine(MedicineRequest request, CallerContext caller) {
        requireOwnBranch(caller, request.getBranchId(), "add stock to");
        Medicine medicine = new Medicine();
        applyRequest(medicine, request);
        return toResponse(medicineRepository.save(medicine));
    }

    @Override
    public List<MedicineResponse> getAllMedicines() {
        return medicineRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public MedicineResponse getMedicineById(Long id) {
        return toResponse(findMedicineOrThrow(id));
    }

    @Override
    public MedicineResponse updateMedicine(Long id, MedicineRequest request, CallerContext caller) {
        Medicine medicine = findMedicineOrThrow(id);
        // Both ends have to be checked: the branch that currently holds the batch, so a manager
        // cannot edit another branch's stock, and the branch the edit moves it to, so the batch
        // cannot be handed to a branch the caller has no claim on either.
        requireOwnBranch(caller, medicine.getBranchId(), "edit stock held by");
        requireOwnBranch(caller, request.getBranchId(), "reassign stock to");
        applyRequest(medicine, request);
        return toResponse(medicineRepository.save(medicine));
    }

    @Override
    public void deleteMedicine(Long id, CallerContext caller) {
        Medicine medicine = findMedicineOrThrow(id);
        requireOwnBranch(caller, medicine.getBranchId(), "delete stock held by");
        medicineRepository.delete(medicine);
    }

    @Override
    public List<MedicineResponse> getMedicinesByBranchId(Long branchId) {
        return medicineRepository.findByBranchId(branchId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<MedicineResponse> checkAvailability(String medicineName, Integer requiredQuantity, String city) {
        if (requiredQuantity == null || requiredQuantity <= 0) {
            throw new IllegalArgumentException("requiredQuantity must be a positive number");
        }

        List<Medicine> batches = medicineRepository
                .findByMedicineNameIgnoreCaseAndQuantityGreaterThanEqualAndExpiryDateAfterOrderByExpiryDateAsc(
                        medicineName, requiredQuantity, LocalDate.now());

        if (StringUtils.hasText(city)) {
            // Inventory only records each batch's branchId, so the city has to be resolved into
            // branch ids through branch-service. An unknown city legitimately narrows the result
            // to nothing rather than falling back to the nationwide list.
            Set<Long> branchIdsInCity = branchClient.getBranchIdsInCity(city.trim());
            batches = batches.stream()
                    .filter(m -> branchIdsInCity.contains(m.getBranchId()))
                    .collect(Collectors.toList());
        }

        return batches.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Deducts from a single batch. The sufficiency check is left to the database as one
     * conditional update: reading the quantity and writing it back as two steps let two
     * concurrent transfer approvals of the same batch both pass the check and both deduct.
     */
    @Override
    @Transactional
    public MedicineResponse deductStock(Long id, Integer quantity, CallerContext caller) {
        // Without this, a negative quantity slipped past `quantity > available` (trivially false
        // for any negative number) and the subtraction became an addition - a free way to mint
        // stock that never existed.
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Deduct quantity must be a positive number");
        }

        Medicine medicine = findMedicineOrThrow(id);
        requireOwnBranch(caller, medicine.getBranchId(), "deduct stock held by");

        int updated = medicineRepository.deductIfSufficient(id, quantity, LocalDateTime.now());
        if (updated == 0) {
            // Either the batch never had enough, or a concurrent deduction got there first.
            Medicine current = findMedicineOrThrow(id);
            throw new InsufficientStockException(
                    "Insufficient stock for medicine id: " + id + ". Available: " + current.getQuantity()
                            + ", requested: " + quantity);
        }

        return toResponse(findMedicineOrThrow(id));
    }

    /**
     * Stock is branch-owned: a caller tied to a branch may only touch that branch's rows. ADMIN
     * carries no branch of its own and is trusted across branches. Previously nothing downstream
     * of the gateway checked this at all, so an INVENTORY_MANAGER at one branch could edit or
     * drain another branch's stock directly.
     */
    private void requireOwnBranch(CallerContext caller, Long branchId, String action) {
        if (caller == null || caller.isInternal() || caller.isAdmin() || branchId == null) {
            return;
        }
        if (caller.belongsToBranch(branchId)) {
            return;
        }
        log.warn("Blocked attempt by user '{}' (role={}, branch={}) to {} branch {}",
                caller.username(), caller.role(), caller.branchId(), action, branchId);
        throw new ForbiddenException("You can only manage stock belonging to your own branch");
    }

    private void applyRequest(Medicine medicine, MedicineRequest request) {
        if (request.getBranchId() == null) {
            throw new IllegalArgumentException("branchId is required");
        }

        // Resolve the branch instead of trusting the caller: an unvalidated branchId let stock be
        // attached to a branch that does not exist, which made every transfer request offering
        // that stock invisible - no branch could claim it and no user could approve it.
        // branchName is taken from branch-service for the same reason; clients have sent the
        // wrong value (the username), and that stale name propagates into transfer requests.
        BranchResponse branch = branchClient.getExistingBranch(request.getBranchId());
        medicine.setBranchId(branch.getId());
        medicine.setBranchName(branch.getBranchName());

        medicine.setMedicineName(request.getMedicineName());
        medicine.setBatchNumber(request.getBatchNumber());
        medicine.setQuantity(request.getQuantity());
        medicine.setUnitPrice(request.getUnitPrice());
        medicine.setExpiryDate(request.getExpiryDate());
        medicine.setAvgDailyConsumption(request.getAvgDailyConsumption());
    }

    private Medicine findMedicineOrThrow(Long id) {
        return medicineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine not found with id: " + id));
    }

    private MedicineResponse toResponse(Medicine medicine) {
        return new MedicineResponse(
                medicine.getId(),
                medicine.getMedicineName(),
                medicine.getBatchNumber(),
                medicine.getBranchId(),
                medicine.getBranchName(),
                medicine.getQuantity(),
                medicine.getUnitPrice(),
                medicine.getExpiryDate(),
                medicine.getAvgDailyConsumption(),
                medicine.getCreatedAt(),
                medicine.getUpdatedAt());
    }
}