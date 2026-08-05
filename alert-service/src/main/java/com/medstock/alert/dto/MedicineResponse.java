package com.medstock.alert.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MedicineResponse {

    private Long id;
    private String medicineName;
    private Long branchId;
    private Integer quantity;
    private Double avgDailyConsumption;
}