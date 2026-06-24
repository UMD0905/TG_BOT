package com.umd.stobooking.bot.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Partial booking/registration context serialized as JSON into bot_state.state_data.
 * Fields prefixed with "temp" are used only during multi-step registration and cleared afterwards.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StateContext {

    // Registration (temporary — cleared after car is saved)
    private Long tempBrandId;
    private Long tempModelId;

    // Booking
    private Long categoryId;
    private Long serviceId;
    private String problemDescription;
    private Long carId;
    private String scheduledDate;   // ISO: "2026-06-25"
    private String scheduledTime;   // "HH:mm"
}
