package com.medstock.auth.controller;

import com.medstock.auth.dto.AuthResponse;
import com.medstock.auth.dto.ChangePasswordRequest;
import com.medstock.auth.dto.LoginRequest;
import com.medstock.auth.dto.RegisterRequest;
import com.medstock.auth.dto.WarehouseCodeRequest;
import com.medstock.auth.entity.WarehouseAccessCode;
import com.medstock.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return new ResponseEntity<>(authService.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/warehouse-codes")
    public ResponseEntity<WarehouseAccessCode> generateWarehouseCode(
            @RequestBody(required = false) WarehouseCodeRequest request) {
        return new ResponseEntity<>(authService.generateWarehouseCode(request), HttpStatus.CREATED);
    }

    @GetMapping("/warehouse-codes")
    public ResponseEntity<List<WarehouseAccessCode>> getAllWarehouseCodes() {
        return ResponseEntity.ok(authService.getAllWarehouseCodes());
    }

    @PutMapping("/change-password")
    public ResponseEntity<String> changePassword(@RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(authService.changePassword(request));
    }
}