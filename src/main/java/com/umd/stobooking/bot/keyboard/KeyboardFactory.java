package com.umd.stobooking.bot.keyboard;

import com.umd.stobooking.model.Car;
import com.umd.stobooking.model.CarBrand;
import com.umd.stobooking.model.CarModel;
import com.umd.stobooking.model.ServiceCategory;
import com.umd.stobooking.model.ServiceItem;
import com.umd.stobooking.util.MessageFormatter;

import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

@Component
public class KeyboardFactory {

    // ── Generic builder ───────────────────────────────────────────────────────

    private InlineKeyboardMarkup buildKeyboard(List<String> labels,
                                               List<String> callbacks,
                                               int perRow) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        for (int i = 0; i < labels.size(); i++) {
            row.add(InlineKeyboardButton.builder()
                    .text(labels.get(i))
                    .callbackData(callbacks.get(i))
                    .build());
            if (row.size() == perRow) {
                rows.add(new ArrayList<>(row));
                row.clear();
            }
        }
        if (!row.isEmpty()) rows.add(row);
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    // ── Registration ──────────────────────────────────────────────────────────

    public InlineKeyboardMarkup brandsKeyboard(List<CarBrand> brands) {
        return buildKeyboard(
                brands.stream().map(CarBrand::getName).toList(),
                brands.stream().map(b -> "brand:" + b.getId()).toList(),
                2);
    }

    public InlineKeyboardMarkup modelsKeyboard(List<CarModel> models) {
        return buildKeyboard(
                models.stream().map(CarModel::getName).toList(),
                models.stream().map(m -> "model:" + m.getId()).toList(),
                2);
    }

    // ── Booking: path choice ──────────────────────────────────────────────────

    public InlineKeyboardMarkup bookPathKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        InlineKeyboardButton.builder()
                                .text("📋 Выбрать из меню").callbackData("path:menu").build(),
                        InlineKeyboardButton.builder()
                                .text("💬 Описать проблему").callbackData("path:problem").build()
                ))
                .build();
    }

    // ── Booking: categories ───────────────────────────────────────────────────

    public InlineKeyboardMarkup categoriesKeyboard(List<ServiceCategory> categories) {
        return buildKeyboard(
                categories.stream().map(ServiceCategory::getName).toList(),
                categories.stream().map(c -> "cat:" + c.getId()).toList(),
                1);
    }

    // ── Booking: services ─────────────────────────────────────────────────────

    public InlineKeyboardMarkup servicesKeyboard(List<ServiceItem> services) {
        return buildKeyboard(
                services.stream().map(MessageFormatter::serviceLabel).toList(),
                services.stream().map(s -> "svc:" + s.getId()).toList(),
                1);
    }

    // ── Booking: user's cars ──────────────────────────────────────────────────

    public InlineKeyboardMarkup carsKeyboard(List<Car> cars) {
        return buildKeyboard(
                cars.stream().map(MessageFormatter::carLabel).toList(),
                cars.stream().map(c -> "car:" + c.getId()).toList(),
                1);
    }

    // ── Booking: dates ────────────────────────────────────────────────────────

    public InlineKeyboardMarkup datesKeyboard(List<LocalDate> dates) {
        return buildKeyboard(
                dates.stream().map(MessageFormatter::dateButtonLabel).toList(),
                dates.stream().map(d -> "date:" + d).toList(),
                3);
    }

    // ── Booking: times ────────────────────────────────────────────────────────

    public InlineKeyboardMarkup timesKeyboard(List<LocalTime> times) {
        return buildKeyboard(
                times.stream().map(MessageFormatter::formatTime).toList(),
                times.stream().map(t -> "time:" + MessageFormatter.formatTime(t)).toList(),
                4);
    }

    // ── My bookings: cancel button ────────────────────────────────────────────

    public InlineKeyboardMarkup cancelBookingKeyboard(long bookingId) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        InlineKeyboardButton.builder()
                                .text("❌ Отменить запись #" + bookingId)
                                .callbackData("cancel_booking:" + bookingId)
                                .build()
                ))
                .build();
    }

    // ── Confirmation ──────────────────────────────────────────────────────────

    public InlineKeyboardMarkup confirmKeyboard(String yesCallback, String noCallback) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        InlineKeyboardButton.builder()
                                .text("✅ Подтвердить").callbackData(yesCallback).build(),
                        InlineKeyboardButton.builder()
                                .text("❌ Отмена").callbackData(noCallback).build()
                ))
                .build();
    }
}
