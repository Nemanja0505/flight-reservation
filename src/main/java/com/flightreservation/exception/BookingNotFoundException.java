package com.flightreservation.exception;

import java.util.UUID;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(UUID id) {
        super("Booking not found with id: " + id);
    }
}
