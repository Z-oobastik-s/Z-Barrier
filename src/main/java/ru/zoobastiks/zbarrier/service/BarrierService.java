package ru.zoobastiks.zbarrier.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.zoobastiks.zbarrier.config.PluginConfiguration;
import ru.zoobastiks.zbarrier.config.PluginSettings;
import ru.zoobastiks.zbarrier.model.CenterMode;
import ru.zoobastiks.zbarrier.model.DynamicResizeSettings;
import ru.zoobastiks.zbarrier.model.WorldBarrierSettings;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class BarrierService {
    private final JavaPlugin plugin;

    private PluginSettings pluginSettings;
    private final Map<String, WorldBarrierSettings> worldSettings;

    public BarrierService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.worldSettings = new HashMap<>();
    }

    public void updateConfiguration(PluginConfiguration configuration) {
        this.pluginSettings = configuration.pluginSettings();
        this.worldSettings.clear();
        this.worldSettings.putAll(configuration.worldSettings());
    }

    public PluginSettings settings() {
        return pluginSettings;
    }

    public Map<String, WorldBarrierSettings> allWorlds() {
        return Collections.unmodifiableMap(new HashMap<>(worldSettings));
    }

    public WorldBarrierSettings world(String worldName) {
        return worldSettings.get(worldName);
    }

    public boolean isEnabledFor(World world) {
        WorldBarrierSettings worldSettings = world(world.getName());
        return settings().pluginEnabled() && worldSettings != null && worldSettings.enabled() && settings().enforceBarrier();
    }

    public boolean isDynamicAllowedWorld(String worldName) {
        if (!settings().pluginEnabled()) {
            return false;
        }
        String normalizedName = worldName.toLowerCase(Locale.ROOT);
        for (String entry : settings().dynamicAllowedWorlds()) {
            if ("*".equals(entry)) {
                return true;
            }
            if (entry.equalsIgnoreCase(normalizedName)) {
                return true;
            }
            if (entry.equalsIgnoreCase(worldName)) {
                return true;
            }
        }
        return false;
    }

    public boolean isInside(World world, double x, double z) {
        if (!settings().pluginEnabled()) {
            return true;
        }
        WorldBarrierSettings worldSettings = world(world.getName());
        if (worldSettings == null || !worldSettings.enabled()) {
            return true;
        }
        double centerX = resolveCenterX(world, worldSettings);
        double centerZ = resolveCenterZ(world, worldSettings);
        double radius = worldSettings.radius();
        return x >= centerX - radius && x <= centerX + radius
                && z >= centerZ - radius && z <= centerZ + radius;
    }

    public double distanceToEdge(World world, double x, double z) {
        if (!settings().pluginEnabled()) {
            return Double.MAX_VALUE;
        }
        WorldBarrierSettings worldSettings = world(world.getName());
        if (worldSettings == null) {
            return Double.MAX_VALUE;
        }
        double centerX = resolveCenterX(world, worldSettings);
        double centerZ = resolveCenterZ(world, worldSettings);
        double radius = worldSettings.radius();
        double dx = radius - Math.abs(x - centerX);
        double dz = radius - Math.abs(z - centerZ);
        return Math.min(dx, dz);
    }

    public Location pullbackLocation(Location from, Location attempted) {
        World world = attempted.getWorld();
        if (world == null) {
            return from;
        }
        WorldBarrierSettings worldSettings = world(world.getName());
        if (worldSettings == null) {
            return from;
        }

        double pullback = settings().pullbackDistance();
        double centerX = resolveCenterX(world, worldSettings);
        double centerZ = resolveCenterZ(world, worldSettings);
        double radius = worldSettings.radius();

        double minX = centerX - radius + pullback;
        double maxX = centerX + radius - pullback;
        double minZ = centerZ - radius + pullback;
        double maxZ = centerZ + radius - pullback;

        double x = clamp(attempted.getX(), minX, maxX);
        double z = clamp(attempted.getZ(), minZ, maxZ);
        Location safe = attempted.clone();
        safe.setX(x);
        safe.setZ(z);
        return safe;
    }

    public boolean setWorldEnabled(String world, boolean enabled) {
        WorldBarrierSettings current = worldSettings.get(world);
        if (current == null) {
            return false;
        }
        setConfigValue("worlds." + world + ".enabled", enabled);
        worldSettings.put(world, new WorldBarrierSettings(
                current.worldName(), enabled, current.centerMode(), current.centerX(), current.centerZ(),
                current.size(), current.warningEnabled(), current.warningDistance(),
                current.denyMove(), current.denyTeleport(), current.denyVehicle(), current.dynamic()
        ));
        return true;
    }

    public boolean setWorldSize(String world, double size) {
        WorldBarrierSettings current = worldSettings.get(world);
        if (current == null) {
            return false;
        }
        setConfigValue("worlds." + world + ".size", size);
        worldSettings.put(world, new WorldBarrierSettings(
                current.worldName(), current.enabled(), current.centerMode(), current.centerX(), current.centerZ(),
                size, current.warningEnabled(), current.warningDistance(),
                current.denyMove(), current.denyTeleport(), current.denyVehicle(), current.dynamic()
        ));
        return true;
    }

    public boolean setWorldCenter(String world, double x, double z, CenterMode mode) {
        WorldBarrierSettings current = worldSettings.get(world);
        if (current == null) {
            return false;
        }
        setConfigValue("worlds." + world + ".center-mode", mode.name());
        setConfigValue("worlds." + world + ".center.x", x);
        setConfigValue("worlds." + world + ".center.z", z);
        worldSettings.put(world, new WorldBarrierSettings(
                current.worldName(), current.enabled(), mode, x, z,
                current.size(), current.warningEnabled(), current.warningDistance(),
                current.denyMove(), current.denyTeleport(), current.denyVehicle(), current.dynamic()
        ));
        return true;
    }

    public boolean setWarningDistance(String world, double distance) {
        WorldBarrierSettings current = worldSettings.get(world);
        if (current == null) {
            return false;
        }
        setConfigValue("worlds." + world + ".warning.distance", distance);
        worldSettings.put(world, new WorldBarrierSettings(
                current.worldName(), current.enabled(), current.centerMode(), current.centerX(), current.centerZ(),
                current.size(), current.warningEnabled(), distance,
                current.denyMove(), current.denyTeleport(), current.denyVehicle(), current.dynamic()
        ));
        return true;
    }

    public boolean setDynamic(String world, DynamicResizeSettings dynamic) {
        WorldBarrierSettings current = worldSettings.get(world);
        if (current == null) {
            return false;
        }
        String base = "worlds." + world + ".dynamic.";
        setConfigValue(base + "enabled", dynamic.enabled());
        setConfigValue(base + "mode", dynamic.mode().name());
        setConfigValue(base + "step", dynamic.step());
        setConfigValue(base + "interval-seconds", dynamic.intervalSeconds());
        setConfigValue(base + "min-size", dynamic.minSize());
        setConfigValue(base + "max-size", dynamic.maxSize());
        worldSettings.put(world, new WorldBarrierSettings(
                current.worldName(), current.enabled(), current.centerMode(), current.centerX(), current.centerZ(),
                current.size(), current.warningEnabled(), current.warningDistance(),
                current.denyMove(), current.denyTeleport(), current.denyVehicle(), dynamic
        ));
        return true;
    }

    public boolean setDynamicEnabled(String world, boolean enabled) {
        WorldBarrierSettings current = worldSettings.get(world);
        if (current == null) {
            return false;
        }
        setConfigValue("worlds." + world + ".dynamic.enabled", enabled);
        DynamicResizeSettings currentDynamic = current.dynamic();
        DynamicResizeSettings updated = new DynamicResizeSettings(
                enabled,
                currentDynamic.mode(),
                currentDynamic.step(),
                currentDynamic.intervalSeconds(),
                currentDynamic.minSize(),
                currentDynamic.maxSize()
        );
        worldSettings.put(world, new WorldBarrierSettings(
                current.worldName(), current.enabled(), current.centerMode(), current.centerX(), current.centerZ(),
                current.size(), current.warningEnabled(), current.warningDistance(),
                current.denyMove(), current.denyTeleport(), current.denyVehicle(), updated
        ));
        return true;
    }

    public void applyWorldBorder(World world) {
        String worldName = world.getName();
        if (settings().debug()) {
            plugin.getLogger().info("[debug] applyWorldBorder start world=" + worldName
                    + " mainThread=" + Bukkit.isPrimaryThread()
                    + " env=" + world.getEnvironment()
                    + " key=" + world.getKey().asString());
        }
        if (!settings().pluginEnabled()) {
            debugSkip(worldName, "plugin-enabled=false");
            return;
        }
        WorldBarrierSettings worldSettings = world(world.getName());
        if (worldSettings == null) {
            debugSkip(worldName, "no section in worlds config for this loaded world");
            return;
        }
        if (!worldSettings.enabled()) {
            debugSkip(worldName, "worlds." + worldName + ".enabled=false");
            return;
        }
        double centerX = resolveCenterX(world, worldSettings);
        double centerZ = resolveCenterZ(world, worldSettings);
        double diameter = worldSettings.size();
        WorldBorder border = world.getWorldBorder();
        double beforeSize = border.getSize();
        double beforeCx = border.getCenter().getX();
        double beforeCz = border.getCenter().getZ();
        if (settings().debug()) {
            plugin.getLogger().info("[debug] before API world=" + worldName
                    + " borderSize=" + beforeSize
                    + " borderCenter=" + beforeCx + "," + beforeCz
                    + " targetCenter=" + centerX + "," + centerZ
                    + " targetDiameter=" + diameter
                    + " centerMode=" + worldSettings.centerMode());
        }
        border.setCenter(centerX, centerZ);
        border.setSize(diameter, 0L);
        double afterSize = border.getSize();
        double afterCx = border.getCenter().getX();
        double afterCz = border.getCenter().getZ();
        if (settings().debug()) {
            plugin.getLogger().info("[debug] after API world=" + worldName
                    + " borderSize=" + afterSize
                    + " borderCenter=" + afterCx + "," + afterCz);
            if (Math.abs(afterSize - diameter) > 0.01D) {
                plugin.getLogger().warning("[debug] size mismatch after setSize: expected " + diameter + " got " + afterSize);
            }
            if (Math.abs(afterCx - centerX) > 0.01D || Math.abs(afterCz - centerZ) > 0.01D) {
                plugin.getLogger().warning("[debug] center mismatch after setCenter: expected " + centerX + "," + centerZ
                        + " got " + afterCx + "," + afterCz);
            }
            debugLogAllDimensionBorderSnapshot();
        }
        repullPlayersIfOutside(world, worldSettings);
    }

    /**
     * После смены размера границы игрок может остаться в том же блоке, но уже за
     * пределами лимита плагина; без этого движение иногда не обрабатывается (см. PlayerMoveEvent).
     */
    private void repullPlayersIfOutside(World world, WorldBarrierSettings worldSettings) {
        if (!settings().enforceBarrier() || !worldSettings.denyMove()) {
            return;
        }
        for (Player player : world.getPlayers()) {
            Location loc = player.getLocation();
            if (!isInside(world, loc.getX(), loc.getZ())) {
                player.teleport(pullbackLocation(loc, loc));
            }
        }
    }

    private void debugLogAllDimensionBorderSnapshot() {
        StringBuilder sb = new StringBuilder("[debug] border size per loaded world (name=blocks, /worldborder get = your current dimension only): ");
        for (World w : plugin.getServer().getWorlds()) {
            sb.append(w.getName())
                    .append("=")
                    .append(w.getWorldBorder().getSize())
                    .append(" ")
                    .append(w.getKey().asString())
                    .append("; ");
        }
        plugin.getLogger().info(sb.toString());
    }

    private void debugSkip(String worldName, String reason) {
        if (settings().debug()) {
            plugin.getLogger().info("[debug] applyWorldBorder skip world=" + worldName + " reason=" + reason);
        }
    }

    public void applyAllToWorldBorders() {
        for (World world : plugin.getServer().getWorlds()) {
            applyWorldBorder(world);
        }
    }

    public double resolveCenterX(World world, WorldBarrierSettings settings) {
        return settings.centerMode() == CenterMode.AUTO ? world.getSpawnLocation().getX() : settings.centerX();
    }

    public double resolveCenterZ(World world, WorldBarrierSettings settings) {
        return settings.centerMode() == CenterMode.AUTO ? world.getSpawnLocation().getZ() : settings.centerZ();
    }

    public double clampToAllowed(double size) {
        return clamp(size, this.settings().minSize(), this.settings().maxSize());
    }

    private void setConfigValue(String path, Object value) {
        FileConfiguration config = plugin.getConfig();
        config.set(path, value);
        plugin.saveConfig();
    }

    private double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

}
