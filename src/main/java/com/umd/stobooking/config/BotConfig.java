package com.umd.stobooking.config;

import com.umd.stobooking.bot.StoBookingBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotConfig {

    private final StoBookingBot bot;

    @EventListener(ContextRefreshedEvent.class)
    public void registerBot() {
        try {
            TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
            api.registerBot(bot);
            log.info("Bot registered: @{}", bot.getBotUsername());
        } catch (TelegramApiException e) {
            log.error("Failed to register bot", e);
            throw new RuntimeException(e);
        }
    }
}
