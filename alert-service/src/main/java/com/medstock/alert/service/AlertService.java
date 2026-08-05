package com.medstock.alert.service;

import com.medstock.alert.dto.RestockAlertResponse;

import java.util.List;

public interface AlertService {

    void checkAndCreateAlerts();

    List<RestockAlertResponse> getAllAlerts();

    List<RestockAlertResponse> getAlertsByBranch(Long branchId);

    RestockAlertResponse resolveAlert(Long id);
}