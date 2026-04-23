package ru.zoobastiks.zbarrier.config;

import java.util.List;

public record PluginSettings(
        boolean debug,
        boolean pluginEnabled,
        boolean enforceBarrier,
        boolean preventOutsideTeleport,
        double pullbackDistance,
        double defaultSize,
        double minSize,
        double maxSize,
        int warningCooldownSeconds,
        double warningDistanceDefault,
        boolean autoCreateWorldSection,
        boolean autoCenterNewWorlds,
        List<String> dynamicAllowedWorlds
) {
}
