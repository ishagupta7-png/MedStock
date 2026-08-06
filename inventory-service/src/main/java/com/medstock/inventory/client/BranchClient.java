package com.medstock.inventory.client;

import com.medstock.inventory.dto.BranchResponse;
import com.medstock.inventory.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class BranchClient {

    private final RestTemplate restTemplate;

    private static final String BY_ID_URL = "http://branch-service/api/branch/branches/{id}";

    private static final String BY_CITY_URL = "http://branch-service/api/branch/branches/city/{cityName}";

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

    /**
     * The branches in a city, used to narrow the availability search. Inventory only stores each
     * batch's branchId, so the city has to be turned into a set of branch ids here.
     *
     * <p>No circuit breaker fallback on purpose: degrading to "no city information" would return
     * the unfiltered nationwide list, which is indistinguishable from a working filter and is
     * exactly the bug this lookup exists to fix. An unusable filter has to fail loudly.
     */
    public Set<Long> getBranchIdsInCity(String cityName) {
        try {
            BranchResponse[] branches =
                    restTemplate.getForObject(BY_CITY_URL, BranchResponse[].class, cityName);
            return Arrays.stream(branches != null ? branches : new BranchResponse[0])
                    .map(BranchResponse::getId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (RestClientException ex) {
            log.warn("Branch service unavailable while resolving branches in city {}", cityName, ex);
            throw new ServiceUnavailableException(
                    "Branch service is temporarily unavailable, so results cannot be filtered by city - "
                            + "please retry or search without a city");
        }
    }
}
