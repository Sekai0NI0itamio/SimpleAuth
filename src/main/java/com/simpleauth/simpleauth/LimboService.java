package com.simpleauth.simpleauth;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Hides unauthenticated players in a configurable limbo spot with an emptied
 * inventory. Real state is snapshotted to disk first (plain gzipped NBT, one
 * file per player) and restored on login. Snapshots are never overwritten
 * once taken, so a frozen logout or a restart cannot destroy real state.
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
                CompoundTag tag = player.saveWithoutId(new CompoundTag());
                tag.putString("Dim", player.serverLevel().dimension().location().toString());
                tag.putDouble("X", player.getX());
                tag.putDouble("Y", player.getY());
                tag.putDouble("Z", player.getZ());
                tag.putFloat("Yaw", player.getYRot());
                tag.putFloat("Pitch", player.getXRot());
                writeTag(file(dir, player.getUUID()), tag);
                SimpleAuth.LOGGER.info("SimpleAuth snapshotted {} items for {}",
                        countItems(player), player.getGameProfile().getName());
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
            CompoundTag tag = readTag(snapshot);
            ServerLevel level = levelOf(player.getServer(), tag.getString("Dim"));
            player.teleportTo(level,
                    tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"),
                    tag.getFloat("Yaw"), tag.getFloat("Pitch"));
            player.load(tag);
            if (tag.contains("Inventory", Tag.TAG_LIST)) {
                player.getInventory().load(tag.getList("Inventory", Tag.TAG_COMPOUND));
            }
            SimpleAuth.LOGGER.info("SimpleAuth restored snapshot ({} items now) for {}",
                    countItems(player), player.getGameProfile().getName());
            if (!keepFile) {
                Files.deleteIfExists(snapshot);
            }
            return true;
        } catch (IOException e) {
            SimpleAuth.LOGGER.error("Failed to read SimpleAuth limbo snapshot for {}", player.getGameProfile().getName(), e);
            return false;
        }
    }

    private static int countItems(ServerPlayer player) {
        int count = 0;
        for (net.minecraft.world.item.ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
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

    private static void writeTag(Path file, CompoundTag tag) throws IOException {
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(file))))) {
            NbtIo.write(tag, out);
        }
    }

    private static CompoundTag readTag(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
            return NbtIo.read(in);
        }
    }
}
