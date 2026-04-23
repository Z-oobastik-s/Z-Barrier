package ru.zoobastiks.zbarrier.config;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.zoobastiks.zbarrier.model.CenterMode;
import ru.zoobastiks.zbarrier.model.DynamicMode;
import ru.zoobastiks.zbarrier.model.DynamicResizeSettings;
import ru.zoobastiks.zbarrier.model.WorldBarrierSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConfigurationLoader {
    private static final String ROOT_SETTINGS = "settings";
    private static final String ROOT_WORLDS = "worlds";
    /** Общий шаблон настроек мира; сливается с {@code worlds.<имя>} (переопределения поверх шаблона). */
    private static final String ROOT_WORLD_DEFAULTS = "world-defaults";

    private final JavaPlugin plugin;

    public ConfigurationLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public PluginConfiguration load() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        PluginSettings pluginSettings = loadPluginSettings(config);
        ensureWorldSections(config, pluginSettings);

        Map<String, WorldBarrierSettings> worlds = new HashMap<>();
        ConfigurationSection worldsSection = config.getConfigurationSection(ROOT_WORLDS);
        ConfigurationSection worldDefaultsSection = config.getConfigurationSection(ROOT_WORLD_DEFAULTS);
        if (worldsSection != null) {
            for (String worldName : worldsSection.getKeys(false)) {
                ConfigurationSection worldSection = worldsSection.getConfigurationSection(worldName);
                if (worldSection != null) {
                    ConfigurationSection effective = mergeWorldTemplate(worldDefaultsSection, worldSection);
                    worlds.put(worldName, loadWorldSettings(worldName, effective, pluginSettings));
                }
            }
        }

        plugin.saveConfig();
        return new PluginConfiguration(pluginSettings, worlds);
    }

    private PluginSettings loadPluginSettings(FileConfiguration config) {
        String path = ROOT_SETTINGS + ".";
        List<String> dynamicAllowedWorlds = new ArrayList<>(config.getStringList(path + "dynamic-allowed-worlds"));
        if (dynamicAllowedWorlds.isEmpty()) {
            dynamicAllowedWorlds = List.of("*");
        }
        return new PluginSettings(
                config.getBoolean("debug", true),
                config.getBoolean(path + "plugin-enabled", true),
                config.getBoolean(path + "enforce-barrier", true),
                config.getBoolean(path + "prevent-outside-teleport", true),
                config.getDouble(path + "pullback-distance", 1.5D),
                config.getDouble(path + "default-size", 300000.0D),
                config.getDouble(path + "min-size", 16.0D),
                config.getDouble(path + "max-size", 60000000.0D),
                config.getInt(path + "warning-cooldown-seconds", 3),
                config.getDouble(path + "warning-distance-default", 50.0D),
                config.getBoolean(path + "auto-create-world-section", true),
                config.getBoolean(path + "auto-center-new-worlds", true),
                dynamicAllowedWorlds
        );
    }

    private WorldBarrierSettings loadWorldSettings(String worldName, ConfigurationSection section, PluginSettings defaults) {
        String centerPath = "center.";
        String warningPath = "warning.";
        String actionPath = "action.";
        String dynamicPath = "dynamic.";

        boolean enabled = section.getBoolean("enabled", true);
        CenterMode centerMode = CenterMode.fromString(section.getString("center-mode", "AUTO"), CenterMode.AUTO);
        double centerX = section.getDouble(centerPath + "x", 0.0D);
        double centerZ = section.getDouble(centerPath + "z", 0.0D);
        double size = section.getDouble("size", defaults.defaultSize());
        boolean warningEnabled = section.getBoolean(warningPath + "enabled", true);
        double warningDistance = section.getDouble(warningPath + "distance", defaults.warningDistanceDefault());
        boolean denyMove = section.getBoolean(actionPath + "deny-move", true);
        boolean denyTeleport = section.getBoolean(actionPath + "deny-teleport", true);
        boolean denyVehicle = section.getBoolean(actionPath + "deny-vehicle", true);

        DynamicResizeSettings dynamic = new DynamicResizeSettings(
                section.getBoolean(dynamicPath + "enabled", false),
                DynamicMode.fromString(section.getString(dynamicPath + "mode", "SHRINK"), DynamicMode.SHRINK),
                section.getDouble(dynamicPath + "step", 25.0D),
                section.getLong(dynamicPath + "interval-seconds", 3600L),
                section.getDouble(dynamicPath + "min-size", defaults.minSize()),
                section.getDouble(dynamicPath + "max-size", defaults.maxSize())
        );

        return new WorldBarrierSettings(
                worldName,
                enabled,
                centerMode,
                centerX,
                centerZ,
                clamp(size, defaults.minSize(), defaults.maxSize()),
                warningEnabled,
                warningDistance,
                denyMove,
                denyTeleport,
                denyVehicle,
                dynamic
        );
    }

    private void ensureWorldSections(FileConfiguration config, PluginSettings settings) {
        if (!settings.autoCreateWorldSection()) {
            return;
        }
        boolean compactNewWorlds = hasWorldDefaultsTemplate(config);
        for (World world : Bukkit.getWorlds()) {
            String worldPath = ROOT_WORLDS + "." + world.getName();
            if (config.contains(worldPath)) {
                continue;
            }
            if (compactNewWorlds) {
                config.createSection(worldPath);
                continue;
            }
            config.set(worldPath + ".enabled", true);
            config.set(worldPath + ".center-mode", settings.autoCenterNewWorlds() ? "AUTO" : "MANUAL");
            config.set(worldPath + ".center.x", world.getSpawnLocation().getX());
            config.set(worldPath + ".center.z", world.getSpawnLocation().getZ());
            config.set(worldPath + ".size", settings.defaultSize());
            config.set(worldPath + ".warning.enabled", true);
            config.set(worldPath + ".warning.distance", settings.warningDistanceDefault());
            config.set(worldPath + ".action.deny-move", true);
            config.set(worldPath + ".action.deny-teleport", true);
            config.set(worldPath + ".action.deny-vehicle", true);
            config.set(worldPath + ".dynamic.enabled", false);
            config.set(worldPath + ".dynamic.mode", "SHRINK");
            config.set(worldPath + ".dynamic.step", 25.0D);
            config.set(worldPath + ".dynamic.interval-seconds", 3600L);
            config.set(worldPath + ".dynamic.min-size", settings.minSize());
            config.set(worldPath + ".dynamic.max-size", settings.maxSize());
        }
    }

    /**
     * Есть непустой {@code world-defaults} - новые миры добавляются как пустая секция (в YAML {@code {}}),
     * настройки берутся из шаблона.
     */
    private boolean hasWorldDefaultsTemplate(FileConfiguration config) {
        ConfigurationSection wd = config.getConfigurationSection(ROOT_WORLD_DEFAULTS);
        return wd != null && !wd.getKeys(false).isEmpty();
    }

    /**
     * Глубокое слияние: сначала копируется шаблон, затем поверх накладываются ключи мира.
     */
    private ConfigurationSection mergeWorldTemplate(ConfigurationSection defaults, ConfigurationSection world) {
        if (defaults == null || defaults.getKeys(false).isEmpty()) {
            return world;
        }
        MemoryConfiguration merged = new MemoryConfiguration();
        copySection(defaults, merged);
        mergeSection(merged, world);
        return merged;
    }

    private void copySection(ConfigurationSection from, ConfigurationSection to) {
        for (String key : from.getKeys(false)) {
            if (from.isConfigurationSection(key)) {
                ConfigurationSection sub = from.getConfigurationSection(key);
                if (sub != null) {
                    copySection(sub, to.createSection(key));
                }
            } else {
                to.set(key, from.get(key));
            }
        }
    }

    private void mergeSection(ConfigurationSection base, ConfigurationSection overlay) {
        for (String key : overlay.getKeys(false)) {
            if (overlay.isConfigurationSection(key)) {
                ConfigurationSection oSub = overlay.getConfigurationSection(key);
                if (oSub == null) {
                    continue;
                }
                ConfigurationSection bSub = base.getConfigurationSection(key);
                if (bSub == null) {
                    bSub = base.createSection(key);
                }
                mergeSection(bSub, oSub);
            } else {
                base.set(key, overlay.get(key));
            }
        }
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
