package com.medstock.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BranchResponse {

    private Long id;
    private String branchName;
    private String city;
    private String contactNumber;
}