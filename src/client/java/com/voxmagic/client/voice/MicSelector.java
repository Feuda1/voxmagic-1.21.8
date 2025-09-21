package com.voxmagic.client.voice;

import com.voxmagic.VoxMagicMode;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;

public final class MicSelector {
    public record MicEntry(int index, String name, String desc, boolean supportsFormat) {}

    public static List<MicEntry> listMics(AudioFormat format) {
        List<MicEntry> list = new ArrayList<>();
        Mixer.Info[] infos = AudioSystem.getMixerInfo();
        for (int i = 0; i < infos.length; i++) {
            Mixer.Info mi = infos[i];
            boolean ok = false;
            try {
                Mixer m = AudioSystem.getMixer(mi);
                ok = m.isLineSupported(new DataLine.Info(TargetDataLine.class, format));
            } catch (Exception ignored) {}
            list.add(new MicEntry(i, mi.getName(), mi.getDescription(), ok));
        }
        return list;
    }

    public static TargetDataLine openPreferred(AudioFormat format, String preferredSubstring) throws LineUnavailableException {
        return openPreferred(List.of(format), preferredSubstring);
    }

    public static TargetDataLine openPreferred(List<AudioFormat> formats, String preferredSubstring) throws LineUnavailableException {
        if (formats == null || formats.isEmpty()) {
            throw new LineUnavailableException("No audio formats provided");
        }
        LineUnavailableException lastError = null;
        boolean hasPreference = preferredSubstring != null && !preferredSubstring.isBlank();
        if (hasPreference) {
            String needle = preferredSubstring.toLowerCase();
            Mixer.Info[] infos = AudioSystem.getMixerInfo();
            for (Mixer.Info mi : infos) {
                String hay = (mi.getName() + " " + mi.getDescription()).toLowerCase();
                if (!hay.contains(needle)) {
                    continue;
                }
                Mixer mixer = AudioSystem.getMixer(mi);
                for (AudioFormat format : formats) {
                    try {
                        TargetDataLine line = tryOpen(mixer, format);
                        if (line != null) {
                            VoxMagicMode.LOGGER.info("Using mic: {} ({}) at {} Hz", mi.getName(), mi.getDescription(), (int) line.getFormat().getSampleRate());
                            return line;
                        }
                    } catch (LineUnavailableException e) {
                        lastError = e;
                        VoxMagicMode.LOGGER.warn("Mic found but unavailable: {}", mi, e);
                    }
                }
            }
            if (lastError != null) {
                throw lastError;
            }
            throw new LineUnavailableException("Preferred microphone not found or unsupported: " + preferredSubstring);
        }
        for (AudioFormat format : formats) {
            try {
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
                line.open(format);
                line.start();
                VoxMagicMode.LOGGER.info("Using default mic (no preference configured) at {} Hz", (int) line.getFormat().getSampleRate());
                return line;
            } catch (LineUnavailableException e) {
                lastError = e;
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new LineUnavailableException("No supported microphone format found");
    }

    private static TargetDataLine tryOpen(Mixer mixer, AudioFormat format) throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!mixer.isLineSupported(info)) {
            return null;
        }
        TargetDataLine line = (TargetDataLine) mixer.getLine(info);
        line.open(format);
        line.start();
        return line;
    }
}
