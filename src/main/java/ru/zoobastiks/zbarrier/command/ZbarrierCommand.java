package ru.zoobastiks.zbarrier.command;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.zoobastiks.zbarrier.ZbarrierPlugin;
import ru.zoobastiks.zbarrier.message.MessageService;
import ru.zoobastiks.zbarrier.model.CenterMode;
import ru.zoobastiks.zbarrier.model.DynamicMode;
import ru.zoobastiks.zbarrier.model.DynamicResizeSettings;
import ru.zoobastiks.zbarrier.model.WorldBarrierSettings;
import ru.zoobastiks.zbarrier.service.BarrierService;
import ru.zoobastiks.zbarrier.service.DynamicBorderTaskService;
import ru.zoobastiks.zbarrier.util.ParseUtil;

import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalLong;

public final class ZbarrierCommand implements CommandExecutor {
    private final ZbarrierPlugin plugin;
    private final BarrierService barrierService;
    private final DynamicBorderTaskService dynamicTaskService;
    private final MessageService messages;

    public ZbarrierCommand(
            ZbarrierPlugin plugin,
            BarrierService barrierService,
            DynamicBorderTaskService dynamicTaskService,
            MessageService messages
    ) {
        this.plugin = plugin;
        this.barrierService = barrierService;
        this.dynamicTaskService = dynamicTaskService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("zbarrier.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            messages.sendList(sender, "help");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!sub.equals("reload") && !sub.equals("debugwhere") && !barrierService.settings().pluginEnabled()) {
            messages.send(sender, "plugin-disabled");
            return true;
        }
        return switch (sub) {
            case "reload" -> handleReload(sender);
            case "debugwhere" -> handleDebugWhere(sender);
            case "setsize" -> handleSetSize(sender, args);
            case "setcenter" -> handleSetCenter(sender, args);
            case "setcenterhere" -> handleSetCenterHere(sender, args);
            case "autocenter" -> handleAutoCenter(sender, args);
            case "enable" -> handleEnable(sender, args, true);
            case "disable" -> handleEnable(sender, args, false);
            case "warning" -> handleWarning(sender, args);
            case "dynamic" -> handleDynamic(sender, args);
            case "dynamicstop" -> handleDynamicStop(sender, args);
            default -> {
                messages.send(sender, "unknown-subcommand");
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender) {
        plugin.reloadPluginState();
        messages.send(sender, "reloaded");
        return true;
    }

    private boolean handleDebugWhere(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        World w = player.getWorld();
        double size = w.getWorldBorder().getSize();
        double cx = w.getWorldBorder().getCenter().getX();
        double cz = w.getWorldBorder().getCenter().getZ();
        messages.send(sender, "debugwhere-result", Map.of(
                "world", w.getName(),
                "key", w.getKey().asString(),
                "env", w.getEnvironment().name(),
                "size", String.format(Locale.ROOT, "%.2f", size),
                "center", String.format(Locale.ROOT, "%.2f,%.2f", cx, cz)
        ));
        if (barrierService.settings().debug()) {
            plugin.getLogger().info("[debug] debugwhere player=" + player.getName()
                    + " world=" + w.getName()
                    + " key=" + w.getKey().asString()
                    + " env=" + w.getEnvironment()
                    + " borderSize=" + size
                    + " borderCenter=" + cx + "," + cz);
        }
        return true;
    }

    private boolean handleSetSize(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return false;
        }
        String worldName = args[1];
        World world = Bukkit.getWorld(worldName);
        if (world == null || barrierService.world(worldName) == null) {
            messages.send(sender, "world-not-found", Map.of("world", worldName));
            return true;
        }

        OptionalDouble parsed = ParseUtil.parseDouble(args[2]);
        if (parsed.isEmpty()) {
            messages.send(sender, "invalid-number", Map.of("value", args[2]));
            return true;
        }

        double size = parsed.getAsDouble();
        double clamped = barrierService.clampToAllowed(size);
        if (Double.compare(size, clamped) != 0) {
            messages.send(sender, "size-out-of-range", Map.of(
                    "min", format(barrierService.settings().minSize()),
                    "max", format(barrierService.settings().maxSize())
            ));
            return true;
        }

        barrierService.setWorldSize(worldName, clamped);
        barrierService.applyWorldBorder(world);
        messages.send(sender, "size-updated", Map.of("world", worldName, "size", format(clamped)));
        return true;
    }

    private boolean handleSetCenter(CommandSender sender, String[] args) {
        if (args.length < 4) {
            return false;
        }
        String worldName = args[1];
        World world = Bukkit.getWorld(worldName);
        if (world == null || barrierService.world(worldName) == null) {
            messages.send(sender, "world-not-found", Map.of("world", worldName));
            return true;
        }

        OptionalDouble x = ParseUtil.parseDouble(args[2]);
        OptionalDouble z = ParseUtil.parseDouble(args[3]);
        if (x.isEmpty()) {
            messages.send(sender, "invalid-number", Map.of("value", args[2]));
            return true;
        }
        if (z.isEmpty()) {
            messages.send(sender, "invalid-number", Map.of("value", args[3]));
            return true;
        }

        barrierService.setWorldCenter(worldName, x.getAsDouble(), z.getAsDouble(), CenterMode.MANUAL);
        barrierService.applyWorldBorder(world);
        messages.send(sender, "center-updated", Map.of(
                "world", worldName,
                "x", format(x.getAsDouble()),
                "z", format(z.getAsDouble())
        ));
        return true;
    }

    private boolean handleSetCenterHere(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        String worldName = args.length >= 2 ? args[1] : player.getWorld().getName();
        World world = Bukkit.getWorld(worldName);
        if (world == null || barrierService.world(worldName) == null) {
            messages.send(sender, "world-not-found", Map.of("world", worldName));
            return true;
        }

        LocationSnapshot location = new LocationSnapshot(player.getLocation().getX(), player.getLocation().getZ());
        barrierService.setWorldCenter(worldName, location.x(), location.z(), CenterMode.MANUAL);
        barrierService.applyWorldBorder(world);
        messages.send(sender, "center-updated", Map.of(
                "world", worldName,
                "x", format(location.x()),
                "z", format(location.z())
        ));
        return true;
    }

    private boolean handleAutoCenter(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return false;
        }
        String worldName = args[1];
        World world = Bukkit.getWorld(worldName);
        if (world == null || barrierService.world(worldName) == null) {
            messages.send(sender, "world-not-found", Map.of("world", worldName));
            return true;
        }

        barrierService.setWorldCenter(worldName, 0.0D, 0.0D, CenterMode.AUTO);
        barrierService.applyWorldBorder(world);
        messages.send(sender, "center-auto", Map.of("world", worldName));
        return true;
    }

