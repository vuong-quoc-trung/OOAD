package com.example.calendar.dto;

public record AppointmentMutationResponse(
    String code,
    String message,
    AppointmentResponse appointment,
    Long autoMergeAppointmentId,
    String autoMergeTitle
) {
}
