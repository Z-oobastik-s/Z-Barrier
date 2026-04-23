package ru.zoobastiks.zbarrier.model;

public record DynamicResizeSettings(
        boolean enabled,
        DynamicMode mode,
        double step,
        long intervalSeconds,
        double minSize,
        double maxSize
) {
}
