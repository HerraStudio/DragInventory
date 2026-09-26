package dev.draginventory.client.compass;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 方位条皮肤（风格）。控件负责迭代刻度/标签/标点并计算位置、透明度与动画系数，
 * 皮肤只决定每一类元素“长什么样”。所有皮肤共享 {@link CompassStyleContext} 工具。
 *
 * <p>新增皮肤：继承本类并注册到 {@link #ALL} 即可被指令 / 设置界面识别。</p>
 */
public abstract class CompassStyle {
    private static final Map<String, CompassStyle> ALL = new LinkedHashMap<>();

    static {
        register(new MinimalStyle());
        register(new GlassStyle());
        register(new TacticalStyle());
        register(new NeonStyle());
    }

    private static void register(CompassStyle style) {
        ALL.put(style.id(), style);
    }

    public static CompassStyle byId(String id) {
        CompassStyle style = ALL.get(id);
        return style != null ? style : ALL.get("minimal");
    }

    /** 精确查找（不回退），指令校验未知 id 用。 */
    public static CompassStyle byIdOrNull(String id) {
        return ALL.get(id);
    }

    /** 逗号分隔的合法 id 列表（指令错误提示用）。 */
    public static String idList() {
        return String.join(", ", ALL.keySet());
    }

    public static Collection<CompassStyle> all() {
        return ALL.values();
    }

    protected CompassStyle() {}

    /** 皮肤 id（配置文件 / 指令使用）。 */
    public abstract String id();

    /** 显示名（lang key）。 */
    public abstract String nameKey();

    // ==================== 布局度量（未缩放局部坐标） ====================

    /** 标签行（方位字 + 数字）基线 y。 */
    public float labelBaselineY() {
        return 23f;
    }

    /** 刻度顶部 y（刻度向下生长）。 */
    public float tickTopY() {
        return 28f;
    }

    /** 各级刻度高度。 */
    public float tickHeight(TickKind kind) {
        return switch (kind) {
            case CARDINAL -> 9f;
            case MAJOR -> 6.5f;
            case MINOR -> 4f;
        };
    }

    /** 标点行中心 y（刻度基线下方）。 */
    public float markerY() {
        return 43f;
    }

    /** 控件总高（含标点距离文字）。 */
    public float widgetHeight() {
        return 56f;
    }

    // ==================== 视觉钩子 ====================

    /** 半透明背景（整条）。 */
    public abstract void drawBackground(CompassStyleContext ctx, GuiGraphics g);

    /** 单根刻度线。x 为局部坐标，alpha 已含边缘渐隐 / 入场 / 全局透明度。 */
    public abstract void drawTick(CompassStyleContext ctx, GuiGraphics g, float x, TickKind kind, float alpha);

    /** 基数方位字（北/东/南/西）。nearest = 当前吸附目标。 */
    public abstract void drawCardinal(CompassStyleContext ctx, Font font, GuiGraphics g,
                                      float x, String label, boolean nearest, float alpha);

    /** 次方位字（东北等）。 */
    public void drawIntercardinal(CompassStyleContext ctx, Font font, GuiGraphics g,
                                  float x, String label, float alpha) {
        CompassStyleContext.text(font, g, Component.literal(label), x - font.width(label) / 2f,
                labelBaselineY(), ctx.palette.text(), alpha * 0.72f, false);
    }

    /** 角度数字。 */
    public abstract void drawNumber(CompassStyleContext ctx, Font font, GuiGraphics g,
                                     float x, int degrees, float alpha);

    /** 中心指示（caret + 当前角度数字）。 */
    public abstract void drawCenter(CompassStyleContext ctx, Font font, GuiGraphics g);

    /** 标点。dist 可为 null（不显示距离）。 */
    public abstract void drawMarker(CompassStyleContext ctx, Font font, GuiGraphics g,
                                    CompassMark mark, float x, float alpha, @Nullable String dist);

    /** 前景装饰（边缘渐隐遮罩 / 风格化元素）。 */
    public void drawForeground(CompassStyleContext ctx, GuiGraphics g) {
        edgeFadeMask(ctx, g);
    }

    /** 默认边缘渐隐：两侧向外的暗色遮罩渐变（不依赖背景，任何皮肤可用）。 */
    protected static void edgeFadeMask(CompassStyleContext ctx, GuiGraphics g) {
        float left = ctx.originX;
        float right = ctx.originX + ctx.width;
        int segments = 14;
        float segWidth = ctx.width * 0.26f / segments;
        for (int i = 0; i < segments; i++) {
            float t = (i + 1f) / segments; // 越靠外越不透明
            int a = Math.round(t * t * 200 * ctx.alpha);
            int color = (a << 24) | (ctx.palette.background() & 0xFFFFFF);
            float lx = left + i * segWidth;
            float rx = right - (i + 1) * segWidth;
            g.fill(Math.round(lx), Math.round(ctx.originY), Math.round(lx + segWidth + 1), Math.round(ctx.originY + 38), color);
            g.fill(Math.round(rx), Math.round(ctx.originY), Math.round(rx + segWidth + 1), Math.round(ctx.originY + 38), color);
        }
    }

    /** 刻度种类。 */
    public enum TickKind { MINOR, MAJOR, CARDINAL }
}
