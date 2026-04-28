package com.example.calendar.config;

import com.example.calendar.entity.Appointment;
import com.example.calendar.entity.AppointmentType;
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
    public void run(String... args) {
        User alice = createUser("alice", "123");
        User bob = createUser("bob", "123");
        createUser("charlie", "123");
        createSampleGroup(bob);
        createSamplePersonal(alice);
    }

    private User createUser(String username, String password) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            User user = new User();
            user.setUsername(username);
            user.setPassword(password);
            return userRepository.save(user);
        });
    }

    private void createSampleGroup(User host) {
        if (appointmentRepository.existsByTitleIgnoreCaseAndType("Daily Meeting", AppointmentType.GROUP)) {
            return;
        }

        Appointment appointment = new Appointment();
        appointment.setTitle("Daily Meeting");
        appointment.setStartTime(LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0));
        appointment.setEndTime(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0));
        appointment.setType(AppointmentType.GROUP);
        appointment.setHost(host);
        appointmentRepository.save(appointment);
    }

    private void createSamplePersonal(User host) {
        if (appointmentRepository.existsByTitleIgnoreCaseAndType("Personal Task", AppointmentType.PERSONAL)) {
            return;
        }

        Appointment appointment = new Appointment();
        appointment.setTitle("Personal Task");
        appointment.setStartTime(LocalDateTime.now().plusDays(2).withHour(14).withMinute(0).withSecond(0).withNano(0));
        appointment.setEndTime(LocalDateTime.now().plusDays(2).withHour(15).withMinute(0).withSecond(0).withNano(0));
        appointment.setType(AppointmentType.PERSONAL);
        appointment.setHost(host);
        appointmentRepository.save(appointment);
    }
}
