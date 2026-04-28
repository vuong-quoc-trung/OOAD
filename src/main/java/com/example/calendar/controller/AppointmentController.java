package com.example.calendar.controller;

import com.example.calendar.dto.AppointmentCreateRequest;
import com.example.calendar.dto.AppointmentMutationResponse;
import com.example.calendar.dto.AppointmentResponse;
import com.example.calendar.dto.UserIdRequest;
import com.example.calendar.service.AppointmentService;
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

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {
    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public List<AppointmentResponse> getAppointments(@RequestParam Long userId) {
        return appointmentService.getAppointments(userId);
    }

    @PostMapping
    public ResponseEntity<AppointmentMutationResponse> createAppointment(
        @RequestBody AppointmentCreateRequest request
    ) {
        AppointmentMutationResponse response = appointmentService.createAppointment(request);
        HttpStatus status = "AUTO_MERGE".equals(response.code()) ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/{appointmentId}/join")
    public AppointmentMutationResponse joinAppointment(
        @RequestParam(required = false) Long userId,
        @RequestBody(required = false) UserIdRequest request,
        @PathVariable Long appointmentId
    ) {
        return appointmentService.joinAppointment(resolveUserId(userId, request), appointmentId);
    }

    @DeleteMapping("/{appointmentId}")
    public AppointmentMutationResponse deleteOrLeaveAppointment(
        @RequestParam(required = false) Long userId,
        @RequestBody(required = false) UserIdRequest request,
        @PathVariable Long appointmentId
    ) {
        return appointmentService.deleteOrLeaveAppointment(resolveUserId(userId, request), appointmentId);
    }

    private Long resolveUserId(Long queryUserId, UserIdRequest request) {
        if (queryUserId != null) {
            return queryUserId;
        }
        return request == null ? null : request.userId();
    }
}
