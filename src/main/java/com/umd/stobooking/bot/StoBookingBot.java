package com.umd.stobooking.bot;

import com.umd.stobooking.bot.handler.CallbackHandler;
import com.umd.stobooking.bot.handler.CommandHandler;
import com.umd.stobooking.bot.handler.TextMessageHandler;
import com.umd.stobooking.service.ReminderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Component
public class StoBookingBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final CommandHandler commandHandler;
    private final TextMessageHandler textMessageHandler;
    private final CallbackHandler callbackHandler;

    public StoBookingBot(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            CommandHandler commandHandler,
            TextMessageHandler textMessageHandler,
            CallbackHandler callbackHandler,
            ReminderService reminderService) {
        super(botToken);
        this.botUsername = botUsername;
        this.commandHandler = commandHandler;
        this.textMessageHandler = textMessageHandler;
        this.callbackHandler = callbackHandler;
        reminderService.setSender(this);
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                callbackHandler.handle(update, this);
                return;
            }

            if (!update.hasMessage()) return;

            Message msg = update.getMessage();

            if (!msg.hasText()) return;

            String text = msg.getText().trim();
            log.debug("Message from userId={}: {}", msg.getFrom().getId(), text);

            if (text.startsWith("/")) {
                commandHandler.handle(update, this);
            } else {
                textMessageHandler.handle(update, this);
            }

        } catch (Exception e) {
            log.error("Unhandled exception in onUpdateReceived", e);
        }
    }

    public void sendText(long chatId, String text) {
        try {
            execute(SendMessage.builder().chatId(chatId).text(text).build());
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chatId={}", chatId, e);
        }
    }
}
