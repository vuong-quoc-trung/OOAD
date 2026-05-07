package com.example.calendar.repository;

import com.example.calendar.entity.Calendar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * CalendarRepository — truy vấn lớp Calendar theo UML.
 * Quan hệ: User (1) ─ Owns ─ (1) Calendar
 */
public interface CalendarRepository extends JpaRepository<Calendar, Long> {

    /** Tìm Calendar theo userId của owner */
    Optional<Calendar> findByOwner_UserId(Long userId);
}
