package com.umd.stobooking.repository;

import com.umd.stobooking.model.Booking;
import com.umd.stobooking.model.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Client's active bookings (future appointments)
    List<Booking> findByClientIdAndStatusInAndScheduledAtAfterOrderByScheduledAtAsc(
            Long clientId, List<BookingStatus> statuses, LocalDateTime after);

    // Slot conflict check: find any active booking that overlaps [start, end).
    // PESSIMISTIC_WRITE issues SELECT ... FOR UPDATE so that concurrent transactions
    // block on this query rather than both seeing "no conflict" and double-booking
    // the same slot. See SPEC.md §12 Pitfall 1 (Race Condition on Booking).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
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
