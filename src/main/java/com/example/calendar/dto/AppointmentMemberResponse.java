package com.example.calendar.dto;

public record AppointmentMemberResponse(
    Long userId,
    String username,
    boolean isHost
) {
}
