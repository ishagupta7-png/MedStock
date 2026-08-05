package com.medstock.auth.repository;

import com.medstock.auth.entity.WarehouseAccessCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WarehouseAccessCodeRepository extends JpaRepository<WarehouseAccessCode, Long> {

    Optional<WarehouseAccessCode> findByCode(String code);
}