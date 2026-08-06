package com.medstock.auth.repository;

import com.medstock.auth.entity.WarehouseAccessCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WarehouseAccessCodeRepository extends JpaRepository<WarehouseAccessCode, Long> {

    Optional<WarehouseAccessCode> findByCode(String code);

    /**
     * Claims a code for one user in a single conditional statement, so the "is it still unused?"
     * check cannot be separated from the write that consumes it.
     *
     * <p>Read-then-write let concurrent registrations all observe {@code isUsed = false} before
     * any of them committed: five simultaneous registrations with the same code all succeeded and
     * created five WAREHOUSE_ADMIN accounts, while the row only recorded the last writer. The
     * {@code isUsed = false} predicate makes the database decide the winner, and the affected-row
     * count tells the loser it lost.
     *
     * @return 1 if this caller claimed the code, 0 if it did not exist or was already used
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE WarehouseAccessCode c SET c.isUsed = true, c.assignedToUsername = :username "
            + "WHERE c.code = :code AND c.isUsed = false")
    int claimCode(@Param("code") String code, @Param("username") String username);
}