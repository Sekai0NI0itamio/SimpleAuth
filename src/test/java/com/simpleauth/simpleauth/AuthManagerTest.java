package com.simpleauth.simpleauth;

import com.simpleauth.simpleauth.auth.AuthManager;
import com.simpleauth.simpleauth.auth.PasswordStore;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuthManagerTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private AuthManager manager;
    private UUID id;

    @Before
    public void setup() {
        manager = new AuthManager(new PasswordStore(folder.getRoot().toPath().resolve("auth.json")));
        id = UUID.randomUUID();
        manager.noteLogin(id, 0, 64, 0, 0, 0, 1000L);
    }

    @Test
    public void registerLogsInImmediately() {
        assertEquals(AuthManager.Result.OK, manager.register(id, "itamio", "s3cret!", "s3cret!", 4));
        assertTrue(manager.isAuthed(id));
    }

    @Test
    public void registerRejectsMismatchAndShortPasswords() {
        assertEquals(AuthManager.Result.MISMATCH, manager.register(id, "itamio", "one", "two", 4));
        assertEquals(AuthManager.Result.TOO_SHORT, manager.register(id, "itamio", "abc", "abc", 4));
        assertFalse(manager.isAuthed(id));
    }

    @Test
    public void loginCountsFailuresThenKicks() {
        manager.register(id, "itamio", "s3cret!", "s3cret!", 4);
        manager.forget(id);
        assertEquals(AuthManager.Result.WRONG_PASSWORD, manager.login(id, "nope", 3));
        assertEquals(AuthManager.Result.WRONG_PASSWORD, manager.login(id, "nope", 3));
        assertEquals(AuthManager.Result.SHOULD_KICK, manager.login(id, "nope", 3));
        assertEquals(AuthManager.Result.OK, manager.login(id, "s3cret!", 3));
    }

    @Test
    public void changePasswordNeedsOldPassword() {
        manager.register(id, "itamio", "s3cret!", "s3cret!", 4);
        assertEquals(AuthManager.Result.WRONG_PASSWORD, manager.changePassword(id, "itamio", "nope", "newpass1", 4));
        assertEquals(AuthManager.Result.OK, manager.changePassword(id, "itamio", "s3cret!", "newpass1", 4));
        manager.forget(id);
        assertEquals(AuthManager.Result.OK, manager.login(id, "newpass1", 5));
    }

    @Test
    public void sessionAutoLoginWithinWindow() {
        manager.register(id, "itamio", "s3cret!", "s3cret!", 4);
        manager.saveSession(id, "1.2.3.4", 10_000L, 24L * 3600_000L);
        manager.forget(id);
        assertTrue(manager.trySession(id, "1.2.3.4", 10_000L + 3600_000L, 24L * 3600_000L));
        assertTrue(manager.isAuthed(id));
    }

    @Test
    public void sessionRejectedWhenExpiredOrWrongIpOrDisabled() {
        manager.register(id, "itamio", "s3cret!", "s3cret!", 4);
        manager.saveSession(id, "1.2.3.4", 10_000L, 3600_000L);
        manager.forget(id);
        assertFalse(manager.trySession(id, "1.2.3.4", 10_000L + 3601_000L, 3600_000L));
        assertFalse(manager.trySession(id, "5.6.7.8", 10_000L + 1000L, 3600_000L));
        assertFalse(manager.trySession(id, "1.2.3.4", 10_000L + 1000L, 0L));
        assertFalse(manager.isAuthed(id));
    }

    @Test
    public void timeoutOnlyCatchesUnauthenticated() {        UUID slow = UUID.randomUUID();
        manager.noteLogin(slow, 0, 64, 0, 0, 0, 1000L);
        assertTrue(manager.expired(1000L + 121_000L, 120_000L).contains(slow));
        manager.register(slow, "slow", "pw12", "pw12", 4);
        assertFalse(manager.expired(1000L + 121_000L, 120_000L).contains(slow));
    }
}
