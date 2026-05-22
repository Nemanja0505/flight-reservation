package com.flightreservation;

import com.flightreservation.config.BookingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(BookingProperties.class)
@EnableScheduling
public class FlightReservationApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlightReservationApplication.class, args);
    }

}
