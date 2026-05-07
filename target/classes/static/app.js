/**
 * Calendar System — app.js
 * Ánh xạ UML: UI (AppointmentWindow) ─ Calls ─ Calendar
 *
 * activeDate / activeTime được quản lý qua setDefaultDateTime()
 * openAddAppointmentWindow / inputAppointmentInfo / validateInfo()
 * showWarningMessage() / promptJoinGroupMeeting()
 */

// ─── State (activeDate / activeTime trong UML AppointmentWindow) ───────────
let currentUser = null;

// ─── DOM refs ──────────────────────────────────────────────────────────────
const loginBox        = document.getElementById("login-box");
const appBox          = document.getElementById("app-box");
const loginForm       = document.getElementById("login-form");
const appointmentForm = document.getElementById("appointment-form");
const appointmentsBox = document.getElementById("appointments");
const currentUserText = document.getElementById("current-user");
const reminderEnabled = document.getElementById("reminder-enabled");
const reminderFields  = document.getElementById("reminder-fields");

// ─── Toast helper ──────────────────────────────────────────────────────────
const toast = Swal.mixin({
    toast: true,
    position: "top-end",
    showConfirmButton: false,
    timer: 2400,
    timerProgressBar: true
});

// ─── Event listeners ───────────────────────────────────────────────────────
loginForm.addEventListener("submit", login);
appointmentForm.addEventListener("submit", createAppointment);
document.getElementById("reload-button").addEventListener("click", loadAppointments);
document.getElementById("logout-button").addEventListener("click", logout);

// Toggle reminder fields (UML: inputAppointmentInfo)
reminderEnabled.addEventListener("change", () => {
    reminderFields.classList.toggle("hidden", !reminderEnabled.checked);
    if (reminderEnabled.checked) {
        setDefaultReminderTime();
    }
});

// Khởi tạo ngày giờ mặc định
setDefaultDateTime();

// ═══════════════════════════════════════════════════════════════════════════
// Auth
// ═══════════════════════════════════════════════════════════════════════════

async function login(event) {
    event.preventDefault();
    const response = await fetch("/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            username: document.getElementById("username").value.trim(),
            password: document.getElementById("password").value
        })
    });

    if (!response.ok) {
        showWarningMessage("Sai tên đăng nhập hoặc mật khẩu");
        return;
    }

    currentUser = await response.json();
    // UML User: userId, name
    currentUserText.textContent = `${currentUser.name || currentUser.username} (ID: ${currentUser.userId || currentUser.id})`;
    loginBox.classList.add("hidden");
    appBox.classList.remove("hidden");
    await loadAppointments();
}

function logout() {
    currentUser = null;
    appointmentsBox.innerHTML = "";
    loginForm.reset();
    loginBox.classList.remove("hidden");
    appBox.classList.add("hidden");
}

// ═══════════════════════════════════════════════════════════════════════════
// Load appointments (Calendar +appointmentsList)
// ═══════════════════════════════════════════════════════════════════════════

async function loadAppointments() {
    if (!currentUser) return;

    const userId = currentUser.userId || currentUser.id;
    const response = await fetch(`/api/appointments?userId=${userId}`);
    const data = await readJson(response);

    if (!response.ok) {
        toast.fire({ icon: "error", title: data.message || "Không tải được lịch" });
        return;
    }

    renderAppointments(data);
}

// ═══════════════════════════════════════════════════════════════════════════
// Create appointment (UML: AppointmentWindow +openAddAppointmentWindow)
// ═══════════════════════════════════════════════════════════════════════════

