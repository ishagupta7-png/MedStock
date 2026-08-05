package com.medstock.auth.service;

import com.medstock.auth.dto.AuthResponse;
import com.medstock.auth.dto.ChangePasswordRequest;
import com.medstock.auth.dto.LoginRequest;
import com.medstock.auth.dto.RegisterRequest;
import com.medstock.auth.dto.WarehouseCodeRequest;
import com.medstock.auth.entity.WarehouseAccessCode;

import java.util.List;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    WarehouseAccessCode generateWarehouseCode(WarehouseCodeRequest request);

    List<WarehouseAccessCode> getAllWarehouseCodes();

    String changePassword(ChangePasswordRequest request);
}