package com.medstock.transfer.repository;

import com.medstock.transfer.entity.TransferRequest;
import com.medstock.transfer.entity.TransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TransferRequestRepository extends JpaRepository<TransferRequest, Long> {

    List<TransferRequest> findByStatus(TransferStatus status);

    /** Requests another branch raised that this branch could still supply. */
    List<TransferRequest> findByStatusAndRequestingBranchIdNot(TransferStatus status, Long requestingBranchId);

    List<TransferRequest> findByRequestingBranchIdOrCurrentTargetBranchId(Long requestingBranchId, Long currentTargetBranchId);

    /** Requests this branch raised itself - the outgoing direction only. */
    List<TransferRequest> findByRequestingBranchId(Long requestingBranchId);

    List<TransferRequest> findByStatusAndLastAttemptedAtBefore(TransferStatus status, LocalDateTime cutoff);
}