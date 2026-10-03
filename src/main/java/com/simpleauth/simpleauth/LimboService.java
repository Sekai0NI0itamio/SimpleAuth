package com.simpleauth.simpleauth;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Hides unauthenticated players in a configurable limbo spot with an emptied
 * inventory. Real state is snapshotted to disk first and restored on login.
 * Snapshots are never overwritten once taken, so a frozen logout or a restart
 * cannot destroy the real state.
 */
public final class LimboService {
    private LimboService() {
    }

    public static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve("simpleauth-limbo");
    }

    private static Path file(Path dir, UUID id) {
        return dir.resolve(id + ".dat");
    }

    public static boolean hasSnapshot(Path dir, UUID id) {
        return Files.isRegularFile(file(dir, id));
    }

    /**
     * Snapshots the player's real state unless one is already stored, then
     * moves them to limbo with an empty inventory.
     */
    public static void capture(ServerPlayer player, Path dir) {
        try {
            Files.createDirectories(dir);
            if (!hasSnapshot(dir, player.getUUID())) {
                NbtIo.writeCompressed(file(dir, player.getUUID()), snapshotOf(player));
            }
        } catch (IOException e) {
            SimpleAuth.LOGGER.error("Failed to write SimpleAuth limbo snapshot for {}", player.getGameProfile().getName(), e);
        }
        applyLimbo(player);
    }

    /**
     * Restores a snapshot. When keepFile is true the file stays for the next
     * join (used on frozen logout so later restarts still recover).
     */
    public static boolean restore(ServerPlayer player, Path dir, boolean keepFile) {
        Path snapshot = file(dir, player.getUUID());
        if (!Files.isRegularFile(snapshot)) {
            return false;
        }
        try {
            apply(player, NbtIo.readCompressed(snapshot));
            if (!keepFile) {
                Files.deleteIfExists(snapshot);
            }
            return true;
        } catch (IOException e) {
            SimpleAuth.LOGGER.error("Failed to read SimpleAuth limbo snapshot for {}", player.getGameProfile().getName(), e);
            return false;
        }
    }

    private static CompoundTag snapshotOf(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dim", player.serverLevel().dimension().location().toString());
        tag.putDouble("X", player.getX());
        tag.putDouble("Y", player.getY());
        tag.putDouble("Z", player.getZ());
        tag.putFloat("Yaw", player.getYRot());
        tag.putFloat("Pitch", player.getXRot());
        tag.put("Inventory", player.getInventory().save(new ListTag()));
        tag.put("EnderItems", player.getEnderChestInventory().save(new ListTag()));
        tag.putFloat("Health", player.getHealth());
        tag.putInt("Food", player.getFoodData().getFoodLevel());
        tag.putFloat("Sat", player.getFoodData().getSaturationLevel());
        tag.putFloat("XpP", player.experienceProgress);
        tag.putInt("XpLevel", player.experienceLevel);
        tag.putInt("XpTotal", player.totalExperience);
        return tag;
    }

    private static void apply(ServerPlayer player, CompoundTag tag) {
        ServerLevel level = levelOf(player.getServer(), tag.getString("Dim"));
        player.teleportTo(level,
                tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"),
                tag.getFloat("Yaw"), tag.getFloat("Pitch"));
        if (tag.contains("Inventory", Tag.TAG_LIST)) {
            player.getInventory().load(tag.getList("Inventory", Tag.TAG_COMPOUND));
        }
        if (tag.contains("EnderItems", Tag.TAG_LIST)) {
            player.getEnderChestInventory().load(tag.getList("EnderItems", Tag.TAG_COMPOUND));
        }
        player.setHealth(tag.getFloat("Health"));
        player.getFoodData().setFoodLevel(tag.getInt("Food"));
        player.getFoodData().setSaturation(tag.getFloat("Sat"));
        player.experienceProgress = tag.getFloat("XpP");
        player.experienceLevel = tag.getInt("XpLevel");
        player.totalExperience = tag.getInt("XpTotal");
    }

    private static void applyLimbo(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getEnderChestInventory().clearContent();
        MinecraftServer server = player.getServer();
        player.teleportTo(levelOf(server, SimpleAuthConfig.LIMBO_DIMENSION.get()),
                SimpleAuthConfig.LIMBO_X.get(), SimpleAuthConfig.LIMBO_Y.get(), SimpleAuthConfig.LIMBO_Z.get(),
                0.0F, 0.0F);
    }

    private static ServerLevel levelOf(MinecraftServer server, String dim) {
        try {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dim)));
            if (level != null) {
                return level;
            }
        } catch (Exception e) {
            SimpleAuth.LOGGER.error("Unknown limbo dimension '{}', using overworld", dim);
        }
        return server.overworld();
    }
}
