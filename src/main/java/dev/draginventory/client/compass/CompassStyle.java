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
 * <p>当前皮肤库为五套游戏风格还原：delta（三角洲行动）/ pubg（绝地求生）/
 * apex（Apex 英雄）/ battlefield（战地）/ warzone（使命召唤）。</p>
 *
 * <p>新增皮肤：继承本类并注册到 {@link #ALL} 即可被指令 / 设置界面识别。</p>
 */
public abstract class CompassStyle {
    private static final Map<String, CompassStyle> ALL = new LinkedHashMap<>();

    static {
        register(new DeltaStyle());
        register(new PubgStyle());
        register(new ApexStyle());
        register(new BattlefieldStyle());
        register(new WarzoneStyle());
    }

    private static void register(CompassStyle style) {
        ALL.put(style.id(), style);
    }

    public static CompassStyle byId(String id) {
        CompassStyle style = ALL.get(id);
        return style != null ? style : ALL.get("delta");
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

    /**
     * 元素边缘渐隐跨度（占半宽比例，0.26 = 最外 13% 全宽渐隐）。
     * 大多数游戏（三角洲/Apex/COD/战地）在两端 10-15% 内线性渐隐；
     * PUBG 原作是硬截止（刻度直接被裁掉），覆写为 0。
     */
    public float edgeFadeSpan() {
        return 0.26f;
    }

    // ==================== 视觉钩子 ====================

    /** 半透明背景（整条）。 */
    public abstract void drawBackground(CompassStyleContext ctx, GuiGraphics g);

    /**
     * 单根刻度线。x 为局部坐标，alpha 已含边缘渐隐 / 入场 / 全局透明度。
     * degrees 为该刻度对应的罗盘角度（0-359 已归一），
     * 皮肤据此区分“字母位刻度”（45 倍数）与“数字位刻度”：
     * Apex 只在数字位画细刻度（字母本身占据全高），PUBG 在字母位画粗刻度。
     */
    public abstract void drawTick(CompassStyleContext ctx, GuiGraphics g, float x, TickKind kind,
                                   int degrees, float alpha);

    /** 基数方位字（北/东/南/西）。nearest = 当前吸附目标。 */
    public abstract void drawCardinal(CompassStyleContext ctx, Font font, GuiGraphics g,
                                      float x, String label, boolean nearest, float alpha);

    /** 次方位字（东北等）。 */
    public void drawIntercardinal(CompassStyleContext ctx, Font font, GuiGraphics g,
                                  float x, String label, float alpha) {
        CompassStyleContext.text(font, g, Component.literal(label), x - font.width(label) / 2f,
                ctx.originY + labelBaselineY(), ctx.palette.text(), alpha * 0.72f, false);
    }

    /** 角度数字。 */
    public abstract void drawNumber(CompassStyleContext ctx, Font font, GuiGraphics g,
                                     float x, int degrees, float alpha);

    /** 中心指示（caret + 当前角度数字）。 */
    public abstract void drawCenter(CompassStyleContext ctx, Font font, GuiGraphics g);

    /** 标点。text 为标点下方文字行（标签/距离，可为 null 不显示）。 */
    public abstract void drawMarker(CompassStyleContext ctx, Font font, GuiGraphics g,
                                    CompassMark mark, float x, float alpha, @Nullable String text);

    /** 前景装饰（风格化元素，绘制在所有元素之上）。 */
    public void drawForeground(CompassStyleContext ctx, GuiGraphics g) {
    }

    /**
     * 按类型分型的标点核心形状（皮肤只需决定尺寸/发光/脉冲等气质）：
     * ENEMY=菱形、LOCATION=上三角、ITEM=空心方块、DEATH=X 十字、EXTERNAL=菱形。
     * 不同类型的形状差异让玩家无需读字就能区分标点含义。
     */
    protected static void markerShape(GuiGraphics g, CompassMark.Kind kind, float cx, float cy,
                                      float half, int rgb, float alpha) {
        switch (kind) {
            case LOCATION -> CompassPaint.triangleUp(g, cx, cy, half * 1.15f,
                    Math.max(3, Math.round(half * 1.6f)), rgb, alpha);
            case ITEM -> CompassPaint.squareOutline(g, cx, cy, half * 0.95f, 1f, rgb, alpha);
            case DEATH -> CompassPaint.crossX(g, cx, cy, half * 1.1f, rgb, alpha);
            default -> CompassPaint.diamond(g, cx, cy, half, rgb, alpha);
        }
    }

    /** 刻度种类。 */
    public enum TickKind { MINOR, MAJOR, CARDINAL }
}
