package com.umd.stobooking.bot.handler;

import com.umd.stobooking.bot.keyboard.KeyboardFactory;
import com.umd.stobooking.bot.state.BotStateEnum;
import com.umd.stobooking.bot.state.StateContext;
import com.umd.stobooking.config.AdminConfig;
import com.umd.stobooking.model.Booking;
import com.umd.stobooking.model.Car;
import com.umd.stobooking.model.Client;
import com.umd.stobooking.service.AdminService;
import com.umd.stobooking.service.BookingService;
import com.umd.stobooking.service.CarService;
import com.umd.stobooking.service.ClientService;
import com.umd.stobooking.service.StateService;
import com.umd.stobooking.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommandHandler {

    private final StateService stateService;
    private final ClientService clientService;
    private final CarService carService;
    private final BookingService bookingService;
    private final AdminService adminService;
    private final AdminConfig adminConfig;
    private final KeyboardFactory keyboardFactory;

    public void handle(Update update, AbsSender sender) {
        long chatId = update.getMessage().getChatId();
        long userId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText().trim();
        String firstName = update.getMessage().getFrom().getFirstName();
        String username = update.getMessage().getFrom().getUserName();

        String[] parts = text.split("\\s+");
        String command = parts[0].toLowerCase();

        switch (command) {
            // ── User commands ──────────────────────────────────────────────
            case "/start"      -> handleStart(chatId, userId, firstName, username, sender);
            case "/cancel"     -> handleCancel(chatId, userId, sender);
            case "/help"       -> handleHelp(chatId, userId, sender);
            case "/book"       -> handleBook(chatId, userId, sender);
            case "/mycars"     -> handleMyCars(chatId, userId, sender);
            case "/mybookings" -> handleMyBookings(chatId, userId, sender);

            // ── Admin commands ─────────────────────────────────────────────
            case "/today"      -> handleAdminList(chatId, userId, "today", sender);
            case "/tomorrow"   -> handleAdminList(chatId, userId, "tomorrow", sender);
            case "/week"       -> handleAdminList(chatId, userId, "week", sender);
            case "/pending"    -> handleAdminList(chatId, userId, "pending", sender);
            case "/confirm"    -> handleAdminConfirm(chatId, userId, parts, sender);
            case "/reject"     -> handleAdminReject(chatId, userId, parts, sender);
            case "/complete"   -> handleAdminComplete(chatId, userId, parts, sender);
            case "/stats"      -> handleAdminStats(chatId, userId, sender);

            default            -> log.debug("Unknown command '{}' from userId={}", command, userId);
        }
    }

    // ── /start ───────────────────────────────────────────────────────────────

    private void handleStart(long chatId, long userId, String firstName,
                             String username, AbsSender sender) {
        Optional<Client> existing = clientService.findByTelegramId(userId);

        if (existing.isEmpty()) {
            clientService.registerOrUpdate(userId, username, firstName);
            stateService.setState(userId, BotStateEnum.AWAITING_PHONE);
            send(sender, chatId,
                    "👋 Добро пожаловать в бот записи на СТО!\n\n" +
                    "Давайте зарегистрируем вас. Укажите ваш номер телефона.\n" +
                    "Формат: +998XXXXXXXXX");
        } else if (existing.get().getPhone() == null) {
            stateService.setState(userId, BotStateEnum.AWAITING_PHONE);
            send(sender, chatId,
                    "Давайте завершим регистрацию. Укажите ваш номер телефона.\n" +
                    "Формат: +998XXXXXXXXX");
        } else {
            stateService.clearState(userId);
            send(sender, chatId,
                    "👋 С возвращением, " + firstName + "!\n\n" +
                    "/book — Записаться на обслуживание\n" +
                    "/mybookings — Мои записи\n" +
                    "/mycars — Мои автомобили\n" +
                    "/help — Справка");
        }
    }

    // ── /cancel ──────────────────────────────────────────────────────────────

    private void handleCancel(long chatId, long userId, AbsSender sender) {
        BotStateEnum current = stateService.getState(userId);
        stateService.clearState(userId);
        if (current == BotStateEnum.IDLE) {
            send(sender, chatId, "Нет активного действия для отмены.");
        } else {
            send(sender, chatId,
                    "❌ Действие отменено.\n\n" +
                    "/book — Новая запись\n" +
                    "/start — Главное меню");
        }
    }

    // ── /help ────────────────────────────────────────────────────────────────

    private void handleHelp(long chatId, long userId, AbsSender sender) {
        StringBuilder sb = new StringBuilder();
        sb.append("📋 Доступные команды:\n\n");
        sb.append("/start — Главное меню\n");
        sb.append("/book — Записаться на обслуживание\n");
        sb.append("/mybookings — Мои активные записи\n");
        sb.append("/mycars — Мои автомобили\n");
        sb.append("/cancel — Отменить текущее действие\n");
        sb.append("/help — Эта справка");

        if (adminConfig.isAdmin(userId)) {
            sb.append("\n\n🔧 Команды администратора:\n");
            sb.append("/today — Записи на сегодня\n");
            sb.append("/tomorrow — Записи на завтра\n");
            sb.append("/week — Записи на 7 дней\n");
            sb.append("/pending — Ожидающие подтверждения\n");
            sb.append("/confirm <id> — Подтвердить запись\n");
            sb.append("/reject <id> [причина] — Отклонить запись\n");
            sb.append("/complete <id> — Завершить запись\n");
            sb.append("/stats — Статистика");
        }

        send(sender, chatId, sb.toString());
    }

    // ── /book ─────────────────────────────────────────────────────────────────

    private void handleBook(long chatId, long userId, AbsSender sender) {
        Optional<Client> clientOpt = clientService.findByTelegramId(userId);

        if (clientOpt.isEmpty() || clientOpt.get().getPhone() == null) {
            send(sender, chatId,
                    "Сначала завершите регистрацию. Отправьте /start");
            return;
        }

        List<Car> cars = carService.getCarsForClient(clientOpt.get().getId());
        if (cars.isEmpty()) {
            send(sender, chatId,
                    "У вас ещё нет зарегистрированных автомобилей.\n" +
                    "Отправьте /start чтобы добавить автомобиль.");
            return;
        }

        stateService.setState(userId, BotStateEnum.AWAITING_BOOK_PATH, new StateContext());
        send(sender, chatId,
                "🔧 Как вы хотите выбрать услугу?",
                keyboardFactory.bookPathKeyboard());
    }

    // ── /mycars ───────────────────────────────────────────────────────────────

    private void handleMyCars(long chatId, long userId, AbsSender sender) {
        Optional<Client> clientOpt = clientService.findByTelegramId(userId);
        if (clientOpt.isEmpty()) {
            send(sender, chatId, "Сначала завершите регистрацию. Отправьте /start");
            return;
        }

        List<Car> cars = carService.getCarsForClient(clientOpt.get().getId());
        if (cars.isEmpty()) {
            send(sender, chatId,
                    "У вас нет зарегистрированных автомобилей.\n" +
                    "Отправьте /start чтобы добавить автомобиль.");
            return;
        }

        StringBuilder sb = new StringBuilder("🚗 Ваши автомобили:\n\n");
        for (int i = 0; i < cars.size(); i++) {
            sb.append(i + 1).append(". ").append(MessageFormatter.carLabel(cars.get(i))).append("\n");
        }
        send(sender, chatId, sb.toString().trim());
    }

    // ── /mybookings ───────────────────────────────────────────────────────────

    private void handleMyBookings(long chatId, long userId, AbsSender sender) {
        Optional<Client> clientOpt = clientService.findByTelegramId(userId);
        if (clientOpt.isEmpty()) {
            send(sender, chatId, "Сначала завершите регистрацию. Отправьте /start");
            return;
        }

        List<Booking> bookings = bookingService.activeBookingsForClient(clientOpt.get().getId());

        if (bookings.isEmpty()) {
            send(sender, chatId,
                    "У вас нет активных записей.\n\nИспользуйте /book для новой записи.");
            return;
        }

        send(sender, chatId, "📋 Ваши активные записи (" + bookings.size() + "):");

        for (Booking booking : bookings) {
            send(sender, chatId,
                    MessageFormatter.bookingCard(booking),
                    keyboardFactory.cancelBookingKeyboard(booking.getId()));
        }
    }

    // ── Admin: list commands ───────────────────────────────────────────────────

    private void handleAdminList(long chatId, long userId, String period, AbsSender sender) {
        if (!adminConfig.isAdmin(userId)) {
            send(sender, chatId, "⛔ Нет доступа.");
            return;
        }

        List<Booking> bookings = switch (period) {
            case "today"    -> adminService.getTodayBookings();
            case "tomorrow" -> adminService.getTomorrowBookings();
            case "week"     -> adminService.getWeekBookings();
            case "pending"  -> adminService.getPendingBookings();
            default         -> List.of();
        };

        String title = switch (period) {
            case "today"    -> "📅 Записи на сегодня";
            case "tomorrow" -> "📅 Записи на завтра";
            case "week"     -> "📅 Записи на 7 дней";
            case "pending"  -> "⏳ Ожидают подтверждения";
            default         -> "";
        };

        send(sender, chatId, MessageFormatter.adminBookingList(title, bookings));
    }

    // ── Admin: /confirm <id> ──────────────────────────────────────────────────

    private void handleAdminConfirm(long chatId, long userId, String[] parts, AbsSender sender) {
        if (!adminConfig.isAdmin(userId)) {
            send(sender, chatId, "⛔ Нет доступа.");
            return;
        }
        if (parts.length < 2) {
            send(sender, chatId, "Использование: /confirm <id>");
            return;
        }
        long bookingId;
        try {
            bookingId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            send(sender, chatId, "Неверный ID: " + parts[1]);
            return;
        }

        try {
            Booking booking = adminService.confirmBooking(bookingId);
            send(sender, chatId, "✅ Запись #" + bookingId + " подтверждена.");
            notifyClient(sender, booking, MessageFormatter.clientConfirmedText(booking));
        } catch (Exception e) {
            send(sender, chatId, "Ошибка: " + e.getMessage());
        }
    }

    // ── Admin: /reject <id> [reason] ──────────────────────────────────────────

    private void handleAdminReject(long chatId, long userId, String[] parts, AbsSender sender) {
        if (!adminConfig.isAdmin(userId)) {
            send(sender, chatId, "⛔ Нет доступа.");
            return;
        }
        if (parts.length < 2) {
            send(sender, chatId, "Использование: /reject <id> [причина]");
            return;
        }
        long bookingId;
        try {
            bookingId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            send(sender, chatId, "Неверный ID: " + parts[1]);
            return;
        }

        String reason = parts.length > 2
                ? String.join(" ", java.util.Arrays.copyOfRange(parts, 2, parts.length))
                : null;

        try {
            Booking booking = adminService.rejectBooking(bookingId, reason);
            send(sender, chatId, "🚫 Запись #" + bookingId + " отклонена.");
            notifyClient(sender, booking, MessageFormatter.clientRejectedText(booking));
        } catch (Exception e) {
            send(sender, chatId, "Ошибка: " + e.getMessage());
        }
    }

    // ── Admin: /complete <id> ──────────────────────────────────────────────────

    private void handleAdminComplete(long chatId, long userId, String[] parts, AbsSender sender) {
        if (!adminConfig.isAdmin(userId)) {
            send(sender, chatId, "⛔ Нет доступа.");
            return;
        }
        if (parts.length < 2) {
            send(sender, chatId, "Использование: /complete <id>");
            return;
        }
        long bookingId;
        try {
            bookingId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            send(sender, chatId, "Неверный ID: " + parts[1]);
            return;
        }

        try {
            Booking booking = adminService.completeBooking(bookingId);
            send(sender, chatId, "🏁 Запись #" + bookingId + " завершена.");
            notifyClient(sender, booking, MessageFormatter.clientCompletedText(booking));
        } catch (Exception e) {
            send(sender, chatId, "Ошибка: " + e.getMessage());
        }
    }

    // ── Admin: /stats ─────────────────────────────────────────────────────────

    private void handleAdminStats(long chatId, long userId, AbsSender sender) {
        if (!adminConfig.isAdmin(userId)) {
            send(sender, chatId, "⛔ Нет доступа.");
            return;
        }
        send(sender, chatId, adminService.buildStatsText());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Send notification to the client's Telegram account. */
    private void notifyClient(AbsSender sender, Booking booking, String text) {
        long telegramUserId = booking.getClient().getTelegramUserId();
        try {
            sender.execute(SendMessage.builder()
                    .chatId(telegramUserId)
                    .text(text)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Failed to notify client (userId={}) for booking #{}",
                    telegramUserId, booking.getId(), e);
        }
    }

    private void send(AbsSender sender, long chatId, String text) {
        try {
            sender.execute(SendMessage.builder().chatId(chatId).text(text).build());
        } catch (TelegramApiException e) {
            log.error("Failed to send to chatId={}", chatId, e);
        }
    }

    private void send(AbsSender sender, long chatId, String text, InlineKeyboardMarkup keyboard) {
        try {
            sender.execute(SendMessage.builder()
                    .chatId(chatId).text(text).replyMarkup(keyboard).build());
        } catch (TelegramApiException e) {
            log.error("Failed to send to chatId={}", chatId, e);
        }
    }
}
