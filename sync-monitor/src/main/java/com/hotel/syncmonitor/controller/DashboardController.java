package com.hotel.syncmonitor.controller;

import com.hotel.syncmonitor.dto.DashboardDto;
import com.hotel.syncmonitor.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Operational dashboard metrics")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(summary = "Get dashboard summary",
               description = "Returns counts for total reservations, unassigned, not-in-PMS, overbooking risk, and average sync delay.")
    public DashboardDto getDashboard() {
        return dashboardService.getDashboard();
    }
}
