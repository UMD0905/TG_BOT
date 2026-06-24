package com.umd.stobooking.service;

import com.umd.stobooking.model.Booking;
import com.umd.stobooking.model.BookingStatus;
import com.umd.stobooking.repository.BookingRepository;
import com.umd.stobooking.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final BookingRepository bookingRepository;

    // ── View queries ──────────────────────────────────────────────────────────

    public List<Booking> getTodayBookings() {
        LocalDate today = DateTimeUtil.todayTashkent();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);
        return bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(start, end);
    }

    public List<Booking> getTomorrowBookings() {
        LocalDate tomorrow = DateTimeUtil.todayTashkent().plusDays(1);
        LocalDateTime start = tomorrow.atStartOfDay();
        LocalDateTime end = tomorrow.atTime(LocalTime.MAX);
        return bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(start, end);
    }

    public List<Booking> getWeekBookings() {
        LocalDate today = DateTimeUtil.todayTashkent();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(7).atTime(LocalTime.MAX);
        return bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(start, end);
    }

    public List<Booking> getPendingBookings() {
        return bookingRepository.findByStatusOrderByScheduledAtAsc(BookingStatus.PENDING);
    }

    public Optional<Booking> findById(long id) {
        return bookingRepository.findById(id);
    }

    // ── Status changes ────────────────────────────────────────────────────────

    @Transactional
    public Booking confirmBooking(long bookingId) {
        Booking booking = getActive(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException(
                    "Запись #" + bookingId + " имеет статус " + booking.getStatus() +
                    " — подтвердить можно только PENDING.");
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        log.info("Booking #{} CONFIRMED by admin", bookingId);
        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking rejectBooking(long bookingId, String reason) {
        Booking booking = getActive(bookingId);
        if (booking.getStatus() == BookingStatus.CANCELLED
                || booking.getStatus() == BookingStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Запись #" + bookingId + " уже завершена или отменена.");
        }
        booking.setStatus(BookingStatus.REJECTED);
        if (reason != null && !reason.isBlank()) {
            String note = (booking.getProblemDescription() != null
                    ? booking.getProblemDescription() + " | "
                    : "")
                    + "Причина отказа: " + reason;
            booking.setProblemDescription(note);
        }
        log.info("Booking #{} REJECTED by admin. Reason: {}", bookingId, reason);
        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking completeBooking(long bookingId) {
        Booking booking = getActive(bookingId);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Запись #" + bookingId + " имеет статус " + booking.getStatus() +
                    " — завершить можно только CONFIRMED.");
        }
        booking.setStatus(BookingStatus.COMPLETED);
        log.info("Booking #{} COMPLETED by admin", bookingId);
        return bookingRepository.save(booking);
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    public String buildStatsText() {
        LocalDate today = DateTimeUtil.todayTashkent();
        LocalDate weekStart = today.minusDays(6);

        long todayCount   = bookingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(
                today.atStartOfDay(), today.atTime(LocalTime.MAX)).size();
        long pendingCount = bookingRepository.findByStatusOrderByScheduledAtAsc(BookingStatus.PENDING).size();
        long weekDone     = bookingRepository
                .findByStatusAndScheduledAtBetween(BookingStatus.COMPLETED,
                        weekStart.atStartOfDay(), today.atTime(LocalTime.MAX)).size();
        long weekCancelled = bookingRepository
                .findByStatusAndScheduledAtBetween(BookingStatus.CANCELLED,
                        weekStart.atStartOfDay(), today.atTime(LocalTime.MAX)).size();

        return "📊 Статистика СТО:\n\n"
                + "📅 Записей на сегодня: " + todayCount + "\n"
                + "⏳ Ожидают подтверждения: " + pendingCount + "\n"
                + "✅ Завершено за неделю: " + weekDone + "\n"
                + "❌ Отменено за неделю: " + weekCancelled;
    }

    // ── private ───────────────────────────────────────────────────────────────

    private Booking getActive(long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Запись #" + bookingId + " не найдена."));
    }
}
