package com.flightreservation.controller;

import com.flightreservation.dto.BookingRequest;
import com.flightreservation.dto.BookingResponse;
import com.flightreservation.dto.FlightResponse;
import com.flightreservation.service.BookingService;
import com.flightreservation.service.FlightService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/flights")
@RequiredArgsConstructor
@Slf4j
public class FlightController {

    private final FlightService flightService;
    private final BookingService bookingService;

    @GetMapping
    public ResponseEntity<List<FlightResponse>> getFlights(
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.debug("GET /flights — origin={}, destination={}, date={}", origin, destination, date);
        List<FlightResponse> flights = flightService.getAvailableFlights(origin, destination, date);
        return ResponseEntity.ok(flights);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FlightResponse> getFlight(@PathVariable UUID id) {
        log.debug("GET /flights/{}", id);
        return ResponseEntity.ok(flightService.getFlightById(id));
    }

    @PostMapping("/{flightId}/bookings")
    public ResponseEntity<BookingResponse> createBooking(
            @PathVariable UUID flightId,
            @Valid @RequestBody BookingRequest request) {
        log.debug("POST /flights/{}/bookings — passenger={}", flightId, request.passengerName());
        BookingResponse created = bookingService.createBooking(flightId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/bookings/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }
}
