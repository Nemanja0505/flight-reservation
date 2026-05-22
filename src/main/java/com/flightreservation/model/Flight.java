package com.flightreservation.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.Hibernate;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "flights")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Flight {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String flightNumber;

    /** IATA airport code of the origin, e.g. {@code "DUB"}. */
    @Column(nullable = false, length = 3)
    private String originAirportCode;

    /** IATA airport code of the destination, e.g. {@code "LHR"}. */
    @Column(nullable = false, length = 3)
    private String destinationAirportCode;

    private LocalDateTime departureTime;

    /** IANA timezone of the origin airport, e.g. {@code "Europe/Dublin"}. */
    private String originTimezone;

    private int totalSeats;

    private int availableSeats;

    @Column(nullable = false)
    private boolean active;

    public void releaseOneSeat() {
        int released = this.availableSeats + 1;
        if (released > this.totalSeats) {
            this.availableSeats = this.totalSeats;
        } else {
            this.availableSeats = released;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        Flight flight = (Flight) o;
        return getId() != null && getId().equals(flight.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
