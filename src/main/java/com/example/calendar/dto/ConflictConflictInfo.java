package com.example.calendar.dto;

/**
 * DTO chứa thông tin chi tiết về xung đột lịch
 */
public record ConflictConflictInfo(
    Long conflictingAppointmentId,
    String conflictingTitle,
    String conflictingStartTime,
    String conflictingEndTime
) {
}
