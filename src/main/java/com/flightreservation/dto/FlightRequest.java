package com.flightreservation.dto;

import com.flightreservation.validation.ValidTimezone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record FlightRequest(
        @NotBlank @Size(max = 20) String flightNumber,
        @NotBlank @Size(min = 3, max = 3) String originAirportCode,
        @NotBlank @Size(min = 3, max = 3) String destinationAirportCode,
        @NotNull LocalDateTime departureTime,
        @NotBlank @ValidTimezone String originTimezone,
        @NotNull @Positive Integer totalSeats
) {}
