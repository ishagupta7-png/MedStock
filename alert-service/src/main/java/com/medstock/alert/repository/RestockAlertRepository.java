package com.medstock.alert.repository;

import com.medstock.alert.entity.RestockAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RestockAlertRepository extends JpaRepository<RestockAlert, Long> {

    Optional<RestockAlert> findByMedicineIdAndBranchIdAndResolvedFalse(Long medicineId, Long branchId);

    List<RestockAlert> findByBranchId(Long branchId);
}