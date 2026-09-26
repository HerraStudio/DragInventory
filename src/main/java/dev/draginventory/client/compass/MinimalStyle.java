package dev.draginventory.client.compass;

import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 极简皮肤（默认）：细线条、半透明暗底、无装饰。
 * 中心角度数字使用等宽平滑字体，基数方位接近时放大提亮。
 */
final class MinimalStyle extends CompassStyle {

    @Override
    public String id() {
        return "minimal";
    }

    @Override
    public String nameKey() {
        return "draginventory.compass.style.minimal";
    }

    @Override
    public void drawBackground(CompassStyleContext ctx, GuiGraphics g) {
        // 半透明暗色条带，柔和收边；刻意压低存在感。
        CompassPaint.roundedRect(g, ctx.originX, ctx.originY + 12, ctx.width, 28,
                ctx.palette.background(), 0.24f * ctx.alpha, 3);
    }

    @Override
    public void drawTick(CompassStyleContext ctx, GuiGraphics g, float x, CompassStyle.TickKind kind, float alpha) {
        float top = tickTopY();
        float height = tickHeight(kind);
        float y = top + ctx.skewAt(x);
        switch (kind) {
            case CARDINAL -> {
                // 基数刻度：2px，接近时向主题色过渡并发光。
                boolean nearest = isNearestCardinal(ctx, x);
                int rgb = nearest ? blend(ctx.palette.tick(), ctx.palette.accent(), ctx.cardinalGlow * 0.9f)
                        : ctx.palette.tick();
                CompassPaint.glowVLine(g, x, y, height, 2f, rgb, alpha, ctx.cardinalGlow > 0.05f && nearest);
            }
            case MAJOR -> CompassStyleContext.vline(g, x, y, height, 1f, ctx.palette.tick(), alpha * 0.78f);
            default -> CompassStyleContext.vline(g, x, y, height, 1f, ctx.palette.tick(), alpha * 0.5f);
        }
    }

    @Override
    public void drawCardinal(CompassStyleContext ctx, Font font, GuiGraphics g,
                             float x, String label, boolean nearest, float alpha) {
        float glow = nearest ? ctx.cardinalGlow : 0;
        int rgb = blend(ctx.palette.text(), ctx.palette.accent(), glow * 0.85f);
        float scale = (float) (CompassConfig.CARDINAL_SCALE.get().doubleValue() * (1f + glow * 0.18f));
        float boost = alpha * (1f + glow * 0.25f);
        float y = labelBaselineY() + ctx.skewAt(x);
        CompassPaint.centeredScaled(font, g, Component.literal(label), x, y - 10f, scale, rgb,
                Math.min(1f, boost), false);
    }

    @Override
    public void drawNumber(CompassStyleContext ctx, Font font, GuiGraphics g, float x, int degrees, float alpha) {
        var text = Component.literal(String.valueOf(degrees))
                .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
        float y = labelBaselineY() + ctx.skewAt(x);
        CompassPaint.centeredScaled(font, g, text, x, y - 8f, 0.78f, ctx.palette.dim(), alpha * 0.92f, false);
    }

    @Override
    public void drawCenter(CompassStyleContext ctx, Font font, GuiGraphics g) {
        float cx = ctx.centerX;
        float glow = ctx.cardinalGlow;
        // 中心竖线：贯穿标签与刻度行，接近基数方位时更亮。
        CompassPaint.glowVLine(g, cx, 16f, 22f, 1.5f, ctx.palette.accent(),
                ctx.alpha * (0.85f + glow * 0.15f), glow > 0.1f);
        // [ 206 ]：括号弱化、数字主题色，等宽平滑字体逐位稳定。
        int degrees = Math.round(ctx.heading) % 360;
        String number = CompassConfig.DEGREE_SYMBOL.get() ? degrees + "\u00B0" : String.valueOf(degrees);
        var digits = Component.literal(number)
                .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
        float digitW = font.width(digits);
        CompassStyleContext.text(font, g, Component.literal("[ "), cx - digitW / 2f - 5f, 1f,
                ctx.palette.dim(), ctx.alpha * 0.6f, false);
        CompassStyleContext.text(font, g, digits, cx - digitW / 2f, 0f, ctx.palette.accent(), ctx.alpha, false);
        CompassStyleContext.text(font, g, Component.literal(" ]"), cx + digitW / 2f + 1f, 1f,
                ctx.palette.dim(), ctx.alpha * 0.6f, false);
    }

    @Override
    public void drawMarker(CompassStyleContext ctx, Font font, GuiGraphics g,
                           CompassMark mark, float x, float alpha, @Nullable String dist) {
        float pulse = ctx.markerPulse
                ? 1f + 0.14f * (float) Math.sin((ctx.now - mark.createdAtMillis()) / 260.0 * Math.PI * 2)
                : 1f;
        float half = 2.6f * pulse;
        float y = markerY();
        CompassPaint.diamond(g, x, y, half, mark.color(), alpha);
        CompassPaint.diamond(g, x, y, half * 0.45f, 0xFFFFFF, alpha * 0.9f);
        if (dist != null && alpha > 0.35f) {
            var text = Component.literal(dist)
                    .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
            CompassPaint.centeredScaled(font, g, text, x, y + 4.5f, 0.72f, mark.color(), alpha * 0.85f, false);
        }
    }

    private static boolean isNearestCardinal(CompassStyleContext ctx, float x) {
        return Math.abs(x - ctx.centerX) < 3f && ctx.cardinalGlow > 0.03f;
    }

    /** 颜色线性混合（RGB）。 */
    static int blend(int from, int to, float t) {
        if (t <= 0) return from;
        if (t >= 1) return to;
        int fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int r = Math.round(fr + (tr - fr) * t);
        int gg = Math.round(fg + (tg - fg) * t);
        int b = Math.round(fb + (tb - fb) * t);
        return (r << 16) | (gg << 8) | b;
    }
}
