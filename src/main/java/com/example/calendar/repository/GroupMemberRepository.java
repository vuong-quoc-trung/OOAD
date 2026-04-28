package com.example.calendar.repository;

import com.example.calendar.entity.GroupMember;
import com.example.calendar.entity.GroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {
    boolean existsByAppointment_IdAndUser_Id(Long appointmentId, Long userId);

    Optional<GroupMember> findByAppointment_IdAndUser_Id(Long appointmentId, Long userId);
}
