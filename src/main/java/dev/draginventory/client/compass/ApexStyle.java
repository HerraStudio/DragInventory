package dev.draginventory.client.compass;

import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Apex 英雄皮肤：高不透明度磨砂深色条带，左右两端 45 度斜切（倒角），
 * 细亮描边；条带底缘中央一枚小下指 caret，条带正下方是大号加粗的
 * 当前朝向读数。取自 Apex Legends 顶部罗盘的"重底 + 斜切端"语言。
 */
final class ApexStyle extends CompassStyle {

    /** 斜切倒角宽度（像素）。 */
    private static final float CHAMFER = 8f;

    @Override
    public String id() {
        return "apex";
    }

    @Override
    public String nameKey() {
        return "draginventory.compass.style.apex";
    }

    // ==================== 布局 ====================

    @Override
    public float labelBaselineY() {
        return 25f;
    }

    @Override
    public float tickTopY() {
        return 31f;
    }

    @Override
    public float tickHeight(TickKind kind) {
        return switch (kind) {
            case CARDINAL -> 8.5f;
            case MAJOR -> 6f;
            case MINOR -> 3.5f;
        };
    }

    @Override
    public float markerY() {
        return 56f;
    }

    @Override
    public float widgetHeight() {
        // 比其它皮肤更高：条带下方还要放大号中心读数；标点文字到 y+68。
        return 70f;
    }

    // ==================== 绘制 ====================

    @Override
    public void drawBackground(CompassStyleContext ctx, GuiGraphics g) {
        float x = ctx.originX, y = ctx.originY + 6, w = ctx.width, h = 38;
        float a = 0.80f * ctx.alpha;
        int c = CompassStyleContext.rgba(ctx.palette.background(), a);
        int ci = Math.round(CHAMFER);
        int yi = Math.round(y), hi = Math.round(h);
        // 斜切端条带 = 三段 fill：上边条与下边条左右内缩倒角量，中段全宽。
        g.fill(Math.round(x) + ci, yi, Math.round(x + w) - ci, yi + 4, c);
        g.fill(Math.round(x), yi + 4, Math.round(x + w), yi + hi - 4, c);
        g.fill(Math.round(x) + ci, yi + hi - 4, Math.round(x + w) - ci, yi + hi, c);
        // 细亮描边：沿斜切轮廓（顶/底横线 + 中段竖端线 + 两级阶梯近似斜边）。
        int edge = CompassStyleContext.rgba(ctx.palette.text(), 0.16f * ctx.alpha);
        g.fill(Math.round(x) + ci, yi, Math.round(x + w) - ci, yi + 1, edge);
        g.fill(Math.round(x) + ci, yi + hi - 1, Math.round(x + w) - ci, yi + hi, edge);
        g.fill(Math.round(x), yi + 4, Math.round(x) + 1, yi + hi - 4, edge);
        g.fill(Math.round(x + w) - 1, yi + 4, Math.round(x + w), yi + hi - 4, edge);
        // 斜边两级阶梯近似 45 度切角。
        for (int i = 0; i < 4; i++) {
            g.fill(Math.round(x) + ci - 2 * i - 2, yi + i, Math.round(x) + ci - 2 * i - 1, yi + i + 1, edge);
            g.fill(Math.round(x + w) - ci + 2 * i + 1, yi + i, Math.round(x + w) - ci + 2 * i + 2, yi + i + 1, edge);
            g.fill(Math.round(x) + ci - 2 * i - 2, yi + hi - i - 1, Math.round(x) + ci - 2 * i - 1, yi + hi - i, edge);
            g.fill(Math.round(x + w) - ci + 2 * i + 1, yi + hi - i - 1, Math.round(x + w) - ci + 2 * i + 2, yi + hi - i, edge);
        }
    }

