package com.umd.stobooking.util;

public class PhoneValidator {

    private PhoneValidator() {}

    /**
     * Normalizes and validates an Uzbek phone number.
     * Accepts: +998901234567 / 998901234567 / 901234567 / 0901234567
     *
     * @return normalized form "+998XXXXXXXXX" or null if invalid
     */
    public static String normalize(String raw) {
        if (raw == null) return null;

        // Strip everything except digits
        String digits = raw.replaceAll("[^\\d]", "");

        // Handle local 9-digit format: 901234567
        if (digits.length() == 9) {
            digits = "998" + digits;
        }
        // Handle 0-prefixed local format: 0901234567
        else if (digits.length() == 10 && digits.startsWith("0")) {
            digits = "998" + digits.substring(1);
        }

        // Validate: must be exactly 12 digits starting with 998
        if (digits.length() == 12 && digits.startsWith("998")) {
            return "+" + digits;
        }

        return null; // invalid
    }

    public static boolean isValid(String raw) {
        return normalize(raw) != null;
    }
}
