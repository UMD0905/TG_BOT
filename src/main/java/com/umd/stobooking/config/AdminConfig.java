package com.umd.stobooking.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Getter
@Component
public class AdminConfig {

    private final List<Long> adminIds;

    public AdminConfig(@Value("${admin.telegram-ids}") String raw) {
        this.adminIds = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(Long::parseLong)
                .toList();
    }

    public boolean isAdmin(long telegramUserId) {
        return adminIds.contains(telegramUserId);
    }
}
