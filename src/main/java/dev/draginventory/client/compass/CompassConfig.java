package dev.draginventory.client.compass;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 方位条 HUD 的客户端配置（NeoForge 配置系统，TOML 文件 draginventory-compass-client.toml）。
 *
 * <p>设计约定：
 * <ul>
 *   <li>所有数值均带范围校验，改错值会被自动纠正回边界。</li>
 *   <li>颜色覆盖项使用 -1 表示“跟随当前配色方案”，正值为 0xRRGGBB 覆盖。</li>
 *   <li>配置在客户端生效，文件被外部编辑时由 NeoForge 自动热重载。</li>
 * </ul></p>
 */
public final class CompassConfig {
    public static final ModConfigSpec SPEC;

    /** 用于“恢复默认”与遍历校验的全部值注册表。 */
    private static final List<ModConfigSpec.ConfigValue<?>> ALL = new ArrayList<>();

    /** 防抖落盘状态：仅在设置真正变更时置脏，静默 500ms 后由 client tick 落盘。 */
    private static volatile boolean dirty;
    private static volatile long lastChangeMillis;
    private static final long SAVE_QUIET_PERIOD_MS = 500L;

    // ==================== 常规 ====================
    public static final ModConfigSpec.BooleanValue ENABLED;
    /** 打开 F3 调试屏时自动隐藏（避免遮挡调试信息）。 */
    public static final ModConfigSpec.BooleanValue HIDE_WITH_DEBUG;

    // ==================== 位置与大小 ====================
    public static final ModConfigSpec.IntValue OFFSET_X;
    public static final ModConfigSpec.IntValue OFFSET_Y;
    /** 条带宽度（界面像素，不含缩放）。 */
    public static final ModConfigSpec.IntValue BAR_WIDTH;
    /** 整体缩放。 */
    public static final ModConfigSpec.DoubleValue SCALE;

    // ==================== 风格与配色 ====================
    /** 皮肤 id：minimal / glass / tactical / neon。 */
    public static final ModConfigSpec.ConfigValue<String> STYLE;
    /** 配色 id：aurora / frost / amber / crimson / violet / slate。 */
    public static final ModConfigSpec.ConfigValue<String> PALETTE;
    /** 整体不透明度 0.15 ~ 1。 */
    public static final ModConfigSpec.DoubleValue OPACITY;
    /** 角度数字使用 ° 符号。 */
    public static final ModConfigSpec.BooleanValue DEGREE_SYMBOL;

    // ==================== 颜色覆盖（-1 = 跟随配色） ====================
    public static final ModConfigSpec.IntValue COLOR_ACCENT;
    public static final ModConfigSpec.IntValue COLOR_TEXT;
    public static final ModConfigSpec.IntValue COLOR_DIM;
    public static final ModConfigSpec.IntValue COLOR_TICK;
    public static final ModConfigSpec.IntValue COLOR_BACKGROUND;

    // ==================== 动画 ====================
    /** 平滑响应速度（弹簧刚度 omega，越大越跟手）。 */
    public static final ModConfigSpec.DoubleValue SMOOTHNESS;
    /** 基数方位吸附辅助（接近东南西北时标签放大提亮）。 */
    public static final ModConfigSpec.BooleanValue SNAP_ASSIST;
    public static final ModConfigSpec.IntValue SNAP_RANGE;
    /** 转向时刻度的惯性倾斜。 */
    public static final ModConfigSpec.BooleanValue INERTIA_TILT;
    public static final ModConfigSpec.DoubleValue TILT_INTENSITY;
    /** HUD 出现时的入场动画。 */
    public static final ModConfigSpec.BooleanValue ENTRY_ANIMATION;

    // ==================== 内容 ====================
    /** 可见视野总角度（度）。 */
    public static final ModConfigSpec.IntValue RANGE;
    /** 次级刻度间隔（度）。 */
    public static final ModConfigSpec.IntValue MINOR_STEP;
    /** 数字标注间隔（度）。 */
    public static final ModConfigSpec.IntValue NUMBER_STEP;
    public static final ModConfigSpec.BooleanValue SHOW_CARDINALS;
    /** 显示 45 度倍数的次方位（东北/东南/西南/西北）。 */
    public static final ModConfigSpec.BooleanValue SHOW_INTERCARDINALS;
    public static final ModConfigSpec.BooleanValue SHOW_NUMBERS;
    /** 东南西北使用中文（关闭则用 N/E/S/W）。 */
    public static final ModConfigSpec.BooleanValue CJK_LABELS;
    /** 基数方位文字相对缩放。 */
    public static final ModConfigSpec.DoubleValue CARDINAL_SCALE;

    // ==================== 标点联动 ====================
    public static final ModConfigSpec.BooleanValue MARKERS_ENABLED;
    /** 联动本模组战术标点（中键标记）。 */
    public static final ModConfigSpec.BooleanValue MARKERS_TACTICAL;
    /** 显示标点距离（米）。 */
    public static final ModConfigSpec.BooleanValue MARKERS_DISTANCE;
    /** 标点脉冲动画。 */
    public static final ModConfigSpec.BooleanValue MARKERS_PULSE;
    /** 指令创建的测试标点。 */
    public static final ModConfigSpec.BooleanValue MARKERS_TEST;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("方位条 HUD (HERRA Compass) — 客户端配置", "修改后立即生效；外部编辑文件由 NeoForge 自动热重载。").push("general");
        ENABLED = reg(b.define("enabled", true));
        HIDE_WITH_DEBUG = reg(b.define("hide_with_debug_screen", true));
        b.pop();

