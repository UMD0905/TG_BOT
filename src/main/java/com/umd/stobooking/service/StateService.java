package com.umd.stobooking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umd.stobooking.bot.state.BotStateEnum;
import com.umd.stobooking.bot.state.StateContext;
import com.umd.stobooking.model.BotStateEntity;
import com.umd.stobooking.repository.BotStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class StateService {

    private final BotStateRepository botStateRepository;
    private final ObjectMapper objectMapper;

    public BotStateEnum getState(long telegramUserId) {
        return botStateRepository.findById(telegramUserId)
                .map(e -> {
                    try {
                        return BotStateEnum.valueOf(e.getCurrentState());
                    } catch (Exception ex) {
                        return BotStateEnum.IDLE;
                    }
                })
                .orElse(BotStateEnum.IDLE);
    }

    public StateContext getContext(long telegramUserId) {
        return botStateRepository.findById(telegramUserId)
                .map(e -> deserialize(e.getStateData()))
                .orElse(new StateContext());
    }

    @Transactional
    public void setState(long telegramUserId, BotStateEnum state) {
        setState(telegramUserId, state, new StateContext());
    }

    @Transactional
    public void setState(long telegramUserId, BotStateEnum state, StateContext context) {
        BotStateEntity entity = botStateRepository.findById(telegramUserId)
                .orElseGet(() -> {
                    BotStateEntity e = new BotStateEntity();
                    e.setTelegramUserId(telegramUserId);
                    return e;
                });
        entity.setCurrentState(state.name());
        entity.setStateData(serialize(context));
        entity.setUpdatedAt(LocalDateTime.now());
        botStateRepository.save(entity);
    }

    @Transactional
    public void clearState(long telegramUserId) {
        setState(telegramUserId, BotStateEnum.IDLE, new StateContext());
    }

    /**
     * Resets all non-IDLE states that were last updated before {@code cutoff}.
     * Called by the daily 3 AM job to free stuck flows.
     *
     * @return number of rows reset
     */
    @Transactional
    public int resetStaleStates(LocalDateTime cutoff) {
        return botStateRepository.resetStaleStates(cutoff, LocalDateTime.now());
    }

    private String serialize(StateContext ctx) {
        try {
            return objectMapper.writeValueAsString(ctx);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize StateContext", e);
            return "{}";
        }
    }

    private StateContext deserialize(String json) {
        if (json == null || json.isBlank()) return new StateContext();
        try {
            return objectMapper.readValue(json, StateContext.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize StateContext: {}", json, e);
            return new StateContext();
        }
    }
}
