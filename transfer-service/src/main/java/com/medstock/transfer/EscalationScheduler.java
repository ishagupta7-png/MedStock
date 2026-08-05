package com.medstock.transfer;

import com.medstock.transfer.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EscalationScheduler {

    private final TransferService transferService;

    @Scheduled(fixedRate = 60000)
    public void escalateOverdueRequests() {
        transferService.escalateOverdueRequests();
    }
}