package com.medstock.inventory.service.impl;

import com.medstock.inventory.client.BranchClient;
import com.medstock.inventory.dto.BranchResponse;
import com.medstock.inventory.dto.MedicineRequest;
import com.medstock.inventory.dto.MedicineResponse;
import com.medstock.inventory.entity.Medicine;
import com.medstock.inventory.exception.InsufficientStockException;
import com.medstock.inventory.exception.ResourceNotFoundException;
import com.medstock.inventory.repository.MedicineRepository;
import com.medstock.inventory.service.MedicineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicineServiceImpl implements MedicineService {

    private final MedicineRepository medicineRepository;
    private final BranchClient branchClient;

    @Override
    public MedicineResponse createMedicine(MedicineRequest request) {
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
    public MedicineResponse updateMedicine(Long id, MedicineRequest request) {
        Medicine medicine = findMedicineOrThrow(id);
        applyRequest(medicine, request);
        return toResponse(medicineRepository.save(medicine));
    }

    @Override
    public void deleteMedicine(Long id) {
        medicineRepository.delete(findMedicineOrThrow(id));
    }

    @Override
    public List<MedicineResponse> getMedicinesByBranchId(Long branchId) {
        return medicineRepository.findByBranchId(branchId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<MedicineResponse> checkAvailability(String medicineName, Integer requiredQuantity) {
        return medicineRepository
                .findByMedicineNameIgnoreCaseAndQuantityGreaterThanEqualAndExpiryDateAfterOrderByExpiryDateAsc(
                        medicineName, requiredQuantity, LocalDate.now())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public MedicineResponse deductStock(Long id, Integer quantity) {
        Medicine medicine = findMedicineOrThrow(id);
        if (quantity > medicine.getQuantity()) {
            throw new InsufficientStockException(
                    "Insufficient stock for medicine id: " + id + ". Available: " + medicine.getQuantity()
                            + ", requested: " + quantity);
        }
        medicine.setQuantity(medicine.getQuantity() - quantity);
        return toResponse(medicineRepository.save(medicine));
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