async function createAppointment(event) {
    event.preventDefault();

    // UML AppointmentWindow: validateInfo(): boolean
    if (!validateInfo()) return;

    const userId = currentUser.userId || currentUser.id;
    const hasReminder = reminderEnabled.checked;

    const payload = {
        userId,
        name:     document.getElementById("apt-name").value.trim(),
        location: document.getElementById("apt-location").value.trim(),
        startTime: document.getElementById("startTime").value,
        endTime:   document.getElementById("endTime").value,
        type:      document.getElementById("apt-type").value,
        reminderTime:    hasReminder ? document.getElementById("reminder-time").value || null : null,
        reminderMessage: hasReminder ? document.getElementById("reminder-message").value.trim() || null : null
    };

    const response = await fetch("/api/appointments", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
    });
    const data = await readJson(response);

    if (response.status === 400) {
        toast.fire({ icon: "error", title: data.message || "Dữ liệu không hợp lệ" });
        return;
    }

    // Xung đột lịch → 2 lựa chọn (UML: showWarningMessage)
    if (response.status === 409 && data.code === "CONFLICT_DETECTED") {
        await handleConflict(data, payload);
        return;
    }

    if (response.status === 409) {
        showWarningMessage(data.message || "Lỗi xung đột lịch");
        return;
    }

    if (!response.ok) {
        toast.fire({ icon: "error", title: data.message || "Không tạo được lịch" });
        return;
    }

    // Auto-merge hoặc gợi ý tham gia nhóm (UML: promptJoinGroupMeeting)
    if (data.code === "AUTO_MERGE" || data.code === "GROUP_TIME_CONFLICT") {
        const joined = await promptJoinGroupMeeting(data.code);
        if (joined) await joinAppointment(data.autoMergeAppointmentId);
        return;
    }

    resetForm();
    toast.fire({ icon: "success", title: "✅ Đã tạo lịch hẹn" });
    await loadAppointments();
}

// ─── UML: AppointmentWindow +validateInfo(): boolean ───────────────────────
function validateInfo() {
    const name = document.getElementById("apt-name").value.trim();
    const start = document.getElementById("startTime").value;
    const end   = document.getElementById("endTime").value;

    if (!name) {
        toast.fire({ icon: "warning", title: "Vui lòng nhập tên lịch hẹn" });
        return false;
    }
    if (!start || !end) {
        toast.fire({ icon: "warning", title: "Vui lòng chọn thời gian" });
        return false;
    }
    if (new Date(start) >= new Date(end)) {
        toast.fire({ icon: "warning", title: "Thời gian kết thúc phải sau thời gian bắt đầu" });
        return false;
    }
    if (reminderEnabled.checked) {
        const rTime = document.getElementById("reminder-time").value;
        if (!rTime) {
            toast.fire({ icon: "warning", title: "Vui lòng chọn thời gian nhắc nhở" });
            return false;
        }
    }
    return true;
}

// ─── UML: AppointmentWindow +showWarningMessage() ──────────────────────────
function showWarningMessage(message) {
    Swal.fire({ icon: "warning", title: message });
}

// ─── UML: AppointmentWindow +promptJoinGroupMeeting(): boolean ─────────────
async function promptJoinGroupMeeting(code) {
    const title = code === "GROUP_TIME_CONFLICT"
        ? "⚠️ Trùng giờ với cuộc họp nhóm. Bạn có muốn tham gia không?"
        : "🔍 Tìm thấy nhóm họp trùng khớp. Tham gia luôn?";

    const result = await Swal.fire({
        icon: "question",
        title,
        showCancelButton: true,
        confirmButtonText: "Tham gia",
        cancelButtonText: "Bỏ qua",
        confirmButtonColor: "#22c55e"
    });
    return result.isConfirmed;
}

// ═══════════════════════════════════════════════════════════════════════════
// Conflict handling (UML: showWarningMessage + Calendar +checkConflict)
// ═══════════════════════════════════════════════════════════════════════════

async function handleConflict(data, payload) {
    const c = data.conflict;
    const html = `
        <div style="text-align:left;font-size:0.9rem">
            <p><strong>Lịch xung đột:</strong></p>
            <p>📌 <strong>${escapeHtml(c.conflictingTitle)}</strong></p>
            <p>🕐 ${c.conflictingStartTime} → ${c.conflictingEndTime}</p>
        </div>`;

    const result = await Swal.fire({
        icon: "warning",
        title: "⚡ Phát hiện xung đột lịch!",
        html,
        showDenyButton: true,
        confirmButtonText: "Thay thế lịch cũ",
        denyButtonText: "Hủy lịch mới",
        allowOutsideClick: false
    });

    if (result.isConfirmed) {
        await resolveConflict(c.conflictingAppointmentId, true, data.autoMergeAppointmentId);
    } else if (result.isDenied) {
        await resolveConflict(c.conflictingAppointmentId, false, data.autoMergeAppointmentId);
    }
}

