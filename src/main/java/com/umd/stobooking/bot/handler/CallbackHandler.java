package com.umd.stobooking.bot.handler;

import com.umd.stobooking.bot.keyboard.KeyboardFactory;
import com.umd.stobooking.bot.state.BotStateEnum;
import com.umd.stobooking.bot.state.StateContext;
import com.umd.stobooking.config.AdminConfig;
import com.umd.stobooking.exception.CancellationTooLateException;
import com.umd.stobooking.exception.SlotUnavailableException;
import com.umd.stobooking.model.*;
import com.umd.stobooking.service.*;
import com.umd.stobooking.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallbackHandler {

    private final StateService stateService;
    private final CarService carService;
    private final ClientService clientService;
    private final CatalogService catalogService;
    private final ScheduleService scheduleService;
    private final BookingService bookingService;
    private final KeyboardFactory keyboardFactory;
    private final AdminConfig adminConfig;

    public void handle(Update update, AbsSender sender) {
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        long userId = update.getCallbackQuery().getFrom().getId();
        String callbackId = update.getCallbackQuery().getId();
        String data = update.getCallbackQuery().getData();

        ack(sender, callbackId);

        BotStateEnum state = stateService.getState(userId);
        StateContext ctx   = stateService.getContext(userId);

        if      (data.startsWith("brand:"))          handleBrand(chatId, userId, data, state, ctx, sender);
        else if (data.startsWith("model:"))          handleModel(chatId, userId, data, state, ctx, sender);
        else if (data.startsWith("path:"))           handlePath(chatId, userId, data, state, sender);
        else if (data.startsWith("cat:"))            handleCategory(chatId, userId, data, state, ctx, sender);
        else if (data.startsWith("svc:"))            handleService(chatId, userId, data, state, ctx, sender);
        else if (data.startsWith("car:"))            handleCar(chatId, userId, data, state, ctx, sender);
        else if (data.startsWith("date:"))           handleDate(chatId, userId, data, state, ctx, sender);
        else if (data.startsWith("time:"))           handleTime(chatId, userId, data, state, ctx, sender);
        else if (data.startsWith("confirm:"))        handleConfirm(chatId, userId, data, state, ctx, sender);
        else if (data.startsWith("cancel_booking:")) handleCancelBooking(chatId, userId, data, sender);
        else log.debug("Unhandled callback '{}' state={} userId={}", data, state, userId);
    }

    // ── Registration: brand:<id> ──────────────────────────────────────────────

    private void handleBrand(long chatId, long userId, String data,
                             BotStateEnum state, StateContext ctx, AbsSender sender) {
        if (state != BotStateEnum.AWAITING_CAR_BRAND) {
            send(sender, chatId, "Используйте /cancel и начните заново.");
            return;
        }
        long brandId = Long.parseLong(data.substring("brand:".length()));
        String brandName = carService.findBrand(brandId).map(CarBrand::getName).orElse("?");
        List<CarModel> models = carService.getModelsForBrand(brandId);
        if (models.isEmpty()) {
            send(sender, chatId, "Для этой марки нет моделей. Выберите другую.");
            return;
        }
        stateService.setState(userId, BotStateEnum.AWAITING_CAR_MODEL,
                StateContext.builder().tempBrandId(brandId).build());
        send(sender, chatId,
                "Марка: " + brandName + " ✅\n\nВыберите модель:",
                keyboardFactory.modelsKeyboard(models));
    }

    // ── Registration: model:<id> ──────────────────────────────────────────────

    private void handleModel(long chatId, long userId, String data,
                             BotStateEnum state, StateContext ctx, AbsSender sender) {
        if (state != BotStateEnum.AWAITING_CAR_MODEL) {
            send(sender, chatId, "Используйте /cancel и начните заново.");
            return;
        }
        long modelId = Long.parseLong(data.substring("model:".length()));
        String modelName = carService.findModel(modelId).map(CarModel::getName).orElse("?");
        stateService.setState(userId, BotStateEnum.AWAITING_CAR_YEAR,
                StateContext.builder()
                        .tempBrandId(ctx.getTempBrandId())
                        .tempModelId(modelId)
                        .build());
        send(sender, chatId, "Модель: " + modelName + " ✅\n\nВведите год выпуска (например: 2018):");
    }

    // ── Booking: path:menu | path:problem ────────────────────────────────────

    private void handlePath(long chatId, long userId, String data,
                            BotStateEnum state, AbsSender sender) {
        if (state != BotStateEnum.AWAITING_BOOK_PATH) {
            send(sender, chatId, "Используйте /book чтобы начать запись.");
            return;
        }
        if (data.equals("path:menu")) {
            stateService.setState(userId, BotStateEnum.AWAITING_CATEGORY, new StateContext());
            send(sender, chatId, "Выберите категорию услуг:",
                    keyboardFactory.categoriesKeyboard(catalogService.listCategories()));
        } else if (data.equals("path:problem")) {
            stateService.setState(userId, BotStateEnum.AWAITING_PROBLEM_DESCRIPTION, new StateContext());
            send(sender, chatId,
                    "💬 Опишите проблему своими словами.\n\n" +
                    "Мастер уточнит детали при приёме.");
        }
    }

    // ── Booking: cat:<id> ─────────────────────────────────────────────────────

    private void handleCategory(long chatId, long userId, String data,
                                BotStateEnum state, StateContext ctx, AbsSender sender) {
        if (state != BotStateEnum.AWAITING_CATEGORY) {
            send(sender, chatId, "Используйте /book чтобы начать запись.");
            return;
        }
        long categoryId = Long.parseLong(data.substring("cat:".length()));
        List<ServiceItem> services = catalogService.listServices(categoryId);
        if (services.isEmpty()) {
            send(sender, chatId, "В этой категории нет услуг. Выберите другую.");
            return;
        }
        stateService.setState(userId, BotStateEnum.AWAITING_SERVICE,
                StateContext.builder().categoryId(categoryId).build());
        send(sender, chatId, "Выберите услугу:",
                keyboardFactory.servicesKeyboard(services));
    }

    // ── Booking: svc:<id> ─────────────────────────────────────────────────────

    private void handleService(long chatId, long userId, String data,
                               BotStateEnum state, StateContext ctx, AbsSender sender) {
        if (state != BotStateEnum.AWAITING_SERVICE) {
            send(sender, chatId, "Используйте /book чтобы начать запись.");
            return;
        }
        long serviceId = Long.parseLong(data.substring("svc:".length()));
        Optional<ServiceItem> serviceOpt = catalogService.findService(serviceId);
        if (serviceOpt.isEmpty()) {
            send(sender, chatId, "Услуга не найдена. Попробуйте снова.");
            return;
        }
        ServiceItem service = serviceOpt.get();

        Client client = requireClient(chatId, userId, sender);
        if (client == null) return;

        List<Car> cars = carService.getCarsForClient(client.getId());
        if (cars.isEmpty()) {
            send(sender, chatId, "У вас нет автомобилей. Отправьте /start чтобы добавить.");
            return;
        }

        stateService.setState(userId, BotStateEnum.AWAITING_BOOK_CAR,
                StateContext.builder()
                        .categoryId(ctx.getCategoryId())
                        .serviceId(serviceId)
                        .build());

        send(sender, chatId,
                "Услуга: " + service.getName() + " ✅\n"
                + "Длительность: " + MessageFormatter.formatDuration(service.getDurationMinutes()) + "\n"
                + "Цена: " + MessageFormatter.formatPrice(service.getPriceFrom(), service.getPriceTo())
                + "\n\nВыберите автомобиль:",
                keyboardFactory.carsKeyboard(cars));
    }

    // ── Booking: car:<id> ─────────────────────────────────────────────────────

    private void handleCar(long chatId, long userId, String data,
                           BotStateEnum state, StateContext ctx, AbsSender sender) {
        if (state != BotStateEnum.AWAITING_BOOK_CAR) {
            send(sender, chatId, "Используйте /book чтобы начать запись.");
            return;
        }
        long carId = Long.parseLong(data.substring("car:".length()));

        Client client = requireClient(chatId, userId, sender);
        if (client == null) return;

        // Verify the car belongs to this client
        boolean owned = carService.getCarsForClient(client.getId())
                .stream().anyMatch(c -> c.getId().equals(carId));
        if (!owned) {
            send(sender, chatId, "Автомобиль не найден.");
            return;
        }

        // Determine service for slot calculation
        ServiceItem service = null;
        if (ctx.getServiceId() != null) {
            service = catalogService.findService(ctx.getServiceId()).orElse(null);
        }

        // Show available dates
        List<LocalDate> dates = (service != null)
                ? scheduleService.availableDates(service, 7)
                : scheduleService.availableDates(dummyDiagnosticsService(), 7);

        if (dates.isEmpty()) {
            send(sender, chatId, "К сожалению, нет свободных слотов на ближайшие 2 месяца. " +
                    "Пожалуйста, свяжитесь с нами напрямую.");
            return;
        }

        stateService.setState(userId, BotStateEnum.AWAITING_BOOK_DATE,
                StateContext.builder()
                        .categoryId(ctx.getCategoryId())
                        .serviceId(ctx.getServiceId())
                        .problemDescription(ctx.getProblemDescription())
                        .carId(carId)
                        .build());

        send(sender, chatId,
                "🚗 Автомобиль: " + carLabel(carId, client) + " ✅\n\n📅 Выберите дату:",
                keyboardFactory.datesKeyboard(dates));
    }

    // ── Booking: date:<YYYY-MM-DD> ────────────────────────────────────────────

    private void handleDate(long chatId, long userId, String data,
                            BotStateEnum state, StateContext ctx, AbsSender sender) {
        if (state != BotStateEnum.AWAITING_BOOK_DATE) {
            send(sender, chatId, "Используйте /book чтобы начать запись.");
            return;
        }
        LocalDate date = LocalDate.parse(data.substring("date:".length()));

        ServiceItem service = ctx.getServiceId() != null
                ? catalogService.findService(ctx.getServiceId()).orElse(null)
                : null;

        List<LocalTime> slots = scheduleService.availableSlots(
                date, service != null ? service : dummyDiagnosticsService());

        if (slots.isEmpty()) {
            send(sender, chatId,
                    "На эту дату нет свободных слотов. Пожалуйста, выберите другую дату:");
            // Re-show date keyboard
            List<LocalDate> dates = scheduleService.availableDates(
                    service != null ? service : dummyDiagnosticsService(), 7);
            if (!dates.isEmpty()) {
                send(sender, chatId, "📅 Выберите дату:", keyboardFactory.datesKeyboard(dates));
            }
            return;
        }

        stateService.setState(userId, BotStateEnum.AWAITING_BOOK_TIME,
                StateContext.builder()
                        .categoryId(ctx.getCategoryId())
                        .serviceId(ctx.getServiceId())
                        .problemDescription(ctx.getProblemDescription())
                        .carId(ctx.getCarId())
                        .scheduledDate(date.toString())
                        .build());

        send(sender, chatId,
                "📅 Дата: " + MessageFormatter.dateLong(date) + " ✅\n\n🕐 Выберите время:",
                keyboardFactory.timesKeyboard(slots));
    }

    // ── Booking: time:<HH:mm> ─────────────────────────────────────────────────

    private void handleTime(long chatId, long userId, String data,
                            BotStateEnum state, StateContext ctx, AbsSender sender) {
        if (state != BotStateEnum.AWAITING_BOOK_TIME) {
            send(sender, chatId, "Используйте /book чтобы начать запись.");
            return;
        }
        LocalTime time = LocalTime.parse(data.substring("time:".length()));
        LocalDate date = LocalDate.parse(ctx.getScheduledDate());

        Client client = requireClient(chatId, userId, sender);
        if (client == null) return;

        Car car = carService.getCarsForClient(client.getId()).stream()
                .filter(c -> c.getId().equals(ctx.getCarId()))
                .findFirst().orElse(null);
        if (car == null) {
            send(sender, chatId, "Ошибка: автомобиль не найден. Начните заново с /book");
            return;
        }

        ServiceItem service = ctx.getServiceId() != null
                ? catalogService.findService(ctx.getServiceId()).orElse(null)
                : null;

        stateService.setState(userId, BotStateEnum.AWAITING_BOOK_CONFIRM,
                StateContext.builder()
                        .categoryId(ctx.getCategoryId())
                        .serviceId(ctx.getServiceId())
                        .problemDescription(ctx.getProblemDescription())
                        .carId(ctx.getCarId())
                        .scheduledDate(date.toString())
                        .scheduledTime(MessageFormatter.formatTime(time))
                        .build());

        String confirmText = (service != null)
                ? MessageFormatter.bookingConfirmText(car, service, date, time)
                : MessageFormatter.bookingConfirmTextProblem(car, ctx.getProblemDescription(), date, time);

        send(sender, chatId, confirmText,
                keyboardFactory.confirmKeyboard("confirm:yes", "confirm:no"));
    }

    // ── Booking: confirm:yes | confirm:no ────────────────────────────────────

    private void handleConfirm(long chatId, long userId, String data,
                               BotStateEnum state, StateContext ctx, AbsSender sender) {
        if (state != BotStateEnum.AWAITING_BOOK_CONFIRM) {
            send(sender, chatId, "Используйте /book чтобы начать запись.");
            return;
        }

        if (data.equals("confirm:no")) {
            stateService.clearState(userId);
            send(sender, chatId, "❌ Запись отменена. Используйте /book для новой записи.");
            return;
        }

        // confirm:yes — create the booking
        Client client = requireClient(chatId, userId, sender);
        if (client == null) return;

        Car car = carService.getCarsForClient(client.getId()).stream()
                .filter(c -> c.getId().equals(ctx.getCarId()))
                .findFirst().orElse(null);
        if (car == null) {
            send(sender, chatId, "Ошибка: автомобиль не найден. Начните заново с /book");
            stateService.clearState(userId);
            return;
        }

        LocalDateTime scheduledAt = LocalDate.parse(ctx.getScheduledDate())
                .atTime(LocalTime.parse(ctx.getScheduledTime()));

        Booking booking;
        try {
            if (ctx.getServiceId() != null) {
                ServiceItem service = catalogService.findService(ctx.getServiceId()).orElseThrow();
                booking = bookingService.createBooking(client, car, service, scheduledAt);
            } else {
                booking = bookingService.createBookingWithProblem(
                        client, car, ctx.getProblemDescription(), scheduledAt);
            }
        } catch (SlotUnavailableException e) {
            stateService.clearState(userId);
            send(sender, chatId,
                    "⚠️ " + e.getMessage() + "\n\nИспользуйте /book и выберите другое время.");
            return;
        }

        stateService.clearState(userId);

        // Confirm to client
        send(sender, chatId,
                "✅ Запись #" + booking.getId() + " создана!\n\n" +
                "Статус: ожидает подтверждения администратора.\n" +
                "Мы уведомим вас, когда запись будет подтверждена.\n\n" +
                "Ваши записи: /mybookings\n" +
                "Отменить запись: /mybookings");

        // Notify all admins
        String adminText = MessageFormatter.newBookingAdminText(booking);
        for (long adminId : adminConfig.getAdminIds()) {
            send(sender, adminId, adminText);
        }
    }

    // ── cancel_booking:<id> ───────────────────────────────────────────────────

    private void handleCancelBooking(long chatId, long userId, String data, AbsSender sender) {
        long bookingId = Long.parseLong(data.substring("cancel_booking:".length()));
        try {
            bookingService.cancelBooking(bookingId, userId);
            send(sender, chatId, "✅ Запись #" + bookingId + " отменена.");
        } catch (CancellationTooLateException e) {
            send(sender, chatId, "⚠️ " + e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            send(sender, chatId, "❌ " + e.getMessage());
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Client requireClient(long chatId, long userId, AbsSender sender) {
        return clientService.findByTelegramId(userId).orElseGet(() -> {
            send(sender, chatId, "Ошибка: клиент не найден. Отправьте /start");
            return null;
        });
    }

    private String carLabel(long carId, Client client) {
        return carService.getCarsForClient(client.getId()).stream()
                .filter(c -> c.getId().equals(carId))
                .map(MessageFormatter::carLabel)
                .findFirst().orElse("Автомобиль #" + carId);
    }

    /** Fallback service (60 min) used for slot calc on the free-text path. */
    private ServiceItem dummyDiagnosticsService() {
        ServiceItem dummy = new ServiceItem();
        dummy.setDurationMinutes(60);
        return dummy;
    }

    private void ack(AbsSender sender, String callbackId) {
        try {
            sender.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackId).build());
        } catch (TelegramApiException e) {
            log.warn("Failed to ack callback {}", callbackId, e);
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
