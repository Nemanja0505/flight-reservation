package com.flightreservation.unit.service;

import com.flightreservation.dto.FlightRequest;
import com.flightreservation.service.FlightService;
import com.flightreservation.dto.FlightResponse;
import com.flightreservation.exception.BookingNotAllowedException;
import com.flightreservation.mapper.FlightMapper;
import com.flightreservation.model.Booking;
import com.flightreservation.model.BookingStatus;
import com.flightreservation.model.Flight;
import com.flightreservation.repository.BookingRepository;
import com.flightreservation.repository.FlightRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlightServiceTest {

    @Mock
    private FlightRepository flightRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private FlightMapper flightMapper;

    @InjectMocks
    private FlightService flightService;

    @Test
    void getAvailableFlights_noFilters_returnsAllActive() {
        List<Flight> activeFlights = List.of(flight("FR101"), flight("BA202"));
        when(flightRepository.findByActiveTrue()).thenReturn(activeFlights);

        List<FlightResponse> mapped = List.of(flightResponse("FR101"), flightResponse("BA202"));
        when(flightMapper.toResponseList(activeFlights)).thenReturn(mapped);

        List<FlightResponse> result = flightService.getAvailableFlights(null, null, null);

        assertThat(result).isEqualTo(mapped);
    }

    @Test
    void createFlight_validRequest_setsAvailableSeatsAndActive() {
        FlightRequest request = new FlightRequest(
                "FR101", "DUB", "LHR", LocalDateTime.now().plusDays(2), "Europe/Dublin", 180);
        Flight mappedEntity = Flight.builder()
                .flightNumber("FR101")
                .originAirportCode("DUB")
                .destinationAirportCode("LHR")
                .departureTime(request.departureTime())
                .originTimezone("Europe/Dublin")
                .totalSeats(180)
                .build();
        when(flightMapper.toEntity(request)).thenReturn(mappedEntity);
        when(flightRepository.save(any(Flight.class))).thenReturn(mappedEntity);

        FlightResponse expected = flightResponse("FR101");
        when(flightMapper.toResponse(mappedEntity)).thenReturn(expected);

        FlightResponse result = flightService.createFlight(request);

        assertThat(result).isEqualTo(expected);

        ArgumentCaptor<Flight> captor = ArgumentCaptor.forClass(Flight.class);
        verify(flightRepository).save(captor.capture());
        Flight saved = captor.getValue();
        assertThat(saved.getAvailableSeats()).isEqualTo(180);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void deleteFlight_withConfirmedBookings_throwsBookingNotAllowedException() {
        UUID flightId = UUID.randomUUID();
        Flight flight = flight("FR101");
        when(flightRepository.findById(flightId)).thenReturn(Optional.of(flight));
        when(bookingRepository.findByFlightIdAndStatus(flightId, BookingStatus.CONFIRMED))
                .thenReturn(List.of(Booking.builder().id(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> flightService.deleteFlight(flightId))
                .isInstanceOf(BookingNotAllowedException.class);

        assertThat(flight.isActive()).isTrue();
    }

    @Test
    void deleteFlight_noConfirmedBookings_softDeletesFlight() {
        UUID flightId = UUID.randomUUID();
        Flight flight = flight("FR101");
        when(flightRepository.findById(flightId)).thenReturn(Optional.of(flight));
        when(bookingRepository.findByFlightIdAndStatus(flightId, BookingStatus.CONFIRMED))
                .thenReturn(List.of());

        flightService.deleteFlight(flightId);

        assertThat(flight.isActive()).isFalse();
    }

    @Test
    void deleteFlight_withHeldBookings_cancelsHeldBookings() {
        UUID flightId = UUID.randomUUID();
        Flight flight = flight("FR101");
        flight.setAvailableSeats(178);

        Booking held1 = Booking.builder()
                .id(UUID.randomUUID())
                .status(BookingStatus.HELD)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();
        Booking held2 = Booking.builder()
                .id(UUID.randomUUID())
                .status(BookingStatus.HELD)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        when(flightRepository.findById(flightId)).thenReturn(Optional.of(flight));
        when(bookingRepository.findByFlightIdAndStatus(flightId, BookingStatus.CONFIRMED))
                .thenReturn(List.of());
        lenient().when(bookingRepository.findByFlightIdAndStatus(flightId, BookingStatus.HELD))
                .thenReturn(List.of(held1, held2));

        flightService.deleteFlight(flightId);

        assertThat(flight.isActive()).isFalse();
        assertThat(held1.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(held2.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(flight.getAvailableSeats()).isEqualTo(178);
    }

    // --- fixtures ---------------------------------------------------------

    private static Flight flight(String flightNumber) {
        return Flight.builder()
                .id(UUID.randomUUID())
                .flightNumber(flightNumber)
                .originAirportCode("DUB")
                .destinationAirportCode("LHR")
                .departureTime(LocalDateTime.now().plusDays(2))
                .originTimezone("Europe/Dublin")
                .totalSeats(180)
                .availableSeats(180)
                .active(true)
                .build();
    }

    private static FlightResponse flightResponse(String flightNumber) {
        return new FlightResponse(
                UUID.randomUUID(), flightNumber, "DUB", "LHR",
                LocalDateTime.now().plusDays(2), "Europe/Dublin", 180, 180);
    }
}
