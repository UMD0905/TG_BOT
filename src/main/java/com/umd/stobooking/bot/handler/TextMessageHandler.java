package com.umd.stobooking.bot.handler;

import com.umd.stobooking.bot.keyboard.KeyboardFactory;
import com.umd.stobooking.bot.state.BotStateEnum;
import com.umd.stobooking.bot.state.StateContext;
import com.umd.stobooking.model.Car;
import com.umd.stobooking.model.Client;
import com.umd.stobooking.service.CarService;
import com.umd.stobooking.service.CatalogService;
import com.umd.stobooking.service.ClientService;
import com.umd.stobooking.service.StateService;
import com.umd.stobooking.util.PhoneValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Component
@RequiredArgsConstructor
public class TextMessageHandler {

    private final StateService stateService;
    private final ClientService clientService;
    private final CarService carService;
    private final CatalogService catalogService;
    private final KeyboardFactory keyboardFactory;

    public void handle(Update update, AbsSender sender) {
        long chatId = update.getMessage().getChatId();
        long userId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText().trim();

        BotStateEnum state = stateService.getState(userId);
        StateContext ctx = stateService.getContext(userId);

        switch (state) {
            case AWAITING_PHONE                -> handlePhone(chatId, userId, text, sender);
            case AWAITING_CAR_YEAR             -> handleCarYear(chatId, userId, text, ctx, sender);
            case AWAITING_CAR_ENGINE           -> handleCarEngine(chatId, userId, text, ctx, sender);
            case AWAITING_PROBLEM_DESCRIPTION  -> handleProblemDescription(chatId, userId, text, ctx, sender);
            default -> send(sender, chatId,
                    "Я не понял это сообщение. Используйте /help для списка команд " +
                    "или /cancel для отмены текущего действия.");
        }
    }

    // ── Phone ─────────────────────────────────────────────────────────────────

    private void handlePhone(long chatId, long userId, String text, AbsSender sender) {
        String phone = PhoneValidator.normalize(text);
        if (phone == null) {
            send(sender, chatId,
                    "❌ Неверный формат номера. Пожалуйста, введите номер в формате:\n" +
                    "+998XXXXXXXXX");
            return;
        }

        clientService.savePhone(userId, phone);

        // Ask for car brand
        send(sender, chatId,
                "✅ Отлично! Номер сохранён: " + phone + "\n\n" +
                "Теперь добавим ваш автомобиль. Выберите марку:",
                keyboardFactory.brandsKeyboard(carService.getAllBrands()));

        stateService.setState(userId, BotStateEnum.AWAITING_CAR_BRAND, new StateContext());
    }

    // ── Car Year ─────────────────────────────────────────────────────────────

    private void handleCarYear(long chatId, long userId, String text,
                               StateContext ctx, AbsSender sender) {
        int year;
        try {
            year = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            send(sender, chatId, "❌ Укажите год числом, например: 2018");
            return;
        }

        int currentYear = java.time.Year.now().getValue();
        if (year < 1970 || year > currentYear) {
            send(sender, chatId,
                    "❌ Год должен быть от 1970 до " + currentYear + ". Попробуйте ещё раз:");
            return;
        }

        ctx.setTempModelId(ctx.getTempModelId()); // keep modelId
        StateContext updated = StateContext.builder()
                .tempBrandId(ctx.getTempBrandId())
                .tempModelId(ctx.getTempModelId())
                .build();

        // Reuse ctx but store year temporarily in a string field
        // We'll pass it through the engine step via a simple workaround:
        // store as scheduledDate field (repurposed temporarily)
        updated.setScheduledDate(String.valueOf(year));

        stateService.setState(userId, BotStateEnum.AWAITING_CAR_ENGINE, updated);
        send(sender, chatId,
                "Год: " + year + " ✅\n\n" +
                "Укажите информацию о двигателе (например: 2.5 бензин, 1.6 дизель):");
    }

    // ── Car Engine ───────────────────────────────────────────────────────────

    private void handleCarEngine(long chatId, long userId, String text,
                                 StateContext ctx, AbsSender sender) {
        if (text.isBlank()) {
            send(sender, chatId, "❌ Пожалуйста, введите информацию о двигателе.");
            return;
        }

        String engineInfo = text.trim();
        int year = 0;
        try {
            year = Integer.parseInt(ctx.getScheduledDate()); // year was stored here
        } catch (NumberFormatException e) {
            log.error("Could not parse year from context for userId={}", userId);
        }

        Client client = clientService.findByTelegramId(userId)
                .orElseThrow(() -> new IllegalStateException("Client not found: " + userId));

        Car car = carService.addCar(client,
                ctx.getTempBrandId(),
                ctx.getTempModelId(),
                year,
                engineInfo);

        stateService.clearState(userId);

        String brandName = car.getBrand().getName();
        String modelName = car.getModel().getName();

        send(sender, chatId,
                "🚗 Автомобиль добавлен!\n\n" +
                brandName + " " + modelName + " " + year + "\n" +
                "Двигатель: " + engineInfo + "\n\n" +
                "Регистрация завершена! Теперь вы можете:\n" +
                "/book — Записаться на обслуживание\n" +
                "/mycars — Мои автомобили\n" +
                "/help — Справка");
    }

    // ── Problem Description (free-text path) ──────────────────────────────────

    private void handleProblemDescription(long chatId, long userId, String text,
                                          StateContext ctx, AbsSender sender) {
        if (text.isBlank() || text.length() < 5) {
            send(sender, chatId,
                    "❌ Пожалуйста, опишите проблему подробнее (минимум 5 символов).");
            return;
        }

        Client client = clientService.findByTelegramId(userId)
                .orElseThrow(() -> new IllegalStateException("Client not found: " + userId));
        java.util.List<Car> cars = carService.getCarsForClient(client.getId());

        if (cars.isEmpty()) {
            send(sender, chatId,
                    "У вас нет зарегистрированных автомобилей. Отправьте /start чтобы добавить.");
            return;
        }

        stateService.setState(userId, BotStateEnum.AWAITING_BOOK_CAR,
                StateContext.builder().problemDescription(text).build());

        send(sender, chatId,
                "Проблема записана ✅\n\nВыберите автомобиль:",
                keyboardFactory.carsKeyboard(cars));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

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
