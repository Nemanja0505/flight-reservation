package com.flightreservation.integration;

import com.flightreservation.dto.ErrorResponse;
import com.flightreservation.dto.FlightRequest;
import com.flightreservation.dto.FlightResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FlightControllerIntegrationTest {

    private static final String ORIGIN_TIMEZONE = "Europe/Dublin";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void getFlights_noFilters_returns200WithList() {
        // Given
        createFlight("FC-LIST", LocalDateTime.now().plusDays(3), 50);

        // When
        ResponseEntity<FlightResponse[]> response =
                restTemplate.getForEntity(url("/flights"), FlightResponse[].class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody())
                .extracting(FlightResponse::flightNumber)
                .contains("FC-LIST");
    }

    @Test
    void getFlights_byRoute_returnsFilteredList() {
        // Given
        createFlightOnRoute("FC-ROUTE", "GLA", "BFS", LocalDateTime.now().plusDays(3), 50);

        // When
        ResponseEntity<FlightResponse[]> response = restTemplate.getForEntity(
                url("/flights?origin=GLA&destination=BFS"), FlightResponse[].class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody())
                .isNotEmpty()
                .allSatisfy(flight -> {
                    assertThat(flight.originAirportCode()).isEqualTo("GLA");
                    assertThat(flight.destinationAirportCode()).isEqualTo("BFS");
                })
                .extracting(FlightResponse::flightNumber)
                .contains("FC-ROUTE");
    }

    @Test
    void getFlight_existingId_returns200() {
        // Given
        FlightResponse created = createFlight("FC-GET", LocalDateTime.now().plusDays(3), 50);

        // When
        ResponseEntity<FlightResponse> response =
                restTemplate.getForEntity(url("/flights/" + created.id()), FlightResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(created.id());
        assertThat(response.getBody().flightNumber()).isEqualTo("FC-GET");
    }

    @Test
    void getFlight_nonExistingId_returns404() {
        // Given
        UUID unknownId = UUID.randomUUID();

        // When
        ResponseEntity<ErrorResponse> response =
                restTemplate.getForEntity(url("/flights/" + unknownId), ErrorResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("FLIGHT_NOT_FOUND");
    }

    @Test
    void createFlight_validRequest_returns201WithLocationHeader() {
        // Given
        FlightRequest request = new FlightRequest(
                "FC-CREATE", "DUB", "LHR",
                LocalDateTime.now().plusDays(5), ORIGIN_TIMEZONE, 120);

        // When
        ResponseEntity<FlightResponse> response =
                restTemplate.postForEntity(url("/admin/flights"), request, FlightResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().flightNumber()).isEqualTo("FC-CREATE");
        assertThat(response.getBody().totalSeats()).isEqualTo(120);
        assertThat(response.getBody().availableSeats()).isEqualTo(120);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().toString())
                .endsWith("/flights/" + response.getBody().id());
    }

    @Test
    void createFlight_invalidRequest_returns400WithFieldErrors() {
        // Given
        FlightRequest request = new FlightRequest(
                "", "XX", "YY", LocalDateTime.now().minusDays(1), "", -5);

        // When
        ResponseEntity<ErrorResponse> response =
                restTemplate.postForEntity(url("/admin/flights"), request, ErrorResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().errors()).isNotEmpty();
    }

    @Test
    void createFlight_invalidTimezone_returns400WithFieldError() {
        // Given
        FlightRequest request = new FlightRequest(
                "FC-TZ", "DUB", "LHR",
                LocalDateTime.now().plusDays(5), "Not/A_Zone", 50);

        // When
        ResponseEntity<ErrorResponse> response =
                restTemplate.postForEntity(url("/admin/flights"), request, ErrorResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().errors())
                .extracting(ErrorResponse.FieldError::field)
                .contains("originTimezone");
    }

    @Test
    void deleteFlight_existingId_returns204() {
        // Given
        FlightResponse created = createFlight("FC-DELETE", LocalDateTime.now().plusDays(3), 50);

        // When
        ResponseEntity<Void> response = restTemplate.exchange(
                url("/admin/flights/" + created.id()), HttpMethod.DELETE, null, Void.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // --- helpers ----------------------------------------------------------

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private FlightResponse createFlight(String flightNumber, LocalDateTime departureTime, int totalSeats) {
        return createFlightOnRoute(flightNumber, "DUB", "LHR", departureTime, totalSeats);
    }

    private FlightResponse createFlightOnRoute(String flightNumber, String origin, String destination,
                                               LocalDateTime departureTime, int totalSeats) {
        FlightRequest request = new FlightRequest(
                flightNumber, origin, destination, departureTime, ORIGIN_TIMEZONE, totalSeats);
        ResponseEntity<FlightResponse> response =
                restTemplate.postForEntity(url("/admin/flights"), request, FlightResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }
}
