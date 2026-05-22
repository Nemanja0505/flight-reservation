package com.flightreservation.concurrency;

import com.flightreservation.dto.BookingRequest;
import com.flightreservation.dto.BookingResponse;
import com.flightreservation.model.Flight;
import com.flightreservation.repository.FlightRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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
class SeatOversellConcurrencyTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final int CONCURRENT_REQUESTS = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private FlightRepository flightRepository;

    @Test
    void createBooking_concurrentRequests_exactlyOneSucceeds() throws Exception {
        // Given — a flight with a single seat persisted directly so the
        // service layer never normalises availableSeats against totalSeats.
        Flight flight = flightRepository.saveAndFlush(Flight.builder()
                .flightNumber("OS-" + UUID.randomUUID().toString().substring(0, 8))
                .originAirportCode("DUB")
                .destinationAirportCode("LHR")
                .departureTime(LocalDateTime.now().plusDays(2))
                .originTimezone(ZONE.getId())
                .totalSeats(1)
                .availableSeats(1)
                .active(true)
                .build());
        UUID flightId = flight.getId();

        List<BookingRequest> requests = new ArrayList<>(CONCURRENT_REQUESTS);
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            requests.add(new BookingRequest(
                    "Passenger " + i,
                    "passenger" + i + "@example.com",
                    "1" + (char) ('A' + i)));
        }

        // When — all 5 requests are released at the same moment.
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<ResponseEntity<BookingResponse>>> futures = new ArrayList<>();

        for (BookingRequest request : requests) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return restTemplate.postForEntity(
                        url("/flights/" + flightId + "/bookings"),
                        request,
                        BookingResponse.class);
            }));
        }

        assertThat(readyLatch.await(5, TimeUnit.SECONDS))
                .as("all threads should reach the start gate")
                .isTrue();
        startLatch.countDown();

        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS))
                .as("all booking requests should complete")
                .isTrue();

        // Then — exactly one request wins the seat; the other four are rejected.
        List<HttpStatusCode> statuses = new ArrayList<>();
        for (Future<ResponseEntity<BookingResponse>> future : futures) {
            statuses.add(future.get().getStatusCode());
        }

        long created = statuses.stream().filter(s -> s == HttpStatus.CREATED).count();
        long conflicts = statuses.stream().filter(s -> s == HttpStatus.CONFLICT).count();

        assertThat(created)
                .as("exactly one booking should succeed — got statuses %s", statuses)
                .isEqualTo(1);
        assertThat(conflicts)
                .as("the remaining four bookings should be rejected with 409 — got statuses %s", statuses)
                .isEqualTo(4);

        int finalAvailable = flightRepository.findById(flightId)
                .orElseThrow()
                .getAvailableSeats();
        assertThat(finalAvailable)
                .as("the single seat must be consumed exactly once")
                .isZero();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
