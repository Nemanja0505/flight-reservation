package com.flightreservation.mapper;

import com.flightreservation.dto.FlightRequest;
import com.flightreservation.dto.FlightResponse;
import com.flightreservation.model.Flight;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface FlightMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "availableSeats", source = "totalSeats")
    @Mapping(target = "active", constant = "true")
    Flight toEntity(FlightRequest request);

    FlightResponse toResponse(Flight flight);

    List<FlightResponse> toResponseList(List<Flight> flights);
}
