package com.example.calendar.service;

import com.example.calendar.dto.ReminderCreateRequest;
import com.example.calendar.dto.ReminderResponse;
import com.example.calendar.entity.Appointment;
import com.example.calendar.entity.Calendar;
import com.example.calendar.entity.Reminder;
import com.example.calendar.exception.ApiNotFoundException;
import com.example.calendar.exception.ApiValidationException;
import com.example.calendar.repository.AppointmentRepository;
import com.example.calendar.repository.ReminderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final AppointmentRepository appointmentRepository;
    private final CalendarCoreService calendarCoreService;

    public ReminderService(
            ReminderRepository reminderRepository,
            AppointmentRepository appointmentRepository,
            CalendarCoreService calendarCoreService
    ) {
        this.reminderRepository = reminderRepository;
        this.appointmentRepository = appointmentRepository;
        this.calendarCoreService = calendarCoreService;
    }

    @Transactional
    public ReminderResponse addReminder(ReminderCreateRequest request) {
        if (request.appointmentId() == null) throw new ApiValidationException("appointmentId la bat buoc.");
        if (request.reminderTime()   == null) throw new ApiValidationException("reminderTime la bat buoc.");

        Appointment appointment = appointmentRepository.findById(request.appointmentId())
                .orElseThrow(() -> new ApiNotFoundException("Khong tim thay lich."));

        Reminder reminder = new Reminder();
        reminder.setReminderTime(request.reminderTime());
        reminder.setMessage(request.message() != null ? request.message().trim() : "Nhac nho lich");
        reminder.setAppointment(appointment);

        // UML: Calendar +addReminder(rem) — gắn vào Calendar entity của user
        Calendar calendar = calendarCoreService.getOrCreateCalendar(appointment.getHost());
        calendar.addReminder(reminder);   // stub method trên entity

        return toReminderResponse(reminderRepository.save(reminder));
    }

    @Transactional(readOnly = true)
    public List<ReminderResponse> getReminders(Long appointmentId) {
        return reminderRepository.findByAppointmentId(appointmentId)
                .stream().map(this::toReminderResponse).toList();
    }

    @Transactional
    public void deleteReminder(Long reminderId) {
        if (!reminderRepository.existsById(reminderId))
            throw new ApiNotFoundException("Khong tim thay reminder.");
        reminderRepository.deleteById(reminderId);
    }

    public ReminderResponse toReminderResponse(Reminder r) {
        return new ReminderResponse(
                r.getReminderId(), r.getReminderTime(), r.getMessage(),
                r.getAppointment() != null ? r.getAppointment().getAppointmentId() : null);
    }
}
