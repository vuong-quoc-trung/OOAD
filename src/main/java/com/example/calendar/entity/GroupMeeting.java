package com.example.calendar.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;

import java.util.ArrayList;
import java.util.List;


@Entity
@DiscriminatorValue("GROUP")
public class GroupMeeting extends Appointment {

    /**
     * + participantsList: List<User>
     * Dùng @ManyToMany trực tiếp User — bảng join: group_meeting_participants
     */
    @ManyToMany
    @JoinTable(
            name = "group_meeting_participants",
            joinColumns        = @JoinColumn(name = "appointment_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> participantsList = new ArrayList<>();


    /**
     * + addParticipant(user: User)
     * Thêm người dùng vào danh sách tham gia cuộc họp nhóm.
     */
    public void addParticipant(User user) {
        if (user != null && !participantsList.contains(user)) {
            participantsList.add(user);
        }
    }

    /**
     * Rời khỏi cuộc họp nhóm.
     */
    public void removeParticipant(User user) {
        participantsList.remove(user);
    }

    /**
     * Kiểm tra user đã tham gia chưa.
     */
    public boolean hasParticipant(User user) {
        return participantsList.stream()
                .anyMatch(u -> u.getUserId().equals(user.getUserId()));
    }

    // ─── Getters / Setters ────────────────────────────────────────────────────

    public List<User> getParticipantsList() {
        return participantsList;
    }

    public void setParticipantsList(List<User> participantsList) {
        this.participantsList = participantsList != null ? participantsList : new ArrayList<>();
    }
}
