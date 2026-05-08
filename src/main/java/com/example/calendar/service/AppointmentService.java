package com.example.calendar.service;

import com.example.calendar.dto.AppointmentCreateRequest;
import com.example.calendar.dto.AppointmentMemberResponse;
import com.example.calendar.dto.AppointmentMutationResponse;
import com.example.calendar.dto.AppointmentResponse;
import com.example.calendar.dto.ConflictConflictInfo;
import com.example.calendar.dto.ConflictResolutionRequest;
import com.example.calendar.dto.ReminderResponse;
import com.example.calendar.entity.Appointment;
import com.example.calendar.entity.Calendar;
import com.example.calendar.entity.GroupMeeting;
import com.example.calendar.entity.Reminder;
import com.example.calendar.entity.User;
import com.example.calendar.exception.ApiForbiddenException;
import com.example.calendar.exception.ApiNotFoundException;
import com.example.calendar.exception.ApiValidationException;
import com.example.calendar.repository.AppointmentRepository;
import com.example.calendar.repository.ReminderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AppointmentService {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final AppointmentRepository appointmentRepository;
    private final CalendarCoreService calendarCoreService;
    private final ReminderRepository reminderRepository;
    private final ReminderService reminderService;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            CalendarCoreService calendarCoreService,
            ReminderRepository reminderRepository,
            ReminderService reminderService
    ) {
        this.appointmentRepository = appointmentRepository;
        this.calendarCoreService = calendarCoreService;
        this.reminderRepository = reminderRepository;
        this.reminderService = reminderService;
    }

    @Transactional(readOnly = true)
    public boolean checkConflict(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        return appointmentRepository.existsOverlappingAppointmentForUser(userId, startTime, endTime);
    }

    @Transactional(readOnly = true)
    public Appointment findGroupMeeting(String name, LocalDateTime startTime, LocalDateTime endTime) {
        return appointmentRepository.findFirstExactGroupMatch(name, startTime, endTime).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointments(Long userId) {
        calendarCoreService.getUser(userId); // Validate user
        return appointmentRepository.findVisibleAppointmentsByUserId(userId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public AppointmentMutationResponse createAppointment(AppointmentCreateRequest request) {
        ValidatedInput input = validateCreateRequest(request);
        User currentUser = calendarCoreService.getUser(input.userId());
        boolean isGroup  = "GROUP".equalsIgnoreCase(input.type());

        // 1. Kiểm tra xung đột lịch cá nhân (UML: checkConflict)
        List<Appointment> conflicting = appointmentRepository.findOverlappingAppointmentsForUser(
                currentUser.getUserId(), input.startTime(), input.endTime());

        if (!conflicting.isEmpty()) {
            Appointment conflict = conflicting.get(0);
            Appointment pending  = buildAppointment(input, currentUser, isGroup);
            Appointment saved    = appointmentRepository.save(pending);
            return new AppointmentMutationResponse(
                    "CONFLICT_DETECTED",
                    "Lich moi bi trung voi lich co san: " + conflict.getName(),
                    toResponse(saved), saved.getAppointmentId(), saved.getName(), buildConflictInfo(conflict));
        }

        // 2. Auto-merge: tìm GroupMeeting khớp chính xác (UML: findGroupMeeting)
        Appointment matchedGroup = findGroupMeeting(input.name(), input.startTime(), input.endTime());
        if (matchedGroup != null) {
            return new AppointmentMutationResponse(
                    "AUTO_MERGE", "Tim thay nhom hop trung khop.",
                    toResponse(matchedGroup), matchedGroup.getAppointmentId(), matchedGroup.getName(), null);
        }

        // 3. GroupMeeting trùng giờ → gợi ý tham gia
        boolean ignoreGroupConflict = request.ignoreGroupConflict() != null && request.ignoreGroupConflict();
        if (!ignoreGroupConflict) {
            Appointment overlapping = appointmentRepository.findOverlappingGroupsForUserToJoin(
                    currentUser.getUserId(), input.startTime(), input.endTime())
                    .stream().findFirst().orElse(null);

            if (overlapping != null) {
                return new AppointmentMutationResponse(
                        "GROUP_TIME_CONFLICT", "Lich nay bi trung gio voi mot cuoc hop nhom.",
                        toResponse(overlapping), overlapping.getAppointmentId(), overlapping.getName(),
                        buildConflictInfo(overlapping));
            }
        }

        // 4. Lưu lịch mới (UML: addAppointment)
        Appointment appointment = buildAppointment(input, currentUser, isGroup);
        Appointment saved       = appointmentRepository.save(appointment);

        // Cập nhật Calendar entity của user
        Calendar calendar = calendarCoreService.getOrCreateCalendar(currentUser);
        calendar.addAppointment(saved);

        // 5. Tạo Reminder nếu user chọn (UML: addReminder)
        if (request.reminderTime() != null) {
            Reminder reminder = new Reminder();
            reminder.setReminderTime(request.reminderTime());
            reminder.setMessage(request.reminderMessage() != null
                    ? request.reminderMessage() : "Nhac nho: " + saved.getName());
            reminder.setAppointment(saved);
            reminderRepository.save(reminder);
        }

        return new AppointmentMutationResponse(
                "CREATED", "Tao lich thanh cong.",
                toResponse(appointmentRepository.findWithMembersById(saved.getAppointmentId()).orElse(saved)),
                null, null, null);
    }

    @Transactional
    public AppointmentMutationResponse joinAppointment(Long userId, Long appointmentId) {
        User currentUser = calendarCoreService.getUser(userId);
        Appointment appointment = appointmentRepository.findWithMembersById(appointmentId)
                .orElseThrow(() -> new ApiNotFoundException("Khong tim thay lich."));

        if (!(appointment instanceof GroupMeeting groupMeeting)) {
            throw new ApiValidationException("Chi co the tham gia lich nhom (GroupMeeting).");
        }

        // Đã là host hoặc participant
        boolean isHost = appointment.getHost().getUserId().equals(userId);
        boolean isMember = groupMeeting.hasParticipant(currentUser);
        if (isHost || isMember) {
            return new AppointmentMutationResponse(
                    "JOINED", "Ban da o trong nhom nay.", toResponse(appointment), null, null, null);
        }

        // Kiểm tra xung đột lịch cá nhân
        List<Appointment> conflicting = appointmentRepository.findOverlappingAppointmentsForUser(
                userId, appointment.getStartTime(), appointment.getEndTime());

        if (!conflicting.isEmpty()) {
            return new AppointmentMutationResponse(
                    "CONFLICT_DETECTED",
                    "Tham gia nhom bi trung voi lich co san: " + conflicting.get(0).getName(),
                    null, null, null, buildConflictInfo(conflicting.get(0)));
        }

        // UML: GroupMeeting +addParticipant(user: User)
        groupMeeting.addParticipant(currentUser);
        return new AppointmentMutationResponse(
                "JOINED", "Tham gia nhom thanh cong.",
                toResponse(appointmentRepository.save(groupMeeting)), null, null, null);
    }

    @Transactional
    public AppointmentMutationResponse resolveConflict(ConflictResolutionRequest request) {
        validateConflictResolutionRequest(request);
        User currentUser = calendarCoreService.getUser(request.userId());

        Appointment conflictingAppointment = appointmentRepository.findById(request.conflictingAppointmentId())
                .orElseThrow(() -> new ApiNotFoundException("Khong tim thay lich xung dot."));

        Appointment newAppointment = null;
        if (request.newAppointmentId() != null) {
            newAppointment = appointmentRepository.findById(request.newAppointmentId())
                    .orElseThrow(() -> new ApiNotFoundException("Khong tim thay lich moi."));
        }

        if (request.replaceExisting()) {
            if (conflictingAppointment instanceof GroupMeeting gm) {
                // Rời nhóm
                gm.removeParticipant(currentUser);
                appointmentRepository.save(gm);
            } else {
                // Xóa lịch cá nhân
                if (!conflictingAppointment.getHost().getUserId().equals(request.userId())) {
                    throw new ApiForbiddenException("Chi co the xoa lich ca nhan cua chinh ban.");
                }
                appointmentRepository.delete(conflictingAppointment);
                appointmentRepository.flush();
            }

            if (newAppointment instanceof GroupMeeting gm) {
                gm.addParticipant(currentUser);
                return new AppointmentMutationResponse("CONFLICT_RESOLVED",
                        "Da xoa lich xung dot va tham gia nhom moi.",
                        toResponse(appointmentRepository.save(gm)), null, null, null);
            }
            if (newAppointment != null) {
                return new AppointmentMutationResponse("CONFLICT_RESOLVED",
                        "Da xoa lich xung dot va tao lich moi.",
                        toResponse(newAppointment), null, null, null);
            }
            return new AppointmentMutationResponse("CONFLICT_RESOLVED",
                    "Da xoa lich xung dot.", null, null, null, null);
        } else {
            if (newAppointment != null) appointmentRepository.delete(newAppointment);
            return new AppointmentMutationResponse("CONFLICT_CANCELLED",
                    "Da huy hanh dong. Lich xung dot van con.",
                    toResponse(conflictingAppointment), null, null, null);
        }
    }

    @Transactional
    public AppointmentMutationResponse deleteOrLeaveAppointment(Long userId, Long appointmentId) {
        User currentUser = calendarCoreService.getUser(userId);
        Appointment appointment = appointmentRepository.findWithMembersById(appointmentId)
                .orElseThrow(() -> new ApiNotFoundException("Khong tim thay lich."));

        boolean isHost = appointment.getHost().getUserId().equals(userId);

        if (!(appointment instanceof GroupMeeting gm)) {
            if (!isHost) throw new ApiForbiddenException("Chi host moi duoc xoa lich ca nhan.");
            appointmentRepository.delete(appointment);
            return new AppointmentMutationResponse("DELETED", "Da xoa lich.", null, null, null, null);
        }

        if (isHost) {
            appointmentRepository.delete(appointment);
            return new AppointmentMutationResponse("DELETED", "Da xoa nhom.", null, null, null, null);
        }

        // Rời nhóm (UML: User +joinGroupMeeting inverse)
        gm.removeParticipant(currentUser);
        appointmentRepository.save(gm);
        return new AppointmentMutationResponse("LEFT_GROUP", "Da roi nhom.", null, null, null, null);
    }

    // ─── Private Helpers ───────────────────────────────────────────────────────

    private AppointmentResponse toResponse(Appointment appointment) {
        List<AppointmentMemberResponse> members = new ArrayList<>();
        members.add(new AppointmentMemberResponse(
                appointment.getHost().getUserId(),
                appointment.getHost().getName(),
                true));

        if (appointment instanceof GroupMeeting gm) {
            gm.getParticipantsList().stream()
                    .filter(u -> !u.getUserId().equals(appointment.getHost().getUserId()))
                    .sorted(Comparator.comparing(User::getName, String.CASE_INSENSITIVE_ORDER))
                    .map(u -> new AppointmentMemberResponse(u.getUserId(), u.getName(), false))
                    .forEach(members::add);
        }

        List<ReminderResponse> reminders = appointment.getReminders().stream()
                .map(reminderService::toReminderResponse).toList();

        String dtype = appointment instanceof GroupMeeting ? "GROUP" : "PERSONAL";

        return new AppointmentResponse(
                appointment.getAppointmentId(),
                appointment.getName(),
                appointment.getLocation(),
                appointment.getStartTime(),
                appointment.getEndTime(),
                appointment.getDuration(),
                dtype,
                appointment.getHost().getUserId(),
                appointment.getHost().getName(),
                members,
                reminders);
    }

    private ConflictConflictInfo buildConflictInfo(Appointment a) {
        return new ConflictConflictInfo(
                a.getAppointmentId(), a.getName(),
                a.getStartTime().format(FORMATTER), a.getEndTime().format(FORMATTER));
    }

    private Appointment buildAppointment(ValidatedInput input, User host, boolean isGroup) {
        Appointment a = isGroup ? new GroupMeeting() : new Appointment();
        a.setName(input.name());
        a.setLocation(input.location());
        a.setStartTime(input.startTime());
        a.setEndTime(input.endTime());
        a.setHost(host);
        return a;
    }

    private ValidatedInput validateCreateRequest(AppointmentCreateRequest request) {
        if (request == null)          throw new ApiValidationException("Request body la bat buoc.");
        if (request.userId() == null) throw new ApiValidationException("userId la bat buoc.");

        String name     = requireNonBlank(request.name(), "name la bat buoc.");
        String location = request.location() != null ? request.location().trim() : "";

        if (request.startTime() == null || request.endTime() == null)
            throw new ApiValidationException("startTime va endTime la bat buoc.");
        if (!request.startTime().isBefore(request.endTime()))
            throw new ApiValidationException("startTime phai nho hon endTime.");

        String type = request.type() != null ? request.type().toUpperCase() : "PERSONAL";
        return new ValidatedInput(request.userId(), name, location, request.startTime(), request.endTime(), type);
    }

    private String requireNonBlank(String value, String msg) {
        String v = value == null ? null : value.trim();
        if (v == null || v.isEmpty()) throw new ApiValidationException(msg);
        return v;
    }

    private void validateConflictResolutionRequest(ConflictResolutionRequest request) {
        if (request == null)                           throw new ApiValidationException("Request body la bat buoc.");
        if (request.userId() == null)                  throw new ApiValidationException("userId la bat buoc.");
        if (request.conflictingAppointmentId() == null) throw new ApiValidationException("conflictingAppointmentId la bat buoc.");
    }

    private record ValidatedInput(Long userId, String name, String location,
                                  LocalDateTime startTime, LocalDateTime endTime, String type) {}
}
