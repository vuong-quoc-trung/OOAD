package com.example.calendar.service;

import com.example.calendar.dto.AppointmentCreateRequest;
import com.example.calendar.dto.AppointmentMemberResponse;
import com.example.calendar.dto.AppointmentMutationResponse;
import com.example.calendar.dto.AppointmentResponse;
import com.example.calendar.entity.Appointment;
import com.example.calendar.entity.AppointmentType;
import com.example.calendar.entity.GroupMember;
import com.example.calendar.entity.User;
import com.example.calendar.exception.ApiConflictException;
import com.example.calendar.exception.ApiForbiddenException;
import com.example.calendar.exception.ApiNotFoundException;
import com.example.calendar.exception.ApiValidationException;
import com.example.calendar.repository.AppointmentRepository;
import com.example.calendar.repository.GroupMemberRepository;
import com.example.calendar.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AppointmentService {
    private static final String CONFLICT_MESSAGE = "Ban da co lich trong gio nay!";

    private final AppointmentRepository appointmentRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    public AppointmentService(
        AppointmentRepository appointmentRepository,
        GroupMemberRepository groupMemberRepository,
        UserRepository userRepository
    ) {
        this.appointmentRepository = appointmentRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointments(Long userId) {
        getUser(userId);
        return appointmentRepository.findVisibleAppointmentsByUserId(userId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public AppointmentMutationResponse createAppointment(AppointmentCreateRequest request) {
        ValidAppointment validAppointment = validateCreateRequest(request);
        User currentUser = getUser(validAppointment.userId());

        if (appointmentRepository.existsOverlappingAppointmentForUser(
            currentUser.getId(),
            validAppointment.startTime(),
            validAppointment.endTime()
        )) {
            throw new ApiConflictException(CONFLICT_MESSAGE);
        }

        Appointment matchedGroup = appointmentRepository.findFirstExactGroupMatch(
            validAppointment.title(),
            validAppointment.startTime(),
            validAppointment.endTime()
        ).orElse(null);

        if (matchedGroup != null) {
            return new AppointmentMutationResponse(
                "AUTO_MERGE",
                "Tim thay nhom hop trung khop.",
                toResponse(matchedGroup),
                matchedGroup.getId(),
                matchedGroup.getTitle()
            );
        }

        Appointment overlappingGroup = appointmentRepository.findOverlappingGroupsForUserToJoin(
            currentUser.getId(),
            validAppointment.startTime(),
            validAppointment.endTime()
        ).stream().findFirst().orElse(null);

        if (overlappingGroup != null) {
            return new AppointmentMutationResponse(
                "GROUP_TIME_CONFLICT",
                "Lich nay bi trung gio voi mot cuoc hop nhom.",
                toResponse(overlappingGroup),
                overlappingGroup.getId(),
                overlappingGroup.getTitle()
            );
        }

        Appointment appointment = new Appointment();
        appointment.setTitle(validAppointment.title());
        appointment.setStartTime(validAppointment.startTime());
        appointment.setEndTime(validAppointment.endTime());
        appointment.setType(validAppointment.type());
        appointment.setHost(currentUser);

        Appointment savedAppointment = appointmentRepository.save(appointment);
        return new AppointmentMutationResponse(
            "CREATED",
            "Tao lich thanh cong.",
            toResponse(savedAppointment),
            null,
            null
        );
    }

    @Transactional
    public AppointmentMutationResponse joinAppointment(Long userId, Long appointmentId) {
        User currentUser = getUser(userId);
        Appointment appointment = appointmentRepository.findWithMembersById(appointmentId)
            .orElseThrow(() -> new ApiNotFoundException("Khong tim thay lich."));

        if (appointment.getType() != AppointmentType.GROUP) {
            throw new ApiValidationException("Chi co the tham gia lich nhom.");
        }

        if (appointment.getHost().getId().equals(userId)
            || groupMemberRepository.existsByAppointment_IdAndUser_Id(appointmentId, userId)) {
            return new AppointmentMutationResponse(
                "JOINED",
                "Ban da o trong nhom nay.",
                toResponse(appointment),
                null,
                null
            );
        }

        if (appointmentRepository.existsOverlappingAppointmentForUser(
            userId,
            appointment.getStartTime(),
            appointment.getEndTime()
        )) {
            throw new ApiConflictException(CONFLICT_MESSAGE);
        }

        appointment.addGroupMember(currentUser);
        Appointment savedAppointment = appointmentRepository.save(appointment);

        return new AppointmentMutationResponse(
            "JOINED",
            "Tham gia nhom thanh cong.",
            toResponse(savedAppointment),
            null,
            null
        );
    }

    @Transactional
    public AppointmentMutationResponse deleteOrLeaveAppointment(Long userId, Long appointmentId) {
        getUser(userId);
        Appointment appointment = appointmentRepository.findWithMembersById(appointmentId)
            .orElseThrow(() -> new ApiNotFoundException("Khong tim thay lich."));

        boolean isHost = appointment.getHost().getId().equals(userId);

        if (appointment.getType() == AppointmentType.PERSONAL) {
            if (!isHost) {
                throw new ApiForbiddenException("Chi host moi duoc xoa lich ca nhan.");
            }
            appointmentRepository.delete(appointment);
            return new AppointmentMutationResponse("DELETED", "Da xoa lich.", null, null, null);
        }

        if (isHost) {
            appointmentRepository.delete(appointment);
            return new AppointmentMutationResponse("DELETED", "Da xoa nhom.", null, null, null);
        }

        GroupMember groupMember = groupMemberRepository.findByAppointment_IdAndUser_Id(appointmentId, userId)
            .orElseThrow(() -> new ApiForbiddenException("Ban khong phai thanh vien cua nhom nay."));
        groupMemberRepository.delete(groupMember);

        return new AppointmentMutationResponse("LEFT_GROUP", "Da roi nhom.", null, null, null);
    }

    private AppointmentResponse toResponse(Appointment appointment) {
        List<AppointmentMemberResponse> members = new ArrayList<>();
        members.add(new AppointmentMemberResponse(
            appointment.getHost().getId(),
            appointment.getHost().getUsername(),
            true
        ));

        appointment.getGroupMembers().stream()
            .map(GroupMember::getUser)
            .filter(user -> user != null && !user.getId().equals(appointment.getHost().getId()))
            .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
            .map(user -> new AppointmentMemberResponse(user.getId(), user.getUsername(), false))
            .forEach(members::add);

        return new AppointmentResponse(
            appointment.getId(),
            appointment.getTitle(),
            appointment.getStartTime(),
            appointment.getEndTime(),
            appointment.getType(),
            appointment.getHost().getId(),
            appointment.getHost().getUsername(),
            members
        );
    }

    private ValidAppointment validateCreateRequest(AppointmentCreateRequest request) {
        if (request == null) {
            throw new ApiValidationException("Request body la bat buoc.");
        }

        if (request.userId() == null) {
            throw new ApiValidationException("userId la bat buoc.");
        }

        String title = normalizeRequiredText(request.title(), "title la bat buoc.");
        if (request.startTime() == null || request.endTime() == null) {
            throw new ApiValidationException("startTime va endTime la bat buoc.");
        }

        if (!request.startTime().isBefore(request.endTime())) {
            throw new ApiValidationException("startTime phai nho hon endTime.");
        }

        AppointmentType type = request.type() == null ? AppointmentType.PERSONAL : request.type();
        return new ValidAppointment(request.userId(), title, request.startTime(), request.endTime(), type);
    }

    private User getUser(Long userId) {
        if (userId == null) {
            throw new ApiValidationException("userId la bat buoc.");
        }
        return userRepository.findById(userId)
            .orElseThrow(() -> new ApiNotFoundException("Khong tim thay user."));
    }

    private String normalizeRequiredText(String value, String message) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new ApiValidationException(message);
        }
        return normalized;
    }

    private record ValidAppointment(
        Long userId,
        String title,
        LocalDateTime startTime,
        LocalDateTime endTime,
        AppointmentType type
    ) {
    }
}
