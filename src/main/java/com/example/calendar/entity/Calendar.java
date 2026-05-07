package com.example.calendar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Calendar — lớp Calendar theo đúng sơ đồ UML Class Diagram.
 *
 * Quan hệ:
 *   User   (1) ─ Owns    ─ (1)   Calendar
 *   Calendar (1) ─ Manages ─ (0..*) Appointment
 *   Calendar (1) ─ Manages ─ (0..*) Reminder
 *   UI (AppointmentWindow) ─ Calls ─ Calendar
 *
 * Các thuộc tính UML:
 *   + appointmentsList: List<Appointment>
 *   + remindersList:    List<Reminder>
 *
 * Các phương thức UML:
 *   + addAppointment(app: Appointment)
 *   + checkConflict(startTime, endTime): boolean
 *   + findGroupMeeting(name, duration): GroupMeeting
 *   + addReminder(rem: Reminder)
 */
@Entity
@Table(name = "calendars")
public class Calendar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "calendar_id")
    private Long calendarId;

    /**
     * Quan hệ: User (1) ─ Owns ─ (1) Calendar
     * Mỗi User sở hữu đúng một Calendar.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User owner;

    // ─── UML: + appointmentsList: List<Appointment> ───────────────────────────
    /**
     * Danh sách lịch hẹn được Calendar quản lý.
     * @Transient: được load động qua CalendarService, không cột DB riêng.
     */
    @Transient
    private List<Appointment> appointmentsList;

    // ─── UML: + remindersList: List<Reminder> ────────────────────────────────
    /**
     * Danh sách nhắc nhở được Calendar quản lý.
     * @Transient: được load động qua CalendarService.
     */
    @Transient
    private List<Reminder> remindersList;

    // ─── UML Methods (stub — business logic thực thi bởi CalendarService) ────

    /**
     * + addAppointment(app: Appointment)
     * Logic thực thi: CalendarService.createAppointment()
     */
    public void addAppointment(Appointment app) {
        if (appointmentsList != null) {
            appointmentsList.add(app);
        }
    }

    /**
     * + checkConflict(startTime, endTime): boolean
     * Logic thực thi: CalendarService.checkConflict()
     */
    public boolean checkConflict(LocalDateTime startTime, LocalDateTime endTime) {
        if (appointmentsList == null) return false;
        return appointmentsList.stream().anyMatch(a ->
                a.getStartTime().isBefore(endTime) && a.getEndTime().isAfter(startTime));
    }

    /**
     * + findGroupMeeting(name, duration): GroupMeeting
     * Logic thực thi: CalendarService.findGroupMeeting()
     */
    public GroupMeeting findGroupMeeting(String name, int duration) {
        if (appointmentsList == null) return null;
        return appointmentsList.stream()
                .filter(a -> a instanceof GroupMeeting)
                .map(a -> (GroupMeeting) a)
                .filter(gm -> gm.getName().equalsIgnoreCase(name)
                           && gm.getDuration() == duration)
                .findFirst()
                .orElse(null);
    }

    /**
     * + addReminder(rem: Reminder)
     * Logic thực thi: CalendarService.addReminder()
     */
    public void addReminder(Reminder rem) {
        if (remindersList != null) {
            remindersList.add(rem);
        }
    }

    // ─── Getters / Setters ────────────────────────────────────────────────────

    public Long getCalendarId() {
        return calendarId;
    }

    public void setCalendarId(Long calendarId) {
        this.calendarId = calendarId;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public List<Appointment> getAppointmentsList() {
        return appointmentsList;
    }

    public void setAppointmentsList(List<Appointment> appointmentsList) {
        this.appointmentsList = appointmentsList;
    }

    public List<Reminder> getRemindersList() {
        return remindersList;
    }

    public void setRemindersList(List<Reminder> remindersList) {
        this.remindersList = remindersList;
    }
}
