package com.example.calendar.controller;

import com.example.calendar.entity.Appointment;
import com.example.calendar.entity.GroupMeeting;
import com.example.calendar.service.CalendarService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/appointments")
public class CalendarController {
    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping
    public List<Appointment> getAppointments() {
        return calendarService.getAllAppointments();
    }

    @PostMapping
    public ResponseEntity<?> addAppointment(@RequestBody Appointment appointment) {
        try {
            Optional<GroupMeeting> existingMeeting = calendarService.findGroupMeeting(appointment.getName(), appointment.getDuration());
            if (existingMeeting.isPresent()) {
                Map<String, Object> res = new HashMap<>();
                res.put("message", "Phát hiện một cuộc họp nhóm có cùng tên và thời lượng.");
                res.put("type", "GROUP_JOIN");
                res.put("meetingId", existingMeeting.get().getId());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(res);
            }
            
            Appointment saved = calendarService.addAppointment(appointment);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
            
        } catch (RuntimeException e) {
            if (e.getMessage().startsWith("CONFLICT_TIME:")) {
                String conflictId = e.getMessage().split(":")[1];
                Map<String, Object> res = new HashMap<>();
                res.put("message", "Thời gian này đã có lịch trình khác.");
                res.put("type", "TIME_CONFLICT");
                res.put("conflictId", conflictId);
                return ResponseEntity.status(HttpStatus.CONFLICT).body(res);
            }
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/replace/{id}")
    public ResponseEntity<?> replaceAppointment(@PathVariable Long id, @RequestBody Appointment newApp) {
        Appointment replaced = calendarService.replaceAppointment(id, newApp);
        return ResponseEntity.ok(replaced);
    }

    @PostMapping("/join-group")
    public ResponseEntity<?> joinGroupMeeting(@RequestParam Long meetingId, @RequestParam Long userId) {
        GroupMeeting meeting = calendarService.joinGroupMeeting(meetingId, userId);
        return ResponseEntity.ok(meeting);
    }
}
