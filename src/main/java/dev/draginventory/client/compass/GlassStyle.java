package dev.draginventory.client.compass;

import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 玻璃拟态皮肤：深色半透明面板 + 顶部高光 + 细描边，柔和圆角，
 * 中心数字带胶囊底衬。观感接近现代 OS 的浮层。
 */
final class GlassStyle extends CompassStyle {

    @Override
    public String id() {
        return "glass";
    }

    @Override
    public String nameKey() {
        return "draginventory.compass.style.glass";
    }

    @Override
    public float tickTopY() {
        return 27f;
    }

    @Override
    public void drawBackground(CompassStyleContext ctx, GuiGraphics g) {
        float x = ctx.originX, y = ctx.originY + 9, w = ctx.width, h = 32;
        // 主体：深色半透明 + 阶梯圆角。
        CompassPaint.roundedRect(g, x, y, w, h, ctx.palette.background(), 0.42f * ctx.alpha, 5);
        // 1px 细描边与顶部高光，形成玻璃的“受光面”。
        CompassPaint.roundedRect(g, x, y, w, h, ctx.palette.text(), 0.10f * ctx.alpha, 5);
        CompassStyleContext.hline(g, x + 4, x + w - 4, y + 1.5f, 1f, 0xFFFFFF, 0.16f * ctx.alpha);
        CompassStyleContext.hline(g, x + 4, x + w - 4, y + h - 2f, 1f, ctx.palette.accent(), 0.07f * ctx.alpha);
    }

    @Override
    public void drawTick(CompassStyleContext ctx, GuiGraphics g, float x, CompassStyle.TickKind kind, float alpha) {
        float top = tickTopY();
        float height = tickHeight(kind) + 1.5f;
        float y = top + ctx.skewAt(x);
        switch (kind) {
            case CARDINAL -> {
                boolean nearest = Math.abs(x - ctx.centerX) < 3f && ctx.cardinalGlow > 0.03f;
                int rgb = nearest ? MinimalStyle.blend(ctx.palette.tick(), ctx.palette.accent(), ctx.cardinalGlow * 0.9f)
                        : ctx.palette.tick();
                CompassPaint.glowVLine(g, x, y, height, 2f, rgb, alpha, nearest);
            }
            case MAJOR -> CompassStyleContext.vline(g, x, y, height, 1.2f, ctx.palette.tick(), alpha * 0.8f);
            default -> CompassStyleContext.vline(g, x, y, height, 1f, ctx.palette.tick(), alpha * 0.52f);
        }
    }

    @Override
    public void drawCardinal(CompassStyleContext ctx, Font font, GuiGraphics g,
                             float x, String label, boolean nearest, float alpha) {
        float glow = nearest ? ctx.cardinalGlow : 0;
        int rgb = MinimalStyle.blend(ctx.palette.text(), ctx.palette.accent(), glow * 0.9f);
        float scale = (float) (CompassConfig.CARDINAL_SCALE.get().doubleValue() * (1f + glow * 0.2f));
        float y = labelBaselineY() + ctx.skewAt(x);
        // 双层文字：柔和光晕 + 核心，体现玻璃通透感。
        CompassPaint.centeredScaled(font, g, Component.literal(label), x, y - 10.5f, scale,
                rgb, Math.min(1f, alpha * (1f + glow * 0.3f)), false);
    }

    @Override
    public void drawNumber(CompassStyleContext ctx, Font font, GuiGraphics g, float x, int degrees, float alpha) {
        var text = Component.literal(String.valueOf(degrees))
                .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
        float y = labelBaselineY() + ctx.skewAt(x);
        CompassPaint.centeredScaled(font, g, text, x, y - 8.2f, 0.8f, ctx.palette.dim(), alpha * 0.95f, false);
    }

    @Override
    public void drawCenter(CompassStyleContext ctx, Font font, GuiGraphics g) {
        float cx = ctx.centerX;
        float glow = ctx.cardinalGlow;
        int degrees = Math.round(ctx.heading) % 360;
        String number = CompassConfig.DEGREE_SYMBOL.get() ? degrees + "\u00B0" : String.valueOf(degrees);
        var digits = Component.literal(number)
                .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
        // 数字胶囊底衬。
        float digitW = font.width(digits);
        float pillW = digitW + 14f;
        CompassPaint.roundedRect(g, cx - pillW / 2f, ctx.originY - 1.5f, pillW, 12.5f,
                ctx.palette.background(), 0.62f * ctx.alpha, 5);
        CompassPaint.roundedRect(g, cx - pillW / 2f, ctx.originY - 1.5f, pillW, 12.5f,
                ctx.palette.accent(), 0.22f * ctx.alpha * (0.6f + glow * 0.4f), 5);
        CompassStyleContext.text(font, g, digits, cx - digitW / 2f, ctx.originY + 0.5f,
                ctx.palette.accent(), ctx.alpha, false);
        // 中心 caret：亮芯 + 光晕。
        CompassPaint.glowVLine(g, cx, 15f, 24f, 1.5f, ctx.palette.accent(),
                ctx.alpha * (0.9f + glow * 0.1f), true);
    }

    @Override
    public void drawMarker(CompassStyleContext ctx, Font font, GuiGraphics g,
                           CompassMark mark, float x, float alpha, @Nullable String dist) {
        float pulse = ctx.markerPulse
                ? 1f + 0.16f * (float) Math.sin((ctx.now - mark.createdAtMillis()) / 240.0 * Math.PI * 2)
                : 1f;
        float y = markerY();
        CompassPaint.glowDiamond(g, x, y, 2.7f * pulse, mark.color(), alpha, true);
        if (dist != null && alpha > 0.35f) {
            var text = Component.literal(dist)
                    .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
            CompassPaint.centeredScaled(font, g, text, x, y + 4.5f, 0.72f, 0xFFFFFF, alpha * 0.9f, false);
        }
    }
}
