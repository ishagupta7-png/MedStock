package com.medstock.inventory.service;

import com.medstock.inventory.dto.MedicineRequest;
import com.medstock.inventory.dto.MedicineResponse;

import java.util.List;

public interface MedicineService {

    MedicineResponse createMedicine(MedicineRequest request);

    List<MedicineResponse> getAllMedicines();

    MedicineResponse getMedicineById(Long id);

    MedicineResponse updateMedicine(Long id, MedicineRequest request);

    void deleteMedicine(Long id);

    List<MedicineResponse> getMedicinesByBranchId(Long branchId);

    List<MedicineResponse> checkAvailability(String medicineName, Integer requiredQuantity);

    MedicineResponse deductStock(Long id, Integer quantity);
}