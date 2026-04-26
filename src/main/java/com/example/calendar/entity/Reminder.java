package com.example.calendar.entity;

import jakarta.persistence.*;

@Entity
public class Reminder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private int timeBeforeMinutes;
    private String message;

    @ManyToOne
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    public Reminder() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public int getTimeBeforeMinutes() { return timeBeforeMinutes; }
    public void setTimeBeforeMinutes(int timeBeforeMinutes) { this.timeBeforeMinutes = timeBeforeMinutes; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment appointment) { this.appointment = appointment; }
}
