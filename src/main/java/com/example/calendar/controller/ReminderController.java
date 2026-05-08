package com.example.calendar.controller;

import com.example.calendar.dto.ReminderCreateRequest;
import com.example.calendar.dto.ReminderResponse;
import com.example.calendar.service.ReminderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ReminderController — REST API cho tính năng Reminder.
 *
 * Ánh xạ UML: Calendar +addReminder(rem: Reminder)
 * Ánh xạ UML: Appointment (1) ─ Includes ─ (0..*) Reminder
 */
@RestController
@RequestMapping("/api/reminders")
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    /**
     * POST /api/reminders — tạo reminder cho một appointment.
     * Tương ứng +addReminder(rem: Reminder) trong UML Calendar.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReminderResponse createReminder(@RequestBody ReminderCreateRequest request) {
        return reminderService.addReminder(request);
    }

    /**
     * GET /api/reminders?appointmentId=X — lấy danh sách reminder của appointment.
     */
    @GetMapping
    public List<ReminderResponse> getReminders(@RequestParam Long appointmentId) {
        return reminderService.getReminders(appointmentId);
    }

    /**
     * DELETE /api/reminders/{reminderId} — xóa reminder.
     */
    @DeleteMapping("/{reminderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReminder(@PathVariable Long reminderId) {
        reminderService.deleteReminder(reminderId);
    }
}
