package com.simpleauth.simpleauth.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Salted, iterated password storage. No Minecraft classes: unit-testable.
 * Format on disk: {"<uuid>": {"name": "...", "salt": "...", "hash": "..."}}.
 */
public final class PasswordStore {
    static final int ITERATIONS = 210000;
    static final int KEY_BITS = 256;
    static final int SALT_BYTES = 16;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Entry>>() {
    }.getType();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path file;
    private final Map<String, Entry> entries = new HashMap<>();

    public PasswordStore(Path file) {
        this.file = file;
    }

    public synchronized void load() throws IOException {
        entries.clear();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Entry> loaded = GSON.fromJson(reader, MAP_TYPE);
            if (loaded != null) {
                entries.putAll(loaded);
            }
        }
    }

    public synchronized void save() throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(entries, writer);
        }
    }

    public synchronized boolean has(UUID id) {
        return entries.containsKey(id.toString());
    }

    public synchronized void put(UUID id, String name, String password) {
        byte[] salt = newSalt();
        entries.put(id.toString(), new Entry(name, encode(salt), encode(hash(password, salt))));
    }

    public synchronized boolean check(UUID id, String password) {        Entry entry = entries.get(id.toString());
        if (entry == null || entry.salt == null || entry.hash == null) {
            return false;
        }
        byte[] expected = decode(entry.hash);
        byte[] actual = hash(password, decode(entry.salt));
        return MessageDigest.isEqual(expected, actual);
    }

    /**
     * Removes the account whose last-known name matches, for op resets.
     *
     * @return the removed account id, or null when no name matched
     */
    public synchronized UUID removeByName(String name) {
        for (Map.Entry<String, Entry> candidate : entries.entrySet()) {
            if (candidate.getValue().name != null && candidate.getValue().name.equalsIgnoreCase(name)) {
                entries.remove(candidate.getKey());
                return UUID.fromString(candidate.getKey());
            }
        }
        return null;
    }

    static byte[] hash(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
            try {
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            } finally {
                spec.clearPassword();
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 unavailable", e);
        }
    }

    private static byte[] newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return salt;
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] decode(String text) {
        return Base64.getDecoder().decode(text);
    }

    public synchronized boolean sessionValid(UUID id, String ip, long now) {
        Entry entry = entries.get(id.toString());
        return entry != null
                && entry.sessionIp != null
                && entry.sessionIp.equals(ip)
                && entry.sessionExpires > now;
    }

    public synchronized void saveSession(UUID id, String ip, long expiresAt) {
        Entry entry = entries.get(id.toString());
        if (entry != null) {
            entry.sessionIp = ip;
            entry.sessionExpires = expiresAt;
        }
    }

    static final class Entry {
        String name;
        String salt;
        String hash;
        String sessionIp;
        long sessionExpires;

        Entry(String name, String salt, String hash) {
            this.name = name;
            this.salt = salt;
            this.hash = hash;
        }
    }
}
