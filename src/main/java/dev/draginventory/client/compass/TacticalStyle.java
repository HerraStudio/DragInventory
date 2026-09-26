package dev.draginventory.client.compass;

import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 战术皮肤：基准线 + 两端包角 + 等宽战术数字 + 中心下指箭头，
 * 取自《三角洲行动》一类军事 HUD 的语言，但保持细线条的克制。
 */
final class TacticalStyle extends CompassStyle {

    @Override
    public String id() {
        return "tactical";
    }

    @Override
    public String nameKey() {
        return "draginventory.compass.style.tactical";
    }

    @Override
    public float tickTopY() {
        return 27f;
    }

    @Override
    public void drawBackground(CompassStyleContext ctx, GuiGraphics g) {
        float x = ctx.originX, y = ctx.originY, w = ctx.width;
        // 基准线：刻度的“地面”。
        CompassStyleContext.hline(g, x + 1, x + w - 1, y + 37.5f, 1.5f, ctx.palette.tick(), 0.5f * ctx.alpha);
        // 两端包角（L 形），战术观感的锚点。
        float armY = y + 30f;
        int rgb = ctx.palette.accent();
        float a = 0.75f * ctx.alpha;
        // 左侧
        g.fill(Math.round(x), Math.round(armY), Math.round(x + 1.5f), Math.round(y + 38), CompassStyleContext.rgba(rgb, a));
        g.fill(Math.round(x), Math.round(armY), Math.round(x + 7f), Math.round(armY + 1.5f), CompassStyleContext.rgba(rgb, a));
        // 右侧
        g.fill(Math.round(x + w - 1.5f), Math.round(armY), Math.round(x + w), Math.round(y + 38), CompassStyleContext.rgba(rgb, a));
        g.fill(Math.round(x + w - 7f), Math.round(armY), Math.round(x + w), Math.round(armY + 1.5f), CompassStyleContext.rgba(rgb, a));
        // 极淡底衬，保证浅色环境下也可读。
        CompassPaint.roundedRect(g, x, y + 13, w, 25, ctx.palette.background(), 0.16f * ctx.alpha, 2);
    }

    @Override
    public void drawTick(CompassStyleContext ctx, GuiGraphics g, float x, CompassStyle.TickKind kind, float alpha) {
        float y = tickTopY() + ctx.skewAt(x);
        switch (kind) {
            case CARDINAL -> {
                boolean nearest = Math.abs(x - ctx.centerX) < 3f && ctx.cardinalGlow > 0.03f;
                int rgb = ctx.palette.accent();
                CompassStyleContext.vline(g, x, y, 9.5f, 2f, rgb,
                        alpha * (nearest ? 0.75f + ctx.cardinalGlow * 0.25f : 0.8f));
            }
            case MAJOR -> CompassStyleContext.vline(g, x, y, 6f, 1f, ctx.palette.tick(), alpha * 0.8f);
            default -> CompassStyleContext.vline(g, x, y, 3.5f, 1f, ctx.palette.tick(), alpha * 0.45f);
        }
    }

    @Override
    public void drawCardinal(CompassStyleContext ctx, Font font, GuiGraphics g,
                             float x, String label, boolean nearest, float alpha) {
        float glow = nearest ? ctx.cardinalGlow : 0;
        var text = Component.literal(label)
                .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
        int rgb = MinimalStyle.blend(ctx.palette.text(), ctx.palette.accent(), glow);
        float scale = (float) (CompassConfig.CARDINAL_SCALE.get().doubleValue() * (1f + glow * 0.12f));
        float y = labelBaselineY() + ctx.skewAt(x);
        CompassPaint.centeredScaled(font, g, text, x, y - 9.5f, scale, rgb, Math.min(1f, alpha * (1 + glow * 0.2f)), false);
        // 战术风：方位字下加短下划线。
        float underlineW = Math.min(14f, font.width(text) * scale);
        CompassStyleContext.hline(g, x - underlineW / 2f, x + underlineW / 2f, y + 1.5f, 1f,
                glow > 0.03f ? ctx.palette.accent() : ctx.palette.tick(), alpha * (0.5f + glow * 0.5f));
    }

    @Override
    public void drawNumber(CompassStyleContext ctx, Font font, GuiGraphics g, float x, int degrees, float alpha) {
        var text = Component.literal(String.valueOf(degrees))
                .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
        float y = labelBaselineY() + ctx.skewAt(x);
        CompassPaint.centeredScaled(font, g, text, x, y - 8f, 0.76f, ctx.palette.dim(), alpha * 0.95f, false);
    }

    @Override
    public void drawCenter(CompassStyleContext ctx, Font font, GuiGraphics g) {
        float cx = ctx.centerX;
        float glow = ctx.cardinalGlow;
        int degrees = Math.round(ctx.heading) % 360;
        String number = CompassConfig.DEGREE_SYMBOL.get() ? degrees + "\u00B0" : String.valueOf(degrees);
        var digits = Component.literal(number)
                .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
        // [ 206 ]：粗等宽括号 + 战术数字。
        float digitW = font.width(digits);
        float half = digitW / 2f;
        CompassStyleContext.text(font, g, Component.literal("["), cx - half - 6f, 0.5f,
                ctx.palette.dim(), ctx.alpha * 0.7f, false);
        CompassStyleContext.text(font, g, digits, cx - half, 0f, ctx.palette.accent(), ctx.alpha, false);
        CompassStyleContext.text(font, g, Component.literal("]"), cx + half + 1f, 0.5f,
                ctx.palette.dim(), ctx.alpha * 0.7f, false);
        // 中心下指箭头 + 底部基准线高亮。
        CompassPaint.triangleDown(g, cx, 13.5f, 3.2f, 3, ctx.palette.accent(),
                ctx.alpha * (0.9f + glow * 0.1f));
        float baseA = (0.5f + glow * 0.5f) * ctx.alpha;
        CompassStyleContext.hline(g, cx - 10f, cx + 10f, ctx.originY + 37.5f, 1.5f,
                ctx.palette.accent(), baseA);
    }

    @Override
    public void drawMarker(CompassStyleContext ctx, Font font, GuiGraphics g,
                           CompassMark mark, float x, float alpha, @Nullable String dist) {
        float pulse = CompassConfig.MARKERS_PULSE.get()
                ? 1f + 0.12f * (float) Math.sin((ctx.now - mark.createdAtMillis()) / 280.0 * Math.PI * 2)
                : 1f;
        float y = markerY();
        CompassPaint.squareOutline(g, x, y, 3.2f * pulse, 1f, mark.color(), alpha);
        g.fill(Math.round(x - 1), Math.round(y - 1), Math.round(x + 1), Math.round(y + 1),
                CompassStyleContext.rgba(mark.color(), alpha * 0.85f));
        if (dist != null && alpha > 0.35f) {
            var text = Component.literal(dist)
                    .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
            CompassPaint.centeredScaled(font, g, text, x, y + 4.5f, 0.7f, mark.color(), alpha * 0.9f, false);
        }
    }
}
