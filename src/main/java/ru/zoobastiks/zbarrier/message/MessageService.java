package ru.zoobastiks.zbarrier.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class MessageService {
    private static final String MESSAGE_FILE_NAME = "messages.yml";
    private static final String PREFIX_PATH = "prefix";
    private static final String ROOT_MESSAGES = "messages.";

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage;

    private YamlConfiguration messageConfig;
    private String prefix;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), MESSAGE_FILE_NAME);
        if (!file.exists()) {
            plugin.saveResource(MESSAGE_FILE_NAME, false);
        }
        this.messageConfig = YamlConfiguration.loadConfiguration(file);
        this.prefix = messageConfig.getString(PREFIX_PATH, "");
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Collections.emptyMap());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String raw = messageConfig.getString(ROOT_MESSAGES + key);
        if (raw == null || raw.isBlank()) {
            return;
        }
        sender.sendMessage(parse(prefix + raw, placeholders));
    }

    public void sendList(CommandSender sender, String key) {
        List<String> lines = messageConfig.getStringList(ROOT_MESSAGES + key);
        for (String line : lines) {
            sender.sendMessage(parse(line, Collections.emptyMap()));
        }
    }

    public void broadcast(String key, Map<String, String> placeholders) {
        String raw = messageConfig.getString(ROOT_MESSAGES + key);
        if (raw == null || raw.isBlank()) {
            return;
        }
        Component component = parse(prefix + raw, placeholders);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.sendMessage(component);
        }
    }

    private Component parse(String input, Map<String, String> placeholders) {
        if (placeholders.isEmpty()) {
            return miniMessage.deserialize(input);
        }
        List<TagResolver> resolvers = new ArrayList<>(placeholders.size());
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolvers.add(Placeholder.parsed(entry.getKey(), entry.getValue()));
        }
        return miniMessage.deserialize(input, TagResolver.resolver(resolvers));
    }
}
