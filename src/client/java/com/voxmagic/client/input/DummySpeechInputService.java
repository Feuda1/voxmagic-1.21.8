package com.voxmagic.client.input;

import com.voxmagic.common.config.ModConfig;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class DummySpeechInputService implements SpeechInputService {
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> new Thread(r, "VoxDummySpeech"));
    private volatile boolean running = false;

    @Override
    public void startOnce(Consumer<Result> callback) {
        running = true;
        exec.submit(() -> {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            if (!running) return;
            String transcript = System.getProperty("voxmagic.test_phrase", "");
            String matched = match(transcript);
            callback.accept(new Result(transcript, matched));
            running = false;
        });
    }

    private static String match(String transcript) {
        if (transcript == null) return null;
        String t = transcript.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, java.util.List<String>> e : ModConfig.INSTANCE.voice.phrases.entrySet()) {
            for (String s : e.getValue()) {
                if (t.equals(s.toLowerCase(Locale.ROOT))) return e.getKey();
            }
        }
        return null;
    }

    @Override
    public void stop() { running = false; }
}
