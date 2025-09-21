package com.voxmagic.server;

import com.voxmagic.VoxMagicMode;
import com.voxmagic.common.config.ModConfig;
import com.voxmagic.network.NetworkInit;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class VoiceSpellTracker {
    private static final long SHARE_WINDOW_MS = 400L;
    private static final long NONCE_RESET_GRACE_MS = 10_000L;

    private static final Map<UUID, LastNonce> LAST_NONCES = new HashMap<>();
    private static final Map<UUID, Long> LAST_NOTICE = new HashMap<>();
    private static final Map<SpellKey, ActiveCast> ACTIVE_CASTS = new HashMap<>();

    private VoiceSpellTracker() {
    }

    public static boolean allowCast(ServerPlayerEntity player, NetworkInit.SpellCastPayload payload) {
        long now = System.currentTimeMillis();
        cleanupExpired(now);

        UUID playerId = player.getUuid();
        LastNonce last = LAST_NONCES.computeIfAbsent(playerId, id -> new LastNonce());
        if (payload.nonce <= last.nonce && now - last.processedAt < NONCE_RESET_GRACE_MS) {
            VoxMagicMode.LOGGER.debug("Ignoring duplicate spell nonce {} from {}", payload.nonce, player.getGameProfile().getName());
            return false;
        }
        last.nonce = payload.nonce;
        last.processedAt = now;

        String spellId = payload.spellId;
        if (spellId == null || spellId.isEmpty()) {
            return false;
        }

        SpellKey key = new SpellKey(spellId, canonicalize(payload.transcript));
        ActiveCast active = ACTIVE_CASTS.get(key);
        if (active != null && active.expiresAt > now) {
            if (active.playerId.equals(playerId)) {
                active.expiresAt = now + SHARE_WINDOW_MS;
                return true;
            }
            notifySuppressed(player, active.playerName, spellId);
            VoxMagicMode.LOGGER.debug("Ignoring spell '{}' from {} because {} recently cast it", spellId, player.getGameProfile().getName(), active.playerName);
            return false;
        }

        ACTIVE_CASTS.put(key, new ActiveCast(playerId, player.getGameProfile().getName(), now + SHARE_WINDOW_MS));
        return true;
    }

    public static void clear(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUuid();
        LAST_NONCES.remove(playerId);
        LAST_NOTICE.remove(playerId);
        Iterator<Map.Entry<SpellKey, ActiveCast>> it = ACTIVE_CASTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<SpellKey, ActiveCast> entry = it.next();
            if (entry.getValue().playerId.equals(playerId)) {
                it.remove();
            }
        }
    }

    private static void cleanupExpired(long now) {
        Iterator<Map.Entry<SpellKey, ActiveCast>> it = ACTIVE_CASTS.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expiresAt <= now) {
                it.remove();
            }
        }
    }

    private static void notifySuppressed(ServerPlayerEntity player, String ownerName, String spellId) {
        if (!ModConfig.INSTANCE.voice.debug_chat) {
            return;
        }
        long now = System.currentTimeMillis();
        UUID playerId = player.getUuid();
        Long last = LAST_NOTICE.get(playerId);
        if (last != null && now - last < 2000L) {
            return;
        }
        LAST_NOTICE.put(playerId, now);
        player.sendMessage(Text.literal("[VoxMagic] \u0417\u0430\u043a\u043b\u0438\u043d\u0430\u043d\u0438\u0435 '" + spellId + "' \u0443\u0436\u0435 \u043f\u0440\u043e\u0438\u0437\u043d\u0435\u0441\u0435\u043d\u043e \u0438\u0433\u0440\u043e\u043a\u043e\u043c " + ownerName + "."), false);
    }

    private static String canonicalize(String transcript) {
        if (transcript == null) {
            return "";
        }
        String lower = transcript.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        boolean space = false;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
                space = false;
            } else if (!space) {
                sb.append(' ');
                space = true;
            }
        }
        int len = sb.length();
        while (len > 0 && sb.charAt(len - 1) == ' ') {
            sb.deleteCharAt(--len);
        }
        while (sb.length() > 0 && sb.charAt(0) == ' ') {
            sb.deleteCharAt(0);
        }
        return sb.toString();
    }

    private static final class LastNonce {
        long nonce = -1L;
        long processedAt = 0L;
    }

    private static final class ActiveCast {
        final UUID playerId;
        final String playerName;
        long expiresAt;

        private ActiveCast(UUID playerId, String playerName, long expiresAt) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.expiresAt = expiresAt;
        }
    }

    private static final class SpellKey {
        final String spellId;
        final String transcript;

        private SpellKey(String spellId, String transcript) {
            this.spellId = spellId;
            this.transcript = transcript;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SpellKey key)) return false;
            return spellId.equals(key.spellId) && transcript.equals(key.transcript);
        }

        @Override
        public int hashCode() {
            int result = spellId.hashCode();
            result = 31 * result + transcript.hashCode();
            return result;
        }
    }
}


