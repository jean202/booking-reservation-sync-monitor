package com.hotel.syncmonitor.dto;

import com.hotel.syncmonitor.domain.enums.RoomType;

public record RoomCandidateDto(Long id, String roomNumber, RoomType roomType, int capacity) {}
