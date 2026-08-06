package com.medstock.inventory.controller;

import com.medstock.inventory.dto.CallerContext;
import com.medstock.inventory.dto.MedicineRequest;
import com.medstock.inventory.dto.MedicineResponse;
import com.medstock.inventory.service.MedicineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/medicines")
@RequiredArgsConstructor
public class MedicineController {

    /** Injected by the API gateway's AuthenticationFilter once it has validated the JWT. */
    private static final String AUTH_USERNAME_HEADER = "X-Auth-Username";
    private static final String AUTH_ROLE_HEADER = "X-Auth-Role";
    private static final String AUTH_BRANCH_ID_HEADER = "X-Auth-BranchId";

    private final MedicineService medicineService;

    @PostMapping
    public ResponseEntity<MedicineResponse> createMedicine(
            @Valid @RequestBody MedicineRequest request,
            @RequestHeader(value = AUTH_USERNAME_HEADER, required = false) String username,
            @RequestHeader(value = AUTH_ROLE_HEADER, required = false) String role,
            @RequestHeader(value = AUTH_BRANCH_ID_HEADER, required = false) String branchId) {
        CallerContext caller = CallerContext.fromHeaders(username, role, branchId);
        return new ResponseEntity<>(medicineService.createMedicine(request, caller), HttpStatus.CREATED);
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
    public ResponseEntity<MedicineResponse> updateMedicine(
            @PathVariable Long id,
            @Valid @RequestBody MedicineRequest request,
            @RequestHeader(value = AUTH_USERNAME_HEADER, required = false) String username,
            @RequestHeader(value = AUTH_ROLE_HEADER, required = false) String role,
            @RequestHeader(value = AUTH_BRANCH_ID_HEADER, required = false) String branchId) {
        CallerContext caller = CallerContext.fromHeaders(username, role, branchId);
        return ResponseEntity.ok(medicineService.updateMedicine(id, request, caller));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMedicine(
            @PathVariable Long id,
            @RequestHeader(value = AUTH_USERNAME_HEADER, required = false) String username,
            @RequestHeader(value = AUTH_ROLE_HEADER, required = false) String role,
            @RequestHeader(value = AUTH_BRANCH_ID_HEADER, required = false) String branchId) {
        medicineService.deleteMedicine(id, CallerContext.fromHeaders(username, role, branchId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<List<MedicineResponse>> getMedicinesByBranchId(@PathVariable Long branchId) {
        return ResponseEntity.ok(medicineService.getMedicinesByBranchId(branchId));
    }

    /**
     * {@code city} is optional and narrows results to branches in that city. It has to be declared
     * here to have any effect: Spring silently drops query parameters that are not bound to a
     * method argument, so the documented city filter was accepted and ignored, and every search
     * returned unfiltered nationwide results.
     */
    @GetMapping("/availability")
    public ResponseEntity<List<MedicineResponse>> checkAvailability(
            @RequestParam String medicineName,
            @RequestParam Integer requiredQuantity,
            @RequestParam(required = false) String city) {
        return ResponseEntity.ok(medicineService.checkAvailability(medicineName, requiredQuantity, city));
    }

    @PutMapping("/{id}/deduct")
    public ResponseEntity<MedicineResponse> deductStock(
            @PathVariable Long id,
            @RequestParam Integer quantity,
            @RequestHeader(value = AUTH_USERNAME_HEADER, required = false) String username,
            @RequestHeader(value = AUTH_ROLE_HEADER, required = false) String role,
            @RequestHeader(value = AUTH_BRANCH_ID_HEADER, required = false) String branchId) {
        CallerContext caller = CallerContext.fromHeaders(username, role, branchId);
        return ResponseEntity.ok(medicineService.deductStock(id, quantity, caller));
    }
}