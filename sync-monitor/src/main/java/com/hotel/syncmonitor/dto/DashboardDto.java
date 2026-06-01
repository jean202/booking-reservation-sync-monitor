package com.hotel.syncmonitor.dto;

public record DashboardDto(
        long totalReservationCount,
        long unassignedReservationCount,
        long notImportedToPmsCount,
        long overbookingRiskCount,
        double averagePmsSyncDelayMinutes
) {}
