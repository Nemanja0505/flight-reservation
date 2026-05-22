package com.flightreservation.controller;

import com.flightreservation.dto.FlightRequest;
import com.flightreservation.dto.FlightResponse;
import com.flightreservation.service.FlightService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final FlightService flightService;

    @PostMapping("/flights")
    public ResponseEntity<FlightResponse> createFlight(@Valid @RequestBody FlightRequest request) {
        log.debug("POST /admin/flights — flightNumber={}", request.flightNumber());
        FlightResponse created = flightService.createFlight(request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/flights/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @DeleteMapping("/flights/{id}")
    public ResponseEntity<Void> deleteFlight(@PathVariable UUID id) {
        log.debug("DELETE /admin/flights/{}", id);
        flightService.deleteFlight(id);
        return ResponseEntity.noContent().build();
    }
}
