package com.example.calendar.config;

import com.example.calendar.entity.Appointment;
import com.example.calendar.entity.GroupMeeting;
import com.example.calendar.entity.User;
import com.example.calendar.repository.AppointmentRepository;
import com.example.calendar.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * DataSeeder — tạo dữ liệu mẫu khi khởi động.
 * Sử dụng GroupMeeting (thay AppointmentType.GROUP) theo sơ đồ UML.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;

    public DataSeeder(UserRepository userRepository, AppointmentRepository appointmentRepository) {
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Override
    public void run(String... args) {
        User alice   = createUser("alice",   "123");
        User bob     = createUser("bob",     "123");
        createUser("charlie", "123");
        createSampleGroupMeeting(bob);
        createSamplePersonal(alice);
    }

    private User createUser(String username, String password) {
        return userRepository.findByName(username).orElseGet(() -> {
            User user = new User();
            user.setUsername(username);
            user.setPassword(password);
            return userRepository.save(user);
        });
    }

    /** Seed GroupMeeting (kế thừa Appointment, dtype = "GROUP") */
    private void createSampleGroupMeeting(User host) {
        // Kiểm tra tồn tại bằng discriminator dtype = "GROUP"
        if (appointmentRepository.existsByNameIgnoreCaseAndDtype("Daily Meeting", "GROUP")) {
            return;
        }
        GroupMeeting meeting = new GroupMeeting();
        meeting.setName("Daily Meeting");
        meeting.setLocation("Phòng họp A");
        meeting.setStartTime(LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0));
        meeting.setEndTime(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0));
        meeting.setHost(host);
        appointmentRepository.save(meeting);
    }

    /** Seed Appointment cá nhân (dtype = "PERSONAL") */
    private void createSamplePersonal(User host) {
        if (appointmentRepository.existsByNameIgnoreCaseAndDtype("Personal Task", "PERSONAL")) {
            return;
        }
        Appointment appointment = new Appointment();
        appointment.setName("Personal Task");
        appointment.setLocation("Văn phòng");
        appointment.setStartTime(LocalDateTime.now().plusDays(2).withHour(14).withMinute(0).withSecond(0).withNano(0));
        appointment.setEndTime(LocalDateTime.now().plusDays(2).withHour(15).withMinute(0).withSecond(0).withNano(0));
        appointment.setHost(host);
        appointmentRepository.save(appointment);
    }
}
