package com.umd.stobooking.repository;

import com.umd.stobooking.model.Booking;
import com.umd.stobooking.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Client's active bookings (future appointments)
    List<Booking> findByClientIdAndStatusInAndScheduledAtAfterOrderByScheduledAtAsc(
            Long clientId, List<BookingStatus> statuses, LocalDateTime after);

    // Slot conflict check: find any active booking that overlaps [start, end)
    @Query("""
            SELECT b FROM Booking b
            WHERE b.status IN :statuses
              AND b.scheduledAt < :end
              AND b.endAt > :start
            """)
    List<Booking> findOverlapping(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("statuses") List<BookingStatus> statuses);

    // Admin: bookings on a specific day
    List<Booking> findByScheduledAtBetweenOrderByScheduledAtAsc(
            LocalDateTime from, LocalDateTime to);

    // Admin: bookings by status
    List<Booking> findByStatusOrderByScheduledAtAsc(BookingStatus status);

    // Reminder: confirmed bookings whose scheduled_at falls in [from, to]
    List<Booking> findByStatusAndScheduledAtBetween(
            BookingStatus status, LocalDateTime from, LocalDateTime to);
}
