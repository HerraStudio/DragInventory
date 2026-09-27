package dev.draginventory.client.map;

import com.mojang.math.Axis;
import dev.draginventory.client.TacticalMarker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Shared tactical drawing vocabulary; icons use crisp GUI geometry. */
final class FactoryMapUI {
    static final int TEXT = 0xFFE0E9E8, DIM = 0xFF829594, GREEN = 0xFF35D8A0;
    static final int LINE = 0xFF304344, PANEL = 0xEB0B1519;
    private FactoryMapUI() {}
    static void text(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(Minecraft.getInstance().font, text, x, y, color, false);
    }
    static String tr(String key, Object... args) { return Component.translatable("draginventory.map." + key, args).getString(); }
    static void box(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color); g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color); g.fill(x + w - 1, y, x + w, y + h, color);
    }
    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL); box(g, x, y, w, h, LINE);
    }
    static int color(TacticalMarker.Type type) {
        return switch(type) { case LOCATION -> 0xFF67BBD2; case ENEMY -> 0xFFE45E62; case ITEM -> 0xFFE8AF52; };
    }
    static void glyph(GuiGraphics g, int x, int y, TacticalMarker.Type type, int color) {
        g.pose().pushPose(); g.pose().translate(x, y, 0);
        if (type == TacticalMarker.Type.LOCATION) {
            g.pose().mulPose(Axis.ZP.rotationDegrees(45));
            g.fill(-4, -4, 4, 4, 0xEF0B171D); box(g, -4, -4, 8, 8, color); g.fill(-1, -1, 1, 1, color);
        } else if (type == TacticalMarker.Type.ENEMY) {
            box(g, -5, -7, 10, 14, color); g.fill(-1, -4, 1, 1, color); g.fill(-1, 3, 1, 5, color);
        } else {
            g.fill(-6, -4, 6, 5, 0xEF211D14);
            box(g, -6, -4, 12, 9, color); box(g, -3, -6, 6, 3, color); g.fill(-1, -1, 1, 2, color);
        }
        g.pose().popPose();
    }
    static void player(GuiGraphics g, int x, int y, float yaw) {
        g.pose().pushPose(); g.pose().translate(x, y, 0);
        // +X (yaw -90) points right; +Z (yaw 0) points down.
        g.pose().mulPose(Axis.ZP.rotationDegrees(180 + yaw));
        for (int i = 0; i < 9; i++) {
            int half = i / 2; g.fill(-half, -7 + i, half + 1, -6 + i, GREEN);
        }
        g.fill(-1, 2, 2, 4, TEXT); g.pose().popPose();
    }
    static Button button(int x, int y, int w, String label, Runnable action) {
        return new Button(x, y, w, 20, Component.literal(label), ignored -> action.run(), message -> message.get()) {
            @Override protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
                boolean hover = isHoveredOrFocused();
                g.fill(getX(), getY(), getX() + width, getY() + height, hover ? 0xFF203A39 : 0xBB152226);
                box(g, getX(), getY(), width, height, hover ? GREEN : LINE);
                g.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + width / 2, getY() + 6, hover ? GREEN : TEXT);
            }
        };
    }
}
