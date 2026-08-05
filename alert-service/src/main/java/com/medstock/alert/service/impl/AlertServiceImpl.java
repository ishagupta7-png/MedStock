package com.medstock.alert.service.impl;

import com.medstock.alert.client.InventoryClient;
import com.medstock.alert.dto.MedicineResponse;
import com.medstock.alert.dto.RestockAlertResponse;
import com.medstock.alert.entity.RestockAlert;
import com.medstock.alert.exception.ResourceNotFoundException;
import com.medstock.alert.repository.RestockAlertRepository;
import com.medstock.alert.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertServiceImpl implements AlertService {

    private final RestockAlertRepository restockAlertRepository;
    private final InventoryClient inventoryClient;

    @Value("${alert.days-remaining-threshold}")
    private double daysRemainingThreshold;

    @Override
    @Scheduled(fixedRateString = "${alert.check-interval-ms}")
    public void checkAndCreateAlerts() {
        List<MedicineResponse> medicines = inventoryClient.getAllMedicines();

        for (MedicineResponse medicine : medicines) {
            if (medicine.getAvgDailyConsumption() == null || medicine.getAvgDailyConsumption() <= 0) {
                continue;
            }

            double daysRemaining = medicine.getQuantity() / medicine.getAvgDailyConsumption();

            if (daysRemaining <= daysRemainingThreshold) {
                boolean alreadyExists = restockAlertRepository
                        .findByMedicineIdAndBranchIdAndResolvedFalse(medicine.getId(), medicine.getBranchId())
                        .isPresent();

                if (!alreadyExists) {
                    RestockAlert alert = new RestockAlert();
                    alert.setMedicineId(medicine.getId());
                    alert.setMedicineName(medicine.getMedicineName());
                    alert.setBranchId(medicine.getBranchId());
                    alert.setDaysRemaining(daysRemaining);
                    alert.setResolved(false);
                    restockAlertRepository.save(alert);

                    log.info("Created restock alert for medicine {} (id={}) at branch {} - {} days remaining",
                            medicine.getMedicineName(), medicine.getId(), medicine.getBranchId(), daysRemaining);
                }
            }
        }
    }

    @Override
    public List<RestockAlertResponse> getAllAlerts() {
        return restockAlertRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<RestockAlertResponse> getAlertsByBranch(Long branchId) {
        return restockAlertRepository.findByBranchId(branchId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public RestockAlertResponse resolveAlert(Long id) {
        RestockAlert alert = restockAlertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restock alert not found with id: " + id));
        alert.setResolved(true);
        return toResponse(restockAlertRepository.save(alert));
    }

    private RestockAlertResponse toResponse(RestockAlert alert) {
        return new RestockAlertResponse(
                alert.getId(),
                alert.getMedicineId(),
                alert.getMedicineName(),
                alert.getBranchId(),
                alert.getDaysRemaining(),
                alert.getResolved(),
                alert.getCreatedAt());
    }
}