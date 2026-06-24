package com.umd.stobooking.util;

import com.umd.stobooking.model.Booking;
import com.umd.stobooking.model.BookingStatus;
import com.umd.stobooking.model.Car;
import com.umd.stobooking.model.Client;
import com.umd.stobooking.model.ServiceItem;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class MessageFormatter {

    private static final String[] RU_DAYS_SHORT =
            {"", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};

    private static final String[] RU_MONTHS_SHORT =
            {"", "янв", "фев", "мар", "апр", "май", "июн",
             "июл", "авг", "сен", "окт", "ноя", "дек"};

    private static final String[] RU_MONTHS_FULL =
            {"", "января", "февраля", "марта", "апреля", "мая", "июня",
             "июля", "августа", "сентября", "октября", "ноября", "декабря"};

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private MessageFormatter() {}

    // ── Prices & duration ────────────────────────────────────────────────────

    /** "200 000–350 000 сум" or "цена по запросу" */
    public static String formatPrice(Integer from, Integer to) {
        if (from == null && to == null) return "цена по запросу";
        if (from == null) return "до " + num(to) + " сум";
        if (to == null)   return "от " + num(from) + " сум";
        return num(from) + "–" + num(to) + " сум";
    }

    /** "45 мин" or "1 ч 30 мин" */
    public static String formatDuration(int minutes) {
        if (minutes < 60) return minutes + " мин";
        int h = minutes / 60, m = minutes % 60;
        return m == 0 ? h + " ч" : h + " ч " + m + " мин";
    }

    /** Inline button label: "Замена масла — 45 мин — 150 000–300 000 сум" */
    public static String serviceLabel(ServiceItem s) {
        return s.getName() + " — " + formatDuration(s.getDurationMinutes())
                + " — " + formatPrice(s.getPriceFrom(), s.getPriceTo());
    }

    // ── Car ──────────────────────────────────────────────────────────────────

    /** "Toyota Camry 2018 · 2.5 бензин" */
    public static String carLabel(Car car) {
        String base = car.getBrand().getName() + " " + car.getModel().getName();
        if (car.getYear() != null)     base += " " + car.getYear();
        if (car.getEngineInfo() != null) base += " · " + car.getEngineInfo();
        return base;
    }

    // ── Dates & times ────────────────────────────────────────────────────────

    /** Keyboard button label: "Пн 24 июн" */
    public static String dateButtonLabel(LocalDate date) {
        return RU_DAYS_SHORT[date.getDayOfWeek().getValue()]
                + " " + date.getDayOfMonth()
                + " " + RU_MONTHS_SHORT[date.getMonthValue()];
    }

    /** "Понедельник, 24 июня 2026" */
    public static String dateLong(LocalDate date) {
        return date.getDayOfMonth() + " " + RU_MONTHS_FULL[date.getMonthValue()]
                + " " + date.getYear();
    }

    /** "10:00" */
    public static String formatTime(LocalTime time) {
        return TIME_FMT.format(time);
    }

    // ── Booking confirmation text ─────────────────────────────────────────────

    public static String bookingConfirmText(Car car, ServiceItem service,
                                            LocalDate date, LocalTime time) {
        int duration = service.getDurationMinutes();
        LocalTime endTime = time.plusMinutes(duration);
        return "📋 Подтвердите запись:\n\n"
                + "🚗 Автомобиль: " + carLabel(car) + "\n"
                + "🔧 Услуга: " + service.getName() + "\n"
                + "📅 Дата: " + dateLong(date) + "\n"
                + "🕐 Время: " + formatTime(time) + " – " + formatTime(endTime) + "\n"
                + "💰 Цена: " + formatPrice(service.getPriceFrom(), service.getPriceTo());
    }

    public static String bookingConfirmTextProblem(Car car, String problem,
                                                   LocalDate date, LocalTime time) {
        LocalTime endTime = time.plusMinutes(60);
        return "📋 Подтвердите запись:\n\n"
                + "🚗 Автомобиль: " + carLabel(car) + "\n"
                + "🔧 Услуга: Диагностика (описание проблемы)\n"
                + "📝 Проблема: " + problem + "\n"
                + "📅 Дата: " + dateLong(date) + "\n"
                + "🕐 Время: " + formatTime(time) + " – " + formatTime(endTime);
    }

    // ── My bookings card ─────────────────────────────────────────────────────

    /**
     * Single booking card shown in /mybookings.
     * Example:
     *   📌 Запись #3
     *   🔧 Услуга: Замена масла и фильтра (45 мин)
     *   🚗 Toyota Camry 2018 · 2.5 бензин
     *   📅 Вт 25 июн 2026, 10:00 – 10:45
     *   ⏳ Статус: Ожидает подтверждения
     */
    public static String bookingCard(Booking booking) {
        StringBuilder sb = new StringBuilder();
        sb.append("📌 Запись #").append(booking.getId()).append("\n");

        if (booking.getService() != null) {
            sb.append("🔧 Услуга: ").append(booking.getService().getName())
              .append(" (").append(formatDuration(booking.getService().getDurationMinutes())).append(")\n");
        } else {
            sb.append("🔧 Услуга: Диагностика (по описанию)\n");
            if (booking.getProblemDescription() != null) {
                sb.append("📝 ").append(booking.getProblemDescription()).append("\n");
            }
        }

        sb.append("🚗 ").append(carLabel(booking.getCar())).append("\n");
        sb.append("📅 ").append(dateLong(booking.getScheduledAt().toLocalDate()))
          .append(", ").append(formatTime(booking.getScheduledAt().toLocalTime()))
          .append(" – ").append(formatTime(booking.getEndAt().toLocalTime())).append("\n");
        sb.append(formatStatus(booking.getStatus()));

        return sb.toString();
    }

    public static String formatStatus(BookingStatus status) {
        return switch (status) {
            case PENDING   -> "⏳ Статус: Ожидает подтверждения";
            case CONFIRMED -> "✅ Статус: Подтверждена";
            case CANCELLED -> "❌ Статус: Отменена";
            case REJECTED  -> "🚫 Статус: Отклонена";
            case COMPLETED -> "🏁 Статус: Завершена";
        };
    }

    // ── Admin notification ────────────────────────────────────────────────────

    public static String newBookingAdminText(Booking booking) {
        Car car = booking.getCar();
        Client client = booking.getClient();
        StringBuilder sb = new StringBuilder();
        sb.append("🔔 Новая запись #").append(booking.getId()).append("\n\n");
        sb.append("👤 Клиент: ").append(client.getFirstName());
        if (client.getPhone() != null) sb.append(" (").append(client.getPhone()).append(")");
        if (client.getTelegramUsername() != null) sb.append(" @").append(client.getTelegramUsername());
        sb.append("\n");
        sb.append("🚗 Авто: ").append(carLabel(car)).append("\n");
        if (booking.getService() != null) {
            sb.append("🔧 Услуга: ").append(booking.getService().getName())
              .append(" (").append(formatDuration(booking.getService().getDurationMinutes())).append(")\n");
        } else {
            sb.append("📝 Проблема: ").append(booking.getProblemDescription()).append("\n");
        }
        sb.append("📅 ").append(dateLong(booking.getScheduledAt().toLocalDate()))
          .append(", ").append(formatTime(booking.getScheduledAt().toLocalTime()))
          .append(" – ").append(formatTime(booking.getEndAt().toLocalTime())).append("\n");
        sb.append("\nЧтобы подтвердить: /confirm ").append(booking.getId());
        sb.append("\nЧтобы отклонить: /reject ").append(booking.getId());
        return sb.toString();
    }

    // ── Admin booking list ────────────────────────────────────────────────────

    /**
     * One-line summary for admin list views.
     * Example: "#3 · 10:00 Иван +998901234567 — Замена масла (45 мин)"
     */
    public static String adminBookingLine(Booking booking) {
        String time = formatTime(booking.getScheduledAt().toLocalTime());
        String client = booking.getClient().getFirstName();
        if (booking.getClient().getPhone() != null) {
            client += " " + booking.getClient().getPhone();
        }
        String service = booking.getService() != null
                ? booking.getService().getName() + " (" + formatDuration(booking.getService().getDurationMinutes()) + ")"
                : "Диагностика (по описанию)";
        return "#" + booking.getId() + " · " + time + " " + client + " — " + service;
    }

    /** Multi-line block for a date-grouped admin list. */
    public static String adminBookingList(String title, java.util.List<Booking> bookings) {
        if (bookings.isEmpty()) {
            return title + "\n\n(нет записей)";
        }
        StringBuilder sb = new StringBuilder(title).append("\n\n");
        for (Booking b : bookings) {
            sb.append(adminBookingLine(b)).append("\n");
        }
        return sb.toString().trim();
    }

    // ── Client notification texts ─────────────────────────────────────────────

    public static String clientConfirmedText(Booking booking) {
        return "✅ Ваша запись #" + booking.getId() + " подтверждена!\n\n"
                + bookingCard(booking);
    }

    public static String clientRejectedText(Booking booking) {
        StringBuilder sb = new StringBuilder();
        sb.append("🚫 К сожалению, ваша запись #").append(booking.getId()).append(" отклонена.\n");
        if (booking.getProblemDescription() != null
                && booking.getProblemDescription().contains("Причина отказа:")) {
            String reason = booking.getProblemDescription()
                    .substring(booking.getProblemDescription().indexOf("Причина отказа:"));
            sb.append(reason).append("\n");
        }
        sb.append("\nВы можете записаться на другое время — /book");
        return sb.toString();
    }

    public static String clientCompletedText(Booking booking) {
        return "🏁 Ваш визит #" + booking.getId() + " завершён. Спасибо, что выбрали нас!\n\n"
                + "Запишитесь снова — /book";
    }

    public static String reminderText(Booking booking) {
        LocalTime time = booking.getScheduledAt().toLocalTime();
        String date = dateLong(booking.getScheduledAt().toLocalDate());
        String service = booking.getService() != null
                ? booking.getService().getName()
                : "Диагностика (по описанию)";
        return "🔔 Напоминание!\n\n"
                + "Завтра у вас запись на СТО:\n"
                + "🔧 " + service + "\n"
                + "🚗 " + carLabel(booking.getCar()) + "\n"
                + "📅 " + date + ", " + formatTime(time) + "\n\n"
                + "Отменить (не позднее чем за 2 ч): /mybookings";
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String num(int n) {
        return String.format("%,d", n).replace(",", " ");
    }
}
