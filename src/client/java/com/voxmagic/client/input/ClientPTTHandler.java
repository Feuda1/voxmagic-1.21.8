package com.voxmagic.client.input;

import com.voxmagic.VoxMagicMode;
import com.voxmagic.common.config.ModConfig;
import com.voxmagic.content.ModItems;
import com.voxmagic.network.NetworkInit;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class ClientPTTHandler {
    private static final SpeechInputService speech = createSpeech();
    private static boolean listening = false;
    private static boolean usingDummySpeech = false;
    private static boolean warnedDummyFallback = false;
    private static long nonceCounter = 1;
    private static long lastSentMs = 0;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> tick(mc));
    }

    private static void tick(MinecraftClient mc) {
        if (mc == null) {
            if (listening) { speech.stop(); listening = false; }
            return;
        }

        var player = mc.player;
        if (player == null) {
            if (listening) { speech.stop(); listening = false; }
            return;
        }

        if (usingDummySpeech && !warnedDummyFallback) {
            warnedDummyFallback = true;
            player.sendMessage(Text.literal("[VoxMagic] \u0411\u0438\u0431\u043b\u0438\u043e\u0442\u0435\u043a\u0430 \u0440\u0430\u0441\u043f\u043e\u0437\u043d\u0430\u0432\u0430\u043d\u0438\u044f \u0440\u0435\u0447\u0438 \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u0430; \u043f\u0435\u0440\u0435\u0443\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0435 \u0441\u0431\u043e\u0440\u043a\u0443 \u043c\u043e\u0434\u043e\u0432 \u0441 Vosk."), false);
        }
        boolean shouldListen = player.getMainHandStack().isOf(ModItems.MAGIC_BOOK) || player.getOffHandStack().isOf(ModItems.MAGIC_BOOK);
        if (!shouldListen) {
            if (listening) { speech.stop(); listening = false; }
            return;
        }
        if (!listening) {
            listening = true;
            speech.startOnce(result -> {
                try {
                    mc.execute(() -> {
                        var networkHandler = mc.getNetworkHandler();
                        var executingPlayer = mc.player;
                        if (networkHandler == null || executingPlayer == null) return;
                        String transcript = result.transcript();
                        String spellId = result.matchedSpellId();
                        if (spellId == null || spellId.isEmpty()) {
                            if (ModConfig.INSTANCE.voice.debug_chat && transcript != null && !transcript.isBlank()) {
                                VoxMagicMode.LOGGER.info("Transcript '{}' had no spell match", transcript);
                                System.out.println("[VoxMagic] no spell match for: " + transcript);
                                executingPlayer.sendMessage(Text.literal("[VoxMagic] " + transcript + " (\u043d\u0435\u0442 \u0441\u043e\u0432\u043f\u0430\u0434\u0435\u043d\u0438\u044f)"), false);
                            }
                            return;
                        }
                        boolean debug = ModConfig.INSTANCE.voice.debug_chat;
                        if (debug) {
                            VoxMagicMode.LOGGER.info("Triggering spell '{}' from transcript '{}'", spellId, transcript);
                            System.out.println("[VoxMagic] sending spell: " + spellId + " (" + transcript + ")");
                            String shown = transcript == null || transcript.isBlank() ? "(\u0442\u0438\u0448\u0438\u043d\u0430)" : transcript;
                            executingPlayer.sendMessage(Text.literal("[VoxMagic] " + shown + " -> " + spellId), false);
                        }

                        long now = System.currentTimeMillis();
                        long minInterval = (long) (ModConfig.INSTANCE.global_cooldown_sec * 1000L);
                        if (now - lastSentMs < minInterval) {
                            VoxMagicMode.LOGGER.info("Voice spell '{}' blocked by cooldown", spellId);
                            if (debug) {
                                executingPlayer.sendMessage(Text.literal("[VoxMagic] \u041f\u0435\u0440\u0435\u0437\u0430\u0440\u044f\u0434\u043a\u0430: \u0437\u0430\u043a\u043b\u0438\u043d\u0430\u043d\u0438\u0435 \u043d\u0435 \u043e\u0442\u043f\u0440\u0430\u0432\u043b\u0435\u043d\u043e"), false);
                            }
                            return;
                        }
                        lastSentMs = now;

                        long nonce = nonceCounter++;
                        ClientPlayNetworking.send(new NetworkInit.SpellCastPayload(executingPlayer.getUuid(), spellId, transcript, now, nonce));
                    });
                } finally {
                    listening = false;
                }
            });
        }
    }


    private static SpeechInputService createSpeech() {
        try {
            Class.forName("org.vosk.Model");
            usingDummySpeech = false;
            warnedDummyFallback = false;
            return new VoskSpeechInputService();
        } catch (Throwable t) {
            usingDummySpeech = true;
            VoxMagicMode.LOGGER.warn("Vosk runtime unavailable; falling back to dummy speech", t);
            return new DummySpeechInputService();
        }
    }
}