async function resolveConflict(conflictingId, replaceExisting, newId = null) {
    const userId = currentUser.userId || currentUser.id;
    const response = await fetch("/api/appointments/conflict/resolve", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            userId,
            conflictingAppointmentId: conflictingId,
            newAppointmentId: newId,
            replaceExisting
        })
    });
    const data = await readJson(response);

    if (!response.ok) {
        toast.fire({ icon: "error", title: data.message || "Không xử lý được xung đột" });
        return;
    }

    if (data.code === "CONFLICT_RESOLVED") {
        resetForm();
        toast.fire({ icon: "success", title: replaceExisting ? "✅ Đã thay thế lịch cũ!" : "✅ Hoàn tất!" });
    } else if (data.code === "CONFLICT_CANCELLED") {
        toast.fire({ icon: "info", title: "Đã giữ lại lịch cũ" });
        resetForm();
    }
    await loadAppointments();
}

// ═══════════════════════════════════════════════════════════════════════════
// Join / Leave / Delete
// ═══════════════════════════════════════════════════════════════════════════

async function joinAppointment(appointmentId) {
    const userId = currentUser.userId || currentUser.id;
    const response = await fetch(`/api/appointments/${appointmentId}/join`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ userId })
    });
    const data = await readJson(response);

    if (response.status === 409 && data.code === "CONFLICT_DETECTED") {
        const c = data.conflict;
        const html = `
            <div style="text-align:left;font-size:0.9rem">
                <p><strong>Xung đột khi tham gia nhóm:</strong></p>
                <p>📌 <strong>${escapeHtml(c.conflictingTitle)}</strong></p>
                <p>🕐 ${c.conflictingStartTime} → ${c.conflictingEndTime}</p>
            </div>`;

        const result = await Swal.fire({
            icon: "warning",
            title: "⚡ Xung đột lịch khi tham gia!",
            html,
            showDenyButton: true,
            confirmButtonText: "Xóa lịch cũ & Tham gia",
            denyButtonText: "Giữ lịch cũ",
            allowOutsideClick: false
        });

        if (result.isConfirmed) {
            await resolveConflict(c.conflictingAppointmentId, true, appointmentId);
        } else if (result.isDenied) {
            await resolveConflict(c.conflictingAppointmentId, false, null);
        }
        return;
    }

    if (!response.ok) {
        toast.fire({ icon: "error", title: data.message || "Không tham gia được nhóm" });
        return;
    }

    resetForm();
    toast.fire({ icon: "success", title: "✅ Đã tham gia nhóm" });
    await loadAppointments();
}

async function deleteOrLeave(appointmentId) {
    const userId = currentUser.userId || currentUser.id;
    const response = await fetch(`/api/appointments/${appointmentId}`, {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ userId })
    });
    const data = await readJson(response);

    if (!response.ok) {
        toast.fire({ icon: "error", title: data.message || "Không xử lý được" });
        return;
    }

    toast.fire({ icon: "success", title: data.message || "Thành công" });
    await loadAppointments();
}

// ═══════════════════════════════════════════════════════════════════════════
// Render (UML: AppointmentWindow displays Calendar.appointmentsList)
// ═══════════════════════════════════════════════════════════════════════════

function renderAppointments(appointments) {
    if (!appointments || !appointments.length) {
        appointmentsBox.innerHTML = `<p class="empty">📭 Chưa có lịch hẹn nào.</p>`;
        return;
    }

    const userId = currentUser.userId || currentUser.id;
    appointmentsBox.innerHTML = appointments.map(apt => renderCard(apt, userId)).join("");

    appointmentsBox.querySelectorAll("button[data-id]").forEach(btn => {
        btn.addEventListener("click", () => {
            if (btn.dataset.action === "join") joinAppointment(btn.dataset.id);
            else deleteOrLeave(btn.dataset.id);
        });
    });
}

