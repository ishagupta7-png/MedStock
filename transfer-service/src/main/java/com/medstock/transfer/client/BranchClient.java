package com.medstock.transfer.client;

import com.medstock.transfer.dto.BranchResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
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
}