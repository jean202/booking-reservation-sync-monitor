package com.hotel.syncmonitor.controller;

import com.hotel.syncmonitor.dto.ImportResultDto;
import com.hotel.syncmonitor.service.BookingImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mock/booking")
@Tag(name = "Mock Booking.com", description = "Simulated Booking.com reservation import (no real API calls)")
public class MockBookingReservationController {

    private final BookingImportService bookingImportService;

    public MockBookingReservationController(BookingImportService bookingImportService) {
        this.bookingImportService = bookingImportService;
    }

    @PostMapping("/reservations/import")
    @Operation(summary = "Import mock Booking.com reservations",
               description = "Fetches reservations from the mock connector and upserts them. Safe to call multiple times.")
    public ResponseEntity<ImportResultDto> importReservations() {
        return ResponseEntity.ok(bookingImportService.importReservations());
    }
}
