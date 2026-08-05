package com.medstock.transfer.dto;

import com.medstock.transfer.entity.Criticality;
import com.medstock.transfer.entity.TransferStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponseDTO {

    private Long id;
    private String medicineName;
    private Integer quantity;
    private Long requestingBranchId;
    private String requestingBranchName;
    private String requestingBranchContact;
    private Long currentTargetBranchId;
    private String currentTargetBranchName;
    private String currentTargetBranchContact;
    private Criticality criticality;
    private TransferStatus status;
    private String attemptedBranchIds;
    private LocalDateTime lastAttemptedAt;
    private String remarks;
    private LocalDateTime requestedAt;
    private LocalDateTime updatedAt;
    private Long fulfilledByBranchId;
    private String fulfilledByBranchName;

    /**
     * Set only by the per-branch listing, since both depend on who is asking:
     * whether escalation currently points at the viewer, and whether the viewer holds enough
     * unexpired stock to approve right now.
     */
    private Boolean assignedToYou;
    private Boolean canFulfil;
}