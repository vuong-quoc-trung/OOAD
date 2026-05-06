package com.example.calendar.service;

import com.example.calendar.dto.AppointmentCreateRequest;
import com.example.calendar.dto.AppointmentMemberResponse;
import com.example.calendar.dto.AppointmentMutationResponse;
import com.example.calendar.dto.AppointmentResponse;
import com.example.calendar.dto.ConflictConflictInfo;
import com.example.calendar.dto.ConflictResolutionRequest;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AppointmentService {
    private static final String CONFLICT_MESSAGE = "Ban da co lich trong gio nay!";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

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

        // Kiểm tra xung đột với lịch cá nhân của người dùng TRƯỚC khi lưu
        List<Appointment> conflictingAppointments = appointmentRepository.findOverlappingAppointmentsForUser(
            currentUser.getId(),
            validAppointment.startTime(),
            validAppointment.endTime()
        );

        if (!conflictingAppointments.isEmpty()) {
            // Trả về thông tin xung đột, chưa lưu lịch mới vào DB
            Appointment conflicting = conflictingAppointments.get(0);
            ConflictConflictInfo conflictInfo = new ConflictConflictInfo(
                conflicting.getId(),
                conflicting.getTitle(),
                conflicting.getStartTime().format(DATE_TIME_FORMATTER),
                conflicting.getEndTime().format(DATE_TIME_FORMATTER)
            );
            // Lưu lịch mới tạm thời để resolve sau
            Appointment pendingAppointment = new Appointment();
            pendingAppointment.setTitle(validAppointment.title());
            pendingAppointment.setStartTime(validAppointment.startTime());
            pendingAppointment.setEndTime(validAppointment.endTime());
            pendingAppointment.setType(validAppointment.type());
            pendingAppointment.setHost(currentUser);
            Appointment savedPending = appointmentRepository.save(pendingAppointment);
            return new AppointmentMutationResponse(
                "CONFLICT_DETECTED",
                "Lich moi bi trung voi lich co san: " + conflicting.getTitle(),
                toResponse(savedPending),
                savedPending.getId(),
                validAppointment.title(),
                conflictInfo
            );
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
                matchedGroup.getTitle(),
                null
            );
        }

        Appointment overlappingGroup = appointmentRepository.findOverlappingGroupsForUserToJoin(
            currentUser.getId(),
            validAppointment.startTime(),
            validAppointment.endTime()
        ).stream().findFirst().orElse(null);

        if (overlappingGroup != null) {
            ConflictConflictInfo conflictInfo = new ConflictConflictInfo(
                overlappingGroup.getId(),
                overlappingGroup.getTitle(),
                overlappingGroup.getStartTime().format(DATE_TIME_FORMATTER),
                overlappingGroup.getEndTime().format(DATE_TIME_FORMATTER)
            );
            return new AppointmentMutationResponse(
                "GROUP_TIME_CONFLICT",
                "Lich nay bi trung gio voi mot cuoc hop nhom.",
                toResponse(overlappingGroup),
                overlappingGroup.getId(),
                overlappingGroup.getTitle(),
                conflictInfo
            );
        }

        // Chỉ lưu lịch một lần duy nhất sau khi tất cả kiểm tra đã pass
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
                null,
                null
            );
        }

        // Kiểm tra xung đột với lịch cá nhân
        List<Appointment> conflictingAppointments = appointmentRepository.findOverlappingAppointmentsForUser(
            userId,
            appointment.getStartTime(),
            appointment.getEndTime()
        );

        if (!conflictingAppointments.isEmpty()) {
            Appointment conflicting = conflictingAppointments.get(0);
            ConflictConflictInfo conflictInfo = new ConflictConflictInfo(
                conflicting.getId(),
                conflicting.getTitle(),
                conflicting.getStartTime().format(DATE_TIME_FORMATTER),
                conflicting.getEndTime().format(DATE_TIME_FORMATTER)
            );
            return new AppointmentMutationResponse(
                "CONFLICT_DETECTED",
                "Tham gia nhom bi trung voi lich co san: " + conflicting.getTitle(),
                null,
                null,
                null,
                conflictInfo
            );
        }

        appointment.addGroupMember(currentUser);
        Appointment savedAppointment = appointmentRepository.save(appointment);

        return new AppointmentMutationResponse(
            "JOINED",
            "Tham gia nhom thanh cong.",
            toResponse(savedAppointment),
            null,
            null,
            null
        );
    }

    /**
     * Xử lý quyết định giải quyết xung đột lịch
     * @param request chứa thông tin: userId, newAppointmentId, conflictingAppointmentId, replaceExisting
     */
    @Transactional
    public AppointmentMutationResponse resolveConflict(ConflictResolutionRequest request) {
        validateConflictResolutionRequest(request);
        User currentUser = getUser(request.userId());

        Appointment newAppointment = null;
        Appointment conflictingAppointment = appointmentRepository.findById(request.conflictingAppointmentId())
            .orElseThrow(() -> new ApiNotFoundException("Khong tim thay lich xung dot."));

        // Nếu newAppointmentId được cung cấp (khi tạo lịch mới)
        if (request.newAppointmentId() != null) {
            newAppointment = appointmentRepository.findById(request.newAppointmentId())
                .orElseThrow(() -> new ApiNotFoundException("Khong tim thay lich moi."));
        }

        if (request.replaceExisting()) {
            // Người dùng chọn xóa lịch cũ
            if (conflictingAppointment.getType() == AppointmentType.PERSONAL) {
                if (!conflictingAppointment.getHost().getId().equals(request.userId())) {
                    throw new ApiForbiddenException("Chi co the xoa lich ca nhan cua chinh ban.");
                }
                // Xóa lịch cũ trước
                appointmentRepository.delete(conflictingAppointment);
                appointmentRepository.flush();
            } else {
                // Nếu là lịch nhóm, chỉ rời nhóm
                GroupMember groupMember = groupMemberRepository.findByAppointment_IdAndUser_Id(
                    request.conflictingAppointmentId(), 
                    request.userId()
                ).orElseThrow(() -> new ApiForbiddenException("Ban khong phai thanh vien cua nhom nay."));
                groupMemberRepository.delete(groupMember);
                groupMemberRepository.flush();
            }

            // Nếu là yêu cầu tham gia nhóm mới (newAppointment là nhóm muốn join)
            if (newAppointment != null && newAppointment.getType() == AppointmentType.GROUP) {
                // Xóa lịch pending tạm thời (nếu có, và khác với nhóm muốn join)
                // newAppointment ở đây là nhóm muốn join, không xóa nó
                newAppointment.addGroupMember(currentUser);
                Appointment savedAppointment = appointmentRepository.save(newAppointment);
                return new AppointmentMutationResponse(
                    "CONFLICT_RESOLVED",
                    "Da xoa lich xung dot va tham gia nhom moi.",
                    toResponse(savedAppointment),
                    null,
                    null,
                    null
                );
            }

            // Nếu có lịch mới pending (tạm thời lưu khi CONFLICT_DETECTED), giữ lại lịch đó
            if (newAppointment != null) {
                return new AppointmentMutationResponse(
                    "CONFLICT_RESOLVED",
                    "Da xoa lich xung dot va tao lich moi.",
                    toResponse(newAppointment),
                    null,
                    null,
                    null
                );
            }

            return new AppointmentMutationResponse(
                "CONFLICT_RESOLVED",
                "Da xoa lich xung dot.",
                null,
                null,
                null,
                null
            );
        } else {
            // Người dùng chọn giữ lịch cũ, xóa lịch mới pending nếu có
            if (newAppointment != null) {
                appointmentRepository.delete(newAppointment);
            }
            return new AppointmentMutationResponse(
                "CONFLICT_CANCELLED",
                "Da huy hanh dong. Lich xung dot van con.",
                toResponse(conflictingAppointment),
                null,
                null,
                null
            );
        }
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
            return new AppointmentMutationResponse("DELETED", "Da xoa lich.", null, null, null, null);
        }

        if (isHost) {
            appointmentRepository.delete(appointment);
            return new AppointmentMutationResponse("DELETED", "Da xoa nhom.", null, null, null, null);
        }

        GroupMember groupMember = groupMemberRepository.findByAppointment_IdAndUser_Id(appointmentId, userId)
            .orElseThrow(() -> new ApiForbiddenException("Ban khong phai thanh vien cua nhom nay."));
        appointment.getGroupMembers().remove(groupMember);
        appointmentRepository.save(appointment);

        return new AppointmentMutationResponse("LEFT_GROUP", "Da roi nhom.", null, null, null, null);
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

    private void validateConflictResolutionRequest(ConflictResolutionRequest request) {
        if (request == null) {
            throw new ApiValidationException("Request body la bat buoc.");
        }
        if (request.userId() == null) {
            throw new ApiValidationException("userId la bat buoc.");
        }
        if (request.conflictingAppointmentId() == null) {
            throw new ApiValidationException("conflictingAppointmentId la bat buoc.");
        }
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
