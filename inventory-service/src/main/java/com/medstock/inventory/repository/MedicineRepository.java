package com.medstock.inventory.repository;

import com.medstock.inventory.entity.Medicine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface MedicineRepository extends JpaRepository<Medicine, Long> {

    List<Medicine> findByBranchId(Long branchId);

    List<Medicine> findByMedicineNameIgnoreCaseAndQuantityGreaterThanEqualAndExpiryDateAfterOrderByExpiryDateAsc(
            String medicineName, Integer quantity, LocalDate today);

    /**
     * Deducts stock in one conditional statement so the sufficiency check and the write cannot be
     * split by a concurrent transfer. Read-then-write let two approvals of different requests both
     * pass the same check and both deduct, overdrawing the batch; the {@code quantity >= :quantity}
     * predicate makes the database the single arbiter instead.
     *
     * <p>{@code updatedAt} is set here because JPA lifecycle callbacks such as {@code @PreUpdate}
     * do not run for bulk updates.
     *
     * @return 1 if the deduction was applied, 0 if the batch no longer holds enough stock
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Medicine m SET m.quantity = m.quantity - :quantity, m.updatedAt = :now "
            + "WHERE m.id = :id AND m.quantity >= :quantity")
    int deductIfSufficient(@Param("id") Long id,
                           @Param("quantity") Integer quantity,
                           @Param("now") LocalDateTime now);
}