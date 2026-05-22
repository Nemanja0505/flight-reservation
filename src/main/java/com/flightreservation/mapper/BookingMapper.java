package com.flightreservation.mapper;

import com.flightreservation.dto.BookingResponse;
import com.flightreservation.model.Booking;
import com.flightreservation.model.BookingStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface BookingMapper {

    @Mapping(source = "flight.id", target = "flightId")
    @Mapping(source = "flight.flightNumber", target = "flightNumber")
    @Mapping(source = "status", target = "status", qualifiedByName = "statusToString")
    BookingResponse toResponse(Booking booking);

    @Named("statusToString")
    default String statusToString(BookingStatus status) {
        return status != null ? status.name() : null;
    }
}
