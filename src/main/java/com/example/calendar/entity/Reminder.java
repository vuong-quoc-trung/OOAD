package com.example.calendar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;


@Entity
@Table(name = "reminders")
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reminder_id")
    private Long reminderId;

    /** +reminderTime: DateTime */
    @Column(name = "reminder_time", nullable = false)
    private LocalDateTime reminderTime;

    /** +message: string */
    @Column(name = "message", nullable = false)
    private String message;

    /** ManyToOne → Appointment (FK appointment_id) */
    @ManyToOne
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    // ─── UML Method ───────────────────────────────────────────────────────────

    /**
     * +triggerReminder() — kích hoạt nhắc nhở.
     * Có thể mở rộng để gửi email/push notification.
     */
    public void triggerReminder() {
        String appointmentName = appointment != null ? appointment.getName() : "N/A";
        System.out.printf("[REMINDER] Lịch: '%s' — %s (nhắc lúc: %s)%n",
                appointmentName, message, reminderTime);
    }

    // ─── Getters / Setters ────────────────────────────────────────────────────

    public Long getReminderId() {
        return reminderId;
    }

    public void setReminderId(Long reminderId) {
        this.reminderId = reminderId;
    }

    /** Alias backward-compat */
    public Long getId() {
        return reminderId;
    }

    public LocalDateTime getReminderTime() {
        return reminderTime;
    }

    public void setReminderTime(LocalDateTime reminderTime) {
        this.reminderTime = reminderTime;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public void setAppointment(Appointment appointment) {
        this.appointment = appointment;
    }
}
