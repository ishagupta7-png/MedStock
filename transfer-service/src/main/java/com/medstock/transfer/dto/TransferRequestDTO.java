package com.medstock.transfer.dto;

import com.medstock.transfer.entity.Criticality;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequestDTO {

    private String medicineName;
    private Integer quantity;
    private Long requestingBranchId;
    private String requestingBranchName;
    private Criticality criticality;
    private String remarks;
    private Long targetBranchId;
}