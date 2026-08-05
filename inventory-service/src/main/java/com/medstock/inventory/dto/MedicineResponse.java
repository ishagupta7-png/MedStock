package com.medstock.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MedicineResponse {

    private Long id;
    private String medicineName;
    private String batchNumber;
    private Long branchId;
    private String branchName;
    private Integer quantity;
    private Double unitPrice;
    private LocalDate expiryDate;
    private Double avgDailyConsumption;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}