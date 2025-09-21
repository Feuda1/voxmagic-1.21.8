package com.voxmagic.client.input;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.voxmagic.VoxMagicMode;
import com.voxmagic.client.voice.MicSelector;
import com.voxmagic.client.voice.VoskModelManager;
import com.voxmagic.common.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import javax.sound.sampled.*;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public final class VoskSpeechInputService implements SpeechInputService {
    static {
        try {
            System.setProperty("jna.encoding", "UTF-8");
        } catch (Throwable ignored) {}
    }

    private static final Charset CP866 = Charset.forName("IBM866");
    private static final Charset CP1251 = Charset.forName("windows-1251");
    private static final List<Charset> FALLBACK_ENCODINGS = List.of(CP866, CP1251);

    private static final Map<String, String> CANONICAL_DICT = buildCanonicalDict();

    private static final long MIC_ERROR_COOLDOWN_MS = 5000L;
    private static volatile long lastMicErrorMs = 0L;

    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> new Thread(r, "VoxVoskSpeech"));
    private volatile Future<?> task;

    @Override
    public void startOnce(Consumer<Result> callback) {
        stop();
        task = exec.submit(() -> runOnce(callback));
    }

    private void runOnce(Consumer<Result> callback) {
        try {
            var path = VoskModelManager.ensureModelExtracted();
            VoxMagicMode.LOGGER.info("Preparing Vosk recognizer with model at {}", path);
            System.out.println("[VoxMagic] Loading Vosk model from: " + path);

            try {
                Class<?> lib = Class.forName("org.vosk.LibVosk");
                Method setLogLevel = lib.getMethod("setLogLevel", int.class);
                setLogLevel.invoke(null, 0);
            } catch (Throwable ignored) {}

            int configuredRate = Math.max(8000, ModConfig.INSTANCE.voice.sample_rate);
            List<AudioFormat> formats = buildAudioFormats(configuredRate);
            try (TargetDataLine line = MicSelector.openPreferred(formats, ModConfig.INSTANCE.voice.mic_device)) {
                float lineSampleRate = line.getFormat().getSampleRate();
                if (lineSampleRate <= 0f) {
                    lineSampleRate = configuredRate;
                }
                int displayRate = Math.round(lineSampleRate);
                VoxMagicMode.LOGGER.info("Opened microphone '{}' with buffer size {} at {} Hz", line.getLineInfo(), line.getBufferSize(), displayRate);
                System.out.println("[VoxMagic] Listening on mic: " + line.getLineInfo() + " @ " + displayRate + " Hz");

                Class<?> modelClz = Class.forName("org.vosk.Model");
                Object model = modelClz.getConstructor(String.class).newInstance(path.toString());
                try {
                    Class<?> recClz = Class.forName("org.vosk.Recognizer");
                    Object rec;
                    float recognizerRate = lineSampleRate;
                    try {
                        rec = recClz.getConstructor(modelClz, float.class).newInstance(model, recognizerRate);
                    } catch (NoSuchMethodException ignored) {
                        try {
                            rec = recClz.getConstructor(modelClz, double.class).newInstance(model, (double) recognizerRate);
                        } catch (NoSuchMethodException ex) {
                            rec = recClz.getConstructor(modelClz, int.class).newInstance(model, Math.round(recognizerRate));
                        }
                    }
                    Method accept = recClz.getMethod("acceptWaveForm", byte[].class, int.class);
                    Method getRes = recClz.getMethod("getResult");
                    Method getFinal = recClz.getMethod("getFinalResult");
                    Method getPartial = recClz.getMethod("getPartialResult");
                    try {
                        byte[] buffer = new byte[4096];
                        long start = System.currentTimeMillis();
                        long lastPartialLog = 0L;
                        long bytesTotal = 0L;
                        String finalText = null;
                        while (!Thread.currentThread().isInterrupted()) {
                            int n = line.read(buffer, 0, buffer.length);
                            if (n <= 0) {
                                VoxMagicMode.LOGGER.info("Audio read returned {} bytes (<=0)", n);
                                System.out.println("[VoxMagic] audio read returned " + n + " bytes");
                                break;
                            }
                            bytesTotal += n;
                            boolean done = (boolean) accept.invoke(rec, buffer, n);
                            if (done) {
                                finalText = extractText((String) getRes.invoke(rec));
                                VoxMagicMode.LOGGER.info("Vosk produced final result after {} bytes", bytesTotal);
                                break;
                            } else {
                                long nowMs = System.currentTimeMillis();
                                if (nowMs - lastPartialLog > 400) {
                                    String partial = extractPartial((String) getPartial.invoke(rec));
                                    if (partial != null && !partial.isBlank()) {
                                        partial = ensureUtf8(partial);
                                        VoxMagicMode.LOGGER.info("Vosk partial: '{}' ({} bytes so far)", partial, bytesTotal);
                                        if (ModConfig.INSTANCE.voice.debug_chat) {
                                            System.out.println("[VoxMagic] partial: " + partial);
                                        }
                                        String partialNormalized = normalize(partial);
                                        String partialGuess = dictionaryCandidate(partialNormalized);
                                        if (partialGuess != null) {
                                            VoxMagicMode.LOGGER.info("Partial matched canonically: {}", partialGuess);
                                            finalText = partial;
                                            break;
                                        }
                                    }
                                    lastPartialLog = nowMs;
                                }
                            }
                            if (System.currentTimeMillis() - start > 6000) {
                                finalText = extractText((String) getFinal.invoke(rec));
                                VoxMagicMode.LOGGER.info("Vosk timeout reached; requesting final result ({} bytes)", bytesTotal);
                                break;
                            }
                        }
                        if (bytesTotal == 0) {
                            VoxMagicMode.LOGGER.warn("No audio captured from microphone");
                            System.out.println("[VoxMagic] WARNING: zero audio captured");
                            if (ModConfig.INSTANCE.voice.debug_chat) {
                                notifyClient("\u0417\u0432\u0443\u043a \u0441 \u043c\u0438\u043a\u0440\u043e\u0444\u043e\u043d\u0430 \u043d\u0435 \u043f\u043e\u043b\u0443\u0447\u0435\u043d. \u041f\u0440\u043e\u0432\u0435\u0440\u044c\u0442\u0435 \u0443\u0441\u0442\u0440\u043e\u0439\u0441\u0442\u0432\u043e \u0432\u0432\u043e\u0434\u0430 \u0438 \u0447\u0430\u0441\u0442\u043e\u0442\u0443 \u0434\u0438\u0441\u043a\u0440\u0435\u0442\u0438\u0437\u0430\u0446\u0438\u0438.");
                            }
                        }
                        processResult(callback, finalText);
                    } finally {
                        try { recClz.getMethod("close").invoke(rec); } catch (Throwable ignored) {}
                        line.stop();
                    }
                } finally {
                    try { modelClz.getMethod("close").invoke(model); } catch (Throwable ignored) {}
                }
            }
        } catch (LineUnavailableException e) {
            VoxMagicMode.LOGGER.warn("Microphone unavailable", e);
            System.out.println("[VoxMagic] Mic error: " + e);
            notifyMicError(e.getMessage());
            callback.accept(new Result("", null));
        } catch (Exception e) {
            VoxMagicMode.LOGGER.warn("Vosk recognition error", e);
            System.out.println("[VoxMagic] Vosk error: " + e);
            String msg = e.getMessage();
            if (msg == null || msg.isBlank()) {
                msg = e.getClass().getSimpleName();
            }
            notifyClient("\u041e\u0448\u0438\u0431\u043a\u0430 Vosk: " + msg);
            callback.accept(new Result("", null));
        }
    }

    private static void notifyClient(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return;
        }
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("[VoxMagic] " + message), false);
            }
        });
    }

    private static void notifyMicError(String rawMessage) {
        long now = System.currentTimeMillis();
        if (now - lastMicErrorMs < MIC_ERROR_COOLDOWN_MS) {
            return;
        }
        lastMicErrorMs = now;
        String msg = rawMessage;
        if (msg == null || msg.isBlank()) {
            msg = "\u043f\u0440\u0435\u0434\u043f\u043e\u0447\u0442\u0438\u0442\u0435\u043b\u044c\u043d\u044b\u0439 \u043c\u0438\u043a\u0440\u043e\u0444\u043e\043d \u043d\u0435\u0434\u043e\0441\0442\0443\043f\0435\043d";
        }
        notifyClient("\u041c\u0438\u043a\u0440\u043e\u0444\u043e\043d \u043d\u0435\u0434\u043e\0441\0442\0443\043f\0435\043d: " + msg);
    }

    private static void processResult(Consumer<Result> callback, String rawTranscript) {
        String transcript = ensureUtf8(rawTranscript);
        String normalized = normalize(transcript);
        String dictionaryGuess = dictionaryCandidate(normalized);
        String matched = dictionaryGuess != null ? dictionaryGuess : matchFromConfig(normalized);

        boolean debug = ModConfig.INSTANCE.voice.debug_chat;

        if (debug && dictionaryGuess != null) {
            VoxMagicMode.LOGGER.info("Canonical guess: {}", dictionaryGuess);
            System.out.println("[VoxMagic] guess: " + dictionaryGuess);
        }

        if (debug) {
            if (transcript != null && !transcript.isBlank()) {
                VoxMagicMode.LOGGER.info("Vosk transcript: '{}' (normalized='{}')", transcript, normalized);
                System.out.println("[VoxMagic] transcript: " + transcript + " (" + normalized + ")");
            } else {
                VoxMagicMode.LOGGER.info("Vosk did not capture any speech (empty transcript)");
                System.out.println("[VoxMagic] transcript empty");
            }
            if (matched != null) {
                VoxMagicMode.LOGGER.info("Matched spell: {}", matched);
                System.out.println("[VoxMagic] matched spell: " + matched);
            } else if (transcript != null && !transcript.isBlank()) {
                VoxMagicMode.LOGGER.info("Transcript '{}' did not match any spell", transcript);
                System.out.println("[VoxMagic] no spell match for: " + transcript);
            }
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (debug && mc != null && mc.player != null) {
            StringBuilder msg = new StringBuilder("[VoxMagic] ");
            msg.append(transcript == null || transcript.isBlank() ? "(\u0442\u0438\u0448\u0438\u043d\u0430)" : transcript);
            if (matched != null && !matched.isBlank()) {
                msg.append(" -> ").append(matched);
            }
            String toSend = msg.toString();
            mc.execute(() -> mc.player.sendMessage(Text.literal(toSend), false));
        }

        callback.accept(new Result(transcript == null ? "" : transcript, matched));
    }

    private static Map<String, String> buildCanonicalDict() {
        Map<String, String> map = new HashMap<>();
        addAliases(map, "lightning", "\u043c\u043e\u043b\u043d\u0438\u044f", "\u043c\u043e\u043b\u043d\u0438\u0438", "lightning");
        addAliases(map, "web", "\u043f\u0430\u0443\u0442\u0438\043d\u0430", "\u0441\u0435\u0442\u044c", "web");
        addAliases(map, "heal", "\u043b\u0435\u0447\u0435\043d\u0438\0435", "\u0438\u0441\u0446\u0435\043b\0435\043d\0438\0435", "heal");
        addAliases(map, "ghost", "\u043f\u0440\u0438\u0437\u0440\u0430\043a", "\u0441\u043f\u0435\u043a\u0442\u0440", "ghost");
        addAliases(map, "wall", "\u0441\u0442\u0435\u043d\u0430", "wall");
        addAliases(map, "fireball", "\u043e\u0433\u043e\u043d\u044c", "\u0444\u0430\u0435\u0440\u0431\u043e\u043b", "fireball");
        addAliases(map, "slime", "\u0441\u043b\u0438\u0437\u044c", "\u043f\u043b\u0430\u0442\u0444\u043e\u0440\u043c\u0430", "slime");
        addAliases(map, "dome", "\u043a\u0443\u043f\u043e\u043b", "\u0449\u0438\u0442", "dome");
        addAliases(map, "shockwave", "\u0443\u0434\u0430\u0440\u043d\u0430\044f \u0432\u043e\u043b\u043d\u0430", "shockwave");
        addAliases(map, "levitate", "\u043b\u0435\u0432\u0438\u0442\u0430\u0446\u0438\044f", "\u043f\u043e\u0434\u043d\u044f\u0442\u044c\u0441\u044f", "levitate");
        addAliases(map, "meteor", "\u043c\u0435\u0442\u0435\u043e\u0440", "\u0437\u0432\u0435\u0437\u0434\u043d\u044b\0439 \u043f\u0430\u0434\u0435\u043d\u0438\0435", "meteor");
        addAliases(map, "push", "\u0442\u043e\u043b\u0447\u043e\043a", "\u0440\u044b\u0432\u043e\043a", "push");
        addAliases(map, "teleport", "\u0442\u0435\u043b\u0435\u043f\u043e\u0440\u0442", "\u043f\u0435\u0440\u0435\u043d\u043e\0441", "teleport");
        addAliases(map, "pull", "\u043f\u0440\u0438\u0442\u044f\u0436\u0435\u043d\u0438\0435", "\u043f\u0440\u0438\u0442\u044f\u043d\u0438", "pull");
        return Map.copyOf(map);
    }

    private static void addAliases(Map<String, String> map, String spellId, String... aliases) {
        for (String alias : aliases) {
            String normalized = normalize(alias);
            if (!normalized.isEmpty()) {
                map.putIfAbsent(normalized, spellId);
            }
        }
    }

    private static String matchFromConfig(String normalized) {
        if (normalized.isEmpty()) return null;
        String padded = " " + normalized + " ";
        for (Map.Entry<String, List<String>> entry : ModConfig.INSTANCE.voice.phrases.entrySet()) {
            for (String alias : entry.getValue()) {
                String aliasNorm = normalize(alias);
                if (aliasNorm.isEmpty()) continue;
                if (normalized.equals(aliasNorm)) return entry.getKey();
                if (padded.contains(" " + aliasNorm + " ")) return entry.getKey();
            }
        }
        return null;
    }

    private static List<AudioFormat> buildAudioFormats(int desiredSampleRate) {
        List<AudioFormat> formats = new ArrayList<>();
        addFormat(formats, desiredSampleRate);
        addFormat(formats, 48000);
        addFormat(formats, 44100);
        addFormat(formats, 32000);
        addFormat(formats, 24000);
        addFormat(formats, 22050);
        addFormat(formats, 16000);
        addFormat(formats, 11025);
        addFormat(formats, 8000);
        return formats;
    }

    private static void addFormat(List<AudioFormat> formats, int rate) {
        if (rate < 8000) return;
        for (AudioFormat format : formats) {
            if (Math.round(format.getSampleRate()) == rate) {
                return;
            }
        }
        formats.add(new AudioFormat(rate, 16, 1, true, false));
    }

    private static String dictionaryCandidate(String normalized) {
        if (normalized == null || normalized.isEmpty()) return null;
        String direct = CANONICAL_DICT.get(normalized);
        if (direct != null) return direct;
        String[] tokens = normalized.split(" ");
        for (String token : tokens) {
            direct = CANONICAL_DICT.get(token);
            if (direct != null) return direct;
        }
        for (Map.Entry<String, String> entry : CANONICAL_DICT.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String normalize(String input) {
        if (input == null) return "";
        String s = ensureUtf8(input).toLowerCase(Locale.ROOT);
        s = s.replace('\u0451', '\u0435');
        StringBuilder sb = new StringBuilder();
        boolean prevSpace = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
                prevSpace = false;
            } else {
                if (!prevSpace) {
                    sb.append(' ');
                    prevSpace = true;
                }
            }
        }
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ') {
            sb.deleteCharAt(sb.length() - 1);
        }
        while (sb.length() > 0 && sb.charAt(0) == ' ') {
            sb.deleteCharAt(0);
        }
        return sb.toString();
    }

    private static String ensureUtf8(String input) {
        if (input == null || input.isEmpty()) return input == null ? "" : input;
        if (containsCyrillic(input)) return input;
        for (Charset source : FALLBACK_ENCODINGS) {
            try {
                byte[] bytes = input.getBytes(source);
                String candidate = new String(bytes, StandardCharsets.UTF_8);
                if (containsCyrillic(candidate)) {
                    return candidate;
                }
            } catch (Exception ignored) {}
        }
        try {
            byte[] bytes = input.getBytes(StandardCharsets.ISO_8859_1);
            String candidate = new String(bytes, StandardCharsets.UTF_8);
            if (containsCyrillic(candidate)) {
                return candidate;
            }
        } catch (Exception ignored) {}
        return input;
    }

    private static boolean containsCyrillic(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= '\u0430' && c <= '\u044f') || c == '\u0451' || c == '\u0401') {
                return true;
            }
        }
        return false;
    }

    private static String extractText(String json) {
        return extract(json, "text");
    }

    private static String extractPartial(String json) {
        return extract(json, "partial");
    }

    private static String extract(String json, String key) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has(key)) return obj.get(key).getAsString();
        } catch (Exception ignored) {}
        return "";
    }

    @Override
    public void stop() {
        if (task != null) task.cancel(true);
    }
}









