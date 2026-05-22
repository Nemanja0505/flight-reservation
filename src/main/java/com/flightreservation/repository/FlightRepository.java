package com.flightreservation.repository;

import com.flightreservation.model.Flight;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlightRepository extends JpaRepository<Flight, UUID> {

    List<Flight> findByActiveTrue();

    Optional<Flight> findByIdAndActiveTrue(UUID id);

    List<Flight> findByActiveTrueAndOriginAirportCodeAndDestinationAirportCode(
            String originAirportCode, String destinationAirportCode);

    List<Flight> findByActiveTrueAndDepartureTimeBetween(
            LocalDateTime from, LocalDateTime to);

    List<Flight> findByActiveTrueAndOriginAirportCodeAndDestinationAirportCodeAndDepartureTimeBetween(
            String originAirportCode,
            String destinationAirportCode,
            LocalDateTime from,
            LocalDateTime to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Flight f WHERE f.id = :id AND f.active = true")
    Optional<Flight> findActiveByIdWithLock(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Flight f WHERE f.id = :id")
    Optional<Flight> findByIdWithLock(@Param("id") UUID id);
}
