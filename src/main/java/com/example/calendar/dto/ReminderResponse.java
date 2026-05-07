package com.example.calendar.dto;

import java.time.LocalDateTime;

/**
 * DTO phản hồi thông tin một Reminder.
 */
public record ReminderResponse(
        Long reminderId,
        LocalDateTime reminderTime,
        String message,
        Long appointmentId
) {
}
