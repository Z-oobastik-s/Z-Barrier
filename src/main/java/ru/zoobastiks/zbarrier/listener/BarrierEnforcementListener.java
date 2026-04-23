package ru.zoobastiks.zbarrier.listener;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import ru.zoobastiks.zbarrier.message.MessageService;
import ru.zoobastiks.zbarrier.model.WorldBarrierSettings;
import ru.zoobastiks.zbarrier.service.BarrierService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BarrierEnforcementListener implements Listener {
    private static final long BLOCKED_MOVE_MESSAGE_COOLDOWN_MS = 1500L;

    private final BarrierService barrierService;
    private final MessageService messages;
    private final Map<UUID, Long> warningCooldowns;
    private final Map<UUID, Long> blockedMoveMessageCooldowns;

    public BarrierEnforcementListener(BarrierService barrierService, MessageService messages) {
        this.barrierService = barrierService;
        this.messages = messages;
        this.warningCooldowns = new ConcurrentHashMap<>();
        this.blockedMoveMessageCooldowns = new ConcurrentHashMap<>();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || to.getWorld() == null || from.getWorld() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!barrierService.isEnabledFor(to.getWorld())) {
            return;
        }
        WorldBarrierSettings world = barrierService.world(to.getWorld().getName());
        if (world == null) {
            return;
        }

        boolean sameBlock = from.getBlockX() == to.getBlockX()
                && from.getBlockZ() == to.getBlockZ()
                && from.getBlockY() == to.getBlockY();
        // Нельзя выходить раньше при том же блоке: если граница сжалась, игрок
        // остаётся в том же блоке, но уже вне лимита плагина - иначе не срабатывает откат.
        if (sameBlock && barrierService.isInside(to.getWorld(), to.getX(), to.getZ())) {
            maybeWarn(player, to, world);
            return;
        }

        if (!barrierService.isInside(to.getWorld(), to.getX(), to.getZ()) && world.denyMove()) {
            Location safe = barrierService.pullbackLocation(from, to);
            safe.setYaw(to.getYaw());
            safe.setPitch(to.getPitch());
            event.setTo(safe);
            long now = System.currentTimeMillis();
            Long lastMsg = blockedMoveMessageCooldowns.get(player.getUniqueId());
            if (lastMsg == null || now - lastMsg >= BLOCKED_MOVE_MESSAGE_COOLDOWN_MS) {
                blockedMoveMessageCooldowns.put(player.getUniqueId(), now);
                messages.send(player, "blocked-move");
            }
            maybeWarn(player, safe, world);
            return;
        }
        maybeWarn(player, to, world);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        if (!barrierService.settings().preventOutsideTeleport()) {
            return;
        }
        if (!barrierService.isEnabledFor(to.getWorld())) {
            return;
        }

        WorldBarrierSettings world = barrierService.world(to.getWorld().getName());
        if (world == null || !world.denyTeleport()) {
            return;
        }

        if (!barrierService.isInside(to.getWorld(), to.getX(), to.getZ())) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "blocked-teleport");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        if (!barrierService.isEnabledFor(to.getWorld())) {
            return;
        }
        WorldBarrierSettings world = barrierService.world(to.getWorld().getName());
        if (world == null || !world.denyVehicle()) {
            return;
        }
        if (barrierService.isInside(to.getWorld(), to.getX(), to.getZ())) {
            return;
        }

        Entity vehicle = event.getVehicle();
        Location safe = barrierService.pullbackLocation(event.getFrom(), to);
        safe.setYaw(to.getYaw());
        safe.setPitch(to.getPitch());
        vehicle.teleport(safe);
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof Player player) {
                long now = System.currentTimeMillis();
                Long lastMsg = blockedMoveMessageCooldowns.get(player.getUniqueId());
                if (lastMsg == null || now - lastMsg >= BLOCKED_MOVE_MESSAGE_COOLDOWN_MS) {
                    blockedMoveMessageCooldowns.put(player.getUniqueId(), now);
                    messages.send(player, "blocked-vehicle");
                }
            }
        }
    }

    private void maybeWarn(Player player, Location to, WorldBarrierSettings world) {
        if (!world.warningEnabled()) {
            return;
        }
        double leftDistance = barrierService.distanceToEdge(to.getWorld(), to.getX(), to.getZ());
        if (leftDistance > world.warningDistance()) {
            return;
        }
        long now = System.currentTimeMillis();
        long cooldownMillis = barrierService.settings().warningCooldownSeconds() * 1000L;
        Long lastSent = warningCooldowns.get(player.getUniqueId());
        if (lastSent != null && now - lastSent < cooldownMillis) {
            return;
        }
        warningCooldowns.put(player.getUniqueId(), now);
        messages.send(player, "warning-near-border", Map.of("distance", String.format("%.2f", leftDistance)));
    }
}