    @Override
    public void drawTick(CompassStyleContext ctx, GuiGraphics g, float x, TickKind kind, float alpha) {
        float y = tickTopY() + ctx.skewAt(x);
        switch (kind) {
            case CARDINAL -> {
                boolean nearest = Math.abs(x - ctx.centerX) < 3f && ctx.cardinalGlow > 0.03f;
                int rgb = nearest
                        ? CompassStyleContext.blend(ctx.palette.tick(), ctx.palette.accent(), ctx.cardinalGlow)
                        : ctx.palette.tick();
                CompassPaint.glowVLine(g, x, y, tickHeight(kind), 2f, rgb,
                        alpha * 0.9f, nearest);
            }
            case MAJOR -> CompassStyleContext.vline(g, x, y, tickHeight(kind), 1.1f,
                    ctx.palette.tick(), alpha * 0.72f);
            default -> CompassStyleContext.vline(g, x, y, tickHeight(kind), 1f,
                    ctx.palette.tick(), alpha * 0.42f);
        }
    }

    @Override
    public void drawCardinal(CompassStyleContext ctx, Font font, GuiGraphics g,
                             float x, String label, boolean nearest, float alpha) {
        float glow = nearest ? ctx.cardinalGlow : 0;
        int rgb = CompassStyleContext.blend(ctx.palette.text(), ctx.palette.accent(), glow * 0.85f);
        float scale = (float) (CompassConfig.CARDINAL_SCALE.get().doubleValue() * (1f + glow * 0.12f));
        float y = labelBaselineY() + ctx.skewAt(x);
        CompassPaint.centeredScaled(font, g, Component.literal(label), x, y - 10f, scale, rgb,
                Math.min(1f, alpha * (1f + glow * 0.2f)), false);
    }

    @Override
    public void drawNumber(CompassStyleContext ctx, Font font, GuiGraphics g,
                           float x, int degrees, float alpha) {
        var text = Component.literal(String.valueOf(degrees))
                .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
        float y = labelBaselineY() + ctx.skewAt(x);
        CompassPaint.centeredScaled(font, g, text, x, y - 8f, 0.82f, ctx.palette.dim(), alpha * 0.95f, false);
    }

    @Override
    public void drawCenter(CompassStyleContext ctx, Font font, GuiGraphics g) {
        float cx = ctx.centerX;
        float glow = ctx.cardinalGlow;
        float stripBottom = ctx.originY + 44;
        // 条带底缘中央的小 caret（下指），紧贴条带。
        CompassPaint.triangleDown(g, cx, stripBottom - 5f, 3f, 4, 0xFFFFFF,
                ctx.alpha * (0.85f + glow * 0.15f));
        // 条带下方的大号读数：Apex 的招牌 —— 切角深色底衬框 + 细亮描边 + 大号白字。
        int degrees = Math.round(ctx.heading) % 360;
        String number = CompassConfig.DEGREE_SYMBOL.get() ? degrees + "\u00B0" : String.valueOf(degrees);
        var digits = Component.literal(number)
                .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
        int rgb = glow > 0.03f
                ? CompassStyleContext.blend(0xFFFFFF, ctx.palette.accent(), glow * 0.9f)
                : 0xFFFFFF;
        float digitW = font.width(digits) * 1.32f;
        float boxW = digitW + 16f;
        float boxY = stripBottom + 2f;
        float boxA = (0.85f + glow * 0.15f) * ctx.alpha;
        CompassPaint.chamferRect(g, cx - boxW / 2f, boxY, boxW, 16f, 3,
                ctx.palette.background(), boxA,
                ctx.palette.text(), (0.28f + glow * 0.3f) * ctx.alpha);
        CompassPaint.centeredScaled(font, g, digits, cx, boxY + 2f, 1.32f, rgb,
                ctx.alpha, true);
    }

    @Override
    public void drawMarker(CompassStyleContext ctx, Font font, GuiGraphics g,
                           CompassMark mark, float x, float alpha, @Nullable String text) {
        float pulse = ctx.markerPulse
                ? 1f + 0.14f * (float) Math.sin((ctx.now - mark.createdAtMillis()) / 270.0 * Math.PI * 2)
                : 1f;
        float y = markerY();
        float half = 2.7f * pulse;
        // Apex 风：形状外一圈细描边（ping 的轮廓感），核心形状分型。
        markerShape(g, mark.kind(), x, y, half + 1.6f, mark.color(), alpha * 0.4f);
        markerShape(g, mark.kind(), x, y, half, mark.color(), alpha);
        if (text != null && alpha > 0.35f) {
            var label = Component.literal(text)
                    .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
            CompassPaint.centeredScaled(font, g, label, x, y + 5.5f, 0.72f, mark.color(), alpha * 0.9f, false);
        }
    }
}
