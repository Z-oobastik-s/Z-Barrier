package ru.zoobastiks.zbarrier;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.zoobastiks.zbarrier.command.ZbarrierCommand;
import ru.zoobastiks.zbarrier.command.ZbarrierTabCompleter;
import ru.zoobastiks.zbarrier.config.ConfigurationLoader;
import ru.zoobastiks.zbarrier.config.PluginConfiguration;
import ru.zoobastiks.zbarrier.listener.BarrierEnforcementListener;
import ru.zoobastiks.zbarrier.message.MessageService;
import ru.zoobastiks.zbarrier.service.BarrierService;
import ru.zoobastiks.zbarrier.service.DynamicBorderTaskService;

import java.util.Objects;
import java.util.logging.Level;

public final class ZbarrierPlugin extends JavaPlugin {
    private static final long BORDER_APPLY_DELAY_TICKS = 2L;

    private ConfigurationLoader configurationLoader;
    private MessageService messageService;
    private BarrierService barrierService;
    private DynamicBorderTaskService dynamicBorderTaskService;
    private BukkitTask borderApplyTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("messages.yml");

        configurationLoader = new ConfigurationLoader(this);
        messageService = new MessageService(this);
        barrierService = new BarrierService(this);
        dynamicBorderTaskService = new DynamicBorderTaskService(this, barrierService, messageService);

        reloadPluginState();
        registerCommands();
        registerListeners();
        if (barrierService.settings().debug()) {
            getLogger().info("[Zbarrier] debug=true: use /zbarrier debugwhere in game to print world name, dimension key and border size the server uses for your location.");
        }
    }

    @Override
    public void onDisable() {
        if (borderApplyTask != null) {
            borderApplyTask.cancel();
            borderApplyTask = null;
        }
        if (dynamicBorderTaskService != null) {
            dynamicBorderTaskService.stopAll();
        }
    }

    public void reloadPluginState() {
        try {
            messageService.reload();
            PluginConfiguration configuration = configurationLoader.load();
            barrierService.updateConfiguration(configuration);
            if (dynamicBorderTaskService != null) {
                dynamicBorderTaskService.stopAll();
            }
            scheduleBorderApplyAndDynamicRestart();
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Unable to reload Zbarrier state", exception);
        }
    }

    /**
     * Откладывает применение границ и запуск dynamic, чтобы не пересекаться с асинхронной
     * регистрацией команд Paper при plugman reload и подобных сценариях.
     */
    private void scheduleBorderApplyAndDynamicRestart() {
        if (borderApplyTask != null) {
            borderApplyTask.cancel();
        }
        borderApplyTask = getServer().getScheduler().runTaskLater(this, () -> {
            borderApplyTask = null;
            if (barrierService != null) {
                barrierService.applyAllToWorldBorders();
            }
            if (dynamicBorderTaskService != null) {
                dynamicBorderTaskService.restartAll();
            }
        }, BORDER_APPLY_DELAY_TICKS);
    }

    private void registerCommands() {
        PluginCommand command = Objects.requireNonNull(getCommand("zbarrier"), "zbarrier command missing in plugin.yml");
        ZbarrierCommand executor = new ZbarrierCommand(this, barrierService, dynamicBorderTaskService, messageService);
        command.setExecutor(executor);
        command.setTabCompleter(new ZbarrierTabCompleter());
    }

    private void registerListeners() {
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new BarrierEnforcementListener(barrierService, messageService), this);
    }

    private void saveResourceIfMissing(String resource) {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Cannot create plugin data folder");
            return;
        }
        if (!new java.io.File(getDataFolder(), resource).exists()) {
            saveResource(resource, false);
        }
    }
}
