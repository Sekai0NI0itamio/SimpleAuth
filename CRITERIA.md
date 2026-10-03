# Criteria: SimpleAuth (Forge 1.20.1 server-side auth mod)

- [x] `gradle build` is green on GitHub Actions (ubuntu, Java 17); nothing is ever compiled locally
- [x] CI uploads a working `simpleauth-1.0.0.jar` artifact; `mods.toml` inside declares `modId: simpleauth`
- [x] Unit tests run in CI and pass: password round-trip, wrong password rejected, salts unique per registration
- [x] Passwords stored salted (PBKDF2-HMAC-SHA256, 210k iterations, 16-byte salt), compared constant-time, never logged
- [x] Unauthenticated players cannot move, chat, break/place, interact, attack, or run commands except `/register` and `/login`
- [x] `/register <pw> <pw>` rejects mismatch and short passwords; `/login` tracks attempts and kicks past the limit; `/password change` requires the old password; ops get `/auth reset <name>`
- [x] Server-only safe: no custom packets, no registries, all listeners gated to `DEDICATED_SERVER`
- [x] Login timeout kicks idle unauthenticated players (configurable, 0 disables)

## v1.1.0 additions

- [ ] Same-IP rejoin within the session window (default 24h, configurable, 0 disables) auto-authenticates, no /login needed
- [ ] Sessions persist across server restarts (stored with the account, expiry honored)
- [ ] Wrong IP or expired session falls back to the normal /login flow
- [ ] Unauthenticated players are moved to the limbo spot on join; inventory, ender chest, health, food, XP, and real location snapshotted to disk first
- [ ] Snapshots survive restarts and frozen-logouts (capture-once rule: an existing snapshot is never overwritten by limbo state)
- [ ] Successful auth restores snapshot and deletes it; frozen logout restores best-effort without deleting
- [ ] Limbo spot and session window are config values; unit tests cover session valid/expired/wrong-IP cases in CI
- [ ] Limbo restore verified live on the Seedloaf server by the user (cannot be CI-tested: needs live player I/O)
