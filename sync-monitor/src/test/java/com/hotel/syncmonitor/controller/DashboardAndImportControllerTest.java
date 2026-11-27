package com.hotel.syncmonitor.controller;

import com.hotel.syncmonitor.dto.DashboardDto;
import com.hotel.syncmonitor.dto.ImportResultDto;
import com.hotel.syncmonitor.service.BookingImportService;
import com.hotel.syncmonitor.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({DashboardController.class, MockBookingReservationController.class})
class DashboardAndImportControllerTest {

    @Autowired private MockMvc mvc;
    @MockBean private DashboardService dashboardService;
    @MockBean private BookingImportService bookingImportService;

    @Test
    void dashboardExposesAllMetrics() throws Exception {
        given(dashboardService.getDashboard()).willReturn(new DashboardDto(8, 3, 1, 2, 4.5));

        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReservationCount").value(8))
                .andExpect(jsonPath("$.unassignedReservationCount").value(3))
                .andExpect(jsonPath("$.notImportedToPmsCount").value(1))
                .andExpect(jsonPath("$.overbookingRiskCount").value(2))
                .andExpect(jsonPath("$.averagePmsSyncDelayMinutes").value(4.5));
    }

    @Test
    void importEndpointReturnsResult() throws Exception {
        given(bookingImportService.importReservations()).willReturn(new ImportResultDto(2, 1, 0, "done"));

        mvc.perform(post("/api/mock/booking/reservations/import"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.message").value("done"));
    }

    @Test
    void importRequiresPost() throws Exception {
        mvc.perform(get("/api/mock/booking/reservations/import"))
                .andExpect(status().isMethodNotAllowed());
    }
}
