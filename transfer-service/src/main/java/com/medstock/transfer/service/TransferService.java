package com.medstock.transfer.service;

import com.medstock.transfer.dto.CallerContext;
import com.medstock.transfer.dto.TransferRequestDTO;
import com.medstock.transfer.dto.TransferResponseDTO;
import com.medstock.transfer.entity.TransferStatus;

import java.util.List;

public interface TransferService {

    TransferResponseDTO createRequest(TransferRequestDTO dto);

    List<TransferResponseDTO> getAllRequests();

    List<TransferResponseDTO> getRequestsByStatus(TransferStatus status);

    TransferResponseDTO getRequestById(Long id);

    TransferResponseDTO approveTransfer(Long id, CallerContext caller);

    TransferResponseDTO rejectTransfer(Long id, String remarks, CallerContext caller);

    void cancelTransfer(Long id, CallerContext caller);

    List<TransferResponseDTO> getRequestsByBranch(Long branchId);

    /**
     * Requests this branch raised itself. Kept separate from {@link #getRequestsByBranch} - that
     * one merges both directions, and having the caller filter the merged list client-side is how
     * branches ended up seeing requests that were not theirs.
     */
    List<TransferResponseDTO> getSentRequestsForBranch(Long branchId);

    /**
     * Pending requests raised by other branches that this branch may act on, newest priority
     * first, annotated with whether escalation currently points here and whether this branch
     * actually holds the stock.
     */
    List<TransferResponseDTO> getOpenRequestsForBranch(Long branchId);

    void escalateOverdueRequests();
}