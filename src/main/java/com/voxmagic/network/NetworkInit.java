package com.voxmagic.network;

import com.voxmagic.VoxMagicMode;
import com.voxmagic.common.config.ModConfig;
import com.voxmagic.server.ManaManager;
import com.voxmagic.server.SpellAccessManager;
import com.voxmagic.server.SpellExecutor;
import com.voxmagic.server.VoiceSpellTracker;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

public final class NetworkInit {
    public static void registerC2S() {
        PayloadTypeRegistry.playC2S().register(SpellCastPayload.ID, SpellCastPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SpellCastPayload.ID, (payload, context) -> {
            MinecraftServer server = context.server();
            ServerPlayerEntity player = context.player();
            server.execute(() -> onSpellCast(server, player, payload));
        });
    }

    // Client S2C registration is in client-side init class

    private static void onSpellCast(MinecraftServer server, ServerPlayerEntity player, SpellCastPayload payload) {
        if (!player.getUuid().equals(payload.playerId)) {
            VoxMagicMode.LOGGER.warn("Player {} attempted to cast as {}", player.getGameProfile().getName(), payload.playerId);
            return;
        }
        if (!VoiceSpellTracker.allowCast(player, payload)) {
            return;
        }
        if (!SpellAccessManager.isSpellEnabled(player, payload.spellId)) {
            player.sendMessage(Text.literal("[VoxMagic] \u0417\u0430\u043a\u043b\u0438\u043d\u0430\u043d\u0438\u0435 '" + payload.spellId + "' \u043e\u0442\u043a\u043b\u044e\u0447\u0435\u043d\u043e."), false);
            return;
        }
        if (!SpellExecutor.canCastNow(player)) return;
        if (!ManaManager.tryConsumeForSpell(player, payload.spellId)) return;
        boolean success = SpellExecutor.execute(player, payload);
        if (!success) {
            ManaManager.refundLast(player, payload.spellId);
        } else {
            ManaManager.markGlobalCooldown(player, (int) Math.round(ModConfig.INSTANCE.global_cooldown_sec * 20));
        }
    }

    public static void sendManaSync(PlayerEntity player, int mana, int max, int cdTicks, double regenPerSec) {
        if (player instanceof ServerPlayerEntity spe) {
            ServerPlayNetworking.send(spe, new ManaSyncPayload(mana, max, cdTicks, regenPerSec));
        }
    }

    // Payloads
    public static final class SpellCastPayload implements CustomPayload {
        public static final Id<SpellCastPayload> ID = new Id<>(Identifier.of(VoxMagicMode.MOD_ID, "spell_cast"));
        public static final PacketCodec<RegistryByteBuf, SpellCastPayload> CODEC = new PacketCodec<>() {
            @Override public void encode(RegistryByteBuf buf, SpellCastPayload value) {
                buf.writeUuid(value.playerId);
                buf.writeString(value.spellId);
                buf.writeString(value.transcript);
                buf.writeLong(value.timestamp);
                buf.writeLong(value.nonce);
            }
            @Override public SpellCastPayload decode(RegistryByteBuf buf) {
                return new SpellCastPayload(buf.readUuid(), buf.readString(64), buf.readString(256), buf.readLong(), buf.readLong());
            }
        };

        public final UUID playerId; public final String spellId; public final String transcript; public final long timestamp; public final long nonce;
        public SpellCastPayload(UUID playerId, String spellId, String transcript, long timestamp, long nonce) {
            this.playerId = playerId; this.spellId = spellId; this.transcript = transcript; this.timestamp = timestamp; this.nonce = nonce;
        }
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static final class ManaSyncPayload implements CustomPayload {
        public static final Id<ManaSyncPayload> ID = new Id<>(Identifier.of(VoxMagicMode.MOD_ID, "mana_sync"));
        public static final PacketCodec<RegistryByteBuf, ManaSyncPayload> CODEC = new PacketCodec<>() {
            @Override public void encode(RegistryByteBuf buf, ManaSyncPayload value) {
                buf.writeVarInt(value.mana);
                buf.writeVarInt(value.max);
                buf.writeVarInt(value.cooldownTicks);
                buf.writeDouble(value.regenPerSec);
            }
            @Override public ManaSyncPayload decode(RegistryByteBuf buf) {
                return new ManaSyncPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readDouble());
            }
        };

        public final int mana, max, cooldownTicks; public final double regenPerSec;
        public ManaSyncPayload(int mana, int max, int cooldownTicks, double regenPerSec) { this.mana = mana; this.max = max; this.cooldownTicks = cooldownTicks; this.regenPerSec = regenPerSec; }
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
}

