package com.flightreservation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookingRequest(
        @NotBlank @Size(max = 100) String passengerName,
        @NotBlank @Email String passengerEmail,
        @NotBlank String seatNumber
) {}
