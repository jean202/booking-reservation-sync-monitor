package com.hotel.syncmonitor.dto;

public record ImportResultDto(int imported, int updated, int skipped, String message) {}
