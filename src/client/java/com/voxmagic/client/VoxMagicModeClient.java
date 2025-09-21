package com.voxmagic.client;

import com.voxmagic.client.hud.ManaHudOverlay;
import com.voxmagic.client.input.ClientPTTHandler;
import com.voxmagic.client.network.ClientNetworkInit;
import net.fabricmc.api.ClientModInitializer;
import com.voxmagic.client.command.MicCommand;
import com.voxmagic.client.command.DebugCommand;

public class VoxMagicModeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientNetworkInit.registerS2C();
        ClientPTTHandler.register();
        ManaHudOverlay.register();
        MicCommand.register();
        DebugCommand.register();
    }
}
