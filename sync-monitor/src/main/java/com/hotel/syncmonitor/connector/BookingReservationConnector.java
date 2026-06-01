package com.hotel.syncmonitor.connector;

import com.hotel.syncmonitor.dto.BookingReservationDto;

import java.util.List;

public interface BookingReservationConnector {

    // Keeps OTA integration replaceable: the MVP uses a mock, a production version would use an HTTP client.
    List<BookingReservationDto> fetchNewReservations();

    void acknowledge(String externalReservationId);
}
