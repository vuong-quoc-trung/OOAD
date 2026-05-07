package com.example.calendar.repository;

import com.example.calendar.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /**
     * Lấy tất cả lịch hiển thị cho userId:
     * — GroupMeeting mà user là host hoặc participant
     * — Appointment cá nhân của user (host)
     */
    @Query("""
        select distinct a
        from Appointment a
        left join fetch a.host
        left join fetch a.reminders
        where a.host.userId = :userId
           or (TYPE(a) = com.example.calendar.entity.GroupMeeting
               and :userId in (
                   select p.userId
                   from com.example.calendar.entity.GroupMeeting gm
                   join gm.participantsList p
                   where gm = a
               ))
           or TYPE(a) = com.example.calendar.entity.GroupMeeting
        order by a.startTime asc
        """)
    List<Appointment> findVisibleAppointmentsByUserId(@Param("userId") Long userId);

    /**
     * Kiểm tra user có lịch trùng giờ không.
     * Tương ứng UML Calendar: +checkConflict(startTime, endTime): boolean
     */
    @Query("""
        select count(a) > 0
        from Appointment a
        where a.startTime < :endTime
          and a.endTime   > :startTime
          and (
              a.host.userId = :userId
              or (TYPE(a) = com.example.calendar.entity.GroupMeeting
                  and exists (
                      select 1 from com.example.calendar.entity.GroupMeeting gm
                      join gm.participantsList p
                      where gm = a and p.userId = :userId
                  ))
          )
        """)
    boolean existsOverlappingAppointmentForUser(
            @Param("userId")    Long userId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime")   LocalDateTime endTime
    );

    /**
     * Tìm GroupMeeting khớp chính xác tên + giờ (auto-merge).
     * Tương ứng UML Calendar: +findGroupMeeting(name, duration): GroupMeeting
     */
    @Query("""
        select distinct a
        from Appointment a
        left join fetch a.host
        left join fetch a.reminders
        where TYPE(a) = com.example.calendar.entity.GroupMeeting
          and lower(a.name) = lower(:name)
          and a.startTime = :startTime
          and a.endTime   = :endTime
        """)
    Optional<Appointment> findFirstExactGroupMatch(
            @Param("name")      String name,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime")   LocalDateTime endTime
    );

    /**
     * Tìm GroupMeeting trùng giờ mà user chưa tham gia (gợi ý join).
     */
    @Query("""
        select distinct a
        from Appointment a
        left join fetch a.host
        left join fetch a.reminders
        where TYPE(a) = com.example.calendar.entity.GroupMeeting
          and a.startTime < :endTime
          and a.endTime   > :startTime
          and a.host.userId <> :userId
          and not exists (
              select 1 from com.example.calendar.entity.GroupMeeting gm
              join gm.participantsList p
              where gm = a and p.userId = :userId
          )
        order by a.startTime asc
        """)
    List<Appointment> findOverlappingGroupsForUserToJoin(
            @Param("userId")    Long userId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime")   LocalDateTime endTime
    );

    /**
     * Lấy Appointment kèm reminders theo ID.
     */
    @Query("""
        select distinct a
        from Appointment a
        left join fetch a.host
        left join fetch a.reminders
        where a.appointmentId = :id
        """)
    Optional<Appointment> findWithMembersById(@Param("id") Long id);

    /**
     * Lấy tất cả lịch trùng giờ của user.
     */
    @Query("""
        select distinct a
        from Appointment a
        left join fetch a.host
        left join fetch a.reminders
        where a.startTime < :endTime
          and a.endTime   > :startTime
          and (
              a.host.userId = :userId
              or (TYPE(a) = com.example.calendar.entity.GroupMeeting
                  and exists (
                      select 1 from com.example.calendar.entity.GroupMeeting gm
                      join gm.participantsList p
                      where gm = a and p.userId = :userId
                  ))
          )
        order by a.startTime asc
        """)
    List<Appointment> findOverlappingAppointmentsForUser(
            @Param("userId")    Long userId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime")   LocalDateTime endTime
    );

    boolean existsByNameIgnoreCaseAndDtype(String name, String dtype);
}