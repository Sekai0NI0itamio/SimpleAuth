package com.simpleauth.simpleauth;

import com.simpleauth.simpleauth.auth.AuthManager;
import com.simpleauth.simpleauth.auth.PasswordStore;
import com.simpleauth.simpleauth.command.AuthCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Server-side locks. Registered on the Forge bus only when running as a
 * dedicated server, so the jar is inert anywhere else.
 */
public final class AuthEventHandler {
    private static AuthManager manager;
    private static PasswordStore store;
    private static MinecraftServer server;
    private static int tickCounter;

    private AuthEventHandler() {
    }

    public     static AuthManager manager() {
        return manager;
    }

    static boolean save() {
        if (store == null) {
            return false;
        }
        try {
            store.save();
            return true;
        } catch (IOException e) {
            SimpleAuth.LOGGER.error("Failed to save SimpleAuth data", e);
            return false;
        }
    }

    private static boolean locked(ServerPlayer player) {
        return manager != null && !manager.isAuthed(player.getUUID());
    }

    private static Component prompt(boolean registered) {
        if (registered) {
            return Component.literal("Log in with /login <password>").withStyle(ChatFormatting.YELLOW);
        }
        return Component.literal("Register with /register <password> <password>").withStyle(ChatFormatting.YELLOW);
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        server = event.getServer();
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve("simpleauth.json");
        store = new PasswordStore(file);
        try {
            store.load();
        } catch (IOException e) {
            SimpleAuth.LOGGER.error("Failed to load SimpleAuth data from {}", file, e);
        }
        manager = new AuthManager(store);
        tickCounter = 0;
        SimpleAuth.LOGGER.info("SimpleAuth ready, accounts file: {}", file);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        save();
        manager = null;
        store = null;
        server = null;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // No manager check here on purpose: this event fires during server
        // bootstrap, before ServerStartingEvent. Commands resolve the manager
        // when a player actually runs them, long after init.
        AuthCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (manager == null || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        manager.noteLogin(player.getUUID(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                System.currentTimeMillis());
        if (!manager.isAuthed(player.getUUID())) {
            player.sendSystemMessage(prompt(manager.hasAccount(player.getUUID())), false);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (manager != null && event.getEntity() instanceof ServerPlayer player) {
            manager.forget(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (locked(player)) {
            event.setCanceled(true);
            player.sendSystemMessage(prompt(manager.hasAccount(player.getUUID())), true);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && locked(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && locked(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent event) {
        if (event.isCancelable()
                && event.getEntity() instanceof ServerPlayer player
                && locked(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        boolean attackerLocked = event.getSource().getEntity() instanceof ServerPlayer attacker && locked(attacker);
        boolean victimLocked = event.getEntity() instanceof ServerPlayer victim && locked(victim);
        if (attackerLocked || victimLocked) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && locked(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && locked(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player && locked(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        if (!(event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!locked(player)) {
            return;
        }
        String raw = event.getParseResults().getReader().getString().trim();
        if (raw.startsWith("/")) {
            raw = raw.substring(1);
        }
        String token = raw.split(" ", 2)[0];
        String first = token.contains(":") ? token.substring(token.lastIndexOf(':') + 1) : token;
        if (!first.equals("register") && !first.equals("login")) {
            event.setCanceled(true);
            player.sendSystemMessage(prompt(manager.hasAccount(player.getUUID())), true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || manager == null) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player) || !locked(player)) {
            return;
        }
        double[] frozen = manager.frozenPos(player.getUUID());
        if (frozen == null) {
            return;
        }
        double dx = player.getX() - frozen[0];
        double dy = player.getY() - frozen[1];
        double dz = player.getZ() - frozen[2];
        if (dx * dx + dz * dz > 0.09 || Math.abs(dy) > 0.4) {
            player.teleportTo(player.serverLevel(), frozen[0], frozen[1], frozen[2], (float) frozen[3], (float) frozen[4]);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || manager == null || server == null) {
            return;
        }
        if (++tickCounter % 20 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long timeoutMillis = (long) SimpleAuthConfig.LOGIN_TIMEOUT_SECONDS.get() * 1000L;
        List<UUID> expired = manager.expired(now, timeoutMillis);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!locked(player)) {
                continue;
            }
            if (expired.contains(player.getUUID())) {
                manager.forget(player.getUUID());
                player.connection.disconnect(Component.literal("Login timed out.").withStyle(ChatFormatting.RED));
            } else {
                player.sendSystemMessage(prompt(manager.hasAccount(player.getUUID())), true);
            }
        }
    }
}
