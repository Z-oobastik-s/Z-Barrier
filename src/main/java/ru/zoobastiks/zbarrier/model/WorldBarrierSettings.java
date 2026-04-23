package ru.zoobastiks.zbarrier.model;

public record WorldBarrierSettings(
        String worldName,
        boolean enabled,
        CenterMode centerMode,
        double centerX,
        double centerZ,
        double size,
        boolean warningEnabled,
        double warningDistance,
        boolean denyMove,
        boolean denyTeleport,
        boolean denyVehicle,
        DynamicResizeSettings dynamic
) {
    public double radius() {
        return size / 2.0D;
    }
}
