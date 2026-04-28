package com.example.calendar.dto;

import com.example.calendar.entity.AppointmentType;

import java.time.LocalDateTime;

public record AppointmentCreateRequest(
    Long userId,
    String title,
    LocalDateTime startTime,
    LocalDateTime endTime,
    AppointmentType type
) {
}
