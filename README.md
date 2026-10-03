# SimpleAuth

Server-side-only `/register` + `/login` authentication for **Forge 1.20.1** offline-mode servers.
No client mod needed. Passwords are stored salted and hashed (PBKDF2-HMAC-SHA256,
210,000 iterations, per-account 16-byte salt, constant-time comparison).

## Commands

| Command | Who | What |
|---|---|---|
| `/register <password> <password>` | everyone | Create your account (you are logged in right away) |
| `/login <password>` | everyone | Log in on every join |
| `/password change <old> <new>` | logged in | Change your password |
| `/auth reset <name>` | ops (level 2+) | Delete an account so the player must `/register` again |

Until logged in, a player waits in a limbo spot with an empty inventory:
no moving away, chatting, breaking/placing, interacting, attacking, item
pickup/drop, containers, or any command except `/register` and `/login`.
Their real inventory, ender chest, health, food, XP, and location are
snapshotted to disk first and restored on login. Too many wrong passwords
kicks; idlers are kicked after the login timeout (both configurable,
`0` disables).

Rejoining from the same IP within 24 hours (configurable, `0` disables)
logs you back in automatically. Same-IP sessions are convenience, not a
vault: players behind one NAT share an address, so keep the whitelist on.

## Install

1. Take the `simpleauth-*.jar` from the latest GitHub release (or CI artifact).
2. Drop it into the server `mods` folder. Nothing goes on clients.
3. Restart. Accounts live in `<world>/serverconfig/simpleauth.json`.

## Config (`config/simpleauth-server.toml`)

- `loginTimeoutSeconds` (default 120): kick idle unauthenticated players; `0` disables.
- `maxLoginAttempts` (default 5): wrong `/login` tries before kick; `0` disables.
- `minPasswordLength` (default 4).
- `sessionHours` (default 24): same-IP auto-login window; `0` disables.
- `limboDimension` / `limboX` / `limboY` / `limboZ`: where unauthenticated players wait (default high above overworld spawn).

## Building

Never built locally on purpose: every push compiles on GitHub Actions
(`gradle build` on Java 17, runs the unit tests, uploads the jar).
Tag `v*` publishes a GitHub release with the jar attached.
