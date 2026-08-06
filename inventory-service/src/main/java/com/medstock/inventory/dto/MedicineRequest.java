package com.medstock.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MedicineRequest {

    @NotBlank(message = "medicineName is required")
    private String medicineName;

    @NotBlank(message = "batchNumber is required")
    private String batchNumber;

    @NotNull(message = "branchId is required")
    private Long branchId;

    /**
     * Ignored on write - the authoritative name is read from branch-service, because clients have
     * sent the wrong value here (the username) and that stale name propagated into transfer
     * requests. Kept on the DTO only so existing callers do not break.
     */
    private String branchName;

    /**
     * Zero-quantity rows advertise nothing and only add noise to the availability search, so a
     * batch has to carry at least one unit to be recorded.
     */
    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be greater than zero")
    private Integer quantity;

    @NotNull(message = "unitPrice is required")
    @PositiveOrZero(message = "unitPrice cannot be negative")
    private Double unitPrice;

    @NotNull(message = "expiryDate is required")
    private LocalDate expiryDate;

    @NotNull(message = "avgDailyConsumption is required")
    @PositiveOrZero(message = "avgDailyConsumption cannot be negative")
    private Double avgDailyConsumption;
}