package com.example.calendar.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Lớp Appointment — ánh xạ theo sơ đồ UML.
 * Sử dụng Single Table Inheritance (TPH): GroupMeeting kế thừa lớp này.
 */
@Entity
@Table(name = "appointments")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "dtype", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue("PERSONAL")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appointment_id")
    private Long appointmentId;

    /** Tên cuộc hẹn — tương ứng +name: string trong UML */
    @Column(name = "appointment_name", nullable = false)
    private String name;

    /** Địa điểm — tương ứng +location: string trong UML */
    @Column(name = "location")
    private String location;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "duration")
    private int duration;

    /** Ánh xạ cột discriminator để Spring Data JPA có thể query (read-only) */
    @Column(name = "dtype", insertable = false, updatable = false)
    private String dtype;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    /** Quan hệ Appointment 1 — Includes — 0..* Reminder */
    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Reminder> reminders = new ArrayList<>();

    // ─── Constructors ──────────────────────────────────────────────────────────

    public Appointment() {
    }

    // ─── UML Methods ──────────────────────────────────────────────────────────

    /** +getDetails(): string */
    public String getDetails() {
        return String.format("Appointment[id=%d, name=%s, location=%s, start=%s, end=%s, duration=%dmin]",
                appointmentId, name, location, startTime, endTime, duration);
    }

    /** +setDetails() — đặt đồng thời các trường chính và tự tính duration */
    public void setDetails(String name, String location, LocalDateTime startTime, LocalDateTime endTime) {
        this.name = name;
        this.location = location;
        this.startTime = startTime;
        this.endTime = endTime;
        if (startTime != null && endTime != null) {
            this.duration = (int) Duration.between(startTime, endTime).toMinutes();
        }
    }

    // ─── Getters / Setters ────────────────────────────────────────────────────

    public Long getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(Long appointmentId) {
        this.appointmentId = appointmentId;
    }

    /** Alias backward-compat: getId() → appointmentId */
    public Long getId() {
        return appointmentId;
    }

    public void setId(Long id) {
        this.appointmentId = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** Alias backward-compat: getTitle() → name */
    public String getTitle() {
        return name;
    }

    public void setTitle(String title) {
        this.name = title;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
        recalculateDuration();
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
        recalculateDuration();
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public User getHost() {
        return host;
    }

    public void setHost(User host) {
        this.host = host;
    }

    public List<Reminder> getReminders() {
        return reminders;
    }

    public void setReminders(List<Reminder> reminders) {
        this.reminders = reminders;
    }

    public void addReminder(Reminder reminder) {
        if (reminder == null) return;
        reminders.add(reminder);
        reminder.setAppointment(this);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void recalculateDuration() {
        if (this.startTime != null && this.endTime != null) {
            this.duration = (int) Duration.between(this.startTime, this.endTime).toMinutes();
        }
    }
}
