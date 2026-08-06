package com.medstock.inventory.service;

import com.medstock.inventory.dto.CallerContext;
import com.medstock.inventory.dto.MedicineRequest;
import com.medstock.inventory.dto.MedicineResponse;

import java.util.List;

public interface MedicineService {

    MedicineResponse createMedicine(MedicineRequest request, CallerContext caller);

    List<MedicineResponse> getAllMedicines();

    MedicineResponse getMedicineById(Long id);

    MedicineResponse updateMedicine(Long id, MedicineRequest request, CallerContext caller);

    void deleteMedicine(Long id, CallerContext caller);

    List<MedicineResponse> getMedicinesByBranchId(Long branchId);

    /**
     * Unexpired batches of a medicine that can cover the requested quantity on their own,
     * earliest-expiring first (FEFO).
     *
     * @param city optional - when set, only batches held by branches in that city are returned,
     *             which is what makes "find it at a nearby branch" mean anything
     */
    List<MedicineResponse> checkAvailability(String medicineName, Integer requiredQuantity, String city);

    MedicineResponse deductStock(Long id, Integer quantity, CallerContext caller);
}