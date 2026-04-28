package com.example.calendar.dto;

import com.example.calendar.entity.AppointmentType;

import java.time.LocalDateTime;
import java.util.List;

public record AppointmentResponse(
    Long id,
    String title,
    LocalDateTime startTime,
    LocalDateTime endTime,
    AppointmentType type,
    Long hostId,
    String hostUsername,
    List<AppointmentMemberResponse> members
) {
}
