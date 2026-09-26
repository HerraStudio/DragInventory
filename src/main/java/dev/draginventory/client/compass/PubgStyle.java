package dev.draginventory.client.compass;

import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 绝地求生皮肤：几乎透明的条带背景（轻压暗保证可读），基数方位大号加粗、
 * 每 15 度一档的等宽数字，顶部中央一枚白色下指三角标记当前朝向，
 * 条带下方一行小号暗色度数。取自 PUBG 顶部罗盘的"无框感"设计。
 */
final class PubgStyle extends CompassStyle {

    @Override
    public String id() {
        return "pubg";
    }

    @Override
    public String nameKey() {
        return "draginventory.compass.style.pubg";
    }

    // ==================== 布局 ====================

    @Override
    public float labelBaselineY() {
        return 20f;
    }

    @Override
    public float tickTopY() {
        return 26f;
    }

    @Override
    public float tickHeight(TickKind kind) {
        return switch (kind) {
            case CARDINAL -> 8f;
            case MAJOR -> 5.5f;
            case MINOR -> 3f;
        };
    }

    @Override
    public float markerY() {
        return 45f;
    }

    @Override
    public float widgetHeight() {
        // 标点文字行最低到 y+57，中心度数底衬到 y+48，预留裁剪余量。
        return 58f;
    }

    // ==================== 绘制 ====================

    @Override
    public void drawBackground(CompassStyleContext ctx, GuiGraphics g) {
        // PUBG 原作几乎无底：这里只在文字/刻度区域铺一层极轻的压暗渐变，
        // 两端完全透明，保持"浮在画面上"的无框感。
        float x = ctx.originX, w = ctx.width;
        float y = ctx.originY + 11, h = 22;
        float a = 0.20f * ctx.alpha;
        float fade = w * 0.18f;
        CompassPaint.gradientH(g, x, x + fade, y, h, ctx.palette.background(), 0f, a, 10);
        g.fill(Math.round(x + fade), Math.round(y), Math.round(x + w - fade), Math.round(y + h),
                CompassStyleContext.rgba(ctx.palette.background(), a));
        CompassPaint.gradientH(g, x + w - fade, x + w, y, h, ctx.palette.background(), a, 0f, 10);
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
                CompassStyleContext.vline(g, x, y, tickHeight(kind), 1.6f, rgb, alpha * 0.85f);
            }
            case MAJOR -> CompassStyleContext.vline(g, x, y, tickHeight(kind), 1f,
                    ctx.palette.tick(), alpha * 0.7f);
            default -> CompassStyleContext.vline(g, x, y, tickHeight(kind), 1f,
                    ctx.palette.tick(), alpha * 0.42f);
        }
    }

    @Override
    public void drawCardinal(CompassStyleContext ctx, Font font, GuiGraphics g,
                             float x, String label, boolean nearest, float alpha) {
        // PUBG 的 N/E/S/W 明显大于数字，接近时提亮。
        float glow = nearest ? ctx.cardinalGlow : 0;
        int rgb = CompassStyleContext.blend(ctx.palette.text(), ctx.palette.accent(), glow * 0.7f);
        float scale = (float) (CompassConfig.CARDINAL_SCALE.get().doubleValue() * 1.05f * (1f + glow * 0.08f));
        float y = labelBaselineY() + ctx.skewAt(x);
        CompassPaint.centeredScaled(font, g, Component.literal(label), x, y - 10f, scale, rgb,
                Math.min(1f, alpha * (1f + glow * 0.15f)), false);
    }

    @Override
    public void drawIntercardinal(CompassStyleContext ctx, Font font, GuiGraphics g,
                                  float x, String label, float alpha) {
        var text = Component.literal(label);
        CompassPaint.centeredScaled(font, g, text, x, labelBaselineY() - 9.5f, 0.88f,
                ctx.palette.text(), alpha * 0.85f, false);
    }

    @Override
    public void drawNumber(CompassStyleContext ctx, Font font, GuiGraphics g,
                           float x, int degrees, float alpha) {
        // PUBG 每 15 度一个数字，是条带的主角之一：等宽、清晰、不抢方位字。
        var text = Component.literal(String.valueOf(degrees))
                .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
        float y = labelBaselineY() + ctx.skewAt(x);
        CompassPaint.centeredScaled(font, g, text, x, y - 8f, 0.85f, ctx.palette.dim(), alpha, false);
    }

    @Override
    public void drawCenter(CompassStyleContext ctx, Font font, GuiGraphics g) {
        float cx = ctx.centerX;
        float glow = ctx.cardinalGlow;
        // PUBG 标志：顶部中央的白色下指三角（浮在条带上方），下接一段短中线。
        int rgb = CompassStyleContext.blend(0xFFFFFF, ctx.palette.accent(), glow * 0.5f);
        CompassPaint.triangleDown(g, cx, ctx.originY + 0.5f, 3.4f, 4, rgb,
                ctx.alpha * (0.9f + glow * 0.1f));
        CompassStyleContext.vline(g, cx, ctx.originY + 4.5f, 5.5f, 1.2f, rgb,
                ctx.alpha * (0.55f + glow * 0.2f));
        // 条带下方的小号精确度数（原作没有，保留是为了实用；深色底衬保证与标点交叠时可读）。
        int degrees = Math.round(ctx.heading) % 360;
        String number = CompassConfig.DEGREE_SYMBOL.get() ? degrees + "\u00B0" : String.valueOf(degrees);
        var digits = Component.literal(number)
                .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
        float digitW = font.width(digits) * 0.72f;
        CompassPaint.roundedRect(g, cx - digitW / 2f - 4f, ctx.originY + 38f, digitW + 8f, 10f,
                ctx.palette.background(), 0.6f * ctx.alpha, 2);
        CompassPaint.centeredScaled(font, g, digits, cx, ctx.originY + 40f, 0.72f,
                glow > 0.03f
                        ? CompassStyleContext.blend(ctx.palette.dim(), ctx.palette.accent(), glow)
                        : ctx.palette.dim(),
                ctx.alpha * 0.95f, false);
    }

    @Override
    public void drawMarker(CompassStyleContext ctx, Font font, GuiGraphics g,
                           CompassMark mark, float x, float alpha, @Nullable String text) {
        float pulse = ctx.markerPulse
                ? 1f + 0.12f * (float) Math.sin((ctx.now - mark.createdAtMillis()) / 300.0 * Math.PI * 2)
                : 1f;
        float y = markerY();
        float half = 2.5f * pulse;
        // PUBG 风：扁平小形状，无光晕层（原作的队友标记就是小而扁平的）。
        markerShape(g, mark.kind(), x, y, half, mark.color(), alpha);
        if (text != null && alpha > 0.35f) {
            var label = Component.literal(text)
                    .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
            CompassPaint.centeredScaled(font, g, label, x, y + 5f, 0.7f, mark.color(), alpha * 0.85f, false);
        }
    }
}
