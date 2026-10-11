package dev.draginventory.client.soundprint;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

public final class SoundPrintConfig {
   public static final ModConfigSpec SPEC;
   private static final List<ConfigValue<?>> ALL = new ArrayList<>();
   private static volatile String cachedFootstepCsv = "";
   private static volatile String cachedGunshotCsv = "";
   private static volatile List<String> cachedFootstepKeywords = List.of();
   private static volatile List<String> cachedGunshotKeywords = List.of();
   private static volatile boolean dirty;
   private static volatile long lastChangeMillis;
   private static final long SAVE_QUIET_PERIOD_MS = 500L;
   private static final ConcurrentHashMap<ConfigValue<?>, Object> JOURNAL = new ConcurrentHashMap<>();
   public static final BooleanValue ENABLED;
   public static final BooleanValue FOLLOW_COMPASS;
   public static final IntValue RADIUS;
   public static final BooleanValue FOOTSTEPS;
   public static final BooleanValue GUNSHOTS;
   public static final BooleanValue IGNORE_OWN;
   public static final BooleanValue OWN_GUNSHOTS;
   public static final BooleanValue MOTION_PERSIST;
   public static final ConfigValue<String> FOOTSTEP_KEYWORDS;
   public static final ConfigValue<String> GUNSHOT_KEYWORDS;
   public static final ConfigValue<String> MODE;
   public static final IntValue MERGE_WINDOW;
   public static final IntValue STAGGER_SPACING;
   public static final IntValue BAR_COUNT;
   public static final IntValue BAR_WIDTH;
   public static final IntValue BAR_GAP;
   public static final IntValue MAX_HEIGHT;
   public static final BooleanValue BASELINE;
   public static final BooleanValue ARROWS;
   public static final BooleanValue DISTANCE_DECAY;
   public static final DoubleValue MIN_DECAY;
   public static final BooleanValue VERTICAL_OFFSET;
   public static final IntValue COLOR_FOOTSTEP;
   public static final IntValue COLOR_GUNSHOT;
   public static final DoubleValue OPACITY;
   public static final DoubleValue SCALE;
   public static final DoubleValue ATTACK;
   public static final DoubleValue RELEASE;
   public static final IntValue LINGER_MS;
   public static final BooleanValue OSCILLATE;
   public static final DoubleValue OSC_SPEED;
   public static final IntValue OSC_AMP_MIN;
   public static final IntValue OSC_AMP_MAX;
   public static final DoubleValue STACK_AMP_GAIN;
   public static final DoubleValue STACK_ALPHA_GAIN;
   public static final IntValue SPACING;
   public static final ConfigValue<String> POSITION_PRESET;
   public static final IntValue POSITION_WIDTH;
   public static final IntValue OFFSET_X;
   public static final IntValue OFFSET_Y;
   public static final BooleanValue SHAPE_RETUNED;
   public static final BooleanValue V294_RETUNED;
   public static final BooleanValue V295_RETUNED;

   private SoundPrintConfig() {
   }

   private static <T extends ConfigValue<?>> T reg(T value) {
      ALL.add(value);
      return value;
   }

   public static List<String> footstepKeywords() {
      String csv = (String)FOOTSTEP_KEYWORDS.get();
      if (!Objects.equals(csv, cachedFootstepCsv)) {
         cachedFootstepKeywords = SoundClassifier.parseKeywords(csv);
         cachedFootstepCsv = csv;
      }

      return cachedFootstepKeywords;
   }

   public static List<String> gunshotKeywords() {
      String csv = (String)GUNSHOT_KEYWORDS.get();
      if (!Objects.equals(csv, cachedGunshotCsv)) {
         cachedGunshotKeywords = SoundClassifier.parseKeywords(csv);
         cachedGunshotCsv = csv;
      }

      return cachedGunshotKeywords;
   }

   public static boolean staggerMode() {
      return "stagger".equalsIgnoreCase((String)MODE.get());
   }

   public static boolean isPositionPreset(String id) {
      return "below_compass".equals(id) || "top_center".equals(id) || "center".equals(id) || "bottom_center".equals(id);
   }

   public static <T> void set(ConfigValue<T> value, T newValue) {
      value.set(newValue);
      JOURNAL.put(value, newValue);
      dirty = true;
      lastChangeMillis = System.currentTimeMillis();
   }

   static void tickSave() {
      if (dirty && System.currentTimeMillis() - lastChangeMillis >= 500L) {
         flush();
      }
   }

   public static void flush() {
      if (dirty) {
         dirty = false;
         SPEC.save();
      }
   }

   static void onConfigReloaded() {
      if (!JOURNAL.isEmpty()) {
         boolean reapplied = false;
         Iterator<Entry<ConfigValue<?>, Object>> it = JOURNAL.entrySet().iterator();

         while (it.hasNext()) {
            Entry<ConfigValue<?>, Object> entry = it.next();
            ConfigValue<?> value = entry.getKey();
            Object wanted = entry.getValue();

            try {
               if (Objects.equals(value.getRaw(), wanted)) {
                  it.remove();
               } else {
                  setQuietly(value, wanted);
                  reapplied = true;
               }
            } catch (NullPointerException | IllegalStateException var6) {
            }
         }

         if (reapplied) {
            dirty = true;
            lastChangeMillis = System.currentTimeMillis();
         }
      }
   }

