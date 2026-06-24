package com.umd.stobooking.service;

import com.umd.stobooking.model.Booking;
import com.umd.stobooking.model.BookingStatus;
import com.umd.stobooking.model.ServiceItem;
import com.umd.stobooking.model.WorkingHours;
import com.umd.stobooking.repository.BookingRepository;
import com.umd.stobooking.repository.WorkingHoursRepository;
import com.umd.stobooking.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    /** Slot step in minutes. Clients can start a booking every 30 min. */
    private static final int SLOT_STEP_MINUTES = 30;

    /** Max calendar days to scan when looking for available dates. */
    private static final int MAX_SCAN_DAYS = 60;

    private final WorkingHoursRepository workingHoursRepository;
    private final BookingRepository bookingRepository;

    /**
     * Returns up to {@code maxDates} upcoming dates (starting tomorrow) that
     * have at least one free slot for the given service.
     */
    public List<LocalDate> availableDates(ServiceItem service, int maxDates) {
        List<LocalDate> result = new ArrayList<>();
        LocalDate date = DateTimeUtil.todayTashkent().plusDays(1);

        for (int scanned = 0; scanned < MAX_SCAN_DAYS && result.size() < maxDates; scanned++) {
            if (!availableSlots(date, service).isEmpty()) {
                result.add(date);
            }
            date = date.plusDays(1);
        }
        return result;
    }

    /**
     * Returns available start times on {@code date} for a service of the given
     * duration, considering working hours and existing PENDING/CONFIRMED bookings.
     *
     * Pitfall 6 guard: only offers slots where slotStart + duration ≤ closeTime.
     */
    public List<LocalTime> availableSlots(LocalDate date, ServiceItem service) {
        int dayOfWeek = date.getDayOfWeek().getValue(); // 1=Mon … 7=Sun
        WorkingHours wh = workingHoursRepository.findByDayOfWeek(dayOfWeek).orElse(null);

        if (wh == null || !wh.isWorking()) return List.of();

        int duration = service.getDurationMinutes();
        LocalTime closeTime = wh.getCloseTime();

        // Fetch all bookings that touch this calendar day
        List<Booking> existing = bookingRepository.findOverlapping(
                date.atStartOfDay(),
                date.atTime(23, 59, 59),
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED));

        List<LocalTime> slots = new ArrayList<>();
        LocalTime slot = wh.getOpenTime();

        while (!slot.plusMinutes(duration).isAfter(closeTime)) {
            LocalDateTime slotStart = date.atTime(slot);
            LocalDateTime slotEnd   = slotStart.plusMinutes(duration);

            boolean conflict = existing.stream().anyMatch(b ->
                    b.getScheduledAt().isBefore(slotEnd) && b.getEndAt().isAfter(slotStart));

            if (!conflict) slots.add(slot);

            slot = slot.plusMinutes(SLOT_STEP_MINUTES);
        }
        return slots;
    }
}
