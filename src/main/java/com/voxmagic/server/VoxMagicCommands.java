package com.voxmagic.server;

import com.voxmagic.common.config.ModConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collection;
import java.util.Locale;

public final class VoxMagicCommands {
    private static final SuggestionProvider<ServerCommandSource> SPELL_SUGGESTIONS = (context, builder) ->
            CommandSource.suggestMatching(SpellAccessManager.getKnownSpells(), builder);

    private VoxMagicCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("voxmagic")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("global")
                        .then(CommandManager.argument("spell", StringArgumentType.word())
                                .suggests(SPELL_SUGGESTIONS)
                                .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> toggleGlobal(ctx, StringArgumentType.getString(ctx, "spell"), BoolArgumentType.getBool(ctx, "enabled"))))))
                .then(CommandManager.argument("targets", EntityArgumentType.players())
                        .then(CommandManager.argument("spell", StringArgumentType.word())
                                .suggests(SPELL_SUGGESTIONS)
                                .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> togglePlayers(ctx, EntityArgumentType.getPlayers(ctx, "targets"),
                                                StringArgumentType.getString(ctx, "spell"), BoolArgumentType.getBool(ctx, "enabled"))))))
                .then(CommandManager.literal("list")
                        .executes(VoxMagicCommands::listSpells))
        );
    }

    private static int toggleGlobal(CommandContext<ServerCommandSource> ctx, String spellId, boolean enabled) throws CommandSyntaxException {
        String key = spellId.toLowerCase(Locale.ROOT);
        if (!isKnownSpell(key)) {
            ctx.getSource().sendError(Text.literal("[VoxMagic] \u041d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u043e\u0435 \u0437\u0430\u043a\u043b\u0438\u043d\u0430\u043d\u0438\u0435: " + spellId));
            return 0;
        }
        SpellAccessManager.setGlobal(key, enabled);
        ctx.getSource().sendFeedback(() -> Text.literal("[VoxMagic] \u0417\u0430\u043a\u043b\u0438\u043d\u0430\u043d\u0438\u0435 '" + key + "' \u0433\u043b\u043e\u0431\u0430\u043b\u044c\u043d\u043e " + (enabled ? "\u0432\u043a\u043b\u044e\u0447\u0435\u043d\u043e" : "\u043e\u0442\u043a\u043b\u044e\u0447\u0435\u043d\u043e")), true);
        return 1;
    }

    private static int togglePlayers(CommandContext<ServerCommandSource> ctx, Collection<ServerPlayerEntity> players, String spellId, boolean enabled) throws CommandSyntaxException {
        String key = spellId.toLowerCase(Locale.ROOT);
        if (!isKnownSpell(key)) {
            ctx.getSource().sendError(Text.literal("[VoxMagic] \u041d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u043e\u0435 \u0437\u0430\u043a\u043b\u0438\u043d\u0430\u043d\u0438\u0435: " + spellId));
            return 0;
        }
        SpellAccessManager.setPlayers(players, key, enabled);
        for (ServerPlayerEntity player : players) {
            player.sendMessage(Text.literal("[VoxMagic] \u0417\u0430\u043a\u043b\u0438\u043d\u0430\u043d\u0438\u0435 '" + key + "' \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u043e \u043a\u0430\u043a " + (enabled ? "\u0432\u043a\u043b\u044e\u0447\u0435\u043d\u043e" : "\u043e\u0442\u043a\u043b\u044e\u0447\u0435\u043d\u043e") + " \u0434\u043b\u044f \u0432\u0430\u0441"), false);
        }
        ctx.getSource().sendFeedback(() -> Text.literal("[VoxMagic] \u0417\u0430\u043a\u043b\u0438\u043d\u0430\u043d\u0438\u0435 '" + key + "' \u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u043e \u0434\u043b\u044f " + players.size() + " \u0438\u0433\u0440\u043e\u043a(\u043e\u0432)"), true);
        return players.size();
    }

    private static int listSpells(CommandContext<ServerCommandSource> ctx) {
        if (ModConfig.INSTANCE.spells == null || ModConfig.INSTANCE.spells.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("[VoxMagic] \u0417\u0430\u043a\u043b\u0438\u043d\u0430\u043d\u0438\u044f \u043d\u0435 \u0437\u0430\u0440\u0435\u0433\u0438\u0441\u0442\u0440\u0438\u0440\u043e\u0432\u0430\u043d\u044b."), false);
            return 0;
        }
        String joined = String.join(", ", ModConfig.INSTANCE.spells.keySet());
        ctx.getSource().sendFeedback(() -> Text.literal("[VoxMagic] \u0417\u0430\u043a\u043b\u0438\u043d\u0430\u043d\u0438\u0435: " + joined), false);
        return ModConfig.INSTANCE.spells.size();
    }

    private static boolean isKnownSpell(String spellId) {
        return ModConfig.INSTANCE != null && ModConfig.INSTANCE.spells != null && ModConfig.INSTANCE.spells.containsKey(spellId);
    }
}

