package dev.draginventory.client.compass;

import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 三角洲行动皮肤（默认）：高不透明度深色条带 + 两端在条带内渐隐 +
 * 上下 1px 边线，中心大号朝向数字（深色胶囊底衬）与下指小三角。
 * 观感取自《三角洲行动》顶部方位条：信息密度高、底色沉稳、中心读数突出。
 */
final class DeltaStyle extends CompassStyle {

    @Override
    public String id() {
        return "delta";
    }

    @Override
    public String nameKey() {
        return "draginventory.compass.style.delta";
    }

    // ==================== 布局 ====================

    @Override
    public float labelBaselineY() {
        return 23f;
    }

    @Override
    public float tickTopY() {
        return 29f;
    }

    @Override
    public float tickHeight(TickKind kind) {
        return switch (kind) {
            case CARDINAL -> 9f;
            case MAJOR -> 6f;
            case MINOR -> 3.5f;
        };
    }

    @Override
    public float markerY() {
        return 50f;
    }

    @Override
    public float widgetHeight() {
        // 标点文字行最低到 y+62，预留裁剪余量。
        return 64f;
    }

    // ==================== 绘制 ====================

    @Override
    public void drawBackground(CompassStyleContext ctx, GuiGraphics g) {
        float x = ctx.originX, y = ctx.originY + 8, w = ctx.width, h = 34;
        float a = 0.74f * ctx.alpha;
        // 主体：中间全强 + 两端 14% 在条带自身高度内渐隐（元素另有 edgeFade，
        // 不再需要全高遮罩——旧版全高遮罩正是"黑色哑铃"的来源）。
        float fade = w * 0.14f;
        CompassPaint.gradientH(g, x, x + fade, y, h, ctx.palette.background(), 0f, a, 12);
        g.fill(Math.round(x + fade), Math.round(y), Math.round(x + w - fade), Math.round(y + h),
                CompassStyleContext.rgba(ctx.palette.background(), a));
        CompassPaint.gradientH(g, x + w - fade, x + w, y, h, ctx.palette.background(), a, 0f, 12);
        // 上下 1px 边线（避开渐隐区）：受光上缘 + 沉稳下缘。
        float ex1 = x + fade * 0.5f, ex2 = x + w - fade * 0.5f;
        CompassStyleContext.hline(g, ex1, ex2, y + 0.5f, 1f, 0xFFFFFF, 0.13f * ctx.alpha);
        CompassStyleContext.hline(g, ex1, ex2, y + h - 1f, 1f, ctx.palette.accent(), 0.10f * ctx.alpha);
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
                CompassStyleContext.vline(g, x, y, tickHeight(kind), 2f, rgb,
                        alpha * (nearest ? 0.95f : 0.8f));
            }
            case MAJOR -> CompassStyleContext.vline(g, x, y, tickHeight(kind), 1.2f,
                    ctx.palette.tick(), alpha * 0.75f);
            default -> CompassStyleContext.vline(g, x, y, tickHeight(kind), 1f,
                    ctx.palette.tick(), alpha * 0.45f);
        }
    }

    @Override
    public void drawCardinal(CompassStyleContext ctx, Font font, GuiGraphics g,
                             float x, String label, boolean nearest, float alpha) {
        float glow = nearest ? ctx.cardinalGlow : 0;
        int rgb = CompassStyleContext.blend(ctx.palette.text(), ctx.palette.accent(), glow * 0.85f);
        float scale = (float) (CompassConfig.CARDINAL_SCALE.get().doubleValue() * (1f + glow * 0.15f));
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
        int degrees = Math.round(ctx.heading) % 360;
        String number = CompassConfig.DEGREE_SYMBOL.get() ? degrees + "\u00B0" : String.valueOf(degrees);
        var digits = Component.literal(number)
                .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
        // 中心读数胶囊：深色底 + 主题色数字，压在滚动内容之上（游戏原作同样如此）。
        float digitW = font.width(digits);
        float pillW = digitW * 1.1f + 12f;
        float pillY = ctx.originY + 9.5f;
        float pillA = (0.88f + glow * 0.12f) * ctx.alpha;
        CompassPaint.roundedRect(g, cx - pillW / 2f, pillY, pillW, 13.5f,
                ctx.palette.background(), pillA, 3);
        // 胶囊描边随吸附发光向主题色过渡。
        int edge = CompassStyleContext.rgba(
                CompassStyleContext.blend(ctx.palette.text(), ctx.palette.accent(), glow * 0.8f),
                (0.22f + glow * 0.3f) * ctx.alpha);
        int l = Math.round(cx - pillW / 2f), r = Math.round(cx + pillW / 2f);
        int t = Math.round(pillY), b = Math.round(pillY + 13.5f);
        g.fill(l + 2, t, r - 2, t + 1, edge);
        g.fill(l + 2, b - 1, r - 2, b, edge);
        g.fill(l, t + 2, l + 1, b - 2, edge);
        g.fill(r - 1, t + 2, r, b - 2, edge);
        CompassStyleContext.text(font, g, digits, cx - digitW * 1.1f / 2f, pillY + 3f,
                ctx.palette.accent(), ctx.alpha, false);
        // 胶囊下的小三角：指向刻度行，强调“这就是当前朝向”。
        CompassPaint.triangleDown(g, cx, pillY + 14.5f, 2.8f, 3,
                CompassStyleContext.blend(ctx.palette.dim(), ctx.palette.accent(), 0.4f + glow * 0.6f),
                ctx.alpha * (0.7f + glow * 0.3f));
    }

    @Override
    public void drawMarker(CompassStyleContext ctx, Font font, GuiGraphics g,
                           CompassMark mark, float x, float alpha, @Nullable String text) {
        float pulse = ctx.markerPulse
                ? 1f + 0.13f * (float) Math.sin((ctx.now - mark.createdAtMillis()) / 280.0 * Math.PI * 2)
                : 1f;
        float y = markerY();
        float half = 2.7f * pulse;
        // 三角洲风：实心形状 + 柔和外晕，敌标点加白色内芯。
        markerShape(g, mark.kind(), x, y, half + 1.8f, mark.color(), alpha * 0.22f);
        markerShape(g, mark.kind(), x, y, half, mark.color(), alpha);
        if (mark.kind() == CompassMark.Kind.ENEMY || mark.kind() == CompassMark.Kind.EXTERNAL) {
            CompassPaint.diamond(g, x, y, half * 0.4f, 0xFFFFFF, alpha * 0.85f);
        }
        if (text != null && alpha > 0.35f) {
            var label = Component.literal(text)
                    .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
            CompassPaint.centeredScaled(font, g, label, x, y + 5.5f, 0.72f, mark.color(), alpha * 0.9f, false);
        }
    }
}
