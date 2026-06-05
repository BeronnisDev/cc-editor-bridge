package com.berotech.cceb;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = CCEditorBridge.MODID)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Enable the localhost WebSocket bridge for external editors.")
            .define("enabled", false);

    public static final ModConfigSpec.IntValue SOCKET_PORT = BUILDER
            .comment("Port for the editor WebSocket server (localhost only).")
            .defineInRange("socketPort", 8765, 1024, 65535);

    public static final ModConfigSpec.ConfigValue<String> AUTH_TOKEN = BUILDER
            .comment("Optional auth token editors must send on connect. Leave empty to disable.")
            .define("authToken", "");

    static final ModConfigSpec SPEC = BUILDER.build();

    @SubscribeEvent
    static void onLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getModId().equals(CCEditorBridge.MODID) && event.getConfig().getType() == ModConfig.Type.CLIENT) {
            logConfig("loaded");
        }
    }

    @SubscribeEvent
    static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getModId().equals(CCEditorBridge.MODID) && event.getConfig().getType() == ModConfig.Type.CLIENT) {
            logConfig("reloaded");
        }
    }

    private static void logConfig(String action) {
        CCEditorBridge.LOGGER.info(
                "Bridge config {}: enabled={}, port={}, authToken={}",
                action,
                ENABLED.get(),
                SOCKET_PORT.get(),
                AUTH_TOKEN.get().isEmpty() ? "(none)" : "(set)"
        );
    }
}
