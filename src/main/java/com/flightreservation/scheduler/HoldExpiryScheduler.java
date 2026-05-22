package com.flightreservation.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class HoldExpiryScheduler {

    private final HoldExpiryProcessor holdExpiryProcessor;

    @Scheduled(fixedDelayString = "${booking.hold-expiry-check-interval-ms:60000}")
    public void releaseExpiredHolds() {
        holdExpiryProcessor.processExpiredHolds();
    }
}
