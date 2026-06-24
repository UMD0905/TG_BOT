package com.umd.stobooking.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "bot_state")
public class BotStateEntity {

    // PK is the Telegram user ID — one row per user
    @Id
    @Column(name = "telegram_user_id")
    private Long telegramUserId;

    @Column(name = "current_state", length = 50)
    private String currentState;

    // JSON-serialized StateContext stored as TEXT
    @Column(name = "state_data", columnDefinition = "TEXT")
    private String stateData;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
