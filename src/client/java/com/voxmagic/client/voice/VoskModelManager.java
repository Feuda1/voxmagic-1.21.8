package com.voxmagic.client.voice;

import com.voxmagic.VoxMagicMode;
import net.fabricmc.loader.api.FabricLoader;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Kernel32;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class VoskModelManager {
    public static final String RESOURCE_ZIP_PATH = "/assets/voxmagic/vosk/vosk-model-small-ru.zip";
    public static final String TARGET_DIR_NAME = "vosk-model-small-ru";
    private static final URI FALLBACK_MODEL_URI = URI.create("https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip");
    private static final String CACHED_ZIP_NAME = "vosk-model-small-ru-download.zip";

    public static Path ensureModelExtracted() throws IOException {
        Path cfgDir = FabricLoader.getInstance().getConfigDir().resolve("voxmagicmode").resolve("vosk");
        Path target = cfgDir.resolve(TARGET_DIR_NAME);
        if (Files.isDirectory(target) && Files.exists(target.resolve("am"))) {
            return ensureWindowsCompatiblePath(target);
        }
        Files.createDirectories(cfgDir);

        Path zipToUse = null;
        boolean tempZip = false;
        try (InputStream in = VoskModelManager.class.getResourceAsStream(RESOURCE_ZIP_PATH)) {
            if (in != null) {
                Path temp = Files.createTempFile("vosk-model-", ".zip");
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                zipToUse = temp;
                tempZip = true;
            }
        }

        if (zipToUse == null) {
            Path cached = cfgDir.resolve(CACHED_ZIP_NAME);
            if (Files.exists(cached) && Files.isRegularFile(cached)) {
                zipToUse = cached;
            } else {
                zipToUse = downloadModel(cfgDir);
            }
        }

        if (zipToUse != null) {
            unzip(zipToUse, cfgDir);
            if (tempZip) {
                Files.deleteIfExists(zipToUse);
            }
        }

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(cfgDir)) {
            for (Path p : ds) {
                if (Files.isDirectory(p) && p.getFileName().toString().startsWith("vosk-model-small-ru") && !p.getFileName().toString().equals(TARGET_DIR_NAME)) {
                    if (Files.exists(target)) deleteRecursively(target);
                    Files.move(p, target, StandardCopyOption.REPLACE_EXISTING);
                    break;
                }
            }
        }

        if (!Files.isDirectory(target) || Files.notExists(target.resolve("am"))) {
            throw new IOException("Vosk model missing; expected files under " + target);
        }

        return ensureWindowsCompatiblePath(target);
    }

    private static Path downloadModel(Path cfgDir) {
        try {
            VoxMagicMode.LOGGER.info("Downloading Vosk model from {}", FALLBACK_MODEL_URI);
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(FALLBACK_MODEL_URI)
                    .timeout(Duration.ofMinutes(5))
                    .GET()
                    .build();
            Path temp = Files.createTempFile("vosk-download-", ".zip");
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(temp));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                Path dest = cfgDir.resolve(CACHED_ZIP_NAME);
                Files.createDirectories(dest.getParent());
                Files.move(temp, dest, StandardCopyOption.REPLACE_EXISTING);
                VoxMagicMode.LOGGER.info("Saved Vosk model to {}", dest);
                return dest;
            }
            VoxMagicMode.LOGGER.warn("Failed to download Vosk model; HTTP status {}", response.statusCode());
            Files.deleteIfExists(temp);
        } catch (Exception e) {
            VoxMagicMode.LOGGER.warn("Could not download Vosk model from {}: {}", FALLBACK_MODEL_URI, e.toString());
        }
        return null;
    }

    private static void unzip(Path zipFile, Path destDir) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                Path outPath = destDir.resolve(e.getName()).normalize();
                if (!outPath.startsWith(destDir)) throw new IOException("Zip entry outside dest dir: " + e.getName());
                if (e.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    try (InputStream is = zip.getInputStream(e)) {
                        Files.copy(is, outPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }


    private static Path ensureWindowsCompatiblePath(Path target) throws IOException {
        if (!Platform.isWindows()) {
            return target;
        }
        if (isAsciiPath(target)) {
            return target;
        }
        String shortVariant = tryGetShortPath(target);
        if (shortVariant != null && isAsciiPath(shortVariant)) {
            VoxMagicMode.LOGGER.info("Using short path {} for Vosk model directory {}", shortVariant, target);
            return Path.of(shortVariant);
        }
        Path fallbackBase = resolveFallbackBase();
        Files.createDirectories(fallbackBase);
        Path fallbackTarget = fallbackBase.resolve(TARGET_DIR_NAME);
        if (!Files.isDirectory(fallbackTarget) || Files.notExists(fallbackTarget.resolve("am"))) {
            VoxMagicMode.LOGGER.info("Copying Vosk model to ASCII-safe location {}", fallbackTarget);
            if (Files.exists(fallbackTarget)) {
                deleteRecursively(fallbackTarget);
            }
            copyRecursively(target, fallbackTarget);
        }
        return fallbackTarget;
    }

    private static boolean isAsciiPath(Path path) {
        return isAsciiPath(path.toString());
    }

    private static boolean isAsciiPath(String path) {
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }

    private static String tryGetShortPath(Path path) {
        try {
            char[] buffer = new char[32768];
            int len = Kernel32.INSTANCE.GetShortPathName(path.toString(), buffer, buffer.length);
            if (len > 0) {
                return new String(buffer, 0, len);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Path resolveFallbackBase() {
        String programData = System.getenv("ProgramData");
        if (programData != null && !programData.isBlank()) {
            return Path.of(programData, "VoxMagicMode", "vosk");
        }
        return Path.of("C:/", "VoxMagicMode", "vosk");
    }

    private static void copyRecursively(Path source, Path dest) throws IOException {
        try (var stream = Files.walk(source)) {
            stream.forEach(path -> {
                try {
                    Path relative = source.relativize(path);
                    Path target = dest.resolve(relative);
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
    }
    private static void deleteRecursively(Path p) throws IOException {
        if (Files.notExists(p)) return;
        Files.walk(p)
                .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) {} });
    }
}




