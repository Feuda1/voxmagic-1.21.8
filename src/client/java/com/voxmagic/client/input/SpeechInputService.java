package com.voxmagic.client.input;

import java.util.function.Consumer;

public interface SpeechInputService {
    void startOnce(Consumer<Result> callback);
    void stop();
    record Result(String transcript, String matchedSpellId) {}
}
