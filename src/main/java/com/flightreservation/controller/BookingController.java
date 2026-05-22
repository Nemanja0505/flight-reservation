package com.flightreservation.controller;

import com.flightreservation.dto.BookingResponse;
import com.flightreservation.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
@Slf4j
public class BookingController {

    private final BookingService bookingService;

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable UUID id) {
        log.debug("GET /bookings/{}", id);
        return ResponseEntity.ok(bookingService.getBookingById(id));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<BookingResponse> confirmBooking(@PathVariable UUID id) {
        log.debug("POST /bookings/{}/confirm", id);
        BookingResponse confirmed = bookingService.confirmBooking(id);
        return ResponseEntity.ok(confirmed);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelBooking(@PathVariable UUID id) {
        log.debug("DELETE /bookings/{}", id);
        bookingService.cancelBooking(id);
        return ResponseEntity.noContent().build();
    }
}
