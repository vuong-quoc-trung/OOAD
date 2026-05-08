package com.example.calendar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long userId;

    /** +name: string — cột DB giữ tên "username" cho tương thích */
    @Column(name = "username", nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String password;

    // ─── Constructors ──────────────────────────────────────────────────────────

    public User() {
    }

    // ─── UML Methods (stub — orchestrated by CalendarService / UI) ────────────

    /** +createAppointment() — điều phối bởi CalendarService */
    public void createAppointment() {
        // Business logic handled by CalendarService.addAppointment()
    }

    /** +joinGroupMeeting() — điều phối bởi CalendarService */
    public void joinGroupMeeting() {
        // Business logic handled by CalendarService.joinAppointment()
    }

    // ─── Getters / Setters ────────────────────────────────────────────────────

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /** Alias backward-compat: getId() → userId */
    public Long getId() {
        return userId;
    }

    public void setId(Long id) {
        this.userId = id;
    }

    /** +name: string (UML) */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** Alias backward-compat: getUsername() → name */
    public String getUsername() {
        return name;
    }

    public void setUsername(String username) {
        this.name = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
