package com.example.calendar.dto;

public record AppointmentMemberResponse(
    Long id,
    String username,
    boolean host
) {
}
