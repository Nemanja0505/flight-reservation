package com.flightreservation.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record FlightResponse(
        UUID id,
        String flightNumber,
        String originAirportCode,
        String destinationAirportCode,
        LocalDateTime departureTime,
        String originTimezone,
        int totalSeats,
        int availableSeats
) {}
