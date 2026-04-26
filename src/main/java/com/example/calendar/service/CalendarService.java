package com.example.calendar.service;

import com.example.calendar.entity.Appointment;
import com.example.calendar.entity.GroupMeeting;
import com.example.calendar.entity.User;
import com.example.calendar.repository.AppointmentRepository;
import com.example.calendar.repository.GroupMeetingRepository;
import com.example.calendar.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CalendarService {
    private final AppointmentRepository appointmentRepository;
    private final GroupMeetingRepository groupMeetingRepository;
    private final UserRepository userRepository;

    public CalendarService(AppointmentRepository appointmentRepo, GroupMeetingRepository groupRepo, UserRepository userRepo) {
        this.appointmentRepository = appointmentRepo;
        this.groupMeetingRepository = groupRepo;
        this.userRepository = userRepo;
    }

    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    @Transactional
    public Appointment addAppointment(Appointment app) {
        if (app.getName() == null || app.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Tên lịch trình không được để trống!");
        }
        if (app.getEndTime().isBefore(app.getStartTime())) {
            throw new IllegalArgumentException("Thời gian kết thúc phải lớn hơn thời gian bắt đầu!");
        }
        
        List<Appointment> conflicts = checkConflict(app.getStartTime(), app.getEndTime());
        if (!conflicts.isEmpty()) {
            throw new RuntimeException("CONFLICT_TIME:" + conflicts.get(0).getId());
        }

        return appointmentRepository.save(app);
    }

    public List<Appointment> checkConflict(LocalDateTime start, LocalDateTime end) {
        return appointmentRepository.findConflicts(start, end);
    }

    public Optional<GroupMeeting> findGroupMeeting(String name, int duration) {
        return groupMeetingRepository.findByNameAndDuration(name, duration);
    }

    @Transactional
    public Appointment replaceAppointment(Long oldId, Appointment newApp) {
        appointmentRepository.deleteById(oldId);
        return appointmentRepository.save(newApp);
    }

    @Transactional
    public GroupMeeting joinGroupMeeting(Long meetingId, Long userId) {
        GroupMeeting meeting = groupMeetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc họp!"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy user!"));
        
        if (!meeting.getParticipants().contains(user)) {
            meeting.getParticipants().add(user);
        }
        return groupMeetingRepository.save(meeting);
    }
}
