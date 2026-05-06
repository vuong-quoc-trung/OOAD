package com.example.calendar.dto;

/**
 * DTO phản hồi cho các thao tác trên lịch
 * code: CREATED, JOINED, DELETED, LEFT_GROUP, AUTO_MERGE, CONFLICT_DETECTED, CONFLICT_RESOLVED
 */
public record AppointmentMutationResponse(
    String code,
    String message,
    AppointmentResponse appointment,
    Long autoMergeAppointmentId,
    String autoMergeTitle,
    ConflictConflictInfo conflict
) {
}
