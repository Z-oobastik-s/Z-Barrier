package ru.zoobastiks.zbarrier.model;

public enum CenterMode {
    AUTO,
    MANUAL;

    public static CenterMode fromString(String value, CenterMode fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return CenterMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
