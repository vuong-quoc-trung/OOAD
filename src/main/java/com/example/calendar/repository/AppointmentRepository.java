package com.example.calendar.repository;

import com.example.calendar.entity.Appointment;
import com.example.calendar.entity.AppointmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    @Query("""
        select distinct a
        from Appointment a
        left join fetch a.host
        left join fetch a.groupMembers gm
        left join fetch gm.user
        where a.type = com.example.calendar.entity.AppointmentType.GROUP
           or a.host.id = :userId
           or gm.user.id = :userId
        order by a.startTime asc
        """)
    List<Appointment> findVisibleAppointmentsByUserId(@Param("userId") Long userId);

    @Query("""
        select count(distinct a) > 0
        from Appointment a
        left join a.groupMembers gm
        where (a.host.id = :userId or gm.user.id = :userId)
          and a.startTime < :endTime
          and a.endTime > :startTime
        """)
    boolean existsOverlappingAppointmentForUser(
        @Param("userId") Long userId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    @Query("""
        select distinct a
        from Appointment a
        left join fetch a.host
        left join fetch a.groupMembers gm
        left join fetch gm.user
        where a.type = com.example.calendar.entity.AppointmentType.GROUP
          and lower(a.title) = lower(:title)
          and a.startTime = :startTime
          and a.endTime = :endTime
        """)
    Optional<Appointment> findFirstExactGroupMatch(
        @Param("title") String title,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    @Query("""
        select distinct a
        from Appointment a
        left join fetch a.host
        left join fetch a.groupMembers gm
        left join fetch gm.user
        where a.type = com.example.calendar.entity.AppointmentType.GROUP
          and a.startTime < :endTime
          and a.endTime > :startTime
          and a.host.id <> :userId
          and not exists (
              select 1
              from GroupMember gm2
              where gm2.appointment = a
                and gm2.user.id = :userId
          )
        order by a.startTime asc
        """)
    List<Appointment> findOverlappingGroupsForUserToJoin(
        @Param("userId") Long userId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    @Query("""
        select distinct a
        from Appointment a
        left join fetch a.host
        left join fetch a.groupMembers gm
        left join fetch gm.user
        where a.id = :id
        """)
    Optional<Appointment> findWithMembersById(@Param("id") Long id);

    boolean existsByTitleIgnoreCaseAndType(String title, AppointmentType type);
}
