package com.flightreservation.dto;

import java.time.Instant;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID flightId,
        String flightNumber,
        String passengerName,
        String passengerEmail,
        String seatNumber,
        String status,
        Instant createdAt,
        Instant expiresAt
) {}
