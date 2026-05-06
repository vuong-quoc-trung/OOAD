package com.example.calendar.dto;

/**
 * DTO để xử lý quyết định giải quyết xung đột lịch
 */
public record ConflictResolutionRequest(
    Long userId,
    Long newAppointmentId,
    Long conflictingAppointmentId,
    boolean replaceExisting
) {
}
