package com.voxmagic.client.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.voxmagic.client.voice.MicSelector;
import com.voxmagic.common.config.ModConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;

import javax.sound.sampled.AudioFormat;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class MicCommand {
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("voxmic")
                .then(literal("list").executes(ctx -> {
                    var src = ctx.getSource();
                    var list = MicSelector.listMics(new AudioFormat(ModConfig.INSTANCE.voice.sample_rate, 16, 1, true, false));
                    src.sendFeedback(Text.literal("\u0414\u043e\u0441\u0442\u0443\u043f\u043d\u044b\u0435 \u043c\u0438\u043a\u0440\u043e\u0444\u043e\u043d\u044b:"));
                    for (var e : list) {
                        src.sendFeedback(Text.literal(String.format("[%d] %s | %s%s", e.index(), e.name(), e.desc(), e.supportsFormat()?"":" (\u043d\u0435 \u043f\u043e\u0434\u0434\u0435\u0440\u0436\u0438\u0432\u0430\u0435\u0442\u0441\u044f)")));
                    }
                    return 1;
                }))
                .then(literal("use").then(argument("index", IntegerArgumentType.integer(0))
                    .executes(ctx -> {
                        int idx = IntegerArgumentType.getInteger(ctx, "index");
                        var list = MicSelector.listMics(new AudioFormat(ModConfig.INSTANCE.voice.sample_rate, 16, 1, true, false));
                        if (idx < 0 || idx >= list.size()) {
                            ctx.getSource().sendError(Text.literal("\u041d\u0435\u0434\u043e\u043f\u0443\u0441\u0442\u0438\u043c\u044b\u0439 \u0438\u043d\u0434\u0435\u043a\u0441 \u043c\u0438\u043a\u0440\u043e\u0444\u043e\u043d\u0430"));
                            return 0;
                        }
                        var chosen = list.get(idx);
                        ModConfig.INSTANCE.voice.mic_device = chosen.name();
                        ModConfig.save();
                        ctx.getSource().sendFeedback(Text.literal("\u0412\u044b\u0431\u0440\u0430\u043d \u043c\u0438\u043a\u0440\u043e\u0444\u043e\u043d: " + chosen.name()));
                        return 1;
                    })))
            );
        });
    }
}