    private boolean handleEnable(CommandSender sender, String[] args, boolean enabled) {
        if (args.length < 2) {
            return false;
        }
        String worldName = args[1];
        World world = Bukkit.getWorld(worldName);
        if (world == null || barrierService.world(worldName) == null) {
            messages.send(sender, "world-not-found", Map.of("world", worldName));
            return true;
        }

        barrierService.setWorldEnabled(worldName, enabled);
        barrierService.applyWorldBorder(world);
        messages.send(sender, enabled ? "world-enabled" : "world-disabled", Map.of("world", worldName));
        return true;
    }

    private boolean handleWarning(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return false;
        }
        String worldName = args[1];
        if (barrierService.world(worldName) == null) {
            messages.send(sender, "world-not-found", Map.of("world", worldName));
            return true;
        }
        OptionalDouble distance = ParseUtil.parseDouble(args[2]);
        if (distance.isEmpty() || distance.getAsDouble() < 0.0D) {
            messages.send(sender, "invalid-number", Map.of("value", args[2]));
            return true;
        }
        barrierService.setWarningDistance(worldName, distance.getAsDouble());
        messages.send(sender, "warning-updated", Map.of("world", worldName, "distance", format(distance.getAsDouble())));
        return true;
    }

    private boolean handleDynamic(CommandSender sender, String[] args) {
        if (args.length < 7) {
            return false;
        }
        String worldName = args[1];
        World world = Bukkit.getWorld(worldName);
        WorldBarrierSettings current = barrierService.world(worldName);
        if (world == null || current == null) {
            messages.send(sender, "world-not-found", Map.of("world", worldName));
            return true;
        }
        if (!barrierService.isDynamicAllowedWorld(worldName)) {
            messages.send(sender, "dynamic-world-not-allowed", Map.of("world", worldName));
            return true;
        }

        DynamicMode mode = DynamicMode.fromString(args[2], null);
        if (mode == null) {
            messages.send(sender, "invalid-mode", Map.of("value", args[2]));
            return true;
        }

        OptionalDouble stepParsed = ParseUtil.parseDouble(args[3]);
        OptionalLong intervalParsed = ParseUtil.parseLong(args[4]);
        OptionalDouble minParsed = ParseUtil.parseDouble(args[5]);
        OptionalDouble maxParsed = ParseUtil.parseDouble(args[6]);
        if (stepParsed.isEmpty()) {
            messages.send(sender, "invalid-number", Map.of("value", args[3]));
            return true;
        }
        if (intervalParsed.isEmpty()) {
            messages.send(sender, "invalid-integer", Map.of("value", args[4]));
            return true;
        }
        if (minParsed.isEmpty()) {
            messages.send(sender, "invalid-number", Map.of("value", args[5]));
            return true;
        }
        if (maxParsed.isEmpty()) {
            messages.send(sender, "invalid-number", Map.of("value", args[6]));
            return true;
        }

        double step = stepParsed.getAsDouble();
        long interval = intervalParsed.getAsLong();
        double min = minParsed.getAsDouble();
        double max = maxParsed.getAsDouble();
        if (step <= 0 || interval <= 0 || min <= 0 || max < min) {
            messages.send(sender, "size-out-of-range", Map.of(
                    "min", format(barrierService.settings().minSize()),
                    "max", format(barrierService.settings().maxSize())
            ));
            return true;
        }

        DynamicResizeSettings dynamic = new DynamicResizeSettings(true, mode, step, interval, min, max);
        barrierService.setDynamic(worldName, dynamic);
        dynamicTaskService.start(worldName, dynamic);
        messages.send(sender, "dynamic-updated", Map.of(
                "world", worldName,
                "mode", mode.name().toLowerCase(Locale.ROOT),
                "step", format(step),
                "interval", String.valueOf(interval)
        ));
        messages.send(sender, "dynamic-started", Map.of("world", worldName));
        return true;
    }

    private boolean handleDynamicStop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return false;
        }
        String worldName = args[1];
        if (barrierService.world(worldName) == null) {
            messages.send(sender, "world-not-found", Map.of("world", worldName));
            return true;
        }
        if (!barrierService.isDynamicAllowedWorld(worldName)) {
            messages.send(sender, "dynamic-world-not-allowed", Map.of("world", worldName));
            return true;
        }
        barrierService.setDynamicEnabled(worldName, false);
        dynamicTaskService.stop(worldName);
        messages.send(sender, "dynamic-stopped", Map.of("world", worldName));
        return true;
    }

    private String format(double value) {
        return String.format("%.2f", value);
    }

    private record LocationSnapshot(double x, double z) {
    }
}
