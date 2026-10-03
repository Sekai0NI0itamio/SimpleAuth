# Criteria: SimpleAuth (Forge 1.20.1 server-side auth mod)

- [ ] `gradle build` is green on GitHub Actions (ubuntu, Java 17); nothing is ever compiled locally
- [ ] CI uploads a working `simpleauth-1.0.0.jar` artifact; `mods.toml` inside declares `modId: simpleauth`
- [ ] Unit tests run in CI and pass: password round-trip, wrong password rejected, salts unique per registration
- [ ] Passwords stored salted (PBKDF2-HMAC-SHA256, 210k iterations, 16-byte salt), compared constant-time, never logged
- [ ] Unauthenticated players cannot move, chat, break/place, interact, attack, or run commands except `/register` and `/login`
- [ ] `/register <pw> <pw>` rejects mismatch and short passwords; `/login` tracks attempts and kicks past the limit; `/password change` requires the old password; ops get `/auth reset <name>`
- [ ] Server-only safe: no custom packets, no registries, all listeners gated to `DEDICATED_SERVER`
- [ ] Login timeout kicks idle unauthenticated players (configurable, 0 disables)
