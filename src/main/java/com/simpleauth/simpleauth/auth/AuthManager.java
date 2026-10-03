package com.simpleauth.simpleauth.auth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Session state and account rules. Minecraft-free so the rules stay unit-testable;
 * the event handler translates players into UUID calls.
 */
public final class AuthManager {
    public enum Result {
        OK,
        NO_ACCOUNT,
        HAS_ACCOUNT,
        ALREADY_LOGGED_IN,
        MISMATCH,
        TOO_SHORT,
        WRONG_PASSWORD,
        SHOULD_KICK
    }

    private final PasswordStore store;
    private final Set<UUID> authed = new HashSet<>();
    private final Map<UUID, double[]> frozenAt = new HashMap<>();
    private final Map<UUID, Long> loginAt = new HashMap<>();
    private final Map<UUID, Integer> attempts = new HashMap<>();

    public AuthManager(PasswordStore store) {
        this.store = store;
    }

    public boolean isAuthed(UUID id) {
        return authed.contains(id);
    }

    public boolean hasAccount(UUID id) {
        return store.has(id);
    }

    public void noteLogin(UUID id, double x, double y, double z, float yaw, float pitch, long now) {
        frozenAt.put(id, new double[]{x, y, z, yaw, pitch});
        loginAt.putIfAbsent(id, now);
    }

    public double[] frozenPos(UUID id) {
        return frozenAt.get(id);
    }

    public void forget(UUID id) {
        authed.remove(id);
        frozenAt.remove(id);
        loginAt.remove(id);
        attempts.remove(id);
    }

    public Result register(UUID id, String name, String password, String confirm, int minLength) {
        if (store.has(id)) {
            return Result.HAS_ACCOUNT;
        }
        if (!password.equals(confirm)) {
            return Result.MISMATCH;
        }
        if (password.length() < minLength) {
            return Result.TOO_SHORT;
        }
        store.put(id, name, password);
        authed.add(id);
        attempts.remove(id);
        return Result.OK;
    }

    public Result login(UUID id, String password, int maxAttempts) {
        if (authed.contains(id)) {
            return Result.ALREADY_LOGGED_IN;
        }
        if (!store.has(id)) {
            return Result.NO_ACCOUNT;
        }
        if (store.check(id, password)) {
            authed.add(id);
            attempts.remove(id);
            return Result.OK;
        }
        int fails = attempts.getOrDefault(id, 0) + 1;
        attempts.put(id, fails);
        if (maxAttempts > 0 && fails >= maxAttempts) {
            return Result.SHOULD_KICK;
        }
        return Result.WRONG_PASSWORD;
    }

    public Result changePassword(UUID id, String name, String oldPassword, String next, int minLength) {
        if (!store.has(id)) {
            return Result.NO_ACCOUNT;
        }
        if (!store.check(id, oldPassword)) {
            return Result.WRONG_PASSWORD;
        }
        if (next.length() < minLength) {
            return Result.TOO_SHORT;
        }
        store.put(id, name, next);
        return Result.OK;
    }

    public UUID resetByName(String name) {
        UUID removed = store.removeByName(name);
        if (removed != null) {
            forget(removed);
        }
        return removed;
    }

    public void saveSession(UUID id, String ip, long now, long windowMillis) {
        if (windowMillis > 0 && store.has(id)) {
            store.saveSession(id, ip, now + windowMillis);
        }
    }

    public boolean trySession(UUID id, String ip, long now, long windowMillis) {
        if (windowMillis <= 0 || !store.has(id)) {
            return false;
        }
        if (store.sessionValid(id, ip, now)) {
            authed.add(id);
            attempts.remove(id);
            return true;
        }
        return false;
    }

    public List<UUID> expired(long now, long timeoutMillis) {
        List<UUID> out = new ArrayList<>();
        if (timeoutMillis <= 0) {
            return out;
        }
        for (Map.Entry<UUID, Long> entry : loginAt.entrySet()) {
            if (!authed.contains(entry.getKey()) && now - entry.getValue() >= timeoutMillis) {
                out.add(entry.getKey());
            }
        }
        return out;
    }
}
