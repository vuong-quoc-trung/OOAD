package com.example.calendar.repository;

import com.example.calendar.entity.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    /** Lấy tất cả reminder của một appointment */
    @Query("""
        select r from Reminder r
        where r.appointment.appointmentId = :appointmentId
        order by r.reminderTime asc
        """)
    List<Reminder> findByAppointmentId(@Param("appointmentId") Long appointmentId);

    /** Xóa tất cả reminder của một appointment */
    void deleteByAppointment_AppointmentId(Long appointmentId);
}
