package com.flightreservation.service;

import com.flightreservation.config.BookingProperties;
import com.flightreservation.dto.BookingRequest;
import com.flightreservation.dto.BookingResponse;
import com.flightreservation.exception.BookingNotAllowedException;
import com.flightreservation.exception.BookingNotFoundException;
import com.flightreservation.exception.FlightNotFoundException;
import com.flightreservation.exception.SeatNotAvailableException;
import com.flightreservation.mapper.BookingMapper;
import com.flightreservation.model.Booking;
import com.flightreservation.model.BookingStatus;
import com.flightreservation.model.Flight;
import com.flightreservation.repository.BookingRepository;
import com.flightreservation.repository.FlightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingService {

    private final FlightRepository flightRepository;
    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final BookingProperties bookingProperties;
    private final Clock clock;


    @Transactional
    public BookingResponse createBooking(UUID flightId, BookingRequest request) {
        Flight flight = flightRepository.findActiveByIdWithLock(flightId)
                .orElseThrow(() -> new FlightNotFoundException(flightId));

        // Booking window check — all times resolved in the origin airport's timezone.
        ZoneId originZone = ZoneId.of(flight.getOriginTimezone());
        ZonedDateTime now = ZonedDateTime.now(originZone);
        ZonedDateTime departure = flight.getDepartureTime().atZone(originZone);
        ZonedDateTime cutoff = departure.minusMinutes(bookingProperties.bookingWindowMinutes());
        if (now.isAfter(cutoff)) {
            throw new BookingNotAllowedException(
                    "Booking closed. Flight " + flight.getFlightNumber()
                            + " departs at " + departure + ", cutoff was " + cutoff);
        }

        if (flight.getAvailableSeats() <= 0) {
            throw new SeatNotAvailableException();
        }

        boolean seatTaken = bookingRepository
                .existsByFlightIdAndSeatNumberAndStatusIn(
                        flightId,
                        request.seatNumber(),
                        List.of(BookingStatus.HELD, BookingStatus.CONFIRMED)
                );

        if (seatTaken) {
            throw new SeatNotAvailableException(
                    "Seat " + request.seatNumber() + " is already taken on this flight");
        }

        flight.setAvailableSeats(flight.getAvailableSeats() - 1);
        Instant nowInstant = Instant.now(clock);
        Booking booking = bookingRepository.save(Booking.builder()
                .flight(flight)
                .passengerName(request.passengerName())
                .passengerEmail(request.passengerEmail())
                .seatNumber(request.seatNumber())
                .status(BookingStatus.HELD)
                .createdAt(nowInstant)
                .expiresAt(nowInstant.plus(bookingProperties.holdDurationMinutes(), ChronoUnit.MINUTES))
                .build());

        log.info("Booking {} created — flight {}, seat {}, expires at {}",
                booking.getId(), flightId, request.seatNumber(), booking.getExpiresAt());
        return bookingMapper.toResponse(booking);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingById(UUID id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException(id));
        return bookingMapper.toResponse(booking);
    }

    @Transactional
    public BookingResponse confirmBooking(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (booking.getStatus() != BookingStatus.HELD) {
            throw new BookingNotAllowedException(
                    "Only HELD bookings can be confirmed. Current status: " + booking.getStatus());
        }
        if (booking.getExpiresAt() != null &&
                booking.getExpiresAt().isBefore(Instant.now(clock))) {
            throw new BookingNotAllowedException(
                    "Booking hold has expired. Please create a new booking.");
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        log.info("Booking {} confirmed", bookingId);
        return bookingMapper.toResponse(booking);
    }

    @Transactional
    public void cancelBooking(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        BookingStatus status = booking.getStatus();
        if (status == BookingStatus.CANCELLED || status == BookingStatus.EXPIRED) {
            throw new BookingNotAllowedException("Booking is already cancelled");
        }
        booking.setStatus(BookingStatus.CANCELLED);

        UUID flightId = booking.getFlight().getId();
        Flight flight = flightRepository.findByIdWithLock(flightId)
                .orElseThrow(() -> new FlightNotFoundException(flightId));

        if (flight.getAvailableSeats() >= flight.getTotalSeats()) {
            log.warn("availableSeats would exceed totalSeats on flight {} — clamping", flight.getId());
        }
        flight.releaseOneSeat();

        log.info("Booking {} cancelled, seat released on flight {}",
                bookingId, flight.getId());
    }
}
