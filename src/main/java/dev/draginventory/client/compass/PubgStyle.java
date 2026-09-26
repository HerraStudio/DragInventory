package dev.draginventory.client.compass;

import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 绝地求生皮肤（按实机截图视觉基因重做）：
 * <ul>
 *   <li>完全无框感：仅一层极轻的整幅压暗（原作甚至没有背景），两端硬截止无渐隐</li>
 *   <li>顶部中央：大号当前度数（白色、带文字阴影、无底衬），
 *       其正下方一枚白色下指三角指向刻度带 —— PUBG 的标志性组合</li>
 *   <li>垂直层次（原作同序）：大度数 → 下指三角 → 方位字（大于数字）→
 *       刻度线 → 数字（位于刻度线正下方）→ 标点</li>
 *   <li>每 15 度一档等宽数字；方位字加粗提亮，接近中心时向主题色过渡</li>
 * </ul>
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

    /**
     * 元素边缘硬截止（原作刻度滚到两端直接被裁掉，无渐隐）。
     */
    @Override
    public float edgeFadeSpan() {
        return 0f;
    }

    // ==================== 布局 ====================
    // 垂直层次：大度数 0-12 / 三角 14-18 / 方位字 20-29 / 刻度 31-39 /
    // 数字 40-47（刻度正下方）/ 标点 52。

    @Override
    public float labelBaselineY() {
        return 30f;
    }

    @Override
    public float tickTopY() {
        return 31f;
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
        return 52f;
    }

    @Override
    public float widgetHeight() {
        // 标点文字行最低到 y+64。
        return 66f;
    }

    // ==================== 绘制 ====================

    @Override
    public void drawBackground(CompassStyleContext ctx, GuiGraphics g) {
        // 原作完全透明；这里仅在内容带（方位字~数字）铺一层极轻的整幅压暗，
        // 硬截止（无两端渐隐），保证 MC 亮色天空下仍可读。
        float x = ctx.originX, w = ctx.width;
        float y = ctx.originY + 18, h = 31;
        g.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h),
                CompassStyleContext.rgba(ctx.palette.background(), 0.14f * ctx.alpha));
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
                CompassStyleContext.vline(g, x, y, tickHeight(kind), 1.6f, rgb, alpha * 0.9f);
            }
            case MAJOR -> CompassStyleContext.vline(g, x, y, tickHeight(kind), 1f,
                    ctx.palette.tick(), alpha * 0.75f);
            default -> CompassStyleContext.vline(g, x, y, tickHeight(kind), 1f,
                    ctx.palette.tick(), alpha * 0.45f);
        }
    }

    @Override
    public void drawCardinal(CompassStyleContext ctx, Font font, GuiGraphics g,
                             float x, String label, boolean nearest, float alpha) {
        // PUBG 的 N/E/S/W 明显大于数字（原作 1.2-1.5 倍），白色带细描边感。
        float glow = nearest ? ctx.cardinalGlow : 0;
        int rgb = CompassStyleContext.blend(ctx.palette.text(), ctx.palette.accent(), glow * 0.7f);
        float scale = (float) (CompassConfig.CARDINAL_SCALE.get().doubleValue() * 1.05f * (1f + glow * 0.08f));
        float y = labelBaselineY() + ctx.skewAt(x);
        CompassPaint.centeredScaled(font, g, Component.literal(label), x, y - 10f, scale, rgb,
                Math.min(1f, alpha * (1f + glow * 0.15f)), true);
    }

    @Override
    public void drawIntercardinal(CompassStyleContext ctx, Font font, GuiGraphics g,
                                  float x, String label, float alpha) {
        var text = Component.literal(label);
        CompassPaint.centeredScaled(font, g, text, x, labelBaselineY() - 9.5f, 0.88f,
                ctx.palette.text(), alpha * 0.85f, true);
    }

    @Override
    public void drawNumber(CompassStyleContext ctx, Font font, GuiGraphics g,
                           float x, int degrees, float alpha) {
        // 原作数字在刻度线正下方（区别于其它皮肤的上方）。
        var text = Component.literal(String.valueOf(degrees))
                .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
        float y = 40f + ctx.skewAt(x);
        CompassPaint.centeredScaled(font, g, text, x, y, 0.82f, ctx.palette.dim(), alpha, true);
    }

    @Override
    public void drawCenter(CompassStyleContext ctx, Font font, GuiGraphics g) {
        float cx = ctx.centerX;
        float glow = ctx.cardinalGlow;
        // 顶部大号度数：白色、无底衬、文字阴影保证可读（原作同款）。
        int degrees = Math.round(ctx.heading) % 360;
        String number = CompassConfig.DEGREE_SYMBOL.get() ? degrees + "\u00B0" : String.valueOf(degrees);
        var digits = Component.literal(number)
                .withStyle(s -> s.withFont(com.lowdragmc.lowdraglib2.gui.LDLibFonts.JETBRAINS_MONO_BOLD));
        int rgb = glow > 0.03f
                ? CompassStyleContext.blend(0xFFFFFF, ctx.palette.accent(), glow * 0.6f)
                : 0xFFFFFF;
        CompassPaint.centeredScaled(font, g, digits, cx, ctx.originY + 0.5f, 1.2f, rgb,
                ctx.alpha, true);
        // 大度数正下方的白色下指三角（浮在刻度带上方）+ 短中线。
        int triRgb = CompassStyleContext.blend(0xFFFFFF, ctx.palette.accent(), glow * 0.5f);
        CompassPaint.triangleDown(g, cx, ctx.originY + 14f, 3.4f, 4, triRgb,
                ctx.alpha * (0.9f + glow * 0.1f));
        CompassStyleContext.vline(g, cx, ctx.originY + 18.5f, 4.5f, 1.2f, triRgb,
                ctx.alpha * (0.55f + glow * 0.2f));
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
