package ru.zoobastiks.zbarrier.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class ZbarrierTabCompleter implements TabCompleter {
    private static final List<String> ROOT = List.of(
            "help", "reload", "debugwhere", "setsize", "setcenter", "setcenterhere", "autocenter",
            "enable", "disable", "warning", "dynamic", "dynamicstop"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("zbarrier.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(ROOT, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && needsWorld(sub)) {
            List<String> worlds = Bukkit.getWorlds().stream().map(world -> world.getName()).collect(Collectors.toList());
            return filter(worlds, args[1]);
        }
        if (sub.equals("dynamic") && args.length == 3) {
            return filter(List.of("grow", "shrink"), args[2]);
        }
        return List.of();
    }

    private boolean needsWorld(String sub) {
        return sub.equals("setsize") || sub.equals("setcenter") || sub.equals("setcenterhere")
                || sub.equals("autocenter") || sub.equals("enable") || sub.equals("disable")
                || sub.equals("warning") || sub.equals("dynamic") || sub.equals("dynamicstop");
    }

    private List<String> filter(List<String> source, String userInput) {
        String lower = userInput.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : source) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(value);
            }
        }
        return out;
    }
}