function renderCard(apt, userId) {
    const isGroup  = apt.type === "GROUP";
    const isHost   = apt.hostId === userId;
    const isMember = apt.members && apt.members.some(m => (m.userId || m.id) === userId);

    const actionLabel = getActionLabel(apt, isHost, isMember);
    const actionType  = actionLabel === "Tham gia" ? "join" : "delete";
    const badgeClass  = isGroup ? "group" : "personal";

    const members = apt.members
        ? apt.members.map(m => `${escapeHtml(m.username || m.name)}${m.host ? " 👑" : ""}`).join(", ")
        : "";

    const remindersHtml = renderReminders(apt.reminders);
    const locationHtml  = apt.location
        ? `<span>📍 ${escapeHtml(apt.location)}</span>` : "";
    const durationHtml  = apt.duration ? `<span>⏱ ${apt.duration} phút</span>` : "";

    return `
        <article class="appointment-card ${isGroup ? "group" : ""}">
            <div class="card-header">
                <span class="card-name">${escapeHtml(apt.name || apt.title)}</span>
                <span class="type-badge ${badgeClass}">${isGroup ? "👥 Nhóm" : "📌 Cá nhân"}</span>
            </div>
            <div class="card-meta">
                ${locationHtml}
                <span>🕐 ${formatDateTime(apt.startTime)} → ${formatDateTime(apt.endTime)}</span>
                ${durationHtml}
                <span>👤 Host: ${escapeHtml(apt.hostUsername)}</span>
            </div>
            ${members ? `<div class="card-members">👥 ${members}</div>` : ""}
            ${remindersHtml}
            <div class="card-actions">
                <button type="button"
                    data-id="${apt.id}"
                    data-action="${actionType}"
                    class="btn-action ${actionType}">
                    ${actionLabel === "Tham gia" ? "➕ Tham gia" : actionLabel === "Rời nhóm" ? "🚪 Rời nhóm" : "🗑️ Xóa"}
                </button>
            </div>
        </article>`;
}

function renderReminders(reminders) {
    if (!reminders || !reminders.length) return "";
    const items = reminders.map(r => `
        <div class="reminder-item">
            <span>${escapeHtml(r.message)}</span>
            <span>🔔 ${formatDateTime(r.reminderTime)}</span>
        </div>`).join("");
    return `
        <div class="card-reminders">
            <div class="card-reminders-title">🔔 Nhắc nhở</div>
            ${items}
        </div>`;
}

function getActionLabel(apt, isHost, isMember) {
    if (apt.type === "GROUP" && !isHost && !isMember) return "Tham gia";
    if (apt.type === "GROUP" && !isHost) return "Rời nhóm";
    return "Xóa";
}

// ═══════════════════════════════════════════════════════════════════════════
// DateTime helpers (UML: activeDate / activeTime)
// ═══════════════════════════════════════════════════════════════════════════

function setDefaultDateTime() {
    const start = new Date();
    start.setMinutes(0, 0, 0);
    start.setHours(start.getHours() + 1);
    const end = new Date(start);
    end.setHours(end.getHours() + 1);

    document.getElementById("startTime").value = toDateTimeLocal(start);
    document.getElementById("endTime").value   = toDateTimeLocal(end);
}

function setDefaultReminderTime() {
    const start = document.getElementById("startTime").value;
    if (start) {
        const d = new Date(start);
        d.setMinutes(d.getMinutes() - 15);
        document.getElementById("reminder-time").value = toDateTimeLocal(d);
    }
}

function toDateTimeLocal(date) {
    const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 16);
}

function formatDateTime(value) {
    if (!value) return "—";
    return new Date(value).toLocaleString("vi-VN", {
        day: "2-digit", month: "2-digit", year: "numeric",
        hour: "2-digit", minute: "2-digit"
    });
}

function resetForm() {
    appointmentForm.reset();
    reminderFields.classList.add("hidden");
    setDefaultDateTime();
}

// ═══════════════════════════════════════════════════════════════════════════
// Utilities
// ═══════════════════════════════════════════════════════════════════════════

async function readJson(response) {
    const text = await response.text();
    return text ? JSON.parse(text) : {};
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&",  "&amp;")
        .replaceAll("<",  "&lt;")
        .replaceAll(">",  "&gt;")
        .replaceAll('"',  "&quot;")
        .replaceAll("'",  "&#039;");
}
