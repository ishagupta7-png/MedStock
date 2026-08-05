package com.medstock.inventory.controller;

import com.medstock.inventory.dto.MedicineRequest;
import com.medstock.inventory.dto.MedicineResponse;
import com.medstock.inventory.service.MedicineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/medicines")
@RequiredArgsConstructor
public class MedicineController {

    private final MedicineService medicineService;

    @PostMapping
    public ResponseEntity<MedicineResponse> createMedicine(@RequestBody MedicineRequest request) {
        return new ResponseEntity<>(medicineService.createMedicine(request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<MedicineResponse>> getAllMedicines() {
        return ResponseEntity.ok(medicineService.getAllMedicines());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MedicineResponse> getMedicineById(@PathVariable Long id) {
        return ResponseEntity.ok(medicineService.getMedicineById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MedicineResponse> updateMedicine(@PathVariable Long id, @RequestBody MedicineRequest request) {
        return ResponseEntity.ok(medicineService.updateMedicine(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMedicine(@PathVariable Long id) {
        medicineService.deleteMedicine(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<List<MedicineResponse>> getMedicinesByBranchId(@PathVariable Long branchId) {
        return ResponseEntity.ok(medicineService.getMedicinesByBranchId(branchId));
    }

    @GetMapping("/availability")
    public ResponseEntity<List<MedicineResponse>> checkAvailability(
            @RequestParam String medicineName,
            @RequestParam Integer requiredQuantity) {
        return ResponseEntity.ok(medicineService.checkAvailability(medicineName, requiredQuantity));
    }

    @PutMapping("/{id}/deduct")
    public ResponseEntity<MedicineResponse> deductStock(@PathVariable Long id, @RequestParam Integer quantity) {
        return ResponseEntity.ok(medicineService.deductStock(id, quantity));
    }
}