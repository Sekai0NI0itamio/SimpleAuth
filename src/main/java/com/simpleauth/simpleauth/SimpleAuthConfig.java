package com.simpleauth.simpleauth;

import net.minecraftforge.common.ForgeConfigSpec;

public final class SimpleAuthConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue LOGIN_TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_LOGIN_ATTEMPTS;
    public static final ForgeConfigSpec.IntValue MIN_PASSWORD_LENGTH;

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
        builder.pop();
        SPEC = builder.build();
    }

    private SimpleAuthConfig() {
    }
}
