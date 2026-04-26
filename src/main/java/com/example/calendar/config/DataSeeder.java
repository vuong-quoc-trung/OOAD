package com.example.calendar.config;

import com.example.calendar.entity.Appointment;
import com.example.calendar.entity.GroupMeeting;
import com.example.calendar.entity.User;
import com.example.calendar.repository.AppointmentRepository;
import com.example.calendar.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;

    public DataSeeder(UserRepository userRepository, AppointmentRepository appointmentRepository) {
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        User user = new User();
        user.setUsername("john_doe");
        userRepository.save(user);

        Appointment personalApp = new Appointment();
        personalApp.setName("Khám nha khoa");
        personalApp.setLocation("Phòng khám ABC");
        personalApp.setStartTime(LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0));
        personalApp.setEndTime(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0));
        personalApp.setDuration(60);
        appointmentRepository.save(personalApp);

        GroupMeeting meeting = new GroupMeeting();
        meeting.setName("Sprint Planning");
        meeting.setLocation("Phòng họp số 1");
        meeting.setStartTime(LocalDateTime.now().plusDays(2).withHour(14).withMinute(0).withSecond(0).withNano(0));
        meeting.setEndTime(LocalDateTime.now().plusDays(2).withHour(15).withMinute(0).withSecond(0).withNano(0));
        meeting.setDuration(60);
        appointmentRepository.save(meeting);
        
        System.out.println("✅ Đã chèn dữ liệu mẫu vào H2 Database!");
    }
}
