package com.voxmagic.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.voxmagic.VoxMagicMode;
import com.voxmagic.common.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class SpellAccessManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("voxmagicmode")
            .resolve("spell_access.json");

    private static final Map<String, Boolean> GLOBAL_OVERRIDES = new HashMap<>();
    private static final Map<UUID, Map<String, Boolean>> PLAYER_OVERRIDES = new HashMap<>();

    private SpellAccessManager() {
    }

    public static void load() {
        GLOBAL_OVERRIDES.clear();
        PLAYER_OVERRIDES.clear();
        if (!Files.exists(FILE)) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(FILE)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;
            if (root.has("global") && root.get("global").isJsonObject()) {
                for (Map.Entry<String, com.google.gson.JsonElement> entry : root.getAsJsonObject("global").entrySet()) {
                    boolean enabled = entry.getValue().getAsBoolean();
                    GLOBAL_OVERRIDES.put(normalizeSpell(entry.getKey()), enabled);
                }
            }
            if (root.has("players") && root.get("players").isJsonObject()) {
                for (Map.Entry<String, com.google.gson.JsonElement> entry : root.getAsJsonObject("players").entrySet()) {
                    try {
                        UUID id = UUID.fromString(entry.getKey());
                        Map<String, Boolean> map = new HashMap<>();
                        JsonObject playerObj = entry.getValue().getAsJsonObject();
                        for (Map.Entry<String, com.google.gson.JsonElement> spell : playerObj.entrySet()) {
                            map.put(normalizeSpell(spell.getKey()), spell.getValue().getAsBoolean());
                        }
                        PLAYER_OVERRIDES.put(id, map);
                    } catch (Exception ignored) {
                        VoxMagicMode.LOGGER.warn("Skipping invalid spell_access player entry: {}", entry.getKey());
                    }
                }
            }
        } catch (Exception e) {
            VoxMagicMode.LOGGER.warn("Failed to load spell access settings", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject root = new JsonObject();
            JsonObject global = new JsonObject();
            for (Map.Entry<String, Boolean> entry : GLOBAL_OVERRIDES.entrySet()) {
                global.addProperty(entry.getKey(), entry.getValue());
            }
            root.add("global", global);
            JsonObject players = new JsonObject();
            for (Map.Entry<UUID, Map<String, Boolean>> entry : PLAYER_OVERRIDES.entrySet()) {
                JsonObject playerObj = new JsonObject();
                for (Map.Entry<String, Boolean> spell : entry.getValue().entrySet()) {
                    playerObj.addProperty(spell.getKey(), spell.getValue());
                }
                players.add(entry.getKey().toString(), playerObj);
            }
            root.add("players", players);
            try (BufferedWriter writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            VoxMagicMode.LOGGER.warn("Failed to save spell access settings", e);
        }
    }

    public static boolean isSpellEnabled(ServerPlayerEntity player, String spellId) {
        String key = normalizeSpell(spellId);
        Map<String, Boolean> perPlayer = PLAYER_OVERRIDES.get(player.getUuid());
        if (perPlayer != null) {
            Boolean value = perPlayer.get(key);
            if (value != null) {
                return value;
            }
        }
        Boolean global = GLOBAL_OVERRIDES.get(key);
        if (global != null) {
            return global;
        }
        return true;
    }

    public static void setGlobal(String spellId, boolean enabled) {
        String key = normalizeSpell(spellId);
        if (enabled) {
            GLOBAL_OVERRIDES.remove(key);
        } else {
            GLOBAL_OVERRIDES.put(key, false);
        }
        save();
    }

    public static void setPlayers(Collection<ServerPlayerEntity> players, String spellId, boolean enabled) {
        String key = normalizeSpell(spellId);
        for (ServerPlayerEntity player : players) {
            PLAYER_OVERRIDES.computeIfAbsent(player.getUuid(), id -> new HashMap<>()).put(key, enabled);
        }
        save();
    }

    public static Collection<String> getKnownSpells() {
        if (ModConfig.INSTANCE == null || ModConfig.INSTANCE.spells == null) {
            return Collections.emptyList();
        }
        return ModConfig.INSTANCE.spells.keySet();
    }

    public static Text describeStatus(ServerPlayerEntity player, String spellId) {
        boolean enabled = isSpellEnabled(player, spellId);
        return Text.literal("[VoxMagic] \u0417\u0430\u043a\u043b\u0438\u043d\u0430\u043d\u0438\u0435 '" + spellId + "' " + (enabled ? "\u0432\u043a\u043b\u044e\u0447\u0435\u043d\u043e" : "\u043e\u0442\u043a\u043b\u044e\u0447\u0435\u043d\u043e"));
    }

    private static String normalizeSpell(String spellId) {
        return spellId.toLowerCase(Locale.ROOT);
    }
}

