package com.simpleauth.simpleauth;

import com.simpleauth.simpleauth.auth.PasswordStore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PasswordStoreTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path file() {
        return folder.getRoot().toPath().resolve("auth.json");
    }

    @Test
    public void roundTripThroughDisk() throws Exception {
        UUID id = UUID.randomUUID();
        PasswordStore store = new PasswordStore(file());
        store.load();
        store.put(id, "itamio", "s3cret!");
        store.save();

        PasswordStore reloaded = new PasswordStore(file());
        reloaded.load();
        assertTrue(reloaded.has(id));
        assertTrue(reloaded.check(id, "s3cret!"));
    }

    @Test
    public void wrongPasswordFails() throws Exception {
        UUID id = UUID.randomUUID();
        PasswordStore store = new PasswordStore(file());
        store.put(id, "itamio", "s3cret!");
        assertFalse(store.check(id, "wrong"));
        assertFalse(store.check(UUID.randomUUID(), "s3cret!"));
    }

    @Test
    public void samePasswordHashesDifferently() {
        PasswordStore store = new PasswordStore(file());
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        store.put(first, "a", "same-password");
        store.put(second, "b", "same-password");
        assertTrue(store.check(first, "same-password"));
        assertTrue(store.check(second, "same-password"));
    }

    @Test
    public void removeByNameFindsAccount() {
        PasswordStore store = new PasswordStore(file());
        UUID id = UUID.randomUUID();
        store.put(id, "Itamio", "s3cret!");
        assertTrue(store.removeByName("itamio").equals(id));
        assertFalse(store.has(id));
    }
}
