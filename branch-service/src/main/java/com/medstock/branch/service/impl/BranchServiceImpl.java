package com.medstock.branch.service.impl;

import com.medstock.branch.dto.BranchRequest;
import com.medstock.branch.dto.BranchResponse;
import com.medstock.branch.entity.Branch;
import com.medstock.branch.exception.ResourceNotFoundException;
import com.medstock.branch.repository.BranchRepository;
import com.medstock.branch.service.BranchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BranchServiceImpl implements BranchService {

    private final BranchRepository branchRepository;

    @Override
    public BranchResponse createBranch(BranchRequest request) {
        Branch branch = new Branch();
        branch.setBranchName(request.getBranchName());
        branch.setCity(request.getCity());
        branch.setContactNumber(request.getContactNumber());
        return toResponse(branchRepository.save(branch));
    }

    @Override
    public List<BranchResponse> getAllBranches() {
        return branchRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public BranchResponse getBranchById(Long id) {
        return toResponse(findBranchOrThrow(id));
    }

    @Override
    public List<BranchResponse> getBranchesByCity(String city) {
        return branchRepository.findByCity(city).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public BranchResponse updateBranch(Long id, BranchRequest request) {
        Branch branch = findBranchOrThrow(id);
        branch.setBranchName(request.getBranchName());
        branch.setCity(request.getCity());
        branch.setContactNumber(request.getContactNumber());
        return toResponse(branchRepository.save(branch));
    }

    @Override
    public void deleteBranch(Long id) {
        branchRepository.delete(findBranchOrThrow(id));
    }

    private Branch findBranchOrThrow(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id: " + id));
    }

    private BranchResponse toResponse(Branch branch) {
        return new BranchResponse(branch.getId(), branch.getBranchName(), branch.getCity(), branch.getContactNumber());
    }
}