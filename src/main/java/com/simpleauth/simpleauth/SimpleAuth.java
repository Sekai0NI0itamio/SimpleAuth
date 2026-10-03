package com.simpleauth.simpleauth;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(SimpleAuth.MOD_ID)
public class SimpleAuth {
    public static final String MOD_ID = "simpleauth";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SimpleAuth() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SimpleAuthConfig.SPEC);
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            MinecraftForge.EVENT_BUS.register(AuthEventHandler.class);
        }
    }
}
