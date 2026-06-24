package com.umd.stobooking.service;

import com.umd.stobooking.model.Booking;
import com.umd.stobooking.model.BookingStatus;
import com.umd.stobooking.repository.BookingRepository;
import com.umd.stobooking.util.DateTimeUtil;
import com.umd.stobooking.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final BookingRepository bookingRepository;
    private final StateService stateService;

    // Injected lazily by the bot after it starts (to avoid circular dependency)
    private AbsSender sender;

    public void setSender(AbsSender sender) {
        this.sender = sender;
    }

    /**
     * Every hour: find CONFIRMED bookings that are 24–25 hours away and
     * send the client a reminder message.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void sendReminders() {
        if (sender == null) {
            log.debug("ReminderService: sender not yet set, skipping");
            return;
        }

        LocalDateTime now  = DateTimeUtil.nowTashkent();
        LocalDateTime from = now.plusHours(24);
        LocalDateTime to   = now.plusHours(25);

        List<Booking> bookings = bookingRepository.findByStatusAndScheduledAtBetween(
                BookingStatus.CONFIRMED, from, to);

        log.info("Reminder job: {} booking(s) in 24-25h window", bookings.size());

        for (Booking booking : bookings) {
            long telegramUserId = booking.getClient().getTelegramUserId();
            try {
                sender.execute(SendMessage.builder()
                        .chatId(telegramUserId)
                        .text(MessageFormatter.reminderText(booking))
                        .build());
                log.info("Sent reminder for booking #{} to userId={}", booking.getId(), telegramUserId);
            } catch (TelegramApiException e) {
                log.error("Failed to send reminder for booking #{}", booking.getId(), e);
            }
        }
    }

    /**
     * Daily at 03:00: expire state rows that have been stuck for more than 1 hour.
     * This prevents users from being locked in a half-finished flow indefinitely.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void expireStaleStates() {
        LocalDateTime cutoff = DateTimeUtil.nowTashkent().minusHours(1);
        int count = stateService.resetStaleStates(cutoff);
        log.info("State expiration job: reset {} stale state(s)", count);
    }
}
