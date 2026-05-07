package com.example.calendar.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO phản hồi thông tin một Appointment (hoặc GroupMeeting).
 * Trường "name" thay thế "title" theo sơ đồ UML.
 */
public record AppointmentResponse(
        Long id,
        /** +name: string (UML Appointment) */
        String name,
        String location,
        LocalDateTime startTime,
        LocalDateTime endTime,
        int duration,
        /** "PERSONAL" hoặc "GROUP" (từ discriminator dtype) */
        String type,
        Long hostId,
        String hostUsername,
        List<AppointmentMemberResponse> members,
        List<ReminderResponse> reminders
) {
    /** Alias backward-compat: getTitle() → name */
    public String getTitle() {
        return name;
    }
}
