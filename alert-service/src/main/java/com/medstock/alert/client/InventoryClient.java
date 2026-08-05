package com.medstock.alert.client;

import com.medstock.alert.dto.MedicineResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class InventoryClient {

    private final RestTemplate restTemplate;

    private static final String ALL_MEDICINES_URL = "http://inventory-service/api/inventory/medicines";

    public List<MedicineResponse> getAllMedicines() {
        MedicineResponse[] response = restTemplate.getForObject(ALL_MEDICINES_URL, MedicineResponse[].class);
        return response != null ? Arrays.asList(response) : Collections.emptyList();
    }
}