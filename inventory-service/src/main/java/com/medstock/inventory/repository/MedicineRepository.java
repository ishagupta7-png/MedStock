package com.medstock.inventory.repository;

import com.medstock.inventory.entity.Medicine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MedicineRepository extends JpaRepository<Medicine, Long> {

    List<Medicine> findByBranchId(Long branchId);

    List<Medicine> findByMedicineNameIgnoreCaseAndQuantityGreaterThanEqualAndExpiryDateAfterOrderByExpiryDateAsc(
            String medicineName, Integer quantity, LocalDate today);
}