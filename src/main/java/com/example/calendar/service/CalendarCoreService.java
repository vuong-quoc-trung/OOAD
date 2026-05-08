package com.example.calendar.service;

import com.example.calendar.entity.Calendar;
import com.example.calendar.entity.User;
import com.example.calendar.exception.ApiNotFoundException;
import com.example.calendar.exception.ApiValidationException;
import com.example.calendar.repository.CalendarRepository;
import com.example.calendar.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class CalendarCoreService {

    private final UserRepository userRepository;
    private final CalendarRepository calendarRepository;

    public CalendarCoreService(UserRepository userRepository, CalendarRepository calendarRepository) {
        this.userRepository = userRepository;
        this.calendarRepository = calendarRepository;
    }

    /**
     * Lấy User theo ID, quăng lỗi nếu không thấy.
     */
    public User getUser(Long userId) {
        if (userId == null) throw new ApiValidationException("userId la bat buoc.");
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiNotFoundException("Khong tim thay user."));
    }

    /**
     * Lấy (hoặc tạo mới) Calendar của user.
     */
    public Calendar getOrCreateCalendar(User user) {
        return calendarRepository.findByOwner_UserId(user.getUserId())
                .orElseGet(() -> {
                    Calendar cal = new Calendar();
                    cal.setOwner(user);
                    return calendarRepository.save(cal);
                });
    }
}
