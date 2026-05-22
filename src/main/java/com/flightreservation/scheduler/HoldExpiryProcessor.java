package com.flightreservation.scheduler;

import com.flightreservation.exception.FlightNotFoundException;
import com.flightreservation.model.Booking;
import com.flightreservation.model.BookingStatus;
import com.flightreservation.model.Flight;
import com.flightreservation.repository.BookingRepository;
import com.flightreservation.repository.FlightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class HoldExpiryProcessor {

    private final BookingRepository bookingRepository;
    private final FlightRepository flightRepository;
    private final Clock clock;

    @Transactional
    public void processExpiredHolds() {
        List<Booking> expiredHolds = bookingRepository.findByStatusAndExpiresAtBefore(
                BookingStatus.HELD, Instant.now(clock));

        if (expiredHolds.isEmpty()) {
            log.debug("Hold expiry run: no expired holds to release");
            return;
        }

        for (Booking booking : expiredHolds) {
            booking.setStatus(BookingStatus.EXPIRED);
            UUID flightId = booking.getFlight().getId();
            Flight flight = flightRepository.findByIdWithLock(flightId)
                    .orElseThrow(() -> new FlightNotFoundException(flightId));

            if (flight.getAvailableSeats() >= flight.getTotalSeats()) {
                log.warn("availableSeats would exceed totalSeats on flight {} — clamping", flight.getId());
            }
            flight.releaseOneSeat();
        }

        log.info("Hold expiry run: released {} held seats", expiredHolds.size());
    }
}
