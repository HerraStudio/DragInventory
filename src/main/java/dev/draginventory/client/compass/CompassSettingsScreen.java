package dev.draginventory.client.compass;

import com.lowdragmc.lowdraglib2.gui.LDLibFonts;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 方位条设置界面（全新像素游戏风重做）：
 * <ul>
 *   <li>原版 {@link Screen} + 纯 fill/drawString 自绘：像素切角面板、双层描边、
 *       贴合 Minecraft 画风又保留现代游戏 UI 的层次感</li>
 *   <li>六个分页签（外观/布局/显示/标点/动效/颜色），单页内容一屏放下，
 *       彻底消灭旧版"滚很久才看到选项"的长滚动列表</li>
 *   <li>顶部实时预览：与 HUD 同一渲染路径（{@link CompassWidget#renderStandalone}），
 *       支持拖拽转向 / 模拟滑杆 / 自动摆动</li>
 *   <li>动效：面板开合缩放淡入、页签切换滑动、开关滑块动画、滑杆悬停高亮、按钮按压位移</li>
 *   <li>行控件全部无状态化（渲染时读配置、交互时写配置），恢复默认后界面即时同步，
 *       不再需要旧版的静默刷新器</li>
 * </ul>
 */
public final class CompassSettingsScreen extends Screen {

    // ==================== 配色常量（Mythic 客户端风暖深灰 + 金色强调，参考图实测） ====================

    private static final int PANEL_BG = 0xF61E2326;      // #1E2326 深空灰
    private static final int PANEL_INNER = 0xFF2B3236;   // #2B3236 炭灰面板
    private static final int PANEL_BORDER = 0xFF4A555C;  // #4A555C 冷灰蓝描边
    private static final int TITLE_TEXT = 0xFFE0E6ED;    // #E0E6ED 云白标题
    private static final int LABEL_TEXT = 0xFFBDC3C7;    // #BDC3C7 正文
    private static final int VALUE_TEXT = 0xFF7F8C8D;    // #7F8C8D 暗灰次要文字
    private static final int ROW_HOVER = 0x14FFFFFF;
    private static final int CONTROL_BG = 0xFF262D33;
    private static final int CONTROL_BG_HOVER = 0xFF333B42;
    private static final int CONTROL_EDGE = 0xFF4A555C;
    private static final int TRACK_EDGE = 0xFF16191D;
    private static final int KNOB = 0xFFE0E6ED;

    /** 界面版本号（标题栏右侧）。 */
    private static final String VERSION = "v1.5.1";

    /** 行高与内边距（界面像素，2 的倍数对齐像素网格）。 */
    private static final int ROW_H = 17;
    private static final int PAD = 12;

    // ==================== 状态 ====================

    private final CompassWidget preview = new CompassWidget(true);
    private final List<List<Control>> tabs = new ArrayList<>();
    private final long openTime;

    /** 各页签的滚动偏移（内容超高时才非 0）。 */
    private final int[] tabScroll = new int[6];
    private int activeTab;
    private long tabSwitchTime;

    /** 交互目标（拖拽中的滑杆）。 */
    private SliderControl draggingSlider;
    private boolean draggingHeading;
    private boolean draggingPreviewArea;
    private float lastDragX;

    private int panelX, panelY, panelW, panelH;
    private int previewBoxY, previewBoxH;
    private int tabBarY;
    private int contentY, contentH;
    private int footerY;

    private int mouseX, mouseY;

    public static Screen create() {
        return new CompassSettingsScreen();
    }

    private CompassSettingsScreen() {
        super(Component.translatable("draginventory.compass.title"));
        this.openTime = System.currentTimeMillis();
        buildTabs();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        CompassConfig.flush();
        super.onClose();
    }

    // ==================== 布局 ====================

    @Override
    protected void init() {
        panelW = Math.min(336, this.width - 8);
        // 内容区可容纳行数决定面板高度：标题24（含副标题）+ 预览66 + 页签16 + 内容(8行页签全部单屏放下)
        // + 页脚16 + 间距。行数按 9 行预留余量，保证最大页签（显示/标点 8 行）零滚动。
        int idealH = 24 + 66 + 16 + ROW_H * 9 + 16 + 22;
        panelH = Math.min(idealH, this.height - 8);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        int y = panelY + 6;
        // 标题栏（主标题 + 副标题两行）
        y += 24;
        // 预览盒
        previewBoxY = y + 3;
        previewBoxH = 63;
        y = previewBoxY + previewBoxH;
        // 页签栏
        tabBarY = y + 3;
        y = tabBarY + 16;
        // 内容区（吃掉剩余高度，至少留出页脚）
        footerY = panelY + panelH - 20;
        contentY = y + 2;
        contentH = Math.max(ROW_H * 3, footerY - 4 - contentY);
        for (int i = 0; i < tabScroll.length; i++) {
            tabScroll[i] = clampTabScroll(i, tabScroll[i]);
        }
    }

    // ==================== 控件模型 ====================

    /** 单个行控件：渲染 + 鼠标路由，全部无状态（值实时读配置）。 */
    private abstract static class Control {
        abstract int height();

        /** 渲染行背景与标签；返回值供子类画控件。rowBottom 为内容区底边（裁剪用）。 */
        abstract void render(CompassSettingsScreen s, GuiGraphics g, int x, int w, int y, int rowBottom);

        /** 返回 true 表示事件已消费。 */
        boolean click(CompassSettingsScreen s, double mx, double my, int x, int w, int y) {
            return false;
        }

        /** 本行是否支持单独恢复默认（右侧显示 ↺ 小按钮）。 */
        boolean canReset() {
            return false;
        }

        /** 恢复本行默认值。 */
        void resetToDefault(CompassSettingsScreen s) {}

        /** 行右侧重置按钮几何（x, y, w, h）；仅 canReset 行使用。 */
        static int[] resetGeom(int x, int w, int y) {
            return new int[]{x + w - 11, y + 4, 9, 9};
        }

        boolean drag(CompassSettingsScreen s, double mx, double my, int x, int w, int y) {
            return false;
        }

        boolean scroll(CompassSettingsScreen s, double delta, double mx, double my, int x, int w, int y) {
            return false;
        }

        void release(CompassSettingsScreen s) {}

        static boolean in(double mx, double my, int x, int y, int w, int h) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /** 开关行（像素滑块开关）。 */
    private static final class BoolControl extends Control {
        private final String labelKey;
        private final ModConfigSpec.BooleanValue config;
        private long changeTime;

        BoolControl(String labelKey, ModConfigSpec.BooleanValue config) {
            this.labelKey = labelKey;
            this.config = config;
        }

        @Override
        int height() {
            return ROW_H;
        }

        @Override
        void render(CompassSettingsScreen s, GuiGraphics g, int x, int w, int y, int rowBottom) {
            if (y + ROW_H <= rowBottom) {
                s.hoverRow(g, x, w, y);
                s.drawLabel(g, labelKey, x, y);
            }
            int tw = 30, th = 13;
            int tx = x + w - tw - 2, ty = y + (ROW_H - th) / 2;
            if (ty + th > rowBottom) return;
            boolean on = config.get();
            // 开关轨道
            int track = on ? s.accent(0.85f) : CONTROL_BG;
            pixelRect(g, tx, ty, tw, th, track, s.hovered(tx, ty, tw, th) ? CONTROL_EDGE : TRACK_EDGE);
            // 滑块（90ms 位移动画）
            float t = anim(changeTime, 90);
            float knobX = on ? Mth.lerp(1f - t, tx + tw - 11, tx + 2) : Mth.lerp(t, tx + 2, tx + tw - 11);
            int kx = Math.round(knobX);
            g.fill(kx, ty + 2, kx + 9, ty + th - 2, KNOB);
            g.fill(kx, ty + 2, kx + 9, ty + 3, 0xFFFFFFFF);
            // 状态点
            g.fill(tx + 4, ty + th / 2, tx + 5, ty + th / 2 + 1, on ? 0xFFFFFFFF : 0xFF4A5262);
        }

        @Override
        boolean click(CompassSettingsScreen s, double mx, double my, int x, int w, int y) {
            int tw = 30, th = 13;
            int tx = x + w - tw - 2, ty = y + (ROW_H - th) / 2;
            if (in(mx, my, tx, ty, tw, th) || in(mx, my, x, y, w, ROW_H)) {
                CompassConfig.set(config, !config.get());
                changeTime = System.currentTimeMillis();
                return true;
            }
            return false;
        }

        @Override
        boolean canReset() {
            return true;
        }

        @Override
        void resetToDefault(CompassSettingsScreen s) {
            CompassConfig.set(config, config.getDefault());
            changeTime = System.currentTimeMillis();
        }
    }

    /** 滑杆行（整型 / 浮点共用，format 负责显示）。 */
    private static final class SliderControl extends Control {
        private final String labelKey;
        private final double min, max;
        private final boolean integer;
        private final ModConfigSpec.ConfigValue<? extends Number> config;
        private final DoubleFunction<String> format;

        SliderControl(String labelKey, ModConfigSpec.IntValue config, int min, int max,
                      DoubleFunction<String> format) {
            this(labelKey, config, min, max, true, format);
        }

        SliderControl(String labelKey, ModConfigSpec.DoubleValue config, double min, double max,
                      DoubleFunction<String> format) {
            this(labelKey, config, min, max, false, format);
        }

        private SliderControl(String labelKey, ModConfigSpec.ConfigValue<? extends Number> config,
                              double min, double max, boolean integer, DoubleFunction<String> format) {
            this.labelKey = labelKey;
            this.config = config;
            this.min = min;
            this.max = max;
            this.integer = integer;
            this.format = format;
        }

        @Override
        int height() {
            return ROW_H;
        }

        double value() {
            return config.get().doubleValue();
        }

        void setFromMouse(CompassSettingsScreen s, double mx, int x, int w) {
            int tx = trackX(s, x, w);
            int tw = trackW(s, x, w);
            double t = Mth.clamp((mx - tx) / tw, 0d, 1d);
            double v = min + (max - min) * t;
            if (integer) {
                v = Math.round(v);
                CompassConfig.set((ModConfigSpec.ConfigValue<Integer>) config, (int) v);
            } else {
                CompassConfig.set((ModConfigSpec.ConfigValue<Double>) config, v);
            }
        }

        /** 轨道几何（标签列之后、右侧数值文字前预留 46px）。 */
        private int trackX(CompassSettingsScreen s, int x, int w) {
            return s.controlX(x, w) + 2;
        }

        private int trackW(CompassSettingsScreen s, int x, int w) {
            int end = x + w - 2 - 46;
            return Math.max(24, end - trackX(s, x, w));
        }

        @Override
        void render(CompassSettingsScreen s, GuiGraphics g, int x, int w, int y, int rowBottom) {
            if (y + ROW_H <= rowBottom) {
                s.hoverRow(g, x, w, y);
                s.drawLabel(g, labelKey, x, y);
            }
            int ty = y + (ROW_H - 6) / 2 + 1;
            if (ty + 6 > rowBottom) return;
            double v = value();
            // 数值（右侧固定区，不与轨道重叠）
            String text = format.apply(v);
            g.drawString(s.font, text, x + w - 2 - s.font.width(text), y + 5, VALUE_TEXT, false);
            // 轨道
            int tx = trackX(s, x, w);
            int tw = trackW(s, x, w);
            boolean hot = s.hovered(tx, ty - 2, tw, 10) || s.draggingSlider == this;
            pixelRect(g, tx, ty, tw, 6, hot ? CONTROL_BG_HOVER : CONTROL_BG, TRACK_EDGE);
            // 填充
            int fillW = (int) Math.round(Mth.clamp((v - min) / (max - min), 0d, 1d) * (tw - 4));
            g.fill(tx + 2, ty + 1, tx + 2 + fillW, ty + 5, s.accent(0.9f));
            // 手柄（像素阶梯造型）
            float t = (float) ((v - min) / (max - min));
            int hx = Math.round(tx + 2 + t * (tw - 4) - 4);
            g.fill(hx, ty - 3, hx + 8, ty + 9, TRACK_EDGE);
            g.fill(hx + 1, ty - 2, hx + 7, ty + 8, hot ? 0xFFFFFFFF : KNOB);
            g.fill(hx + 1, ty - 2, hx + 7, ty - 1, 0xFFFFFFFF);
            g.fill(hx + 3, ty + 1, hx + 5, ty + 4, s.accent(1f));
        }

        @Override
        boolean click(CompassSettingsScreen s, double mx, double my, int x, int w, int y) {
            int cx = s.controlX(x, w);
            int cw = w - (cx - x) - 2;
            int ty = y + (ROW_H - 6) / 2 + 1;
            if (in(mx, my, cx, y, cw, ROW_H)) {
                s.draggingSlider = this;
                setFromMouse(s, mx, x, w);
                return true;
            }
            return false;
        }

        @Override
        boolean drag(CompassSettingsScreen s, double mx, double my, int x, int w, int y) {
            if (s.draggingSlider == this) {
                setFromMouse(s, mx, x, w);
                return true;
            }
            return false;
        }

        @Override
        boolean scroll(CompassSettingsScreen s, double delta, double mx, double my, int x, int w, int y) {
            int cx = s.controlX(x, w);
            int cw = w - (cx - x) - 2;
            if (!in(mx, my, cx, y, cw, ROW_H)) return false;
            double step = (max - min) / (integer ? 40d : 60d);
            if (integer) step = Math.max(1, Math.round(step));
            double v = Mth.clamp(value() + (delta > 0 ? step : -step), min, max);
            if (integer) {
                CompassConfig.set((ModConfigSpec.ConfigValue<Integer>) config, (int) Math.round(v));
            } else {
                CompassConfig.set((ModConfigSpec.ConfigValue<Double>) config, v);
            }
            return true;
        }

        @Override
        boolean canReset() {
            return true;
        }

        @Override
        void resetToDefault(CompassSettingsScreen s) {
            Number d = config.getDefault();
            if (integer) {
                CompassConfig.set((ModConfigSpec.ConfigValue<Integer>) config, d.intValue());
            } else {
                CompassConfig.set((ModConfigSpec.ConfigValue<Double>) config, d.doubleValue());
            }
        }
    }

    /** 循环选择行（皮肤 / 配色 / 步长候选）：[<] 当前值 [>]。 */
    private static final class CyclerControl extends Control {
        private final String labelKey;
        private final List<String> candidates;
        private final IntFunction<Component> namer;
        private final ModConfigSpec.ConfigValue<String> config;
        private final boolean intBacked;
        private final ModConfigSpec.IntValue intConfig;

        CyclerControl(String labelKey, ModConfigSpec.ConfigValue<String> config,
                      List<String> candidates, IntFunction<Component> namer) {
            this.labelKey = labelKey;
            this.config = config;
            this.candidates = candidates;
            this.namer = namer;
            this.intBacked = false;
            this.intConfig = null;
        }

        CyclerControl(String labelKey, ModConfigSpec.IntValue config,
                      List<String> candidates, IntFunction<Component> namer) {
            this.labelKey = labelKey;
            this.intConfig = config;
            this.candidates = candidates;
            this.namer = namer;
            this.config = null;
            this.intBacked = true;
        }

        @Override
        int height() {
            return ROW_H;
        }

        private int index() {
            String current = intBacked ? String.valueOf(intConfig.get()) : config.get();
            int i = candidates.indexOf(current);
            return i >= 0 ? i : 0;
        }

        private void apply(int index) {
            String value = candidates.get(Math.floorMod(index, candidates.size()));
            if (intBacked) {
                CompassConfig.set(intConfig, Integer.parseInt(value));
            } else {
                CompassConfig.set(config, value);
            }
        }

        @Override
        void render(CompassSettingsScreen s, GuiGraphics g, int x, int w, int y, int rowBottom) {
            if (y + ROW_H <= rowBottom) {
                s.hoverRow(g, x, w, y);
                s.drawLabel(g, labelKey, x, y);
            }
            int cx = s.controlX(x, w);
            int cw = w - (cx - x) - 2;
            int by = y + 2;
            if (by + 13 > rowBottom) return;
            int arrowW = 11;
            int midW = cw - arrowW * 2 - 4;
            // 左右箭头按钮
            s.arrowButton(g, cx, by, arrowW, 13, false);
            s.arrowButton(g, cx + arrowW + midW + 4, by, arrowW, 13, true);
            // 当前值（居中，主题色点缀）
            Component name = namer.apply(index());
            int nameColor = intBacked || labelKey.contains("style") || labelKey.contains("palette")
                    ? s.accent(1f) : TITLE_TEXT;
            int nw = s.font.width(name);
            int nx = cx + arrowW + 4 + (midW - nw) / 2;
            g.drawString(s.font, name, nx, y + 5, nameColor, false);
        }

        @Override
        boolean click(CompassSettingsScreen s, double mx, double my, int x, int w, int y) {
            int cx = s.controlX(x, w);
            int cw = w - (cx - x) - 2;
            int by = y + 2;
            int arrowW = 11;
            int midW = cw - arrowW * 2 - 4;
            if (in(mx, my, cx, by, arrowW, 13)) {
                apply(index() - 1);
                return true;
            }
            if (in(mx, my, cx + arrowW + midW + 4, by, arrowW, 13)) {
                apply(index() + 1);
                return true;
            }
            // 点击中间区域也可循环（右移）
            if (in(mx, my, cx, y, cw, ROW_H)) {
                apply(index() + 1);
                return true;
            }
            return false;
        }

        @Override
        boolean canReset() {
            return true;
        }

        @Override
        void resetToDefault(CompassSettingsScreen s) {
            if (intBacked) {
                CompassConfig.set(intConfig, intConfig.getDefault());
            } else {
                CompassConfig.set(config, config.getDefault());
            }
        }
    }

    /** 颜色覆盖行：[跟随配色] [色块]；点色块展开 H/S/L 三条微调滑杆。 */
    private static final class ColorControl extends Control {
        private final String labelKey;
        private final ModConfigSpec.IntValue config;
        private final Supplier<Integer> paletteColor;
        private boolean expanded;

        ColorControl(String labelKey, ModConfigSpec.IntValue config, Supplier<Integer> paletteColor) {
            this.labelKey = labelKey;
            this.config = config;
            this.paletteColor = paletteColor;
        }

        @Override
        int height() {
            return ROW_H + (expanded ? 3 * 12 + 2 : 0);
        }

        private int currentColor() {
            int v = config.get();
            return v >= 0 ? v : paletteColor.get();
        }

        @Override
        void render(CompassSettingsScreen s, GuiGraphics g, int x, int w, int y, int rowBottom) {
            if (y + ROW_H <= rowBottom) {
                s.hoverRow(g, x, w, y);
                s.drawLabel(g, labelKey, x, y);
                int cx = s.controlX(x, w);
                int cw = w - (cx - x) - 2;
                int by = y + 2;
                // 跟随配色按钮
                String follow = I18n.get("draginventory.compass.ui.follow_palette");
                int fw = s.font.width(follow) + 8;
                boolean followHot = s.hovered(cx, by, fw, 13);
                pixelRect(g, cx, by, fw, 13, followHot ? CONTROL_BG_HOVER : CONTROL_BG, CONTROL_EDGE);
                g.drawString(s.font, follow, cx + 4, by + 3, VALUE_TEXT, false);
                // 色块（点击展开 HSL）
                int sx = cx + fw + 6;
                int sw = cw - fw - 6;
                boolean swatchHot = s.hovered(sx, by, sw, 13);
                pixelRect(g, sx, by, sw, 13, 0xFF000000 | currentColor(), swatchHot ? 0xFFFFFFFF : CONTROL_EDGE);
                // 覆盖态角标（左上 3x3 主题色小方块提示"已覆盖"）
                if (config.get() >= 0) {
                    g.fill(sx + 2, by + 2, sx + 5, by + 5, s.accent(1f));
                }
                if (expanded) {
                    g.fill(sx + sw - 6, by + 3, sx + sw - 3, by + 4, 0xFFFFFFFF);
                    g.fill(sx + sw - 6, by + 6, sx + sw - 3, by + 7, 0xFFFFFFFF);
                    g.fill(sx + sw - 6, by + 9, sx + sw - 3, by + 10, 0xFFFFFFFF);
                }
            }
            if (expanded) {
                int subY = y + ROW_H;
                renderHsl(s, g, x + 26, w - 26, subY, rowBottom, 0, "H", 360);
                renderHsl(s, g, x + 26, w - 26, subY + 12, rowBottom, 1, "S", 100);
                renderHsl(s, g, x + 26, w - 26, subY + 24, rowBottom, 2, "L", 100);
            }
        }

        /** 渲染一条 HSL 微调滑杆（component: 0=H 1=S 2=L）。 */
        private void renderHsl(CompassSettingsScreen s, GuiGraphics g, int x, int w,
                               int y, int rowBottom, int component, String tag, int max) {
            if (y + 10 > rowBottom) return;
            float[] hsl = rgbToHsl(currentColor());
            float value = switch (component) {
                case 0 -> hsl[0] * 360f;
                case 1 -> hsl[1] * 100f;
                default -> hsl[2] * 100f;
            };
            g.drawString(s.font, tag, x, y + 1, VALUE_TEXT, false);
            int tx = x + 10;
            int tw = w - 10 - 30;
            // 轨道（色相行渲染彩虹渐变，S/L 渲染灰度/明度渐变）
            for (int i = 0; i < tw; i++) {
                float t = (float) i / Math.max(1, tw - 1);
                int rgb = switch (component) {
                    case 0 -> hslToRgb(t, Math.max(0.55f, hsl[1]), Math.max(0.5f, hsl[2]));
                    case 1 -> hslToRgb(hsl[0], t, hsl[2]);
                    default -> hslToRgb(hsl[0], 0f, t);
                };
                g.fill(tx + i, y + 2, tx + i + 1, y + 8, 0xFF000000 | rgb);
            }
            // 手柄
            int hx = Math.round(tx + (value / max) * (tw - 4));
            g.fill(hx, y, hx + 4, y + 10, TRACK_EDGE);
            g.fill(hx + 1, y + 1, hx + 3, y + 9, KNOB);
            // 数值
            String text = Math.round(value) + (component == 0 ? "\u00B0" : "");
            g.drawString(s.font, text, x + w - 28, y + 1, VALUE_TEXT, false);
        }

        @Override
        boolean click(CompassSettingsScreen s, double mx, double my, int x, int w, int y) {
            int cx = s.controlX(x, w);
            int cw = w - (cx - x) - 2;
            int by = y + 2;
            String follow = I18n.get("draginventory.compass.ui.follow_palette");
            int fw = s.font.width(follow) + 8;
            if (in(mx, my, cx, by, fw, 13)) {
                CompassConfig.set(config, -1);
                return true;
            }
            int sx = cx + fw + 6;
            int sw = cw - fw - 6;
            if (in(mx, my, sx, by, sw, 13)) {
                expanded = !expanded;
                return true;
            }
            // HSL 子滑杆交互
            if (expanded && my > y + ROW_H) {
                return hslClick(s, mx, my, x + 26, w - 26, y + ROW_H);
            }
            return false;
        }

        @Override
        boolean canReset() {
            return true;
        }

        @Override
        void resetToDefault(CompassSettingsScreen s) {
            CompassConfig.set(config, config.getDefault());
            expanded = false;
        }

        /** 命中三条 HSL 滑杆之一并按鼠标位置写色。 */
        private boolean hslClick(CompassSettingsScreen s, double mx, double my, int x, int w, int subY) {
            for (int c = 0; c < 3; c++) {
                int y = subY + c * 12;
                if (my < y || my >= y + 12) continue;
                int tx = x + 10;
                int tw = w - 10 - 30;
                float t = (float) Mth.clamp((mx - tx) / Math.max(1, tw - 4), 0d, 1d);
                float[] hsl = rgbToHsl(currentColor());
                switch (c) {
                    case 0 -> hsl[0] = t;
                    case 1 -> hsl[1] = t;
                    default -> hsl[2] = t;
                }
                CompassConfig.set(config, hslToRgb(hsl[0], hsl[1], hsl[2]));
                return true;
            }
            return false;
        }
    }

    /** 测试标点按钮行（标点页专用）。 */
    private static final class TestButtonsControl extends Control {
        @Override
        int height() {
            return ROW_H + 4;
        }

        @Override
        void render(CompassSettingsScreen s, GuiGraphics g, int x, int w, int y, int rowBottom) {
            if (y + 15 > rowBottom) return;
            String[] keys = {
                    "draginventory.compass.ui.test_enemy",
                    "draginventory.compass.ui.test_location",
                    "draginventory.compass.ui.test_item",
                    "draginventory.compass.ui.test_clear",
            };
            int gap = 4;
            int bw = (w - gap * 3) / 4;
            for (int i = 0; i < 4; i++) {
                int bx = x + i * (bw + gap);
                String text = I18n.get(keys[i]);
                boolean hot = s.hovered(bx, y + 2, bw, 14);
                pixelRect(g, bx, y + 2, bw, 14, hot ? CONTROL_BG_HOVER : CONTROL_BG, CONTROL_EDGE);
                int tw = s.font.width(text);
                g.drawString(s.font, text, bx + (bw - tw) / 2, y + 6,
                        i == 3 ? VALUE_TEXT : s.accent(1f), false);
            }
        }

        @Override
        boolean click(CompassSettingsScreen s, double mx, double my, int x, int w, int y) {
            int gap = 4;
            int bw = (w - gap * 3) / 4;
            for (int i = 0; i < 4; i++) {
                int bx = x + i * (bw + gap);
                if (in(mx, my, bx, y + 2, bw, 14)) {
                    switch (i) {
                        case 0 -> CompassCommands.spawnTestMark(CompassMark.Kind.ENEMY, 15);
                        case 1 -> CompassCommands.spawnTestMark(CompassMark.Kind.LOCATION, 0);
                        case 2 -> CompassCommands.spawnTestMark(CompassMark.Kind.ITEM, -15);
                        default -> CompassHub.clearTestMarks();
                    }
                    return true;
                }
            }
            return false;
        }
    }

    // ==================== 页签内容 ====================

    private void buildTabs() {
        List<Control> appearance = new ArrayList<>();
        appearance.add(new BoolControl("draginventory.compass.cfg.enabled", CompassConfig.ENABLED));
        appearance.add(new CyclerControl("draginventory.compass.cfg.style", CompassConfig.STYLE,
                CompassStyle.all().stream().map(CompassStyle::id).toList(),
                i -> Component.translatable(CompassStyle.all().stream().skip(i).findFirst()
                        .map(CompassStyle::nameKey).orElse(""))));
        appearance.add(new CyclerControl("draginventory.compass.cfg.palette", CompassConfig.PALETTE,
                CompassPalette.all().stream().map(CompassPalette::id).toList(),
                i -> Component.translatable("draginventory.compass.palette."
                        + CompassPalette.all().stream().skip(i).findFirst().map(CompassPalette::id).orElse(""))));
        appearance.add(new SliderControl("draginventory.compass.cfg.opacity", CompassConfig.OPACITY,
                0.15, 1.0, v -> Math.round(v * 100) + "%"));
        appearance.add(new BoolControl("draginventory.compass.cfg.degree_symbol", CompassConfig.DEGREE_SYMBOL));
        tabs.add(appearance);

        List<Control> layout = new ArrayList<>();
        layout.add(new SliderControl("draginventory.compass.cfg.offset_x", CompassConfig.OFFSET_X,
                -640, 640, v -> Math.round(v) + "px"));
        layout.add(new SliderControl("draginventory.compass.cfg.offset_y", CompassConfig.OFFSET_Y,
                -640, 640, v -> Math.round(v) + "px"));
        layout.add(new SliderControl("draginventory.compass.cfg.width", CompassConfig.BAR_WIDTH,
                120, 520, v -> Math.round(v) + "px"));
        layout.add(new SliderControl("draginventory.compass.cfg.scale", CompassConfig.SCALE,
                0.5, 2.0, v -> String.format("%.2fx", v)));
        tabs.add(layout);

        List<Control> display = new ArrayList<>();
        display.add(new SliderControl("draginventory.compass.cfg.range", CompassConfig.RANGE,
                60, 360, v -> Math.round(v) + "\u00B0"));
        display.add(new CyclerControl("draginventory.compass.cfg.minor_step", CompassConfig.MINOR_STEP,
                List.of("5", "10", "15", "20", "25", "30"), i -> Component.literal(i >= 0 && i < 6
                        ? List.of("5", "10", "15", "20", "25", "30").get(i) + "\u00B0" : "")));
        display.add(new CyclerControl("draginventory.compass.cfg.number_step", CompassConfig.NUMBER_STEP,
                List.of("15", "20", "30", "45", "60", "90"), i -> Component.literal(i >= 0 && i < 6
                        ? List.of("15", "20", "30", "45", "60", "90").get(i) + "\u00B0" : "")));
        display.add(new SliderControl("draginventory.compass.cfg.cardinal_scale", CompassConfig.CARDINAL_SCALE,
                0.8, 2.0, v -> String.format("%.2fx", v)));
        display.add(new BoolControl("draginventory.compass.cfg.show_cardinals", CompassConfig.SHOW_CARDINALS));
        display.add(new BoolControl("draginventory.compass.cfg.show_intercardinals", CompassConfig.SHOW_INTERCARDINALS));
        display.add(new BoolControl("draginventory.compass.cfg.show_numbers", CompassConfig.SHOW_NUMBERS));
        display.add(new BoolControl("draginventory.compass.cfg.cjk_labels", CompassConfig.CJK_LABELS));
        tabs.add(display);

        List<Control> markers = new ArrayList<>();
        markers.add(new BoolControl("draginventory.compass.cfg.markers_enabled", CompassConfig.MARKERS_ENABLED));
        markers.add(new BoolControl("draginventory.compass.cfg.markers_tactical", CompassConfig.MARKERS_TACTICAL));
        markers.add(new BoolControl("draginventory.compass.cfg.markers_death", CompassConfig.MARKERS_DEATH));
        markers.add(new BoolControl("draginventory.compass.cfg.markers_distance", CompassConfig.MARKERS_DISTANCE));
        markers.add(new BoolControl("draginventory.compass.cfg.markers_labels", CompassConfig.MARKERS_LABELS));
        markers.add(new BoolControl("draginventory.compass.cfg.markers_pulse", CompassConfig.MARKERS_PULSE));
        markers.add(new BoolControl("draginventory.compass.cfg.markers_test", CompassConfig.MARKERS_TEST));
        markers.add(new TestButtonsControl());
        tabs.add(markers);

        List<Control> motion = new ArrayList<>();
        motion.add(new SliderControl("draginventory.compass.cfg.smoothness", CompassConfig.SMOOTHNESS,
                CompassHeading.OMEGA_MIN, CompassHeading.OMEGA_MAX, v -> String.format("%.0f", v)));
        motion.add(new BoolControl("draginventory.compass.cfg.snap_assist", CompassConfig.SNAP_ASSIST));
        motion.add(new SliderControl("draginventory.compass.cfg.snap_range", CompassConfig.SNAP_RANGE,
                2, 20, v -> Math.round(v) + "\u00B0"));
        motion.add(new BoolControl("draginventory.compass.cfg.inertia_tilt", CompassConfig.INERTIA_TILT));
        motion.add(new SliderControl("draginventory.compass.cfg.tilt_intensity", CompassConfig.TILT_INTENSITY,
                0.0, 3.0, v -> String.format("%.1f", v)));
        motion.add(new BoolControl("draginventory.compass.cfg.entry_animation", CompassConfig.ENTRY_ANIMATION));
        motion.add(new BoolControl("draginventory.compass.cfg.hide_debug", CompassConfig.HIDE_WITH_DEBUG));
        tabs.add(motion);

        List<Control> colors = new ArrayList<>();
        colors.add(new ColorControl("draginventory.compass.cfg.color_accent", CompassConfig.COLOR_ACCENT,
                () -> CompassWidget.currentPalette().accent()));
        colors.add(new ColorControl("draginventory.compass.cfg.color_text", CompassConfig.COLOR_TEXT,
                () -> CompassWidget.currentPalette().text()));
        colors.add(new ColorControl("draginventory.compass.cfg.color_dim", CompassConfig.COLOR_DIM,
                () -> CompassWidget.currentPalette().dim()));
        colors.add(new ColorControl("draginventory.compass.cfg.color_tick", CompassConfig.COLOR_TICK,
                () -> CompassWidget.currentPalette().tick()));
        colors.add(new ColorControl("draginventory.compass.cfg.color_background", CompassConfig.COLOR_BACKGROUND,
                () -> CompassWidget.currentPalette().background()));
        tabs.add(colors);
    }

    private static final String[] TAB_KEYS = {
            "draginventory.compass.ui.tab.appearance",
            "draginventory.compass.ui.tab.layout",
            "draginventory.compass.ui.tab.display",
            "draginventory.compass.ui.tab.markers",
            "draginventory.compass.ui.tab.motion",
            "draginventory.compass.ui.tab.colors",
    };

    // ==================== 渲染 ====================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        super.render(g, mouseX, mouseY, partialTick);

        // 开场动画：150ms 缩放 + 淡入（ease-out）。
        float openT = anim(openTime, 150);
        float scale = 0.95f + 0.05f * openT;
        float alpha = openT;
        if (alpha < 0.999f) {
            g.pose().pushPose();
            float cx = panelX + panelW / 2f;
            float cy = panelY + panelH / 2f;
            g.pose().translate(cx, cy, 0);
            g.pose().scale(scale, scale, 1);
            g.pose().translate(-cx, -cy, 0);
        }
        try {
            renderPanel(g, alpha);
        } finally {
            if (alpha < 0.999f) {
                g.pose().popPose();
            }
        }
    }

    private void renderPanel(GuiGraphics g, float alpha) {
        // 面板主体（像素切角 + 双层描边）
        pixelPanel(g, panelX, panelY, panelW, panelH, alpha);

        // ---- 标题栏（Mythic 风：金色竖线 + 主标题 + 副标题）----
        int barX = panelX + 9;
        g.fill(barX, panelY + 6, barX + 3, panelY + 21, accent(alpha));
        String title = I18n.get("draginventory.compass.title");
        g.drawString(font, title, barX + 6, panelY + 6, TITLE_TEXT, true);
        g.drawString(font, VERSION, panelX + panelW - PAD - font.width(VERSION), panelY + 6,
                VALUE_TEXT, false);
        String subtitle = I18n.get("draginventory.compass.ui.subtitle");
        g.drawString(font, subtitle, barX + 6, panelY + 16, withAlpha(VALUE_TEXT, alpha), false);
        g.fill(panelX + 8, panelY + 27, panelX + panelW - 8, panelY + 28,
                withAlpha(PANEL_BORDER, alpha));

        // ---- 预览盒 ----
        renderPreviewBox(g, alpha);

        // ---- 页签栏 ----
        renderTabBar(g, alpha);

        // ---- 内容区（带 60ms 上滑 + 淡入切换动画） ----
        float tabT = anim(tabSwitchTime, 110);
        int slide = Math.round((1f - tabT) * 4f);
        g.pose().pushPose();
        g.pose().translate(0, slide, 0);
        renderContent(g, alpha * tabT);
        g.pose().popPose();

        // ---- 页脚 ----
        renderFooter(g, alpha);
    }

    private void renderPreviewBox(GuiGraphics g, float alpha) {
        int x = panelX + 8, w = panelW - 16;
        pixelPanel(g, x, previewBoxY, w, previewBoxH, alpha * 0.9f, true);
        int innerW = Math.min(CompassConfig.BAR_WIDTH.get(), w - 20);
        int px = x + (w - innerW) / 2;
        int py = previewBoxY + 4;
        // 方位条实时预览（与 HUD 同一渲染路径）。
        // 皮肤过高时整体等比缩小适配预览盒（apex 的 70px 高皮肤在 63px 盒内 ~0.86x）。
        float styleH = Mth.clamp(CompassWidget.currentStyle().widgetHeight(), 40f, 72f);
        float fit = Math.min(1f, (previewBoxH - 10) / styleH);
        if (fit < 0.999f) {
            g.pose().pushPose();
            g.pose().translate(px + innerW / 2f, py, 0);
            g.pose().scale(fit, fit, 1);
            g.pose().translate(-(px + innerW / 2f), -py, 0);
            preview.renderStandalone(g, LDLibFonts.font(), px, py, innerW, System.currentTimeMillis());
            g.pose().popPose();
        } else {
            preview.renderStandalone(g, LDLibFonts.font(), px, py, innerW, System.currentTimeMillis());
        }
        // 拖拽提示（右下角）
        String hint = I18n.get("draginventory.compass.ui.drag_hint");
        g.drawString(font, hint, x + w - font.width(hint) - 3, previewBoxY + previewBoxH - 11,
                withAlpha(VALUE_TEXT, alpha * 0.7f), false);
        // 自动摆动开关（左下）
        String sway = I18n.get("draginventory.compass.ui.sway_short");
        int sw = font.width(sway) + 8;
        int sx = x + 4, sy = previewBoxY + previewBoxH - 13;
        boolean swayOn = previewSway;
        boolean swayHot = hovered(sx, sy, sw, 12);
        pixelRect(g, sx, sy, sw, 12, swayOn ? accent(0.55f) : (swayHot ? CONTROL_BG_HOVER : CONTROL_BG),
                swayOn ? accent(1f) : CONTROL_EDGE);
        g.drawString(font, sway, sx + 4, sy + 2, swayOn ? 0xFFFFFFFF : VALUE_TEXT, false);
        // 朝向模拟滑杆（底部中央，避开摆动按钮与提示文字）
        int[] hs = headingSliderGeom();
        if (hs != null) {
            renderHeadingSlider(g, hs[0], previewBoxY + previewBoxH - 11, hs[1], 7);
        }
    }

    /** 朝向模拟滑杆几何（x, w）；空间不足返回 null。三处（渲染/点击/拖拽）共用。 */
    private int[] headingSliderGeom() {
        String hint = I18n.get("draginventory.compass.ui.drag_hint");
        String sway = I18n.get("draginventory.compass.ui.sway_short");
        int x = panelX + 8 + 4 + font.width(sway) + 8 + 8;
        int w = (panelW - 16) - (font.width(sway) + 8) - 8 - 8 - font.width(hint) - 10;
        return w > 60 ? new int[]{x, w} : null;
    }

    private void renderHeadingSlider(GuiGraphics g, int x, int y, int w, int h) {
        float t = Mth.clamp(previewHeading / 360f, 0f, 1f);
        boolean hot = hovered(x, y - 2, w, h + 4) || draggingHeading;
        pixelRect(g, x, y, w, h, hot ? CONTROL_BG_HOVER : CONTROL_BG, TRACK_EDGE);
        int fw = Math.round(t * (w - 4));
        g.fill(x + 2, y + 1, x + 2 + fw, y + h - 1, accent(0.9f));
        int hx = Math.round(x + 2 + t * (w - 4) - 2);
        g.fill(hx, y - 2, hx + 5, y + h + 2, TRACK_EDGE);
        g.fill(hx + 1, y - 1, hx + 4, y + h + 1, hot ? 0xFFFFFFFF : KNOB);
    }

    private void renderTabBar(GuiGraphics g, float alpha) {
        int x = panelX + 8, w = panelW - 16;
        int tabW = (w - 5 * 2) / 6;
        for (int i = 0; i < 6; i++) {
            int tx = x + i * (tabW + 2);
            boolean active = i == activeTab;
            boolean hot = hovered(tx, tabBarY, tabW, 14);
            // Mythic 页签：切角描边盒。选中 = 金框金字，未选 = 灰框灰字。
            if (active) {
                pixelRect(g, tx, tabBarY, tabW, 14, withAlpha(accent(1f), 0.10f * alpha), accent(1f));
            } else {
                pixelRect(g, tx, tabBarY, tabW, 14, hot ? withAlpha(PANEL_INNER, alpha) : 0, CONTROL_EDGE);
            }
            String name = I18n.get(TAB_KEYS[i]);
            int nw = font.width(name);
            g.drawString(font, name, tx + (tabW - nw) / 2, tabBarY + 3,
                    active ? accent(1f) : VALUE_TEXT, false);
            // 活动页签底部金线（Mythic 选中态）
            if (active) {
                g.fill(tx + 1, tabBarY + 13, tx + tabW - 1, tabBarY + 14, accent(1f));
            }
        }
    }

    private void renderContent(GuiGraphics g, float alpha) {
        int x = panelX + 8, w = panelW - 16;
        int rowBottom = contentY + contentH;
        int y = contentY - tabScroll[activeTab];
        for (Control control : tabs.get(activeTab)) {
            int h = control.height();
            // 行整体在可视区外则跳过（上方滚出 / 下方未入）。
            if (y + h > contentY && y < rowBottom) {
                if (control.canReset() && h == ROW_H && y + ROW_H <= rowBottom) {
                    // 可重置行：右侧预留 13px 给 ↺ 按钮，控件宽度相应收窄。
                    control.render(this, g, x, w - 13, y, rowBottom);
                    int[] rg = Control.resetGeom(x, w, y);
                    drawResetButton(g, rg[0], rg[1], alpha);
                } else {
                    control.render(this, g, x, w, y, rowBottom);
                }
            }
            y += h;
        }
        // 内容超高时的滚动条提示（右侧 2px 细条）。
        int total = contentHeight(activeTab);
        if (total > contentH) {
            int barH = Math.max(12, contentH * contentH / total);
            int barY = contentY + (contentH - barH) * tabScroll[activeTab] / (total - contentH);
            g.fill(panelX + panelW - 6, barY, panelX + panelW - 5, barY + barH,
                    withAlpha(accent(1f), 0.5f * alpha));
        }
    }

    /** 行级重置按钮（Mythic 参考图的 ↺：小方块 + 左向回退箭头，悬停变金）。 */
    private void drawResetButton(GuiGraphics g, int x, int y, float alpha) {
        boolean hot = hovered(x - 1, y - 1, 11, 11);
        int c = hot ? accent(1f) : withAlpha(VALUE_TEXT, alpha);
        // 小方块底
        pixelRect(g, x - 1, y - 1, 11, 11, hot ? withAlpha(accent(1f), 0.12f) : 0,
                hot ? accent(1f) : CONTROL_EDGE);
        // 回退箭头 "<" + 尾杠
        int cx = x + 4, cy = y + 4;
        g.fill(cx - 1, cy - 3, cx, cy - 2, c);
        g.fill(cx - 2, cy - 2, cx - 1, cy - 1, c);
        g.fill(cx - 3, cy - 1, cx - 2, cy + 1, c);
        g.fill(cx - 2, cy + 1, cx - 1, cy + 2, c);
        g.fill(cx - 1, cy + 2, cx, cy + 3, c);
        g.fill(cx, cy, cx + 5, cy + 1, c);
    }

    private void renderFooter(GuiGraphics g, float alpha) {
        int x = panelX + 8, w = panelW - 16;
        g.fill(x, footerY - 3, x + w, footerY - 2, withAlpha(PANEL_BORDER, alpha * 0.7f));
        // 左侧返回按钮（Mythic 页脚同款）
        String back = I18n.get("draginventory.compass.ui.back");
        int bw = font.width(back) + 12;
        boolean bHot = hovered(x, footerY, bw, 14);
        pixelRect(g, x, footerY, bw, 14, bHot ? CONTROL_BG_HOVER : CONTROL_BG, CONTROL_EDGE);
        g.drawString(font, back, x + 6, footerY + 3, bHot ? TITLE_TEXT : LABEL_TEXT, false);
        // 中间提示
        String hint = "/com \u00B7 /compass \u00B7 Esc";
        int hw = font.width(hint);
        g.drawString(font, hint, x + (w - hw) / 2, footerY + 3, withAlpha(VALUE_TEXT, alpha * 0.8f), false);
        // 右侧按钮
        String reset = I18n.get("draginventory.compass.ui.reset");
        String done = I18n.get("draginventory.compass.ui.done");
        int dw = font.width(done) + 12;
        int rw = font.width(reset) + 12;
        int dx = x + w - dw;
        int rx = dx - rw - 6;
        // 恢复默认（悬停变红提示破坏性）
        boolean rHot = hovered(rx, footerY, rw, 14);
        pixelRect(g, rx, footerY, rw, 14, rHot ? CONTROL_BG_HOVER : CONTROL_BG, CONTROL_EDGE);
        g.drawString(font, reset, rx + 6, footerY + 3, rHot ? 0xFFE08585 : VALUE_TEXT, false);
        // 完成（金色主按钮）
        boolean dHot = hovered(dx, footerY, dw, 14);
        pixelRect(g, dx, footerY, dw, 14, dHot ? accent(0.95f) : accent(0.75f), accent(1f));
        g.drawString(font, done, dx + 6, footerY + 3, 0xFF14181D, false);
    }

    // ==================== 鼠标交互 ====================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;

        // 页脚按钮
        int fx = panelX + 8, fw = panelW - 16;
        String back = I18n.get("draginventory.compass.ui.back");
        int bfw = font.width(back) + 12;
        String reset = I18n.get("draginventory.compass.ui.reset");
        String done = I18n.get("draginventory.compass.ui.done");
        int dw = font.width(done) + 12;
        int rw = font.width(reset) + 12;
        int dx = fx + fw - dw;
        int rx = dx - rw - 6;
        if (in(mx, my, fx, footerY, bfw, 14)) {
            // 返回（与完成等效：落盘并关闭）
            CompassConfig.flush();
            onClose();
            return true;
        }
        if (in(mx, my, dx, footerY, dw, 14)) {
            CompassConfig.flush();
            onClose();
            return true;
        }
        if (in(mx, my, rx, footerY, rw, 14)) {
            CompassConfig.resetToDefaults();
            preview.setPreviewHeading(206f);
            return true;
        }

        // 页签栏
        int tx0 = panelX + 8, tw0 = panelW - 16;
        int tabW = (tw0 - 10) / 6;
        for (int i = 0; i < 6; i++) {
            int tx = tx0 + i * (tabW + 2);
            if (in(mx, my, tx, tabBarY, tabW, 14)) {
                if (i != activeTab) {
                    activeTab = i;
                    tabSwitchTime = System.currentTimeMillis();
                }
                return true;
            }
        }

        // 预览盒：摆动按钮 / 模拟滑杆 / 拖拽转向
        int px = panelX + 8, pw = panelW - 16;
        if (in(mx, my, px, previewBoxY, pw, previewBoxH)) {
            String sway = I18n.get("draginventory.compass.ui.sway_short");
            int sw = font.width(sway) + 8;
            int sx = px + 4, sy = previewBoxY + previewBoxH - 13;
            if (in(mx, my, sx, sy, sw, 12)) {
                previewSway = !previewSway;
                preview.setSway(previewSway);
                return true;
            }
            int[] hs = headingSliderGeom();
            if (hs != null && in(mx, my, hs[0], sy - 1, hs[1], 14)) {
                draggingHeading = true;
                setHeadingFromMouse(mx, hs[0], hs[1]);
                return true;
            }
            // 其余预览区域：拖拽转向（拖拽即停摆动）
            draggingPreviewArea = true;
            lastDragX = (float) mx;
            previewSway = false;
            preview.setSway(false);
            return true;
        }

        // 内容区行
        int cx = panelX + 8, cw = panelW - 16;
        if (in(mx, my, cx, contentY, cw, contentH)) {
            int y = contentY - tabScroll[activeTab];
            for (Control control : tabs.get(activeTab)) {
                int h = control.height();
                if (my >= y && my < y + h) {
                    // 行级重置按钮优先（仅可重置的标准行显示）
                    if (control.canReset() && h == ROW_H) {
                        int[] rg = Control.resetGeom(cx, cw, y);
                        if (in(mx, my, rg[0] - 1, rg[1] - 1, 11, 11)) {
                            control.resetToDefault(this);
                            return true;
                        }
                    }
                    int ctrlW = control.canReset() && h == ROW_H ? cw - 13 : cw;
                    if (control.click(this, mx, my, cx, ctrlW, y)) return true;
                    break;
                }
                y += h;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingHeading) {
            int[] hs = headingSliderGeom();
            if (hs != null) setHeadingFromMouse(mx, hs[0], hs[1]);
            return true;
        }
        if (draggingPreviewArea) {
            preview.dragPreview(((float) mx - lastDragX));
            lastDragX = (float) mx;
            return true;
        }
        if (draggingSlider != null) {
            int cx = panelX + 8, cw = panelW - 16;
            int y = rowOf(draggingSlider);
            if (y >= 0) draggingSlider.drag(this, mx, my, cx, cw, y);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingHeading = false;
        draggingPreviewArea = false;
        if (draggingSlider != null) {
            draggingSlider.release(this);
            draggingSlider = null;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        // 内容区滚动（仅内容超高时）
        int cx = panelX + 8, cw = panelW - 16;
        if (in(mx, my, cx, contentY, cw, contentH)) {
            // 先给行内滑杆机会（滚轮微调数值）
            int y = contentY - tabScroll[activeTab];
            for (Control control : tabs.get(activeTab)) {
                int h = control.height();
                if (my >= y && my < y + h) {
                    if (control.scroll(this, scrollY, mx, my, cx, cw, y)) return true;
                    break;
                }
                y += h;
            }
            int total = contentHeight(activeTab);
            if (total > contentH) {
                tabScroll[activeTab] = clampTabScroll(activeTab,
                        tabScroll[activeTab] - (int) Math.round(scrollY) * ROW_H);
                return true;
            }
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    /** 行内滑杆拖拽时反查它所在的行 y（用于把几何传回控件）。 */
    private int rowOf(Control target) {
        int y = contentY - tabScroll[activeTab];
        for (Control control : tabs.get(activeTab)) {
            if (control == target) return y;
            y += control.height();
        }
        return -1;
    }

    private void setHeadingFromMouse(double mx, int x, int w) {
        float t = (float) Mth.clamp((mx - x) / w, 0d, 1d);
        previewHeading = t * 360f;
        previewSway = false;
        preview.setPreviewHeading(previewHeading);
    }

    private float previewHeading = 206f;
    private boolean previewSway = true;

    private int clampTabScroll(int tab, int value) {
        int total = contentHeight(tab);
        int max = Math.max(0, total - contentH);
        return Mth.clamp(value, 0, max);
    }

    private int contentHeight(int tab) {
        int h = 0;
        for (Control control : tabs.get(tab)) h += control.height();
        return h;
    }

    // ==================== 像素绘制工具 ====================

    /** 带切角与双层描边的像素面板（minor=true 时不画外发光边，用于内嵌盒）。 */
    private void pixelPanel(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        pixelPanel(g, x, y, w, h, alpha, false);
    }

    private void pixelPanel(GuiGraphics g, int x, int y, int w, int h, float alpha, boolean minor) {
        int bg = withAlpha(PANEL_BG, alpha);
        int border = withAlpha(PANEL_BORDER, alpha);
        int inner = withAlpha(PANEL_INNER, alpha);
        int c = 3; // 切角尺寸
        // 主体（三段横带构成 2 级阶梯切角）
        g.fill(x + c, y, x + w - c, y + 2, bg);
        g.fill(x + 1, y + 2, x + w - 1, y + h - 2, bg);
        g.fill(x + c, y + h - 2, x + w - c, y + h, bg);
        // 阶梯缺口的填充（两级）
        for (int i = 0; i < 2; i++) {
            g.fill(x + 1 + i, y + 2 - i, x + c - 1 - i, y + 3 - i, bg);
            g.fill(x + w - c + 1 + i, y + 2 - i, x + w - 1 - i, y + 3 - i, bg);
            g.fill(x + 1 + i, y + h - 3 + i, x + c - 1 - i, y + h - 2 + i, bg);
            g.fill(x + w - c + 1 + i, y + h - 3 + i, x + w - 1 - i, y + h - 2 + i, bg);
        }
        // 外描边（沿切角轮廓）
        g.fill(x + c, y, x + w - c, y + 1, border);
        g.fill(x + c, y + h - 1, x + w - c, y + h, border);
        g.fill(x + 1, y + 2, x + 2, y + h - 2, border);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, border);
        // 斜角描边（两级阶梯）
        for (int i = 0; i < 2; i++) {
            g.fill(x + 2 + i, y + 2 - i, x + 3 + i, y + 3 - i, border);
            g.fill(x + w - 3 - i, y + 2 - i, x + w - 2 - i, y + 3 - i, border);
            g.fill(x + 2 + i, y + h - 3 + i, x + 3 + i, y + h - 2 + i, border);
            g.fill(x + w - 3 - i, y + h - 3 + i, x + w - 2 - i, y + h - 2 + i, border);
        }
        if (!minor) {
            // 内衬高光线（顶部受光）
            g.fill(x + c + 1, y + 1, x + w - c - 1, y + 2, inner);
        }
    }

    /** 像素矩形：主体 + 1px 描边（描边色传 0 表示不画）。 */
    private static void pixelRect(GuiGraphics g, int x, int y, int w, int h, int fill, int edge) {
        g.fill(x, y, x + w, y + h, fill);
        if (edge != 0) {
            g.fill(x, y, x + w, y + 1, edge);
            g.fill(x, y + h - 1, x + w, y + h, edge);
            g.fill(x, y, x + 1, y + h, edge);
            g.fill(x + w - 1, y, x + w, y + h, edge);
        }
    }

    /** 左右箭头小按钮（"<" / ">"，像素阶梯箭头）。 */
    private void arrowButton(GuiGraphics g, int x, int y, int w, int h, boolean right) {
        boolean hot = hovered(x, y, w, h);
        pixelRect(g, x, y, w, h, hot ? CONTROL_BG_HOVER : CONTROL_BG, hot ? 0xFFE8ECF2 : CONTROL_EDGE);
        int cx = x + w / 2, cy = y + h / 2;
        int c = withAlpha(hot ? 0xFFE8ECF2 : 0xFF9AA3B2, 1f);
        if (!right) {
            g.fill(cx - 1, cy - 3, cx, cy - 2, c);
            g.fill(cx - 2, cy - 2, cx - 1, cy - 1, c);
            g.fill(cx - 3, cy - 1, cx - 2, cy + 1, c);
            g.fill(cx - 2, cy + 1, cx - 1, cy + 2, c);
            g.fill(cx - 1, cy + 2, cx, cy + 3, c);
        } else {
            g.fill(cx, cy - 3, cx + 1, cy - 2, c);
            g.fill(cx + 1, cy - 2, cx + 2, cy - 1, c);
            g.fill(cx + 2, cy - 1, cx + 3, cy + 1, c);
            g.fill(cx + 1, cy + 1, cx + 2, cy + 2, c);
            g.fill(cx, cy + 2, cx + 1, cy + 3, c);
        }
    }

    private void hoverRow(GuiGraphics g, int x, int w, int y) {
        if (hovered(x, y, w, ROW_H)) {
            g.fill(x, y, x + w, y + ROW_H, ROW_HOVER);
        }
    }

    private void drawLabel(GuiGraphics g, String key, int x, int y) {
        g.drawString(font, I18n.get(key), x + 4, y + 5, LABEL_TEXT, false);
    }

    /** 控件区起点 x（标签列宽 = 面板宽 * 0.42，至少 118）。 */
    private int controlX(int x, int w) {
        return x + Math.max(118, w * 42 / 100);
    }

    private boolean hovered(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** 鼠标事件坐标命中矩形（double 参数版本，事件处理器共用）。 */
    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** 当前配色主题色（带透明度合成）。 */
    private int accent(float alpha) {
        return withAlpha(0xFF000000 | CompassWidget.currentPalette().accent(), alpha);
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.round((color >>> 24) * Mth.clamp(alpha, 0f, 1f));
        return (a << 24) | (color & 0xFFFFFF);
    }

    /** ease-out 动画进度 [0,1]。 */
    private static float anim(long start, int durationMs) {
        if (durationMs <= 0) return 1f;
        float t = (System.currentTimeMillis() - start) / (float) durationMs;
        t = Mth.clamp(t, 0f, 1f);
        return 1f - (1f - t) * (1f - t);
    }

    // ==================== HSL 颜色转换 ====================

    /** RGB -> HSL（h/s/l 均为 0..1）。 */
    private static float[] rgbToHsl(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float gg = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(gg, b));
        float min = Math.min(r, Math.min(gg, b));
        float l = (max + min) / 2f;
        float d = max - min;
        float h = 0f, s = 0f;
        if (d > 1e-5f) {
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == r) h = (gg - b) / d + (gg < b ? 6f : 0f);
            else if (max == gg) h = (b - r) / d + 2f;
            else h = (r - gg) / d + 4f;
            h /= 6f;
        }
        return new float[]{h, s, l};
    }

    /** HSL（0..1） -> RGB。 */
    private static int hslToRgb(float h, float s, float l) {
        h = Mth.clamp(h, 0f, 1f);
        s = Mth.clamp(s, 0f, 1f);
        l = Mth.clamp(l, 0f, 1f);
        if (s < 1e-5f) {
            int v = Math.round(l * 255f);
            return (v << 16) | (v << 8) | v;
        }
        float q = l < 0.5f ? l * (1f + s) : l + s - l * s;
        float p = 2f * l - q;
        int r = Math.round(hueToRgb(p, q, h + 1f / 3f) * 255f);
        int gg = Math.round(hueToRgb(p, q, h) * 255f);
        int b = Math.round(hueToRgb(p, q, h - 1f / 3f) * 255f);
        return (r << 16) | (gg << 8) | b;
    }

    private static float hueToRgb(float p, float q, float t) {
        if (t < 0f) t += 1f;
        if (t > 1f) t -= 1f;
        if (t < 1f / 6f) return p + (q - p) * 6f * t;
        if (t < 1f / 2f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6f;
        return p;
    }
}
