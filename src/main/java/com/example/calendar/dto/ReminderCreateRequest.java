package com.example.calendar.dto;

import java.time.LocalDateTime;

/**
 * Request tạo Reminder cho một Appointment.
 */
public record ReminderCreateRequest(
        Long appointmentId,
        LocalDateTime reminderTime,
        String message
) {
}
