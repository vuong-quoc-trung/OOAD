package com.example.calendar.repository;

import com.example.calendar.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    @Query("SELECT a FROM Appointment a WHERE (a.startTime < :end AND a.endTime > :start)")
    List<Appointment> findConflicts(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