        b.comment("位置与大小：锚点为屏幕顶部中央，偏移量为界面像素。").push("position");
        OFFSET_X = reg(b.defineInRange("offset_x", 0, -640, 640));
        OFFSET_Y = reg(b.defineInRange("offset_y", 6, -640, 640));
        BAR_WIDTH = reg(b.defineInRange("width", 240, 120, 520));
        SCALE = reg(b.defineInRange("scale", 1.0, 0.5, 2.0));
        b.pop();

        b.comment("风格与配色：style = minimal / glass / tactical / neon；", "palette = aurora / frost / amber / crimson / violet / slate。").push("style");
        STYLE = reg(b.define("style", "minimal"));
        PALETTE = reg(b.define("palette", "aurora"));
        OPACITY = reg(b.defineInRange("opacity", 1.0, 0.15, 1.0));
        DEGREE_SYMBOL = reg(b.define("degree_symbol", false));
        b.pop();

        b.comment("颜色覆盖：-1 表示跟随配色方案，其余值为 0xRRGGBB 整数（TOML 中可用十进制）。").push("colors");
        COLOR_ACCENT = reg(b.defineInRange("accent", -1, -1, 0xFFFFFF));
        COLOR_TEXT = reg(b.defineInRange("text", -1, -1, 0xFFFFFF));
        COLOR_DIM = reg(b.defineInRange("dim_text", -1, -1, 0xFFFFFF));
        COLOR_TICK = reg(b.defineInRange("tick", -1, -1, 0xFFFFFF));
        COLOR_BACKGROUND = reg(b.defineInRange("background", -1, -1, 0xFFFFFF));
        b.pop();

        b.comment("动画：smoothness 为弹簧刚度（4~34，越大越跟手，越小越绵软）。").push("animation");
        SMOOTHNESS = reg(b.defineInRange("smoothness", 15.0, CompassHeading.OMEGA_MIN, CompassHeading.OMEGA_MAX));
        SNAP_ASSIST = reg(b.define("snap_assist", true));
        SNAP_RANGE = reg(b.defineInRange("snap_range", 6, 2, 20));
        INERTIA_TILT = reg(b.define("inertia_tilt", true));
        TILT_INTENSITY = reg(b.defineInRange("tilt_intensity", 1.0, 0.0, 3.0));
        ENTRY_ANIMATION = reg(b.define("entry_animation", true));
        b.pop();

        b.comment("内容：range 为可见视野总角度；minor_step 为次级刻度间隔。").push("content");
        RANGE = reg(b.defineInRange("range", 120, 60, 360));
        MINOR_STEP = reg(b.defineInRange("minor_step", 15, 5, 30));
        NUMBER_STEP = reg(b.defineInRange("number_step", 30, 15, 90));
        SHOW_CARDINALS = reg(b.define("show_cardinals", true));
        SHOW_INTERCARDINALS = reg(b.define("show_intercardinals", true));
        SHOW_NUMBERS = reg(b.define("show_numbers", true));
        CJK_LABELS = reg(b.define("cjk_labels", true));
        CARDINAL_SCALE = reg(b.defineInRange("cardinal_scale", 1.25, 0.8, 2.0));
        b.pop();

        b.comment("标点联动：战术标点来自本模组中键标记系统（只读）。").push("markers");
        MARKERS_ENABLED = reg(b.define("enabled", true));
        MARKERS_TACTICAL = reg(b.define("tactical", true));
        MARKERS_DISTANCE = reg(b.define("show_distance", true));
        MARKERS_PULSE = reg(b.define("pulse", true));
        MARKERS_TEST = reg(b.define("test_markers", true));
        b.pop();

        SPEC = b.build();
    }

    private CompassConfig() {}

    private static <T extends ModConfigSpec.ConfigValue<?>> T reg(T value) {
        ALL.add(value);
        return value;
    }

    /**
     * 运行时修改配置（指令 / 设置界面使用）。
     *
     * <p>NeoForge 的 {@code ConfigValue.set} 只改内存不落盘；这里标记脏位，
     * 由 client tick 在连续修改（拖动滑条）静默 500ms 后统一写盘，
     * 避免拖动过程中每帧全量序列化 TOML + 触发 reload 事件。</p>
     */
    public static <T> void set(ModConfigSpec.ConfigValue<T> value, T newValue) {
        value.set(newValue);
        dirty = true;
        lastChangeMillis = System.currentTimeMillis();
    }

    /** 由 client tick 调用：连续修改静默后统一落盘。 */
    static void tickSave() {
        if (dirty && System.currentTimeMillis() - lastChangeMillis >= SAVE_QUIET_PERIOD_MS) {
            flush();
        }
    }

    /** 立即落盘（退出世界 / 关闭设置界面等时机调用的兑底）。 */
    public static void flush() {
        if (dirty) {
            dirty = false;
            SPEC.save();
        }
    }

    /** 全部恢复默认值并写盘（显式操作，立即落盘）。 */
    public static void resetToDefaults() {
        for (ModConfigSpec.ConfigValue<?> value : ALL) {
            resetOne(value);
        }
        dirty = false;
        SPEC.save();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void resetOne(ModConfigSpec.ConfigValue<?> value) {
        ((ModConfigSpec.ConfigValue) value).set(value.getDefault());
    }

    /** 供主类注册使用。 */
    public static ModConfig.Type type() {
        return ModConfig.Type.CLIENT;
    }

    public static String fileName() {
        return "draginventory-compass-client.toml";
    }
}
