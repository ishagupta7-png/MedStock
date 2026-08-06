package com.medstock.auth.service.impl;

import com.medstock.auth.config.JwtUtil;
import com.medstock.auth.dto.AuthResponse;
import com.medstock.auth.dto.BranchResponse;
import com.medstock.auth.dto.ChangePasswordRequest;
import com.medstock.auth.dto.LoginRequest;
import com.medstock.auth.dto.RegisterRequest;
import com.medstock.auth.dto.WarehouseCodeRequest;
import com.medstock.auth.entity.Role;
import com.medstock.auth.entity.User;
import com.medstock.auth.entity.WarehouseAccessCode;
import com.medstock.auth.exception.AuthException;
import com.medstock.auth.repository.UserRepository;
import com.medstock.auth.repository.WarehouseAccessCodeRepository;
import com.medstock.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final WarehouseAccessCodeRepository warehouseAccessCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final RestTemplate restTemplate;
    private final JwtUtil jwtUtil;

    private static final String BRANCH_SERVICE_URL = "http://branch-service/api/branch/branches/{branchId}";

    /**
     * Transactional so that claiming a warehouse code and creating the account either both happen
     * or neither does - otherwise a registration that fails after the claim (a username taken in
     * the meantime) would consume a single-use code and hand back nothing.
     */
    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
            throw new AuthException("Username and password are required");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AuthException("Username already exists");
        }
        if (request.getRole() == null) {
            throw new AuthException("Role is required");
        }

        Long branchId = null;

        switch (request.getRole()) {
            case ADMIN -> throw new AuthException("Admin accounts cannot be created through registration");
            case BRANCH_STAFF, INVENTORY_MANAGER -> {
                if (request.getBranchId() == null) {
                    throw new AuthException("branchId is required for this role");
                }
                verifyBranchExists(request.getBranchId());
                branchId = request.getBranchId();
            }
            case WAREHOUSE_ADMIN -> {
                if (!StringUtils.hasText(request.getWarehouseCode())) {
                    throw new AuthException("warehouseCode is required for this role");
                }
                redeemWarehouseCode(request.getWarehouseCode(), request.getUsername());
            }
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setBranchId(branchId);
        // Flushed here rather than at commit so that the unique-username constraint fires inside
        // this method, where it still maps cleanly onto the caller's request.
        userRepository.saveAndFlush(user);

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole(), user.getBranchId());
        return new AuthResponse(token, user.getUsername(), user.getRole(), user.getBranchId());
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AuthException("Invalid username or password", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AuthException("Invalid username or password", HttpStatus.UNAUTHORIZED);
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole(), user.getBranchId());
        return new AuthResponse(token, user.getUsername(), user.getRole(), user.getBranchId());
    }

    @Override
    public WarehouseAccessCode generateWarehouseCode(WarehouseCodeRequest request) {
        String code = (request != null && StringUtils.hasText(request.getCode()))
                ? request.getCode()
                : UUID.randomUUID().toString().replace("-", "").toUpperCase();

        if (warehouseAccessCodeRepository.findByCode(code).isPresent()) {
            throw new AuthException("Warehouse code already exists");
        }

        WarehouseAccessCode warehouseAccessCode = new WarehouseAccessCode();
        warehouseAccessCode.setCode(code);
        warehouseAccessCode.setUsed(false);
        warehouseAccessCode.setCreatedAt(LocalDateTime.now());
        return warehouseAccessCodeRepository.save(warehouseAccessCode);
    }

    @Override
    public List<WarehouseAccessCode> getAllWarehouseCodes() {
        return warehouseAccessCodeRepository.findAll();
    }

    @Override
    public String changePassword(ChangePasswordRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AuthException("User not found", HttpStatus.NOT_FOUND));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new AuthException("Old password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        return "Password changed successfully";
    }

    private void verifyBranchExists(Long branchId) {
        try {
            restTemplate.getForObject(BRANCH_SERVICE_URL, BranchResponse.class, branchId);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new AuthException("Branch with id " + branchId + " does not exist");
        } catch (RestClientException ex) {
            throw new AuthException("branch service unavailable, try again");
        }
    }

    /**
     * A warehouse code is single-use, so it is consumed with one conditional update rather than a
     * read followed by a write - two concurrent registrations could both see it unused and both
     * proceed. The claim shares this method's transaction, so a later failure (a duplicate
     * username, say) releases the code instead of burning it.
     */
    private void redeemWarehouseCode(String code, String username) {
        if (warehouseAccessCodeRepository.claimCode(code, username) == 1) {
            return;
        }

        // The claim can fail for two different reasons, and the caller deserves to know which.
        if (warehouseAccessCodeRepository.findByCode(code).isEmpty()) {
            throw new AuthException("Invalid warehouse code");
        }
        throw new AuthException("Warehouse code has already been used");
    }
}