package com.voxmagic.client.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.voxmagic.common.config.ModConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class DebugCommand {
    private DebugCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("voxdebug")
                .executes(ctx -> {
                    boolean newValue = !ModConfig.INSTANCE.voice.debug_chat;
                    ModConfig.INSTANCE.voice.debug_chat = newValue;
                    ModConfig.save();
                    ctx.getSource().sendFeedback(Text.literal("\u041e\u0442\u043b\u0430\u0434\u043e\u0447\u043d\u044b\u0439 \u0447\u0430\u0442 VoxMagic: " + (newValue ? "\u0412\u041a\u041b" : "\u0412\u042b\u041a\u041b")));
                    return 1;
                })
                .then(argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
                    boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                    ModConfig.INSTANCE.voice.debug_chat = enabled;
                    ModConfig.save();
                    ctx.getSource().sendFeedback(Text.literal("\u041e\u0442\u043b\u0430\0434\043e\0447\043d\044b\0439 \u0447\u0430\u0442 VoxMagic \u0443\u0441\u0442\u0430\043d\043e\0432\043b\u0435\u043d \u0432 " + (enabled ? "\u0412\u041a\u041b" : "\u0412\u042b\u041a\u041b")));
                    return 1;
                }))
            );
        });
    }
}
