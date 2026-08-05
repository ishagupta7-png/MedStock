package com.medstock.transfer.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String medicineName;

    private Integer quantity;

    private Long requestingBranchId;

    private String requestingBranchName;

    private Long currentTargetBranchId;

    private String currentTargetBranchName;

    @Enumerated(EnumType.STRING)
    private Criticality criticality;

    @Enumerated(EnumType.STRING)
    private TransferStatus status;

    /**
     * The branch that actually supplied the stock. Any branch holding the medicine may approve,
     * not just the current target, so who fulfilled it has to be recorded separately.
     */
    private Long fulfilledByBranchId;

    private String fulfilledByBranchName;

    /**
     * Guards against two branches approving the same request concurrently - without it both
     * would deduct their own stock and the requester would receive double.
     */
    @Version
    private Long version;

    /** Branches already tried by escalation or that explicitly declined - never re-offered. */
    private String attemptedBranchIds;

    private LocalDateTime lastAttemptedAt;

    private String remarks;

    private LocalDateTime requestedAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.requestedAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = TransferStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}