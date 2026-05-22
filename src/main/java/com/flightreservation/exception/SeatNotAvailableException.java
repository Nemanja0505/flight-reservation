package com.flightreservation.exception;

public class SeatNotAvailableException extends RuntimeException {

    public SeatNotAvailableException() {
        super("No seats available on this flight");
    }

    public SeatNotAvailableException(String message) {
        super(message);
    }
}
