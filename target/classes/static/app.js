let currentUser = null;

const loginBox = document.getElementById("login-box");
const appBox = document.getElementById("app-box");
const loginForm = document.getElementById("login-form");
const appointmentForm = document.getElementById("appointment-form");
const appointmentsBox = document.getElementById("appointments");
const currentUserText = document.getElementById("current-user");

const toast = Swal.mixin({
    toast: true,
    position: "top-end",
    showConfirmButton: false,
    timer: 2200
});

loginForm.addEventListener("submit", login);
appointmentForm.addEventListener("submit", createAppointment);
document.getElementById("reload-button").addEventListener("click", loadAppointments);
document.getElementById("logout-button").addEventListener("click", logout);

setDefaultDateTime();

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
        toast.fire({ icon: "error", title: "Sai username hoặc password" });
        return;
    }

    currentUser = await response.json();
    currentUserText.textContent = `${currentUser.username} (userId=${currentUser.id})`;
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

async function loadAppointments() {
    if (!currentUser) {
        return;
    }

    const response = await fetch(`/api/appointments?userId=${currentUser.id}`);
    const data = await readJson(response);

    if (!response.ok) {
        toast.fire({ icon: "error", title: data.message || "Không tải được lịch" });
        return;
    }

    renderAppointments(data);
}

async function createAppointment(event) {
    event.preventDefault();

    const payload = {
        userId: currentUser.id,
        title: document.getElementById("title").value.trim(),
        startTime: document.getElementById("startTime").value,
        endTime: document.getElementById("endTime").value,
        type: document.getElementById("type").value
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

    if (response.status === 409) {
        await Swal.fire({
            icon: "warning",
            title: "Bạn đã có lịch trong giờ này!"
        });
        return;
    }

    if (!response.ok) {
        toast.fire({ icon: "error", title: data.message || "Không tạo được lịch" });
        return;
    }

    if (data.code === "AUTO_MERGE" || data.code === "GROUP_TIME_CONFLICT") {
        const result = await Swal.fire({
            icon: "question",
            title: getJoinPromptTitle(data.code),
            showCancelButton: true,
            confirmButtonText: "Đồng ý",
            cancelButtonText: "Hủy"
        });

        if (result.isConfirmed) {
            await joinAppointment(data.autoMergeAppointmentId);
        }
        return;
    }

    appointmentForm.reset();
    setDefaultDateTime();
    toast.fire({ icon: "success", title: "Đã tạo lịch" });
    await loadAppointments();
}

function getJoinPromptTitle(code) {
    if (code === "GROUP_TIME_CONFLICT") {
        return "Bị trùng giờ với 1 cuộc họp. Bạn có muốn tham gia không?";
    }

    return "Tìm thấy nhóm họp trùng khớp. Tham gia luôn?";
}

async function joinAppointment(appointmentId) {
    const response = await fetch(`/api/appointments/${appointmentId}/join`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ userId: currentUser.id })
    });
    const data = await readJson(response);

    if (response.status === 409) {
        await Swal.fire({
            icon: "warning",
            title: "Bạn đã có lịch trong giờ này!"
        });
        return;
    }

    if (!response.ok) {
        toast.fire({ icon: "error", title: data.message || "Không tham gia được nhóm" });
        return;
    }

    appointmentForm.reset();
    setDefaultDateTime();
    toast.fire({ icon: "success", title: "Đã tham gia nhóm" });
    await loadAppointments();
}

async function deleteOrLeave(appointmentId) {
    const response = await fetch(`/api/appointments/${appointmentId}`, {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ userId: currentUser.id })
    });
    const data = await readJson(response);

    if (!response.ok) {
        toast.fire({ icon: "error", title: data.message || "Không xử lý được" });
        return;
    }

    toast.fire({ icon: "success", title: data.message || "Thành công" });
    await loadAppointments();
}

function renderAppointments(appointments) {
    if (!appointments.length) {
        appointmentsBox.innerHTML = `<p class="empty">Chưa có lịch.</p>`;
        return;
    }

    appointmentsBox.innerHTML = appointments.map((appointment) => {
        const isHost = appointment.hostId === currentUser.id;
        const isMember = appointment.members.some((member) => member.id === currentUser.id);
        const actionText = getActionText(appointment, isHost, isMember);
        const actionType = actionText === "Tham gia" ? "join" : "delete";
        const actionClass = actionType === "delete" ? "danger" : "";
        const members = appointment.members
            .map((member) => `${escapeHtml(member.username)}${member.host ? " (host)" : ""}`)
            .join(", ");

        return `
            <article class="appointment-card">
                <div>
                    <strong>${escapeHtml(appointment.title)}</strong>
                    <p>${appointment.type} | ${formatDateTime(appointment.startTime)} - ${formatDateTime(appointment.endTime)}</p>
                    <p>Members: ${members}</p>
                </div>
                <button type="button" data-id="${appointment.id}" data-action="${actionType}" class="${actionClass}">${actionText}</button>
            </article>
        `;
    }).join("");

    appointmentsBox.querySelectorAll("button[data-id]").forEach((button) => {
        button.addEventListener("click", () => {
            if (button.dataset.action === "join") {
                joinAppointment(button.dataset.id);
                return;
            }

            deleteOrLeave(button.dataset.id);
        });
    });
}

function getActionText(appointment, isHost, isMember) {
    if (appointment.type === "GROUP" && !isHost && !isMember) {
        return "Tham gia";
    }

    if (appointment.type === "GROUP" && !isHost) {
        return "Rời nhóm";
    }

    return "Xóa";
}

function setDefaultDateTime() {
    const start = new Date();
    start.setMinutes(0, 0, 0);
    start.setHours(start.getHours() + 1);

    const end = new Date(start);
    end.setHours(start.getHours() + 1);

    document.getElementById("startTime").value = toDateTimeLocal(start);
    document.getElementById("endTime").value = toDateTimeLocal(end);
}

function toDateTimeLocal(date) {
    const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 16);
}

function formatDateTime(value) {
    return new Date(value).toLocaleString("vi-VN");
}

async function readJson(response) {
    const text = await response.text();
    return text ? JSON.parse(text) : {};
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}
