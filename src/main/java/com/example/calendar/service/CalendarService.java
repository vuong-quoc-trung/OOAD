package com.example.calendar.service;

import com.example.calendar.dto.AppointmentCreateRequest;
import com.example.calendar.dto.AppointmentMemberResponse;
import com.example.calendar.dto.AppointmentMutationResponse;
import com.example.calendar.dto.AppointmentResponse;
import com.example.calendar.dto.ConflictConflictInfo;
import com.example.calendar.dto.ConflictResolutionRequest;
import com.example.calendar.dto.ReminderCreateRequest;
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
import com.example.calendar.repository.CalendarRepository;
import com.example.calendar.repository.ReminderRepository;
import com.example.calendar.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * CalendarService — Service triển khai nghiệp vụ của lớp Calendar (UML).
 *
 * Quan hệ UML được phản ánh:
 *   UI (AppointmentWindow) ─ Calls ─ Calendar (service này)
 *   Calendar ─ Manages ─ Appointment (via AppointmentRepository)
 *   Calendar ─ Manages ─ Reminder    (via ReminderRepository)
 */
@Service
public class CalendarService {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final AppointmentRepository appointmentRepository;
    private final UserRepository         userRepository;
    private final ReminderRepository     reminderRepository;
    private final CalendarRepository     calendarRepository;

    public CalendarService(
            AppointmentRepository appointmentRepository,
            UserRepository userRepository,
            ReminderRepository reminderRepository,
            CalendarRepository calendarRepository
    ) {
        this.appointmentRepository = appointmentRepository;
        this.userRepository        = userRepository;
        this.reminderRepository    = reminderRepository;
        this.calendarRepository    = calendarRepository;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Calendar entity helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /** Lấy (hoặc tạo mới) Calendar của user. */
    private Calendar getOrCreateCalendar(User user) {
        return calendarRepository.findByOwner_UserId(user.getUserId())
                .orElseGet(() -> {
                    Calendar cal = new Calendar();
                    cal.setOwner(user);
                    return calendarRepository.save(cal);
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UML: Calendar +checkConflict(startTime, endTime): boolean
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public boolean checkConflict(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        return appointmentRepository.existsOverlappingAppointmentForUser(userId, startTime, endTime);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UML: Calendar +findGroupMeeting(name, duration): GroupMeeting
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Appointment findGroupMeeting(String name, LocalDateTime startTime, LocalDateTime endTime) {
        return appointmentRepository.findFirstExactGroupMatch(name, startTime, endTime).orElse(null);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UML: Calendar +addReminder(rem: Reminder)
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public ReminderResponse addReminder(ReminderCreateRequest request) {
        if (request.appointmentId() == null) throw new ApiValidationException("appointmentId la bat buoc.");
        if (request.reminderTime()   == null) throw new ApiValidationException("reminderTime la bat buoc.");

        Appointment appointment = appointmentRepository.findById(request.appointmentId())
                .orElseThrow(() -> new ApiNotFoundException("Khong tim thay lich."));

        Reminder reminder = new Reminder();
        reminder.setReminderTime(request.reminderTime());
        reminder.setMessage(request.message() != null ? request.message().trim() : "Nhac nho lich");
        reminder.setAppointment(appointment);

        // UML: Calendar +addReminder(rem) — gắn vào Calendar entity của user
        Calendar calendar = getOrCreateCalendar(appointment.getHost());
        calendar.addReminder(reminder);   // stub method trên entity

        return toReminderResponse(reminderRepository.save(reminder));
    }

    @Transactional(readOnly = true)
    public List<ReminderResponse> getReminders(Long appointmentId) {
        return reminderRepository.findByAppointmentId(appointmentId)
                .stream().map(this::toReminderResponse).toList();
    }

    @Transactional
    public void deleteReminder(Long reminderId) {
        if (!reminderRepository.existsById(reminderId))
            throw new ApiNotFoundException("Khong tim thay reminder.");
        reminderRepository.deleteById(reminderId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UML: Calendar +addAppointment(app: Appointment)
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointments(Long userId) {
        getUser(userId);
        return appointmentRepository.findVisibleAppointmentsByUserId(userId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public AppointmentMutationResponse createAppointment(AppointmentCreateRequest request) {
        ValidatedInput input = validateCreateRequest(request);
        User currentUser = getUser(input.userId());
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
        Appointment overlapping = appointmentRepository.findOverlappingGroupsForUserToJoin(
                currentUser.getUserId(), input.startTime(), input.endTime())
                .stream().findFirst().orElse(null);

        if (overlapping != null) {
            return new AppointmentMutationResponse(
                    "GROUP_TIME_CONFLICT", "Lich nay bi trung gio voi mot cuoc hop nhom.",
                    toResponse(overlapping), overlapping.getAppointmentId(), overlapping.getName(),
                    buildConflictInfo(overlapping));
        }

        // 4. Lưu lịch mới (UML: addAppointment)
        Appointment appointment = buildAppointment(input, currentUser, isGroup);
        Appointment saved       = appointmentRepository.save(appointment);

        // Cập nhật Calendar entity của user
        Calendar calendar = getOrCreateCalendar(currentUser);
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

    // ═══════════════════════════════════════════════════════════════════════════
    // Join / Leave / Delete (UML: User +joinGroupMeeting)
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public AppointmentMutationResponse joinAppointment(Long userId, Long appointmentId) {
        User currentUser = getUser(userId);
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
        User currentUser = getUser(request.userId());

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
        User currentUser = getUser(userId);
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

    // ═══════════════════════════════════════════════════════════════════════════
    // Mapping helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private AppointmentResponse toResponse(Appointment appointment) {
        // Host luôn là thành viên đầu tiên
        List<AppointmentMemberResponse> members = new ArrayList<>();
        members.add(new AppointmentMemberResponse(
                appointment.getHost().getUserId(),
                appointment.getHost().getName(),
                true));

        // Participants (chỉ GroupMeeting có)
        if (appointment instanceof GroupMeeting gm) {
            gm.getParticipantsList().stream()
                    .filter(u -> !u.getUserId().equals(appointment.getHost().getUserId()))
                    .sorted(Comparator.comparing(User::getName, String.CASE_INSENSITIVE_ORDER))
                    .map(u -> new AppointmentMemberResponse(u.getUserId(), u.getName(), false))
                    .forEach(members::add);
        }

        List<ReminderResponse> reminders = appointment.getReminders().stream()
                .map(this::toReminderResponse).toList();

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

    private ReminderResponse toReminderResponse(Reminder r) {
        return new ReminderResponse(
                r.getReminderId(), r.getReminderTime(), r.getMessage(),
                r.getAppointment() != null ? r.getAppointment().getAppointmentId() : null);
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

    // ═══════════════════════════════════════════════════════════════════════════
    // Validation
    // ═══════════════════════════════════════════════════════════════════════════

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

    private User getUser(Long userId) {
        if (userId == null) throw new ApiValidationException("userId la bat buoc.");
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiNotFoundException("Khong tim thay user."));
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