   static void onConfigUnloaded() {
      JOURNAL.clear();
      dirty = false;
   }

   private static void setQuietly(ConfigValue value, Object wanted) {
      value.set(wanted);
   }

   public static void resetToDefaults() {
      for (ConfigValue<?> value : ALL) {
         resetOne(value);
         JOURNAL.put(value, value.getDefault());
      }

      dirty = false;
      SPEC.save();
   }

   @SuppressWarnings({"unchecked", "rawtypes"})
   private static void resetOne(ConfigValue<?> value) {
      ConfigValue raw = value;
      raw.set(raw.getDefault());
   }

   public static Type type() {
      return Type.CLIENT;
   }

   public static String fileName() {
      return "draginventory-soundprint-client.toml";
   }

   static {
      Builder b = new Builder();
      b.comment(
            new String[]{
               "声纹 HUD (HERRA Soundprint) — 客户端配置",
               "修改后立即生效；外部编辑文件由 NeoForge 自动热重载。",
               "声纹渲染在方位条正下方，默认跟随方位条显隐。",
               "v2.9.1 活振动模型：竖条高度 = 实时响度，连续振动，无声整体收拢。",
               "v2.9.3 竖条高度硬顶棚：所有加成（枪声1.35x/垂直不对称/抖动）叠加后单侧不超 max_height x 类型加成。",
               "v2.9.4 振幅与可视性重调：抖动幅度基础±18%/满强度±42%，多声源叠加整簇变高，",
               "渲染改亚像素几何+双层柔光，max_height 默认 24→30（老配置一次性收敛）。",
               "v2.9.5 用户实测预设固化：bar_count 21 / bar_width 1 / osc_speed 5.6 / osc_amp_max 24",
               "+ offset_y -143 / 自枪声检测默认关（老配置一次性收敛，仍是旧默认值才升级）。",
               "shape_retuned 为 v2.8.0 形态预设收敛标记，v294_retuned / v295_retuned 为",
               "v2.9.4 / v2.9.5 默认值收敛标记，均无需手动修改。"
            }
         )
         .push("general");
      ENABLED = reg(b.define("enabled", true));
      FOLLOW_COMPASS = reg(b.define("follow_compass_visibility", true));
      SHAPE_RETUNED = reg(b.define("shape_retuned", false));
      V294_RETUNED = reg(b.define("v294_retuned", false));
      V295_RETUNED = reg(b.define("v295_retuned", false));
      b.pop();
      b.comment(
            new String[]{
               "检测：radius = 检测半径（米）；关键字表为声音 id 的小写子串匹配，",
               "外部枪械模组的声音 id 可通过 gunshot_keywords 追加。",
               "detect_own_gunshots = 检测自己的枪声（测试用，声纹渲染在条带正中；v2.9.5 起默认关）；",
               "motion_persist = 走动持续点亮（半径内走动实体实时续期，停走后按 linger 淡出）。"
            }
         )
         .push("detection");
      RADIUS = reg(b.defineInRange("radius", 20, 4, 64));
      FOOTSTEPS = reg(b.define("footsteps", true));
      GUNSHOTS = reg(b.define("gunshots", true));
      IGNORE_OWN = reg(b.define("ignore_own_sounds", true));
      OWN_GUNSHOTS = reg(b.define("detect_own_gunshots", false));
      MOTION_PERSIST = reg(b.define("motion_persist", true));
      FOOTSTEP_KEYWORDS = reg(b.define("footstep_keywords", "step, footstep"));
      GUNSHOT_KEYWORDS = reg(b.define("gunshot_keywords", "gun, shot, shoot, rifle, pistol, sniper, cannon, gunfire, muzzle, explosion, blast"));
      b.pop();
      b.comment(new String[]{"多声源：mode = merge（默认：聚合合并，同方位声源合成一簇）", "或 stagger（并排错位，同方位声源保持独立声纹横向错开）。", "merge_window = 聚合窗口（度）；stagger_spacing = 错位间隔（像素）。"})
         .push("multi_source");
      MODE = reg(b.define("mode", "merge"));
      MERGE_WINDOW = reg(b.defineInRange("merge_window", 6, 2, 20));
      STAGGER_SPACING = reg(b.defineInRange("stagger_spacing", 2, 1, 6));
      b.pop();
      b.comment(
            new String[]{
               "形态与配色：bar_count / bar_width / bar_gap / max_height 决定单簇声纹几何；",
               "颜色覆盖 -1 = 跟随默认（脚步白 / 枪声红），其余为 0xRRGGBB 整数。",
               "v2.8.0 预设拉宽：bar_count 9→14、bar_gap 1→3（单簇宽 26px→67px，",
               "消除“太扁”观感）；老配置由 SoundPrintConfigEvents 一次性收敛。",
               "v2.9.5 用户实测预设：bar_count 14→21、bar_width 2→1（更细更密的声纹条，",
               "单簇宽 81px）；仍是旧默认值的配置一次性收敛到新预设。"
            }
         )
         .push("style");
      BAR_COUNT = reg(b.defineInRange("bar_count", 21, 5, 21));
      BAR_WIDTH = reg(b.defineInRange("bar_width", 1, 1, 4));
      BAR_GAP = reg(b.defineInRange("bar_gap", 3, 0, 3));
      MAX_HEIGHT = reg(b.defineInRange("max_height", 30, 8, 56));
      BASELINE = reg(b.define("baseline", true));
      ARROWS = reg(b.define("arrows", true));
      DISTANCE_DECAY = reg(b.define("distance_decay", true));
      MIN_DECAY = reg(b.defineInRange("min_decay", 0.2, 0.0, 0.6));
      VERTICAL_OFFSET = reg(b.define("vertical_offset", true));
      COLOR_FOOTSTEP = reg(b.defineInRange("color_footstep", -1, -1, 16777215));
      COLOR_GUNSHOT = reg(b.defineInRange("color_gunshot", -1, -1, 16777215));
      OPACITY = reg(b.defineInRange("opacity", 1.0, 0.15, 1.0));
      SCALE = reg(b.defineInRange("scale", 1.0, 0.5, 2.0));
      b.pop();
      b.comment(new String[]{"动画：attack / release 为弹簧刚度（omega），帧率无关；", "枪声自动比起同能量脚步更快起振与收尾（尾速 0.6x）；", "linger = 声纹收尾衰减常数（毫秒，指数）：v2.9.1 起为活振动模型——", "响度直达竖条高度，无声后按此常数指数收拢归零，不再静止停留；", "脚步间隔内也会随节参呼吸，值越小呼吸感越明显。", "oscillate = v2.9.2 常驻随机振动：只要有声源，每条竖条独立地随机上下收缩与", "拉长（各自节奏、互不同步），幅度随当前响度与距离缩放（越响越大），默认开启；", "声音消失后才按 linger 渐弱收拢，绝不会定格不动。", "v2.9.4 振动幅度重调：基础 ±18%（最弱活声纹也常驻可见律动）→满强度 ±42%（满能量条在顶棚内约 0.41~1.0 倍高度间大幅舞动，节律比 v2.9.3 明显得多）。", "osc_speed = 振动节奏（Hz，v2.9.5 默认 5.6）；", "osc_amp_min/max_px = 方向箭头伸展振幅（像素，v2.9.2 起不再作用于声纹条）。", "stack_amp_gain / stack_alpha_gain = 每多一个同方位声源带来的振动/不透明度增益；v2.9.4 起每多一源整簇高度也 +18%（封顶 +54%，4 源起不再增长）——多声源叠加一眼可见。"})
         .push("animation");
      ATTACK = reg(b.defineInRange("attack", 18.0, 4.0, 40.0));
      RELEASE = reg(b.defineInRange("release", 7.0, 1.0, 20.0));
      LINGER_MS = reg(b.defineInRange("linger_ms", 280, 100, 1000));
      OSCILLATE = reg(b.define("oscillate", true));
      OSC_SPEED = reg(b.defineInRange("osc_speed_hz", 5.6, 0.5, 8.0));
      OSC_AMP_MIN = reg(b.defineInRange("osc_amp_min_px", 1, 0, 8));
      OSC_AMP_MAX = reg(b.defineInRange("osc_amp_max_px", 24, 0, 24));
      STACK_AMP_GAIN = reg(b.defineInRange("stack_amp_gain", 0.35, 0.0, 1.0));
      STACK_ALPHA_GAIN = reg(b.defineInRange("stack_alpha_gain", 0.30, 0.0, 1.0));
      b.pop();
      b.comment(
            new String[]{
               "位置：preset = 位置预设（below_compass = 方位条下端对齐（水平跟随方位条）；",
               "top_center = 屏幕顶部水平居中；center = 屏幕垂直居中；bottom_center = 屏幕底部水平居中（悬在快捷栏上方））。",
               "v2.9.3 出厂预设（用户实测反馈精确固化）：bottom_center + spacing 0 + width 380",
               "+ offset_x 0 + opacity 1.0 + scale 1.0。",
               "v2.9.5 用户微调固化：offset_y -174→-143（更贴近快捷栏）。",
               "spacing = below_compass 预设下与方位条底边的额外间距（像素）；",
               "width_px = 非 below_compass 预设的条带宽度（像素，below_compass 时跟随方位条宽度）；",
               "offset_x / offset_y = 在预设基础上的像素级微调（Y 正值向下）。",
               "页脚“恢复默认”按钮或 /soundprint reset 可随时回到本组出厂预设。"
            }
         )
         .push("position");
      SPACING = reg(b.defineInRange("spacing", 0, 0, 20));
      POSITION_PRESET = reg(b.define("preset", "bottom_center"));
      POSITION_WIDTH = reg(b.defineInRange("width_px", 380, 60, 480));
      OFFSET_X = reg(b.defineInRange("offset_x_px", 0, -240, 240));
      OFFSET_Y = reg(b.defineInRange("offset_y_px", -143, -240, 240));
      b.pop();
      SPEC = b.build();
   }
}
