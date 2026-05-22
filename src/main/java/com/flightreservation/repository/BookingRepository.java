package com.flightreservation.repository;

import com.flightreservation.model.Booking;
import com.flightreservation.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    List<Booking> findByFlightIdAndStatus(UUID flightId, BookingStatus status);

    List<Booking> findByStatusAndExpiresAtBefore(BookingStatus status, Instant time);

    boolean existsByFlightIdAndSeatNumberAndStatusIn(
            UUID flightId,
            String seatNumber,
            List<BookingStatus> statuses
    );
}
