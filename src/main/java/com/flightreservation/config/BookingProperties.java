package com.flightreservation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "booking")
public record BookingProperties(
        int holdDurationMinutes,
        int bookingWindowMinutes
) {}
