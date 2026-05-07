package com.example.calendar.controller;

import com.example.calendar.dto.AppointmentCreateRequest;
import com.example.calendar.dto.AppointmentMutationResponse;
import com.example.calendar.dto.AppointmentResponse;
import com.example.calendar.dto.ConflictResolutionRequest;
import com.example.calendar.dto.UserIdRequest;
import com.example.calendar.service.CalendarService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AppointmentController — điểm vào REST cho Calendar / Appointment.
 * Delegate toàn bộ sang CalendarService (lớp Calendar trong UML).
 */
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final CalendarService calendarService;

    public AppointmentController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping
    public List<AppointmentResponse> getAppointments(@RequestParam Long userId) {
        return calendarService.getAppointments(userId);
    }

    @PostMapping
    public ResponseEntity<AppointmentMutationResponse> createAppointment(
            @RequestBody AppointmentCreateRequest request
    ) {
        AppointmentMutationResponse response = calendarService.createAppointment(request);
        return ResponseEntity.status(resolveStatus(response.code())).body(response);
    }

    @PostMapping("/{appointmentId}/join")
    public AppointmentMutationResponse joinAppointment(
            @RequestParam(required = false) Long userId,
            @RequestBody(required = false) UserIdRequest request,
            @PathVariable Long appointmentId
    ) {
        return calendarService.joinAppointment(resolveUserId(userId, request), appointmentId);
    }

    @PostMapping("/conflict/resolve")
    public AppointmentMutationResponse resolveConflict(@RequestBody ConflictResolutionRequest request) {
        return calendarService.resolveConflict(request);
    }

    @DeleteMapping("/{appointmentId}")
    public AppointmentMutationResponse deleteOrLeaveAppointment(
            @RequestParam(required = false) Long userId,
            @RequestBody(required = false) UserIdRequest request,
            @PathVariable Long appointmentId
    ) {
        return calendarService.deleteOrLeaveAppointment(resolveUserId(userId, request), appointmentId);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private Long resolveUserId(Long queryUserId, UserIdRequest request) {
        return queryUserId != null ? queryUserId : (request == null ? null : request.userId());
    }

    private HttpStatus resolveStatus(String code) {
        return switch (code) {
            case "CREATED"           -> HttpStatus.CREATED;
            case "CONFLICT_DETECTED",
                 "GROUP_TIME_CONFLICT" -> HttpStatus.CONFLICT;
            case "DELETED",
                 "LEFT_GROUP"        -> HttpStatus.NO_CONTENT;
            default                  -> HttpStatus.OK;
        };
    }
}
