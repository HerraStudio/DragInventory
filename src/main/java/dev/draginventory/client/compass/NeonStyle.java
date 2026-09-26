package dev.draginventory.client.compass;

import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 霓虹皮肤：辉光刻度、主题色文字、脉冲中心线与“速度光刃”扫光。
 * 全部辉光由多层半透明叠加实现，不依赖额外着色器。
 */
final class NeonStyle extends CompassStyle {

    @Override
    public String id() {
        return "neon";
    }

    @Override
    public String nameKey() {
        return "draginventory.compass.style.neon";
    }

    @Override
    public float tickTopY() {
        return 27f;
    }

    @Override
    public void drawBackground(CompassStyleContext ctx, GuiGraphics g) {
        float x = ctx.originX, y = ctx.originY + 11, w = ctx.width, h = 30;
        CompassPaint.roundedRect(g, x, y, w, h, ctx.palette.background(), 0.5f * ctx.alpha, 4);
        // 上下主题色描边，上亮下暗。
        CompassStyleContext.hline(g, x + 2, x + w - 2, y + 1f, 1f, ctx.palette.accent(), 0.4f * ctx.alpha);
        CompassStyleContext.hline(g, x + 2, x + w - 2, y + h - 2f, 1f, ctx.palette.accent(), 0.16f * ctx.alpha);
        // 两侧内发光渐变。
        CompassPaint.gradientH(g, x, x + 26, y + 1, h - 2, ctx.palette.accent(), 0.10f * ctx.alpha, 0f, 10);
        CompassPaint.gradientH(g, x + w - 26, x + w, y + 1, h - 2, ctx.palette.accent(), 0f, 0.10f * ctx.alpha, 10);
    }

    @Override
    public void drawTick(CompassStyleContext ctx, GuiGraphics g, float x, CompassStyle.TickKind kind, float alpha) {
        float y = tickTopY() + ctx.skewAt(x);
        switch (kind) {
            case CARDINAL -> {
                boolean nearest = Math.abs(x - ctx.centerX) < 3f && ctx.cardinalGlow > 0.03f;
                int rgb = ctx.palette.accent();
                CompassPaint.glowVLine(g, x, y, 10f, 2f, rgb, alpha * (nearest ? 1f : 0.9f), true);
            }
            case MAJOR -> CompassPaint.glowVLine(g, x, y, 7f, 1.2f, ctx.palette.tick(), alpha * 0.8f, true);
            default -> CompassStyleContext.vline(g, x, y, 4f, 1f, ctx.palette.tick(), alpha * 0.5f);
        }
    }

    @Override
    public void drawCardinal(CompassStyleContext ctx, Font font, GuiGraphics g,
                             float x, String label, boolean nearest, float alpha) {
        float glow = nearest ? ctx.cardinalGlow : 0;
        int rgb = MinimalStyle.blend(ctx.palette.text(), ctx.palette.accent(), 0.35f + glow * 0.65f);
        float scale = (float) (CompassConfig.CARDINAL_SCALE.get().doubleValue() * (1f + glow * 0.2f));
        float y = labelBaselineY() + ctx.skewAt(x);
        // 四向淡影 + 核心 = 霓虹辉光文字。
        for (int i = 0; i < 4; i++) {
            float dx = (i % 2 == 0 ? 0.8f : -0.8f);
            float dy = (i < 2 ? 0.7f : -0.7f);
            g.pose().pushPose();
            g.pose().translate(dx, dy, 0);
            CompassPaint.centeredScaled(font, g, Component.literal(label), x, y - 10f, scale,
                    rgb, alpha * 0.16f, false);
            g.pose().popPose();
        }
        CompassPaint.centeredScaled(font, g, Component.literal(label), x, y - 10f, scale, rgb,
                Math.min(1f, alpha * (1f + glow * 0.25f)), false);
    }

    @Override
    public void drawNumber(CompassStyleContext ctx, Font font, GuiGraphics g, float x, int degrees, float alpha) {
        var text = Component.literal(String.valueOf(degrees))
                .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
        float y = labelBaselineY() + ctx.skewAt(x);
        CompassPaint.centeredScaled(font, g, text, x, y - 8f, 0.78f, ctx.palette.dim(), alpha * 0.9f, false);
    }

    @Override
    public void drawCenter(CompassStyleContext ctx, Font font, GuiGraphics g) {
        float cx = ctx.centerX;
        float glow = ctx.cardinalGlow;
        // 脉冲中心线：呼吸式辉光。
        float pulse = 0.5f + 0.5f * (float) Math.sin(ctx.now / 420.0 * Math.PI * 2);
        float caretAlpha = ctx.alpha * (0.8f + 0.2f * pulse + glow * 0.15f);
        CompassPaint.glowVLine(g, cx, 15f, 24f, 2f, ctx.palette.accent(), caretAlpha, true);
        int degrees = Math.round(ctx.heading) % 360;
        String number = CompassConfig.DEGREE_SYMBOL.get() ? degrees + "\u00B0" : String.valueOf(degrees);
        var digits = Component.literal(number)
                .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
        float digitW = font.width(digits);
        // 数字辉光底衬。
        CompassPaint.gradientH(g, cx - digitW / 2f - 8, cx - digitW / 2f, ctx.originY, 11,
                ctx.palette.accent(), 0f, 0.2f * ctx.alpha, 6);
        CompassPaint.gradientH(g, cx + digitW / 2f, cx + digitW / 2f + 8, ctx.originY, 11,
                ctx.palette.accent(), 0.2f * ctx.alpha, 0f, 6);
        CompassStyleContext.text(font, g, digits, cx - digitW / 2f, 0f, ctx.palette.accent(), ctx.alpha, false);
    }

    @Override
    public void drawMarker(CompassStyleContext ctx, Font font, GuiGraphics g,
                           CompassMark mark, float x, float alpha, @Nullable String dist) {
        float pulse = CompassConfig.MARKERS_PULSE.get()
                ? 1f + 0.2f * (float) Math.sin((ctx.now - mark.createdAtMillis()) / 220.0 * Math.PI * 2)
                : 1f;
        float y = markerY();
        CompassPaint.glowDiamond(g, x, y, 2.8f * pulse, mark.color(), alpha, true);
        CompassPaint.diamond(g, x, y, 1.1f, 0xFFFFFF, alpha * 0.95f);
        if (dist != null && alpha > 0.35f) {
            var text = Component.literal(dist)
                    .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
            CompassPaint.centeredScaled(font, g, text, x, y + 4.5f, 0.72f, mark.color(), alpha, false);
        }
    }

    @Override
    public void drawForeground(CompassStyleContext ctx, GuiGraphics g) {
        super.drawForeground(ctx, g);
        // 速度光刃：转向时一道柔光沿运动反方向掠过条带，强化“顺滑”手感。
        float speed = Math.abs(ctx.velocity);
        if (speed < 24f) return;
        float strength = Mth.clamp((speed - 24f) / 160f, 0f, 1f) * ctx.alpha;
        float dir = Math.signum(ctx.velocity);
        float sweepX = ctx.centerX - dir * (ctx.width / 2f) * (1f - 0.3f * ((ctx.now / 6f) % 16f / 16f));
        float w = 46f;
        CompassPaint.gradientH(g, sweepX - w, sweepX, ctx.originY + 12, 28,
                ctx.palette.accent(), 0f, 0.20f * strength, 8);
        CompassPaint.gradientH(g, sweepX, sweepX + w * 0.6f, ctx.originY + 12, 28,
                ctx.palette.accent(), 0.20f * strength, 0f, 6);
    }
}
