package com.medstock.alert.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RestockAlertResponse {

    private Long id;
    private Long medicineId;
    private String medicineName;
    private Long branchId;
    private Double daysRemaining;
    private Boolean resolved;
    private LocalDateTime createdAt;
}