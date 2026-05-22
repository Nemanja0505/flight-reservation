package com.flightreservation.service;

import com.flightreservation.dto.FlightRequest;
import com.flightreservation.dto.FlightResponse;
import com.flightreservation.exception.BookingNotAllowedException;
import com.flightreservation.exception.FlightNotFoundException;
import com.flightreservation.mapper.FlightMapper;
import com.flightreservation.model.Booking;
import com.flightreservation.model.BookingStatus;
import com.flightreservation.model.Flight;
import com.flightreservation.repository.BookingRepository;
import com.flightreservation.repository.FlightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class FlightService {

    private final FlightRepository flightRepository;
    private final BookingRepository bookingRepository;
    private final FlightMapper flightMapper;

    @Transactional(readOnly = true)
    public List<FlightResponse> getAvailableFlights(String originCode,
                                                    String destinationCode,
                                                    LocalDate date) {
        boolean hasRoute = originCode != null && destinationCode != null;

        List<Flight> flights;
        if (hasRoute && date != null) {
            flights = flightRepository
                    .findByActiveTrueAndOriginAirportCodeAndDestinationAirportCodeAndDepartureTimeBetween(
                            originCode,
                            destinationCode,
                            date.atStartOfDay(),
                            date.plusDays(1).atStartOfDay());
        } else if (hasRoute) {
            flights = flightRepository.findByActiveTrueAndOriginAirportCodeAndDestinationAirportCode(
                    originCode, destinationCode);
        } else if (date != null) {
            flights = flightRepository.findByActiveTrueAndDepartureTimeBetween(
                    date.atStartOfDay(),
                    date.plusDays(1).atStartOfDay());
        } else {
            flights = flightRepository.findByActiveTrue();
        }
        return flightMapper.toResponseList(flights);
    }

    @Transactional(readOnly = true)
    public FlightResponse getFlightById(UUID id) {
        Flight flight = flightRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new FlightNotFoundException(id));
        return flightMapper.toResponse(flight);
    }

    @Transactional
    public FlightResponse createFlight(FlightRequest request) {
        Flight saved = flightRepository.save(flightMapper.toEntity(request));

        log.info("Flight created: {} ({} -> {})",
                saved.getFlightNumber(),
                saved.getOriginAirportCode(),
                saved.getDestinationAirportCode());

        return flightMapper.toResponse(saved);
    }

    @Transactional
    public void deleteFlight(UUID id) {
        Flight flight = flightRepository.findById(id)
                .orElseThrow(() -> new FlightNotFoundException(id));

        List<Booking> confirmedBookings =
                bookingRepository.findByFlightIdAndStatus(id, BookingStatus.CONFIRMED);
        if (!confirmedBookings.isEmpty()) {
            throw new BookingNotAllowedException("Cannot delete flight with confirmed bookings");
        }

        List<Booking> heldBookings =
                bookingRepository.findByFlightIdAndStatus(id, BookingStatus.HELD);
        heldBookings.forEach(b -> b.setStatus(BookingStatus.CANCELLED));

        flight.setActive(false);

        log.info("Flight {} soft-deleted, {} HELD bookings cancelled", id, heldBookings.size());
    }
}
