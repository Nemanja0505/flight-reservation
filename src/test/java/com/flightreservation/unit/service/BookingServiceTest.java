package com.flightreservation.unit.service;

import com.flightreservation.config.BookingProperties;
import com.flightreservation.service.BookingService;
import com.flightreservation.dto.BookingRequest;
import com.flightreservation.dto.BookingResponse;
import com.flightreservation.exception.BookingNotAllowedException;
import com.flightreservation.exception.FlightNotFoundException;
import com.flightreservation.exception.SeatNotAvailableException;
import com.flightreservation.mapper.BookingMapper;
import com.flightreservation.model.Booking;
import com.flightreservation.model.BookingStatus;
import com.flightreservation.model.Flight;
import com.flightreservation.repository.BookingRepository;
import com.flightreservation.repository.FlightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BookingService}. All collaborators are mocked, so these tests
 * exercise the booking-window, seat-availability and status-transition rules in isolation.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final int HOLD_DURATION_MINUTES = 15;
    private static final int BOOKING_WINDOW_MINUTES = 45;
    private static final Instant FIXED_INSTANT = Instant.parse("2024-06-01T12:00:00Z");

    @Mock
    private FlightRepository flightRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingMapper bookingMapper;

    @Mock
    private BookingProperties bookingProperties;

    private final Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    private BookingService bookingService;

    private final UUID flightId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Lenient: not every test reaches the code paths that read these properties.
        lenient().when(bookingProperties.holdDurationMinutes()).thenReturn(HOLD_DURATION_MINUTES);
        lenient().when(bookingProperties.bookingWindowMinutes()).thenReturn(BOOKING_WINDOW_MINUTES);

        bookingService = new BookingService(
                flightRepository, bookingRepository, bookingMapper, bookingProperties, clock);
    }

    @Test
    void createBooking_validRequest_returnsBookingResponse() {
        Flight flight = flight(10, farFutureDeparture());
        when(flightRepository.findActiveByIdWithLock(flightId)).thenReturn(Optional.of(flight));

        Booking persisted = booking(BookingStatus.HELD, flight);
        when(bookingRepository.save(any(Booking.class))).thenReturn(persisted);

        BookingResponse expected = new BookingResponse(
                bookingId, flightId, "FR101", "Jane Doe", "jane.doe@example.com",
                "12A", "HELD", persisted.getCreatedAt(), persisted.getExpiresAt());
        when(bookingMapper.toResponse(persisted)).thenReturn(expected);

        BookingResponse result = bookingService.createBooking(flightId, bookingRequest());

        assertThat(result).isEqualTo(expected);
        assertThat(flight.getAvailableSeats()).isEqualTo(9);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        Booking saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.HELD);
        assertThat(saved.getFlight()).isSameAs(flight);
        assertThat(saved.getPassengerName()).isEqualTo("Jane Doe");
        assertThat(saved.getPassengerEmail()).isEqualTo("jane.doe@example.com");
        assertThat(saved.getSeatNumber()).isEqualTo("12A");
        assertThat(saved.getCreatedAt()).isEqualTo(FIXED_INSTANT);
        assertThat(saved.getExpiresAt())
                .isEqualTo(FIXED_INSTANT.plus(HOLD_DURATION_MINUTES, ChronoUnit.MINUTES));
    }

    @Test
    void createBooking_departureWithin45Minutes_throwsBookingNotAllowedException() {
        Flight flight = flight(10, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(30));
        when(flightRepository.findActiveByIdWithLock(flightId)).thenReturn(Optional.of(flight));

        assertThatThrownBy(() -> bookingService.createBooking(flightId, bookingRequest()))
                .isInstanceOf(BookingNotAllowedException.class);

        assertThat(flight.getAvailableSeats()).isEqualTo(10);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_noSeatsAvailable_throwsSeatNotAvailableException() {
        Flight flight = flight(0, farFutureDeparture());
        when(flightRepository.findActiveByIdWithLock(flightId)).thenReturn(Optional.of(flight));

        assertThatThrownBy(() -> bookingService.createBooking(flightId, bookingRequest()))
                .isInstanceOf(SeatNotAvailableException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_flightNotFound_throwsFlightNotFoundException() {
        when(flightRepository.findActiveByIdWithLock(flightId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(flightId, bookingRequest()))
                .isInstanceOf(FlightNotFoundException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void confirmBooking_heldBooking_setsStatusConfirmed() {
        Booking held = booking(BookingStatus.HELD, flight(9, farFutureDeparture()));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(held));

        BookingResponse expected = new BookingResponse(
                bookingId, flightId, "FR101", "Jane Doe", "jane.doe@example.com",
                "12A", "CONFIRMED", held.getCreatedAt(), held.getExpiresAt());
        when(bookingMapper.toResponse(held)).thenReturn(expected);

        BookingResponse result = bookingService.confirmBooking(bookingId);

        assertThat(held.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void confirmBooking_cancelledBooking_throwsBookingNotAllowedException() {
        Booking cancelled = booking(BookingStatus.CANCELLED, flight(10, farFutureDeparture()));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> bookingService.confirmBooking(bookingId))
                .isInstanceOf(BookingNotAllowedException.class);

        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void confirmBooking_expiredHold_throwsBookingNotAllowedException() {
        Booking held = booking(BookingStatus.HELD, flight(9, farFutureDeparture()));
        held.setExpiresAt(FIXED_INSTANT.minus(1, ChronoUnit.MINUTES));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(held));

        assertThatThrownBy(() -> bookingService.confirmBooking(bookingId))
                .isInstanceOf(BookingNotAllowedException.class)
                .hasMessageContaining("expired");

        assertThat(held.getStatus()).isEqualTo(BookingStatus.HELD);
    }

    @Test
    void cancelBooking_heldBooking_incrementsAvailableSeats() {
        Flight flight = flight(5, farFutureDeparture());
        Booking held = booking(BookingStatus.HELD, flight);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(held));
        when(flightRepository.findByIdWithLock(flightId)).thenReturn(Optional.of(flight));

        bookingService.cancelBooking(bookingId);

        assertThat(held.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(flight.getAvailableSeats()).isEqualTo(6);
    }

    @Test
    void cancelBooking_confirmedBooking_incrementsAvailableSeats() {
        Flight flight = flight(5, farFutureDeparture());
        Booking confirmed = booking(BookingStatus.CONFIRMED, flight);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(confirmed));
        when(flightRepository.findByIdWithLock(flightId)).thenReturn(Optional.of(flight));

        bookingService.cancelBooking(bookingId);

        assertThat(confirmed.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(flight.getAvailableSeats()).isEqualTo(6);
    }

    @Test
    void cancelBooking_alreadyCancelled_throwsBookingNotAllowedException() {
        Flight flight = flight(5, farFutureDeparture());
        Booking cancelled = booking(BookingStatus.CANCELLED, flight);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> bookingService.cancelBooking(bookingId))
                .isInstanceOf(BookingNotAllowedException.class);

        assertThat(flight.getAvailableSeats()).isEqualTo(5);
    }

    @Test
    void cancelBooking_alreadyExpired_throwsBookingNotAllowedException() {
        // Scheduler already released the seat for an expired hold — user-initiated
        // cancel must not double-release.
        Flight flight = flight(5, farFutureDeparture());
        Booking expired = booking(BookingStatus.EXPIRED, flight);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> bookingService.cancelBooking(bookingId))
                .isInstanceOf(BookingNotAllowedException.class);

        assertThat(expired.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(flight.getAvailableSeats()).isEqualTo(5);
        verify(flightRepository, never()).findByIdWithLock(any());
    }

    @Test
    void confirmBooking_expiredStatus_throwsBookingNotAllowedException() {
        // A scheduler-expired booking has status EXPIRED, not HELD — confirmation must fail.
        Booking expired = booking(BookingStatus.EXPIRED, flight(9, farFutureDeparture()));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> bookingService.confirmBooking(bookingId))
                .isInstanceOf(BookingNotAllowedException.class);

        assertThat(expired.getStatus()).isEqualTo(BookingStatus.EXPIRED);
    }

    // --- fixtures ---------------------------------------------------------

    private Flight flight(int availableSeats, LocalDateTime departureTime) {
        return Flight.builder()
                .id(flightId)
                .flightNumber("FR101")
                .originAirportCode("DUB")
                .destinationAirportCode("LHR")
                .departureTime(departureTime)
                .originTimezone("UTC")
                .totalSeats(10)
                .availableSeats(availableSeats)
                .active(true)
                .build();
    }

    private Booking booking(BookingStatus status, Flight flight) {
        return Booking.builder()
                .id(bookingId)
                .flight(flight)
                .passengerName("Jane Doe")
                .passengerEmail("jane.doe@example.com")
                .seatNumber("12A")
                .status(status)
                .createdAt(FIXED_INSTANT)
                .expiresAt(FIXED_INSTANT.plus(HOLD_DURATION_MINUTES, ChronoUnit.MINUTES))
                .build();
    }

    private static LocalDateTime farFutureDeparture() {
        return LocalDateTime.now(ZoneOffset.UTC).plusHours(6);
    }

    private static BookingRequest bookingRequest() {
        return new BookingRequest("Jane Doe", "jane.doe@example.com", "12A");
    }
}
