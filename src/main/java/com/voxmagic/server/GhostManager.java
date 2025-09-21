package com.voxmagic.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GhostManager {
    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private GhostManager() {}

    public static void enterGhost(ServerPlayerEntity player, double seconds) {
        if (ACTIVE.containsKey(player.getUuid())) return;
        GameMode prev = player.interactionManager.getGameMode();
        ACTIVE.put(player.getUuid(), new State(prev, (int) Math.round(seconds * 20)));
        player.changeGameMode(GameMode.SPECTATOR);
    }

    public static void tick(MinecraftServer server) {
        ACTIVE.entrySet().removeIf(entry -> {
            UUID id = entry.getKey();
            State state = entry.getValue();
            state.ticks--;
            if (state.ticks <= 0) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                if (player != null) {
                    player.changeGameMode(state.previousMode);
                }
                return true;
            }
            return false;
        });
    }

    public static void onJoin(ServerPlayerEntity player) {
        State state = ACTIVE.remove(player.getUuid());
        if (state != null) {
            player.changeGameMode(state.previousMode);
        }
    }

    private static class State {
        final GameMode previousMode;
        int ticks;
        State(GameMode previousMode, int ticks) { this.previousMode = previousMode; this.ticks = ticks; }
    }
}
