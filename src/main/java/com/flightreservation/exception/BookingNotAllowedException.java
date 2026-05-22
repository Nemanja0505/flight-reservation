package com.flightreservation.exception;

public class BookingNotAllowedException extends RuntimeException {

    public BookingNotAllowedException(String reason) {
        super(reason);
    }
}
