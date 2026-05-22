package com.flightreservation.unit.scheduler;

import com.flightreservation.model.Booking;
import com.flightreservation.scheduler.HoldExpiryProcessor;
import com.flightreservation.model.BookingStatus;
import com.flightreservation.model.Flight;
import com.flightreservation.repository.BookingRepository;
import com.flightreservation.repository.FlightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HoldExpiryProcessor}. Verifies the scheduler distinguishes
 * its terminal state ({@code EXPIRED}) from user-initiated cancellations
 * ({@code CANCELLED}) and that seats are released back to the flight.
 */
@ExtendWith(MockitoExtension.class)
class HoldExpiryProcessorTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2024-06-01T12:00:00Z");

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private FlightRepository flightRepository;

    private final Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    private HoldExpiryProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new HoldExpiryProcessor(bookingRepository, flightRepository, clock);
    }

    @Test
    void processExpiredHolds_expiredHold_setsStatusExpiredAndReleasesSeat() {
        UUID flightId = UUID.randomUUID();
        Flight flight = flight(flightId, 10, 4);
        Booking expiredHold = booking(flight);

        when(bookingRepository.findByStatusAndExpiresAtBefore(
                eq(BookingStatus.HELD), any(Instant.class)))
                .thenReturn(List.of(expiredHold));
        when(flightRepository.findByIdWithLock(flightId)).thenReturn(Optional.of(flight));

        processor.processExpiredHolds();

        assertThat(expiredHold.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(flight.getAvailableSeats()).isEqualTo(5);
    }

    @Test
    void processExpiredHolds_multipleExpired_allMarkedExpired() {
        UUID flightId = UUID.randomUUID();
        Flight flight = flight(flightId, 10, 3);
        Booking expired1 = booking(flight);
        Booking expired2 = booking(flight);

        when(bookingRepository.findByStatusAndExpiresAtBefore(
                eq(BookingStatus.HELD), any(Instant.class)))
                .thenReturn(List.of(expired1, expired2));
        when(flightRepository.findByIdWithLock(flightId)).thenReturn(Optional.of(flight));

        processor.processExpiredHolds();

        assertThat(expired1.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(expired2.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(flight.getAvailableSeats()).isEqualTo(5);
    }

    @Test
    void processExpiredHolds_noExpiredHolds_doesNothing() {
        when(bookingRepository.findByStatusAndExpiresAtBefore(
                eq(BookingStatus.HELD), any(Instant.class)))
                .thenReturn(List.of());

        processor.processExpiredHolds();

        verifyNoInteractions(flightRepository);
    }

    @Test
    void processExpiredHolds_releasedSeatsAreClampedToTotalSeats() {
        // Defensive: data corruption shouldn't let availableSeats exceed totalSeats.
        UUID flightId = UUID.randomUUID();
        Flight flight = flight(flightId, 10, 10);
        Booking expiredHold = booking(flight);

        when(bookingRepository.findByStatusAndExpiresAtBefore(
                eq(BookingStatus.HELD), any(Instant.class)))
                .thenReturn(List.of(expiredHold));
        when(flightRepository.findByIdWithLock(flightId)).thenReturn(Optional.of(flight));

        processor.processExpiredHolds();

        assertThat(expiredHold.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(flight.getAvailableSeats()).isEqualTo(10);
    }

    // --- helpers ----------------------------------------------------------

    private static Flight flight(UUID id, int totalSeats, int availableSeats) {
        return Flight.builder()
                .id(id)
                .flightNumber("FR101")
                .originAirportCode("DUB")
                .destinationAirportCode("LHR")
                .departureTime(LocalDateTime.now().plusHours(6))
                .originTimezone("UTC")
                .totalSeats(totalSeats)
                .availableSeats(availableSeats)
                .active(true)
                .build();
    }

    private static Booking booking(Flight flight) {
        return Booking.builder()
                .id(UUID.randomUUID())
                .flight(flight)
                .passengerName("Jane Doe")
                .passengerEmail("jane.doe@example.com")
                .seatNumber("12A")
                .status(BookingStatus.HELD)
                .createdAt(FIXED_INSTANT.minus(20, ChronoUnit.MINUTES))
                .expiresAt(FIXED_INSTANT.minus(5, ChronoUnit.MINUTES))
                .build();
    }
}
