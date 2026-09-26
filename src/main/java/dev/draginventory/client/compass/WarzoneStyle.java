package dev.draginventory.client.compass;

import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 使命召唤皮肤：圆角半透明条带，中央固定读数块——大号当前方位字
 * （八方位，如 北/东北/N/NE）+ 下方小号精确度数，细中线贯穿。
 * 取自 COD 现代战争/战区罗盘：滚动内容 + 中央"字母块"读数的组合。
 */
final class WarzoneStyle extends CompassStyle {

    @Override
    public String id() {
        return "warzone";
    }

    @Override
    public String nameKey() {
        return "draginventory.compass.style.warzone";
    }

    // ==================== 布局 ====================

    @Override
    public float labelBaselineY() {
        return 24f;
    }

    @Override
    public float tickTopY() {
        return 30f;
    }

    @Override
    public float tickHeight(TickKind kind) {
        return switch (kind) {
            case CARDINAL -> 8f;
            case MAJOR -> 5.5f;
            case MINOR -> 3.5f;
        };
    }

    @Override
    public float markerY() {
        return 51f;
    }

    @Override
    public float widgetHeight() {
        // 标点文字行最低到 y+63.5。
        return 64f;
    }

    // ==================== 绘制 ====================

    @Override
    public void drawBackground(CompassStyleContext ctx, GuiGraphics g) {
        float x = ctx.originX, y = ctx.originY + 10, w = ctx.width, h = 32;
        // 圆角条带：两端 12% 渐隐（在条带自身高度内）。
        float a = 0.55f * ctx.alpha;
        float fade = w * 0.12f;
        int l = Math.round(x + fade), r = Math.round(x + w - fade);
        int t = Math.round(y), b = Math.round(y + h);
        int c = CompassStyleContext.rgba(ctx.palette.background(), a);
        // 主体 + 2px 阶梯圆角。
        g.fill(l + 2, t, r - 2, b, c);
        g.fill(l, t + 2, l + 2, b - 2, c);
        g.fill(r - 2, t + 2, r, b - 2, c);
        CompassPaint.gradientH(g, x, x + fade, y, h, ctx.palette.background(), 0f, a, 10);
        CompassPaint.gradientH(g, x + w - fade, x + w, y, h, ctx.palette.background(), a, 0f, 10);
        // 顶底 1px 边线。
        int edge = CompassStyleContext.rgba(ctx.palette.text(), 0.12f * ctx.alpha);
        g.fill(l + 2, t, r - 2, t + 1, edge);
        g.fill(l + 2, b - 1, r - 2, b, edge);
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
                    ctx.palette.tick(), alpha * 0.45f);
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
        CompassPaint.centeredScaled(font, g, text, x, y - 8f, 0.8f, ctx.palette.dim(), alpha * 0.95f, false);
    }

    @Override
    public void drawCenter(CompassStyleContext ctx, Font font, GuiGraphics g) {
        float cx = ctx.centerX;
        float glow = ctx.cardinalGlow;
        int degrees = Math.round(ctx.heading) % 360;
        // 八方位名：0/90/180/270 用基数名，45 倍数用次方位名。
        int nearest8 = Math.floorMod(Math.round(ctx.heading / 45f) * 45, 360);
        String name = nearest8 % 90 == 0
                ? CompassWidget.cardinalName(nearest8)
                : CompassWidget.intercardinalName(nearest8);
        // 中央读数块：深色圆角底 + 1px 描边，大号方位字 + 小号度数两行。
        float blockW = 26f;
        float blockY = ctx.originY - 2f;
        float blockA = (0.86f + glow * 0.14f) * ctx.alpha;
        CompassPaint.roundedRect(g, cx - blockW / 2f, blockY, blockW, 17f,
                ctx.palette.background(), blockA, 3);
        int edge = CompassStyleContext.rgba(
                CompassStyleContext.blend(ctx.palette.text(), ctx.palette.accent(), glow * 0.8f),
                (0.25f + glow * 0.3f) * ctx.alpha);
        int l = Math.round(cx - blockW / 2f), r = Math.round(cx + blockW / 2f);
        int t = Math.round(blockY), b = Math.round(blockY + 17f);
        g.fill(l + 2, t, r - 2, t + 1, edge);
        g.fill(l + 2, b - 1, r - 2, b, edge);
        g.fill(l, t + 2, l + 1, b - 2, edge);
        g.fill(r - 1, t + 2, r, b - 2, edge);
        // 大号方位字（中文两字 / 拉丁两字母均适配）。
        CompassPaint.centeredScaled(font, g, Component.literal(name), cx, blockY + 2.5f, 1.0f,
                glow > 0.03f
                        ? CompassStyleContext.blend(0xFFFFFF, ctx.palette.accent(), glow * 0.9f)
                        : 0xFFFFFF,
                ctx.alpha, false);
        // 小号精确度数。
        String number = CompassConfig.DEGREE_SYMBOL.get() ? degrees + "\u00B0" : String.valueOf(degrees);
        var digits = Component.literal(number)
                .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
        CompassPaint.centeredScaled(font, g, digits, cx, blockY + 11.5f, 0.68f,
                ctx.palette.accent(), ctx.alpha * 0.95f, false);
        // 细中线贯穿条带（读数块下方到底边）。
        CompassStyleContext.vline(g, cx, blockY + 17.5f, 6f, 1f, ctx.palette.accent(),
                ctx.alpha * (0.4f + glow * 0.3f));
    }

    @Override
    public void drawMarker(CompassStyleContext ctx, Font font, GuiGraphics g,
                           CompassMark mark, float x, float alpha, @Nullable String text) {
        float pulse = ctx.markerPulse
                ? 1f + 0.13f * (float) Math.sin((ctx.now - mark.createdAtMillis()) / 285.0 * Math.PI * 2)
                : 1f;
        float y = markerY();
        float half = 2.6f * pulse;
        // 战区风：小实心形状 + 底部短尾线（标记钉在条带边缘的感觉）。
        markerShape(g, mark.kind(), x, y, half, mark.color(), alpha);
        CompassStyleContext.vline(g, x, y + half + 1f, 2.5f, 1f, mark.color(), alpha * 0.6f);
        if (text != null && alpha > 0.35f) {
            var label = Component.literal(text)
                    .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
            CompassPaint.centeredScaled(font, g, label, x, y + 5.5f, 0.7f, mark.color(), alpha * 0.9f, false);
        }
    }
}
