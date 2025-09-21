package com.voxmagic.common.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.voxmagic.VoxMagicMode;

import net.fabricmc.loader.api.FabricLoader;

public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "voxmagicmode.json";

    public static class ManaCfg {
        public int max = 100;
        public int regen_per_sec = 5;
    }

    public static class SpellCfg {
        public int mana_cost = 0;
        public Double lifetime_sec; // nullable
        public Integer cross_radius; // web
        public Integer size; // wall/dome
        public Double power; // fireball/push/range
        public Boolean blockDamage; // fireball
        public Double duration_sec; // ghost
        public Integer regen_sec; // heal
        public Integer saturation_sec; // heal
    }

    public static class VoiceCfg {
        public String mode = "always";
        public String ptt_key_default = "V";
        public List<String> hotwords = new ArrayList<>();
        public Map<String, List<String>> phrases = new HashMap<>();
        public String mic_device = ""; 
        public int sample_rate = 16000;
        public boolean debug_chat = true;
    }

    public ManaCfg mana = new ManaCfg();
    public double global_cooldown_sec = 2.0;
    public int raycast_max_distance = 30;
    public Map<String, SpellCfg> spells = new HashMap<>();
    public VoiceCfg voice = new VoiceCfg();

    public static ModConfig INSTANCE = new ModConfig();

    public static void load() {
        Path cfgDir = FabricLoader.getInstance().getConfigDir();
        Path file = cfgDir.resolve(FILE_NAME);
        if (Files.notExists(file)) {
            VoxMagicMode.LOGGER.info("Writing default config to {}", file);
            ModConfig defaults = defaults();
            try {
                Files.createDirectories(cfgDir);
                try (Writer w = Files.newBufferedWriter(file)) {
                    GSON.toJson(defaults, w);
                }
            } catch (IOException e) {
                VoxMagicMode.LOGGER.warn("Failed to write default config", e);
            }
            INSTANCE = defaults;
            return;
        }
        try (Reader r = Files.newBufferedReader(file)) {
            INSTANCE = GSON.fromJson(r, ModConfig.class);
            if (INSTANCE == null) {
                INSTANCE = defaults();
            }
        } catch (Exception e) {
            VoxMagicMode.LOGGER.error("Failed to read config; using defaults", e);
            INSTANCE = defaults();
        }

        if (INSTANCE.voice == null) {
            INSTANCE.voice = new VoiceCfg();
        }
        if (INSTANCE.voice.phrases == null) {
            INSTANCE.voice.phrases = new HashMap<>();
        }

        boolean updated = applyDefaultVoicePhrases(INSTANCE.voice.phrases, false);
        if (updated) {
            save();
        }
    }

    public static void save() {
        Path cfgDir = FabricLoader.getInstance().getConfigDir();
        Path file = cfgDir.resolve(FILE_NAME);
        try {
            Files.createDirectories(cfgDir);
            try (Writer w = Files.newBufferedWriter(file)) {
                GSON.toJson(INSTANCE, w);
            }
        } catch (IOException e) {
            VoxMagicMode.LOGGER.warn("Failed to save config", e);
        }
    }

    public static ModConfig defaults() {
        ModConfig c = new ModConfig();
        c.mana.max = 100; c.mana.regen_per_sec = 5;
        c.global_cooldown_sec = 0.5;
        c.raycast_max_distance = 30;

        SpellCfg lightning = new SpellCfg(); lightning.mana_cost = 35; c.spells.put("lightning", lightning);
        SpellCfg web = new SpellCfg(); web.mana_cost = 25; web.lifetime_sec = 3.5; web.cross_radius = 2; c.spells.put("web", web);
        SpellCfg heal = new SpellCfg(); heal.mana_cost = 30; heal.regen_sec = 3; heal.saturation_sec = 3; c.spells.put("heal", heal);
        SpellCfg ghost = new SpellCfg(); ghost.mana_cost = 40; ghost.duration_sec = 1.5; c.spells.put("ghost", ghost);
        SpellCfg wall = new SpellCfg(); wall.mana_cost = 35; wall.size = 6; wall.lifetime_sec = 4.0; c.spells.put("wall", wall);
        SpellCfg fireball = new SpellCfg(); fireball.mana_cost = 45; fireball.power = 2.5; fireball.blockDamage = false; c.spells.put("fireball", fireball);

        SpellCfg slime = new SpellCfg(); slime.mana_cost = 15; slime.lifetime_sec = 2.0; c.spells.put("slime", slime);
        SpellCfg dome = new SpellCfg(); dome.mana_cost = 40; dome.lifetime_sec = 2.0; dome.size = 3; c.spells.put("dome", dome);
        SpellCfg shockwave = new SpellCfg(); shockwave.mana_cost = 20; shockwave.power = 6.0; c.spells.put("shockwave", shockwave);
        SpellCfg levitate = new SpellCfg(); levitate.mana_cost = 25; levitate.power = 15.0; c.spells.put("levitate", levitate);
        SpellCfg meteor = new SpellCfg(); meteor.mana_cost = 50; meteor.power = 20.0; c.spells.put("meteor", meteor);
        SpellCfg push = new SpellCfg(); push.mana_cost = 10; push.power = 1.5; c.spells.put("push", push);
        SpellCfg teleport = new SpellCfg(); teleport.mana_cost = 35; teleport.power = 10.0; c.spells.put("teleport", teleport);
        SpellCfg pull = new SpellCfg(); pull.mana_cost = 25; pull.power = 5.0; c.spells.put("pull", pull);

        applyDefaultVoicePhrases(c.voice.phrases, true);
        return c;
    }

    private static boolean applyDefaultVoicePhrases(Map<String, List<String>> phrases, boolean overwrite) {
        Map<String, List<String>> defaults = new LinkedHashMap<>();
        defaults.put("lightning", Arrays.asList("\u043c\u043e\u043b\u043d\u0438\u044f", "\u043c\u043e\u043b\u043d\u0438\u0438", "lightning"));
        defaults.put("web", Arrays.asList("\u043f\u0430\u0443\u0442\u0438\u043d\u0430", "\u0441\u0435\u0442\u044c", "web"));
        defaults.put("heal", Arrays.asList("\u043b\u0435\u0447\u0435\u043d\u0438\u0435", "\u0438\u0441\u0446\u0435\u043b\u0435\u043d\u0438\u0435", "heal"));
        defaults.put("ghost", Arrays.asList("\u043f\u0440\u0438\u0437\u0440\u0430\u043a", "\u0441\u043f\u0435\u043a\u0442\u0440", "ghost"));
        defaults.put("wall", Arrays.asList("\u0441\u0442\u0435\u043d\u0430", "wall"));
        defaults.put("fireball", Arrays.asList("\u043e\u0433\u043e\u043d\u044c", "\u0444\u0430\u0435\u0440\u0431\u043e\u043b", "fireball"));
        defaults.put("slime", Arrays.asList("\u0441\u043b\u0438\u0437\u044c", "\u043f\u043b\u0430\u0442\u0444\u043e\u0440\u043c\u0430", "slime"));
        defaults.put("dome", Arrays.asList("\u043a\u0443\u043f\u043e\u043b", "\u0449\u0438\u0442", "dome"));
        defaults.put("shockwave", Arrays.asList("\u0443\u0434\u0430\u0440\u043d\u0430\u044f \u0432\u043e\u043b\u043d\u0430", "\u0448\u043e\u043a\u0432\u0435\u0439\u0432", "shockwave"));
        defaults.put("levitate", Arrays.asList("\u043b\u0435\u0432\u0438\u0442\u0430\u0446\u0438\u044f", "\u043f\u043e\u0434\u043d\u044f\u0442\u044c\u0441\u044f", "levitate"));
        defaults.put("meteor", Arrays.asList("\u043c\u0435\u0442\u0435\u043e\u0440", "\u0437\u0432\u0435\u0437\u0434\u043d\u044b\u0439 \u043f\u0430\u0434\u0435\u043d\u0438\u0435", "meteor"));
        defaults.put("push", Arrays.asList("\u0442\u043e\u043b\u0447\u043e\u043a", "\u0440\u044b\u0432\u043e\u043a", "push"));
        defaults.put("teleport", Arrays.asList("\u0442\u0435\u043b\u0435\u043f\u043e\u0440\u0442", "\u043f\u0435\u0440\u0435\u043d\u043e\u0441", "teleport"));
        defaults.put("pull", Arrays.asList("\u043f\u0440\u0438\u0442\u044f\u0436\u0435\u043d\u0438\u0435", "\u043f\u0440\u0438\u0442\u044f\u043d\u0438", "pull"));

        boolean changed = false;
        for (Map.Entry<String, List<String>> e : defaults.entrySet()) {
            List<String> existing = phrases.get(e.getKey());
            if (existing == null || overwrite || containsCorruptedText(existing)) {
                phrases.put(e.getKey(), new ArrayList<>(e.getValue()));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean containsCorruptedText(List<String> values) {
        for (String v : values) {
            if (v == null) {
                return true;
            }
            if (v.indexOf('\uFFFD') >= 0) {
                return true;
            }
        }
        return false;
    }
}
