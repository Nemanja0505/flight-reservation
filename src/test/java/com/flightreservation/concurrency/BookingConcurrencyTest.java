package com.flightreservation.concurrency;

import com.flightreservation.dto.BookingRequest;
import com.flightreservation.dto.BookingResponse;
import com.flightreservation.dto.FlightRequest;
import com.flightreservation.dto.FlightResponse;
import com.flightreservation.model.Flight;
import com.flightreservation.repository.FlightRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingConcurrencyTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private FlightRepository flightRepository;

    @Test
    void cancelBooking_concurrentCancellations_noLostUpdate() throws Exception {
        // Given — a flight with 10 seats and 5 CONFIRMED bookings holding 5 seats.
        FlightResponse flight = createFlight("BC-CONCUR", LocalDateTime.now().plusDays(2), 10);

        List<UUID> bookingIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            BookingResponse booking = createBooking(flight.id(), "1" + i + "A");
            confirmBooking(booking.id());
            bookingIds.add(booking.id());
        }

        // Make the starting state explicit: 5 seats remaining for 5 confirmed bookings.
        Flight stored = flightRepository.findById(flight.id()).orElseThrow();
        stored.setAvailableSeats(5);
        flightRepository.saveAndFlush(stored);

        // When — all 5 bookings are cancelled concurrently, released at the same moment.
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch readyLatch = new CountDownLatch(5);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<ResponseEntity<Void>>> futures = new ArrayList<>();

        for (UUID bookingId : bookingIds) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return restTemplate.exchange(
                        url("/bookings/" + bookingId), HttpMethod.DELETE, null, Void.class);
            }));
        }

        assertThat(readyLatch.await(5, TimeUnit.SECONDS))
                .as("all threads should reach the start gate")
                .isTrue();
        startLatch.countDown();

        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS))
                .as("all cancellation requests should complete")
                .isTrue();

        // Then — every cancellation returned 204 and every seat is released.
        for (Future<ResponseEntity<Void>> future : futures) {
            assertThat(future.get().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        int finalAvailable = flightRepository.findById(flight.id())
                .orElseThrow()
                .getAvailableSeats();
        assertThat(finalAvailable)
                .as("all 5 seats should be returned to the flight after concurrent cancellations")
                .isEqualTo(10);
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

    private BookingResponse createBooking(UUID flightId, String seatNumber) {
        BookingRequest request = new BookingRequest("Jane Doe", "jane.doe@example.com", seatNumber);
        ResponseEntity<BookingResponse> response = restTemplate.postForEntity(
                url("/flights/" + flightId + "/bookings"), request, BookingResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private BookingResponse confirmBooking(UUID bookingId) {
        ResponseEntity<BookingResponse> response = restTemplate.postForEntity(
                url("/bookings/" + bookingId + "/confirm"), null, BookingResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }
}
