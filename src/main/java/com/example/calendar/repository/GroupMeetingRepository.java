package com.example.calendar.repository;

import com.example.calendar.entity.GroupMeeting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GroupMeetingRepository extends JpaRepository<GroupMeeting, Long> {
    Optional<GroupMeeting> findByNameAndDuration(String name, int duration);
}
