package ru.zoobastiks.zbarrier.service;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;
import ru.zoobastiks.zbarrier.message.MessageService;
import ru.zoobastiks.zbarrier.model.DynamicMode;
import ru.zoobastiks.zbarrier.model.DynamicResizeSettings;
import ru.zoobastiks.zbarrier.model.WorldBarrierSettings;

import java.util.HashMap;
import java.util.Map;

public final class DynamicBorderTaskService {
    private static final long TICKS_PER_SECOND = 20L;

    private final JavaPlugin plugin;
    private final BarrierService barrierService;
    private final MessageService messages;
    private final Map<String, BukkitTask> tasks;

    public DynamicBorderTaskService(JavaPlugin plugin, BarrierService barrierService, MessageService messages) {
        this.plugin = plugin;
        this.barrierService = barrierService;
        this.messages = messages;
        this.tasks = new HashMap<>();
    }

    public void restartAll() {
        stopAll();
        if (!barrierService.settings().pluginEnabled()) {
            return;
        }
        for (Map.Entry<String, WorldBarrierSettings> entry : barrierService.allWorlds().entrySet()) {
            String worldName = entry.getKey();
            DynamicResizeSettings dynamic = entry.getValue().dynamic();
            if (dynamic.enabled() && barrierService.isDynamicAllowedWorld(worldName)) {
                start(worldName, dynamic);
            }
        }
    }

    public boolean start(String worldName, DynamicResizeSettings dynamic) {
        if (!barrierService.settings().pluginEnabled()) {
            return false;
        }
        if (!barrierService.isDynamicAllowedWorld(worldName)) {
            return false;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return false;
        }
        if (dynamic.intervalSeconds() <= 0 || dynamic.step() <= 0.0D) {
            return false;
        }

        stop(worldName);
        long intervalTicks = dynamic.intervalSeconds() * TICKS_PER_SECOND;
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickWorld(worldName, dynamic), intervalTicks, intervalTicks);
        tasks.put(worldName, task);
        return true;
    }

    public void stop(String worldName) {
        BukkitTask task = tasks.remove(worldName);
        if (task != null) {
            task.cancel();
        }
    }

    public void stopAll() {
        for (BukkitTask task : tasks.values()) {
            task.cancel();
        }
        tasks.clear();
    }

    public boolean isRunning(String worldName) {
        return tasks.containsKey(worldName);
    }

    private void tickWorld(String worldName, DynamicResizeSettings dynamic) {
        if (!barrierService.settings().pluginEnabled()) {
            return;
        }
        if (!barrierService.isDynamicAllowedWorld(worldName)) {
            return;
        }
        World world = Bukkit.getWorld(worldName);
        WorldBarrierSettings current = barrierService.world(worldName);
        if (world == null || current == null || !current.enabled()) {
            return;
        }

        double oldSize = current.size();
        double newSize = oldSize;
        if (dynamic.mode() == DynamicMode.SHRINK) {
            newSize = oldSize - dynamic.step();
        } else if (dynamic.mode() == DynamicMode.GROW) {
            newSize = oldSize + dynamic.step();
        }
        newSize = clamp(newSize, dynamic.minSize(), dynamic.maxSize());

        if (Double.compare(newSize, oldSize) == 0) {
            return;
        }

        barrierService.setWorldSize(worldName, newSize);
        barrierService.applyWorldBorder(world);
        messages.broadcast(
                dynamic.mode() == DynamicMode.SHRINK ? "barrier-global-shrink" : "barrier-global-grow",
                Map.of("world", worldName, "size", format(newSize))
        );
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

    private String format(double value) {
        return String.format("%.2f", value);
    }
}
