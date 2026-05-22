package com.flightreservation.integration;

import com.flightreservation.dto.BookingRequest;
import com.flightreservation.dto.BookingResponse;
import com.flightreservation.dto.ErrorResponse;
import com.flightreservation.dto.FlightRequest;
import com.flightreservation.dto.FlightResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingControllerIntegrationTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createBooking_validFlight_returns201() {
        // Given
        FlightResponse flight = createFlight("BC-BOOK-OK", LocalDateTime.now().plusDays(2), 50);
        BookingRequest request = new BookingRequest("Jane Doe", "jane.doe@example.com", "14C");

        // When
        ResponseEntity<BookingResponse> response = restTemplate.postForEntity(
                url("/flights/" + flight.id() + "/bookings"), request, BookingResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().flightId()).isEqualTo(flight.id());
        assertThat(response.getBody().status()).isEqualTo("HELD");
        assertThat(response.getBody().passengerName()).isEqualTo("Jane Doe");
        assertThat(response.getBody().seatNumber()).isEqualTo("14C");
    }

    @Test
    void createBooking_flightNotFound_returns404() {
        // Given
        UUID unknownFlightId = UUID.randomUUID();
        BookingRequest request = new BookingRequest("Jane Doe", "jane.doe@example.com", "1A");

        // When
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                url("/flights/" + unknownFlightId + "/bookings"), request, ErrorResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("FLIGHT_NOT_FOUND");
    }

    @Test
    void createBooking_flightInsideBookingWindow_returns422() {
        // Given
        FlightResponse flight =
                createFlight("BC-LATE", LocalDateTime.now().plusMinutes(30), 50);
        BookingRequest request = new BookingRequest("Jane Doe", "jane.doe@example.com", "2B");

        // When
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                url("/flights/" + flight.id() + "/bookings"), request, ErrorResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("BOOKING_NOT_ALLOWED");
    }

    @Test
    void createBooking_noSeatsAvailable_returns409() {
        // Given — a flight with a single seat, already taken by an earlier booking
        FlightResponse flight = createFlight("BC-FULL", LocalDateTime.now().plusDays(2), 1);
        createBooking(flight.id());
        BookingRequest request = new BookingRequest("Late Comer", "late.comer@example.com", "9F");

        // When
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                url("/flights/" + flight.id() + "/bookings"), request, ErrorResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("SEAT_NOT_AVAILABLE");
    }

    @Test
    void createBooking_duplicateSeatNumber_returns409() {
        // Given — existing bookable flight EI505 from data.sql
        FlightResponse flight = findFlightByNumber("EI505");
        BookingRequest first = new BookingRequest("Alice Smith", "alice@example.com", "10A");
        BookingRequest second = new BookingRequest("Bob Jones", "bob@example.com", "10A");

        // When — first booking for seat 10A
        ResponseEntity<BookingResponse> firstResponse = restTemplate.postForEntity(
                url("/flights/" + flight.id() + "/bookings"), first, BookingResponse.class);

        // Then — first booking succeeds
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // When — second booking attempts the same seat 10A on the same flight
        ResponseEntity<ErrorResponse> secondResponse = restTemplate.postForEntity(
                url("/flights/" + flight.id() + "/bookings"), second, ErrorResponse.class);

        // Then — second booking is rejected with 409 Conflict
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(secondResponse.getBody()).isNotNull();
        assertThat(secondResponse.getBody().code()).isEqualTo("SEAT_NOT_AVAILABLE");
    }

    @Test
    void confirmBooking_heldBooking_returns200() {
        // Given
        FlightResponse flight = createFlight("BC-CONFIRM", LocalDateTime.now().plusDays(2), 50);
        BookingResponse booking = createBooking(flight.id());

        // When
        ResponseEntity<BookingResponse> response = restTemplate.postForEntity(
                url("/bookings/" + booking.id() + "/confirm"), null, BookingResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("CONFIRMED");
    }

    @Test
    void cancelBooking_existingBooking_returns204() {
        // Given
        FlightResponse flight = createFlight("BC-CANCEL", LocalDateTime.now().plusDays(2), 50);
        BookingResponse booking = createBooking(flight.id());

        // When
        ResponseEntity<Void> response = restTemplate.exchange(
                url("/bookings/" + booking.id()), HttpMethod.DELETE, null, Void.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // --- helpers ----------------------------------------------------------

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private FlightResponse createFlight(String flightNumber, LocalDateTime departureTime, int totalSeats) {
        FlightRequest request = new FlightRequest(
                flightNumber, "DUB", "LHR", departureTime, ZONE.getId(), totalSeats);
        ResponseEntity<FlightResponse> response =
                restTemplate.postForEntity(url("/admin/flights"), request, FlightResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private FlightResponse findFlightByNumber(String flightNumber) {
        ResponseEntity<List<FlightResponse>> response = restTemplate.exchange(
                url("/flights"),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<FlightResponse>>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().stream()
                .filter(f -> flightNumber.equals(f.flightNumber()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Flight " + flightNumber + " not found in seed data"));
    }

    private BookingResponse createBooking(UUID flightId) {
        BookingRequest request = new BookingRequest("Jane Doe", "jane.doe@example.com", "12A");
        ResponseEntity<BookingResponse> response = restTemplate.postForEntity(
                url("/flights/" + flightId + "/bookings"), request, BookingResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }
}
