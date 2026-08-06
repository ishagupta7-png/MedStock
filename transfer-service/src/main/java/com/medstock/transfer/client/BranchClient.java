package com.medstock.transfer.client;

import com.medstock.transfer.dto.BranchResponse;
import com.medstock.transfer.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BranchClient {

    private final RestTemplate restTemplate;

    private static final String BY_CITY_URL = "http://branch-service/api/branch/branches/city/{cityName}";

    private static final String BY_ID_URL = "http://branch-service/api/branch/branches/{id}";

    @CircuitBreaker(name = "branchService", fallbackMethod = "fallbackBranchesByCity")
    public List<BranchResponse> getBranchesByCity(String cityName) {
        BranchResponse[] response = restTemplate.getForObject(BY_CITY_URL, BranchResponse[].class, cityName);
        return response != null ? Arrays.asList(response) : Collections.emptyList();
    }

    public List<BranchResponse> fallbackBranchesByCity(String cityName, Throwable t) {
        log.warn("Branch service unavailable while resolving branches for city {}, skipping city preference", cityName, t);
        return Collections.emptyList();
    }

    @CircuitBreaker(name = "branchService", fallbackMethod = "fallbackBranch")
    public BranchResponse getBranch(Long branchId) {
        return restTemplate.getForObject(BY_ID_URL, BranchResponse.class, branchId);
    }

    public BranchResponse fallbackBranch(Long branchId, Throwable t) {
        log.warn("Branch service unavailable while resolving branch {}", branchId, t);
        return null;
    }

    /**
     * Resolves a branch that the caller requires to exist, keeping "this branch is not real" and
     * "branch-service is unreachable" apart.
     *
     * <p>Deliberately has no circuit breaker: the tolerant {@link #getBranch} above collapses both
     * cases into {@code null}, which is right when a branch name or city is merely decorative, but
     * wrong when validating input. Reusing it on the create path let a transfer request be
     * persisted against a branch id that did not exist, with the failure showing up only as a
     * silently skipped city preference.
     *
     * @throws IllegalArgumentException if the branch does not exist (a 400 for the caller)
     * @throws ServiceUnavailableException if branch-service cannot be reached
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
                    "Branch service is temporarily unavailable, the request was not created - please retry");
        }
    }
}