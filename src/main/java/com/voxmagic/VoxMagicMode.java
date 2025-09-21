package com.voxmagic;

import com.voxmagic.common.config.ModConfig;
import com.voxmagic.content.ModItems;
import com.voxmagic.network.NetworkInit;
import com.voxmagic.server.ManaManager;
import com.voxmagic.server.SpellAccessManager;
import com.voxmagic.server.TemporaryBlockManager;
import com.voxmagic.server.VoiceSpellTracker;
import com.voxmagic.server.VoxMagicCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VoxMagicMode implements ModInitializer {
    public static final String MOD_ID = "voxmagic";
    public static final Logger LOGGER = LoggerFactory.getLogger("VoxMagicMode");

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing VoxMagicMode (template)");
        ModConfig.load();
        SpellAccessManager.load();
        ModItems.register();
        NetworkInit.registerC2S();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> VoxMagicCommands.register(dispatcher));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ManaManager.tick(server);
            TemporaryBlockManager.tick(server);
            com.voxmagic.server.GhostManager.tick(server);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            com.voxmagic.server.GhostManager.onJoin(handler.getPlayer());
            VoiceSpellTracker.clear(handler.getPlayer());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.player != null) {
                VoiceSpellTracker.clear(handler.player);
            }
        });
    }
}

