package com.simpleauth.simpleauth;

import net.minecraftforge.common.ForgeConfigSpec;

public final class SimpleAuthConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue LOGIN_TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_LOGIN_ATTEMPTS;
    public static final ForgeConfigSpec.IntValue MIN_PASSWORD_LENGTH;
    public static final ForgeConfigSpec.IntValue SESSION_HOURS;
    public static final ForgeConfigSpec.ConfigValue<String> LIMBO_DIMENSION;
    public static final ForgeConfigSpec.DoubleValue LIMBO_X;
    public static final ForgeConfigSpec.DoubleValue LIMBO_Y;
    public static final ForgeConfigSpec.DoubleValue LIMBO_Z;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("auth");
        LOGIN_TIMEOUT_SECONDS = builder
                .comment("Seconds an unauthenticated player may stay online before being kicked. 0 disables the timeout.")
                .defineInRange("loginTimeoutSeconds", 120, 0, 3600);
        MAX_LOGIN_ATTEMPTS = builder
                .comment("Wrong /login attempts before the player is kicked. 0 disables the limit.")
                .defineInRange("maxLoginAttempts", 5, 0, 100);
        MIN_PASSWORD_LENGTH = builder
                .comment("Minimum accepted /register password length.")
                .defineInRange("minPasswordLength", 4, 1, 128);
        SESSION_HOURS = builder
                .comment("How long a login session lasts: rejoining from the same IP skips /login. 0 disables sessions.")
                .defineInRange("sessionHours", 24, 0, 168);
        LIMBO_DIMENSION = builder
                .comment("Dimension unauthenticated players wait in, e.g. minecraft:overworld.")
                .define("limboDimension", "minecraft:overworld");
        LIMBO_X = builder.defineInRange("limboX", 0.5, -30000000.0, 30000000.0);
        LIMBO_Y = builder.defineInRange("limboY", 200.0, -2048.0, 4096.0);
        LIMBO_Z = builder.defineInRange("limboZ", 0.5, -30000000.0, 30000000.0);
        builder.pop();
        SPEC = builder.build();
    }

    private SimpleAuthConfig() {
    }
}
