package com.medstock.transfer.controller;

import com.medstock.transfer.dto.CallerContext;
import com.medstock.transfer.dto.StatusUpdateRequest;
import com.medstock.transfer.dto.TransferRequestDTO;
import com.medstock.transfer.dto.TransferResponseDTO;
import com.medstock.transfer.entity.TransferStatus;
import com.medstock.transfer.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transfer/requests")
@RequiredArgsConstructor
public class TransferController {

    /** Injected by the API gateway's AuthenticationFilter once it has validated the JWT. */
    private static final String AUTH_USERNAME_HEADER = "X-Auth-Username";
    private static final String AUTH_ROLE_HEADER = "X-Auth-Role";
    private static final String AUTH_BRANCH_ID_HEADER = "X-Auth-BranchId";

    private final TransferService transferService;

    @PostMapping
    public ResponseEntity<TransferResponseDTO> createRequest(@RequestBody TransferRequestDTO dto) {
        return new ResponseEntity<>(transferService.createRequest(dto), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<TransferResponseDTO>> getAllRequests(
            @RequestParam(required = false) TransferStatus status) {
        if (status != null) {
            return ResponseEntity.ok(transferService.getRequestsByStatus(status));
        }
        return ResponseEntity.ok(transferService.getAllRequests());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferResponseDTO> getRequestById(@PathVariable Long id) {
        return ResponseEntity.ok(transferService.getRequestById(id));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<TransferResponseDTO> approveTransfer(
            @PathVariable Long id,
            @RequestHeader(value = AUTH_USERNAME_HEADER, required = false) String username,
            @RequestHeader(value = AUTH_ROLE_HEADER, required = false) String role,
            @RequestHeader(value = AUTH_BRANCH_ID_HEADER, required = false) String branchId) {
        CallerContext caller = CallerContext.fromHeaders(username, role, branchId);
        return ResponseEntity.ok(transferService.approveTransfer(id, caller));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<TransferResponseDTO> rejectTransfer(
            @PathVariable Long id,
            @RequestBody(required = false) StatusUpdateRequest request,
            @RequestHeader(value = AUTH_USERNAME_HEADER, required = false) String username,
            @RequestHeader(value = AUTH_ROLE_HEADER, required = false) String role,
            @RequestHeader(value = AUTH_BRANCH_ID_HEADER, required = false) String branchId) {
        String remarks = request != null ? request.getRemarks() : null;
        CallerContext caller = CallerContext.fromHeaders(username, role, branchId);
        return ResponseEntity.ok(transferService.rejectTransfer(id, remarks, caller));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelTransfer(
            @PathVariable Long id,
            @RequestHeader(value = AUTH_USERNAME_HEADER, required = false) String username,
            @RequestHeader(value = AUTH_ROLE_HEADER, required = false) String role,
            @RequestHeader(value = AUTH_BRANCH_ID_HEADER, required = false) String branchId) {
        transferService.cancelTransfer(id, CallerContext.fromHeaders(username, role, branchId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<List<TransferResponseDTO>> getRequestsByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(transferService.getRequestsByBranch(branchId));
    }

    /** Pending requests from other branches that this branch can act on. */
    @GetMapping("/open/{branchId}")
    public ResponseEntity<List<TransferResponseDTO>> getOpenRequestsForBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(transferService.getOpenRequestsForBranch(branchId));
    }
}