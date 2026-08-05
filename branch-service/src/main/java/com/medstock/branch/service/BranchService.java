package com.medstock.branch.service;

import com.medstock.branch.dto.BranchRequest;
import com.medstock.branch.dto.BranchResponse;

import java.util.List;

public interface BranchService {

    BranchResponse createBranch(BranchRequest request);

    List<BranchResponse> getAllBranches();

    BranchResponse getBranchById(Long id);

    List<BranchResponse> getBranchesByCity(String city);

    BranchResponse updateBranch(Long id, BranchRequest request);

    void deleteBranch(Long id);
}