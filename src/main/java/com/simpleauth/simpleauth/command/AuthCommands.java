package com.simpleauth.simpleauth.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.simpleauth.simpleauth.AuthEventHandler;
import com.simpleauth.simpleauth.SimpleAuth;
import com.simpleauth.simpleauth.SimpleAuthConfig;
import com.simpleauth.simpleauth.auth.AuthManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;


public final class AuthCommands {
    private AuthCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("register")
                .requires(source -> true)
                .then(Commands.argument("password", StringArgumentType.word())
                        .then(Commands.argument("confirm", StringArgumentType.word())
                                .executes(ctx -> register(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "password"),
                                        StringArgumentType.getString(ctx, "confirm"))))));

        dispatcher.register(Commands.literal("login")
                .requires(source -> true)
                .then(Commands.argument("password", StringArgumentType.word())
                        .executes(ctx -> login(ctx.getSource(),
                                StringArgumentType.getString(ctx, "password")))));

        dispatcher.register(Commands.literal("password")
                .requires(source -> true)
                .then(Commands.literal("change")
                        .then(Commands.argument("old", StringArgumentType.word())
                                .then(Commands.argument("new", StringArgumentType.word())
                                        .executes(ctx -> changePassword(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "old"),
                                                StringArgumentType.getString(ctx, "new")))))));

        dispatcher.register(Commands.literal("auth")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reset")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> reset(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"))))));
    }

    private static AuthManager ready(CommandSourceStack source) {
        AuthManager manager = AuthEventHandler.manager();
        if (manager == null) {
            source.sendFailure(Component.literal("Auth is not ready yet, try again in a moment."));
        }
        return manager;
    }

    private static ServerPlayer playerOf(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player : null;
    }

    private static int register(CommandSourceStack source, String password, String confirm) {
        AuthManager manager = ready(source);
        if (manager == null) {
            return 0;
        }
        ServerPlayer player = playerOf(source);
        if (player == null) {
            source.sendFailure(Component.literal("Only players can register."));
            return 0;
        }
        AuthManager.Result result = manager.register(
                player.getUUID(), player.getGameProfile().getName(),
                password, confirm, SimpleAuthConfig.MIN_PASSWORD_LENGTH.get());
        switch (result) {
            case HAS_ACCOUNT -> source.sendFailure(Component.literal("You already have an account. Use /login <password>.").withStyle(ChatFormatting.RED));
            case MISMATCH -> source.sendFailure(Component.literal("Passwords do not match. Try again.").withStyle(ChatFormatting.RED));
            case TOO_SHORT -> source.sendFailure(Component.literal("Password too short.").withStyle(ChatFormatting.RED));
            case OK -> {
                if (!AuthEventHandler.save()) {
                    source.sendFailure(Component.literal("Registered, but saving failed. Tell an admin!").withStyle(ChatFormatting.RED));
                    return 0;
                }
                source.sendSuccess(() -> Component.literal("Registered! You are logged in.").withStyle(ChatFormatting.GREEN), false);
                return 1;
            }
            default -> source.sendFailure(Component.literal("Registration failed.").withStyle(ChatFormatting.RED));
        }
        return 0;
    }

    private static int login(CommandSourceStack source, String password) {
        AuthManager manager = ready(source);
        if (manager == null) {
            return 0;
        }
        ServerPlayer player = playerOf(source);
        if (player == null) {
            source.sendFailure(Component.literal("Only players can log in."));
            return 0;
        }
        AuthManager.Result result = manager.login(
                player.getUUID(), password, SimpleAuthConfig.MAX_LOGIN_ATTEMPTS.get());
        switch (result) {
            case ALREADY_LOGGED_IN -> source.sendSuccess(() -> Component.literal("You are already logged in.").withStyle(ChatFormatting.YELLOW), false);
            case NO_ACCOUNT -> source.sendFailure(Component.literal("No account yet. Use /register <password> <password>.").withStyle(ChatFormatting.RED));
            case WRONG_PASSWORD -> source.sendFailure(Component.literal("Wrong password.").withStyle(ChatFormatting.RED));
            case SHOULD_KICK -> {
                source.sendFailure(Component.literal("Too many wrong passwords.").withStyle(ChatFormatting.RED));
                player.connection.disconnect(Component.literal("Too many wrong passwords."));
                return 0;
            }
            case OK -> {
                source.sendSuccess(() -> Component.literal("Logged in. Have fun!").withStyle(ChatFormatting.GREEN), false);
                return 1;
            }
            default -> source.sendFailure(Component.literal("Login failed.").withStyle(ChatFormatting.RED));
        }
        return 0;
    }

    private static int changePassword(CommandSourceStack source, String oldPassword, String next) {
        AuthManager manager = ready(source);
        if (manager == null) {
            return 0;
        }
        ServerPlayer player = playerOf(source);
        if (player == null) {
            source.sendFailure(Component.literal("Only players can change passwords."));
            return 0;
        }
        if (!manager.isAuthed(player.getUUID())) {
            source.sendFailure(Component.literal("Log in first with /login <password>.").withStyle(ChatFormatting.RED));
            return 0;
        }
        AuthManager.Result result = manager.changePassword(
                player.getUUID(), player.getGameProfile().getName(),
                oldPassword, next, SimpleAuthConfig.MIN_PASSWORD_LENGTH.get());
        switch (result) {
            case NO_ACCOUNT -> source.sendFailure(Component.literal("No account yet. Use /register first.").withStyle(ChatFormatting.RED));
            case WRONG_PASSWORD -> source.sendFailure(Component.literal("Old password is wrong.").withStyle(ChatFormatting.RED));
            case TOO_SHORT -> source.sendFailure(Component.literal("New password too short.").withStyle(ChatFormatting.RED));
            case OK -> {
                if (!AuthEventHandler.save()) {
                    source.sendFailure(Component.literal("Changed, but saving failed. Tell an admin!").withStyle(ChatFormatting.RED));
                    return 0;
                }
                source.sendSuccess(() -> Component.literal("Password changed.").withStyle(ChatFormatting.GREEN), false);
                return 1;
            }
            default -> source.sendFailure(Component.literal("Could not change password.").withStyle(ChatFormatting.RED));
        }
        return 0;
    }

    private static int reset(CommandSourceStack source, String name) {
        AuthManager manager = ready(source);
        if (manager == null) {
            return 0;
        }
        if (manager.resetByName(name) == null) {
            source.sendFailure(Component.literal("No account found for '" + name + "'.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!AuthEventHandler.save()) {
            SimpleAuth.LOGGER.error("Auth data failed to save after reset of {}", name);
        }
        source.sendSuccess(() -> Component.literal("Account '" + name + "' was reset; they must /register again.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
