package com.medstock.alert.controller;

import com.medstock.alert.dto.RestockAlertResponse;
import com.medstock.alert.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alert/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public ResponseEntity<List<RestockAlertResponse>> getAllAlerts() {
        return ResponseEntity.ok(alertService.getAllAlerts());
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<List<RestockAlertResponse>> getAlertsByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(alertService.getAlertsByBranch(branchId));
    }

    @PutMapping("/{id}/resolve")
    public ResponseEntity<RestockAlertResponse> resolveAlert(@PathVariable Long id) {
        return ResponseEntity.ok(alertService.resolveAlert(id));
    }
}