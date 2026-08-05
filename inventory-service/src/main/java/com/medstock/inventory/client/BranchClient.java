package com.medstock.inventory.client;

import com.medstock.inventory.dto.BranchResponse;
import com.medstock.inventory.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class BranchClient {

    private final RestTemplate restTemplate;

    private static final String BY_ID_URL = "http://branch-service/api/branch/branches/{id}";

    /**
     * Resolves a branch, failing the caller when it does not exist. Deliberately has no circuit
     * breaker fallback: for a write, "branch-service is unreachable" must not be downgraded to
     * either "branch is invalid" or "branch is fine" - both guesses are wrong. Unresolvable stock
     * is exactly how medicine rows ended up pointing at a deleted branch, where no branch could
     * ever act on a transfer request offering that stock.
     */
    public BranchResponse getExistingBranch(Long branchId) {
        try {
            BranchResponse branch = restTemplate.getForObject(BY_ID_URL, BranchResponse.class, branchId);
            if (branch == null || branch.getId() == null) {
                throw new IllegalArgumentException("Branch with id " + branchId + " does not exist");
            }
            return branch;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new IllegalArgumentException("Branch with id " + branchId + " does not exist");
        } catch (RestClientException ex) {
            log.warn("Branch service unavailable while validating branch {}", branchId, ex);
            throw new ServiceUnavailableException(
                    "Branch service is temporarily unavailable, stock was not saved - please retry");
        }
    }
}
