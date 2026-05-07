package com.example.calendar.dto;

import java.time.LocalDateTime;

/**
 * Request tạo lịch hẹn mới.
 * Trường "name" thay thế "title" theo sơ đồ UML.
 * type: "PERSONAL" (default) hoặc "GROUP"
 */
public record AppointmentCreateRequest(
        Long userId,
        /** +name: string (UML Appointment) */
        String name,
        String location,
        LocalDateTime startTime,
        LocalDateTime endTime,
        /** "PERSONAL" hoặc "GROUP" */
        String type,
        /** Thời gian nhắc nhở (tuỳ chọn) */
        LocalDateTime reminderTime,
        /** Nội dung nhắc nhở (tuỳ chọn) */
        String reminderMessage,
        /** Bỏ qua cảnh báo trùng giờ nhóm (nếu có) */
        Boolean ignoreGroupConflict
) {
    /** Alias backward-compat: title() → name */
    public String title() {
        return name;
    }
}
