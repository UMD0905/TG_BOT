package com.umd.stobooking.service;

import com.umd.stobooking.exception.CancellationTooLateException;
import com.umd.stobooking.exception.SlotUnavailableException;
import com.umd.stobooking.model.*;
import com.umd.stobooking.repository.BookingRepository;
import com.umd.stobooking.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final int DEFAULT_DURATION_MINUTES = 60; // used for free-text path

    private final BookingRepository bookingRepository;

    /**
     * Creates a booking via the menu path (service is known).
     *
     * Re-checks slot availability inside the transaction to prevent
     * double-booking even under concurrent requests (SPEC Pitfall 1).
     */
    @Transactional
    public Booking createBooking(Client client, Car car, ServiceItem service,
                                 LocalDateTime scheduledAt) {
        LocalDateTime endAt = scheduledAt.plusMinutes(service.getDurationMinutes());
        assertSlotFree(scheduledAt, endAt);

        Booking booking = new Booking();
        booking.setClient(client);
        booking.setCar(car);
        booking.setService(service);
        booking.setScheduledAt(scheduledAt);
        booking.setEndAt(endAt);
        booking.setStatus(BookingStatus.PENDING);
        booking.setCreatedAt(DateTimeUtil.nowTashkent());
        return bookingRepository.save(booking);
    }

    /**
     * Creates a booking via the free-text path (service is unknown, admin fills in later).
     */
    @Transactional
    public Booking createBookingWithProblem(Client client, Car car,
                                            String problemDescription,
                                            LocalDateTime scheduledAt) {
        LocalDateTime endAt = scheduledAt.plusMinutes(DEFAULT_DURATION_MINUTES);
        assertSlotFree(scheduledAt, endAt);

        Booking booking = new Booking();
        booking.setClient(client);
        booking.setCar(car);
        booking.setProblemDescription(problemDescription);
        booking.setScheduledAt(scheduledAt);
        booking.setEndAt(endAt);
        booking.setStatus(BookingStatus.PENDING);
        booking.setCreatedAt(DateTimeUtil.nowTashkent());
        return bookingRepository.save(booking);
    }

    public Optional<Booking> findById(long id) {
        return bookingRepository.findById(id);
    }

    public List<Booking> activeBookingsForClient(long clientId) {
        return bookingRepository
                .findByClientIdAndStatusInAndScheduledAtAfterOrderByScheduledAtAsc(
                        clientId,
                        List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED),
                        DateTimeUtil.nowTashkent());
    }

    /**
     * Cancels a booking on behalf of a client.
     * Guards: booking must belong to the client, status must be PENDING or CONFIRMED,
     * and the appointment must be more than 2 hours away (SPEC Day 6).
     */
    @Transactional
    public void cancelBooking(long bookingId, long telegramUserId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Запись #" + bookingId + " не найдена."));

        if (!booking.getClient().getTelegramUserId().equals(telegramUserId)) {
            throw new IllegalArgumentException("Это не ваша запись.");
        }

        if (booking.getStatus() != BookingStatus.PENDING
                && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Запись #" + bookingId + " нельзя отменить — текущий статус: " + booking.getStatus());
        }

        LocalDateTime twoHoursFromNow = DateTimeUtil.nowTashkent().plusHours(2);
        if (booking.getScheduledAt().isBefore(twoHoursFromNow)) {
            throw new CancellationTooLateException(
                    "Запись нельзя отменить менее чем за 2 часа до визита.\n" +
                    "Позвоните нам напрямую, если хотите перенести.");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        log.info("Booking #{} cancelled by telegramUserId={}", bookingId, telegramUserId);
    }

    // ── private ───────────────────────────────────────────────────────────────

    /** Throws if the [start, end) window is occupied by any active booking. */
    private void assertSlotFree(LocalDateTime start, LocalDateTime end) {
        List<Booking> conflicts = bookingRepository.findOverlapping(
                start, end,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED));

        if (!conflicts.isEmpty()) {
            log.warn("Slot conflict: {} – {} already taken", start, end);
            throw new SlotUnavailableException(
                    "Этот слот только что заняли. Пожалуйста, выберите другое время.");
        }
    }
}
