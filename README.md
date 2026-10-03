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

Until logged in, a player cannot move, chat, break or place blocks, interact,
attack, pick up or drop items, open containers, or run any command except
`/register` and `/login`. Too many wrong passwords kicks; idlers are kicked
after the login timeout (both configurable, `0` disables).

## Install

1. Take the `simpleauth-*.jar` from the latest GitHub release (or CI artifact).
2. Drop it into the server `mods` folder. Nothing goes on clients.
3. Restart. Accounts live in `<world>/serverconfig/simpleauth.json`.

## Config (`config/simpleauth-server.toml`)

- `loginTimeoutSeconds` (default 120): kick idle unauthenticated players; `0` disables.
- `maxLoginAttempts` (default 5): wrong `/login` tries before kick; `0` disables.
- `minPasswordLength` (default 4).

## Building

Never built locally on purpose: every push compiles on GitHub Actions
(`gradle build` on Java 17, runs the unit tests, uploads the jar).
Tag `v*` publishes a GitHub release with the jar attached.
