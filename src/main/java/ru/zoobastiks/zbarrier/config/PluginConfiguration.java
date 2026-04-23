package ru.zoobastiks.zbarrier.config;

import ru.zoobastiks.zbarrier.model.WorldBarrierSettings;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class PluginConfiguration {
    private final PluginSettings pluginSettings;
    private final Map<String, WorldBarrierSettings> worldSettings;

    public PluginConfiguration(PluginSettings pluginSettings, Map<String, WorldBarrierSettings> worldSettings) {
        this.pluginSettings = pluginSettings;
        this.worldSettings = Collections.unmodifiableMap(new HashMap<>(worldSettings));
    }

    public PluginSettings pluginSettings() {
        return pluginSettings;
    }

    public Map<String, WorldBarrierSettings> worldSettings() {
        return worldSettings;
    }

    public WorldBarrierSettings world(String worldName) {
        return worldSettings.get(worldName);
    }
}
