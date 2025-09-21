package com.voxmagic.server;

import com.voxmagic.common.config.ModConfig;
import com.voxmagic.network.NetworkInit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ManaManager {
    private static final Map<UUID, ManaState> STATES = new HashMap<>();

    public static void tick(MinecraftServer server) {
        int tick = (int) (server.getOverworld().getTime() & Integer.MAX_VALUE);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ManaState s = STATES.computeIfAbsent(p.getUuid(), k -> new ManaState());
            s.tick(server);
            if (tick % 10 == 0) {
                NetworkInit.sendManaSync(p, s.mana, ModConfig.INSTANCE.mana.max, s.globalCooldownTicks, ModConfig.INSTANCE.mana.regen_per_sec);
            }
        }
    }

    public static boolean tryConsumeForSpell(ServerPlayerEntity player, String spellId) {
        ManaState s = STATES.computeIfAbsent(player.getUuid(), k -> new ManaState());
        if (s.globalCooldownTicks > 0) return false;
        int cost = ModConfig.INSTANCE.spells.getOrDefault(spellId, new ModConfig.SpellCfg()).mana_cost;
        if (s.mana < cost) return false;
        s.mana -= cost;
        s.lastSpent = cost;
        return true;
    }

    public static void refundLast(ServerPlayerEntity player, String spellId) {
        ManaState s = STATES.computeIfAbsent(player.getUuid(), k -> new ManaState());
        s.mana = Math.min(ModConfig.INSTANCE.mana.max, s.mana + s.lastSpent);
        s.lastSpent = 0;
    }

    public static void markGlobalCooldown(ServerPlayerEntity player, int ticks) {
        ManaState s = STATES.computeIfAbsent(player.getUuid(), k -> new ManaState());
        s.globalCooldownTicks = Math.max(s.globalCooldownTicks, ticks);
    }

    public static class ManaState {
        public int mana = ModConfig.INSTANCE.mana.max;
        public int globalCooldownTicks = 0;
        public int lastSpent = 0;
        private double carry = 0;

        public void tick(MinecraftServer server) {
            if (globalCooldownTicks > 0) globalCooldownTicks--;
            double perTick = ModConfig.INSTANCE.mana.regen_per_sec / 20.0;
            carry += perTick;
            int add = (int) carry;
            if (add > 0) {
                mana = Math.min(ModConfig.INSTANCE.mana.max, mana + add);
                carry -= add;
            }
        }
    }
}

