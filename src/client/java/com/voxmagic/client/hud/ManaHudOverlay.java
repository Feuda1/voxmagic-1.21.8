package com.voxmagic.client.hud;

import com.voxmagic.content.ModItems;
import com.voxmagic.network.NetworkInit;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public final class ManaHudOverlay {
    private static int mana = 100, max = 100, cooldown = 0; private static double regen = 5.0;
    private static long lastUpdateMs = System.currentTimeMillis();

    @SuppressWarnings("deprecation")
    public static void register() {
        HudRenderCallback.EVENT.register(ManaHudOverlay::render);
    }

    public static void onSync(NetworkInit.ManaSyncPayload p) {
        mana = p.mana; max = p.max; cooldown = p.cooldownTicks; regen = p.regenPerSec;
        lastUpdateMs = System.currentTimeMillis();
    }

    private static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        if (tickCounter == null) {
            return;
        }

        var mc = MinecraftClient.getInstance();
        if (mc == null) {
            return;
        }
        var player = mc.player;
        if (player == null) {
            return;
        }

        boolean holdingMagicBook = player.getMainHandStack().isOf(ModItems.MAGIC_BOOK)
                || player.getOffHandStack().isOf(ModItems.MAGIC_BOOK);
        if (!holdingMagicBook) return;

        if (max <= 0) {
            return;
        }

        long dt = System.currentTimeMillis() - lastUpdateMs;
        int add = (int) Math.floor((regen * dt / 1000.0));
        int pmana = Math.min(max, mana + Math.max(0, add));

        int width = 182;
        int height = 5;
        int x = (ctx.getScaledWindowWidth() - width) / 2;
        int y = ctx.getScaledWindowHeight() - 45;

        int bg = 0x66000000;
        ctx.fill(x, y, x + width, y + height, bg);
        int w = (int) (width * (pmana / (float) max));
        int color = cooldown > 0 ? 0x88AAAAAA : 0xFF3AA3FF;
        ctx.fill(x, y, x + w, y + height, color);
    }
}

