package ru.zoobastiks.zbarrier.model;

public enum DynamicMode {
    GROW,
    SHRINK;

    public static DynamicMode fromString(String value, DynamicMode fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return DynamicMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
