package com.voxmagic.client.network;

import com.voxmagic.client.hud.ManaHudOverlay;
import com.voxmagic.network.NetworkInit;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class ClientNetworkInit {
    public static void registerS2C() {
        PayloadTypeRegistry.playS2C().register(NetworkInit.ManaSyncPayload.ID, NetworkInit.ManaSyncPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(NetworkInit.ManaSyncPayload.ID, (payload, context) -> {
            var client = context.client();
            client.execute(() -> ManaHudOverlay.onSync(payload));
        });
    }
}

