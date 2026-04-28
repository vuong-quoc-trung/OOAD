CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL
);

CREATE TABLE appointments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    start_time DATETIME(6) NOT NULL,
    end_time DATETIME(6) NOT NULL,
    type VARCHAR(20) NOT NULL,
    host_id BIGINT NOT NULL,
    CONSTRAINT fk_appointments_host FOREIGN KEY (host_id) REFERENCES users(id)
);

CREATE TABLE group_member (
    appointment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (appointment_id, user_id),
    CONSTRAINT fk_group_member_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE CASCADE,
    CONSTRAINT fk_group_member_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_appointments_time_window ON appointments(start_time, end_time);
CREATE INDEX idx_group_member_user ON group_member(user_id);
