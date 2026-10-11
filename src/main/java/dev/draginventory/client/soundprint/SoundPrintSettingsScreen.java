package dev.draginventory.client.soundprint;

import dev.draginventory.client.compass.CompassConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

public final class SoundPrintSettingsScreen extends Screen {
   private static final SoundPrintSettingsScreen.MenuTheme AMBER = new SoundPrintSettingsScreen.MenuTheme(-2842538, -932849, -165797082, -13946314, -11905700);
   private static final SoundPrintSettingsScreen.MenuTheme TECH = new SoundPrintSettingsScreen.MenuTheme(-13649754, -10556716, -166323164, -14603725, -12496290);
   private static final SoundPrintSettingsScreen.MenuTheme CRIMSON = new SoundPrintSettingsScreen.MenuTheme(-3255212, -33934, -165669095, -13950684, -11124151);
   private static final int TITLE_TEXT = -2038035;
   private static final int LABEL_TEXT = -4340793;
   private static final int VALUE_TEXT = -8418163;
   private static final int ROW_HOVER = 352321535;
   private static final int CONTROL_BG = -14275277;
   private static final int CONTROL_BG_HOVER = -13419710;
   private static final int TRACK_EDGE = -15328995;
   private static final int KNOB = -2038035;
   private static final int ROW_H = 17;
   private static final int PAD = 12;
   private final SoundPrintWidget preview = new SoundPrintWidget(true);
   private final List<List<SoundPrintSettingsScreen.Control>> tabs = new ArrayList<>();
   private final long openTime;
   private final int[] tabScroll = new int[6];
   private int activeTab;
   private boolean swayOn = true;
   private SoundPrintSettingsScreen.SliderControl draggingSlider;
   private long closeStart;
   private int panelX;
   private int panelY;
   private int panelW;
   private int panelH;
   private int previewBoxY;
   private int previewBoxH;
   private int tabBarY;
   private int contentY;
   private int contentH;
   private int footerY;
   private int mouseX;
   private int mouseY;
   private static final List<String> COLOR_KEYS = List.of("default", "white", "red", "gold", "cyan");
   private static final List<Integer> COLOR_VALUES = List.of(-1, 15921906, 16732754, 15844367, 6220500);
   private boolean doneHot;
   private boolean resetHot;
   private int resetRectX;
   private int resetRectW;

   private static SoundPrintSettingsScreen.MenuTheme theme() {
      String var0 = (String)CompassConfig.MENU_THEME.get();

      return switch (var0) {
         case "tech" -> TECH;
         case "crimson" -> CRIMSON;
         default -> AMBER;
      };
   }

   public static SoundPrintSettingsScreen create() {
      return new SoundPrintSettingsScreen();
   }

   private SoundPrintSettingsScreen() {
      super(Component.translatable("draginventory.soundprint.title"));
      this.openTime = System.currentTimeMillis();
      this.buildTabs();
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void onClose() {
      SoundPrintConfig.flush();
      if (this.closeStart == 0L) {
         this.closeStart = System.currentTimeMillis();
      }
   }

   private void forceClose() {
      SoundPrintConfig.flush();
      super.onClose();
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 256) {
         if (this.closeStart == 0L) {
            this.onClose();
         }

         return true;
      } else {
         return super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   private void buildTabs() {
      this.tabs.clear();
      this.tabs
         .add(
            List.of(
               new SoundPrintSettingsScreen.BoolControl(
                  "draginventory.soundprint.cfg.enabled", SoundPrintConfig.ENABLED::get, v -> SoundPrintConfig.set(SoundPrintConfig.ENABLED, v)
               ),
               new SoundPrintSettingsScreen.BoolControl(
                  "draginventory.soundprint.cfg.follow_compass",
                  SoundPrintConfig.FOLLOW_COMPASS::get,
                  v -> SoundPrintConfig.set(SoundPrintConfig.FOLLOW_COMPASS, v)
               ),
               new SoundPrintSettingsScreen.SliderControl(
                  "draginventory.soundprint.cfg.spacing",
                  0.0F,
                  20.0F,
                  1.0F,
                  () -> (float)((Integer)SoundPrintConfig.SPACING.get()).intValue(),
                  v -> SoundPrintConfig.set(SoundPrintConfig.SPACING, Math.round(v)),
                  "%.0f px"
               ),
               new SoundPrintSettingsScreen.SliderControl(
                  "draginventory.soundprint.cfg.opacity",
                  0.15F,
                  1.0F,
                  0.05F,
                  () -> ((Double)SoundPrintConfig.OPACITY.get()).floatValue(),
                  v -> SoundPrintConfig.set(SoundPrintConfig.OPACITY, round2((double)v.floatValue())),
                  "%.2f"
               ),
               new SoundPrintSettingsScreen.SliderControl(
                  "draginventory.soundprint.cfg.scale",
                  0.5F,
                  2.0F,
                  0.05F,
                  () -> ((Double)SoundPrintConfig.SCALE.get()).floatValue(),
                  v -> SoundPrintConfig.set(SoundPrintConfig.SCALE, round2((double)v.floatValue())),
                  "%.2f"
               ),
               new SoundPrintSettingsScreen.CyclerControl(
                  "draginventory.soundprint.cfg.position_preset",
                  List.of("below_compass", "top_center", "center", "bottom_center"),
                  SoundPrintConfig.POSITION_PRESET::get,
                  v -> SoundPrintConfig.set(SoundPrintConfig.POSITION_PRESET, v)
               ),
               new SoundPrintSettingsScreen.SliderControl(
                  "draginventory.soundprint.cfg.offset_x",
                  -240.0F,
                  240.0F,
                  1.0F,
                  () -> (float)((Integer)SoundPrintConfig.OFFSET_X.get()).intValue(),
                  v -> SoundPrintConfig.set(SoundPrintConfig.OFFSET_X, Math.round(v)),
                  "%+.0f px"
               ),
               new SoundPrintSettingsScreen.SliderControl(
                  "draginventory.soundprint.cfg.offset_y",
                  -240.0F,
                  240.0F,
                  1.0F,
                  () -> (float)((Integer)SoundPrintConfig.OFFSET_Y.get()).intValue(),
                  v -> SoundPrintConfig.set(SoundPrintConfig.OFFSET_Y, Math.round(v)),
                  "%+.0f px"
               ),
               new SoundPrintSettingsScreen.SliderControl(
                  "draginventory.soundprint.cfg.position_width",
                  60.0F,
                  480.0F,
                  10.0F,
                  () -> (float)((Integer)SoundPrintConfig.POSITION_WIDTH.get()).intValue(),
                  v -> SoundPrintConfig.set(SoundPrintConfig.POSITION_WIDTH, Math.round(v)),
                  "%.0f px"
               )
            )
         );
      this.tabs
         .add(
            List.of(
               new SoundPrintSettingsScreen.SliderControl(
                  "draginventory.soundprint.cfg.radius",
                  4.0F,
                  64.0F,
                  1.0F,
                  () -> (float)((Integer)SoundPrintConfig.RADIUS.get()).intValue(),
                  v -> SoundPrintConfig.set(SoundPrintConfig.RADIUS, Math.round(v)),
                  "%.0f m"
               ),
               new SoundPrintSettingsScreen.BoolControl(
                  "draginventory.soundprint.cfg.footsteps", SoundPrintConfig.FOOTSTEPS::get, v -> SoundPrintConfig.set(SoundPrintConfig.FOOTSTEPS, v)
               ),
               new SoundPrintSettingsScreen.BoolControl(
                  "draginventory.soundprint.cfg.gunshots", SoundPrintConfig.GUNSHOTS::get, v -> SoundPrintConfig.set(SoundPrintConfig.GUNSHOTS, v)
               ),
               new SoundPrintSettingsScreen.BoolControl(
                  "draginventory.soundprint.cfg.ignore_own", SoundPrintConfig.IGNORE_OWN::get, v -> SoundPrintConfig.set(SoundPrintConfig.IGNORE_OWN, v)
               ),
               new SoundPrintSettingsScreen.BoolControl(
                  "draginventory.soundprint.cfg.own_gunshots", SoundPrintConfig.OWN_GUNSHOTS::get, v -> SoundPrintConfig.set(SoundPrintConfig.OWN_GUNSHOTS, v)
               ),
               new SoundPrintSettingsScreen.BoolControl(
                  "draginventory.soundprint.cfg.motion_persist",
                  SoundPrintConfig.MOTION_PERSIST::get,
                  v -> SoundPrintConfig.set(SoundPrintConfig.MOTION_PERSIST, v)
               )
            )
         );
      this.tabs
         .add(
            List.of(
               new SoundPrintSettingsScreen.CyclerControl(
                  "draginventory.soundprint.cfg.mode",
                  List.of("merge", "stagger"),
                  SoundPrintConfig.MODE::get,
                  v -> SoundPrintConfig.set(SoundPrintConfig.MODE, v)
               ),
               new SoundPrintSettingsScreen.SliderControl(
                  "draginventory.soundprint.cfg.merge_window",
                  2.0F,
                  20.0F,
                  1.0F,
                  () -> (float)((Integer)SoundPrintConfig.MERGE_WINDOW.get()).intValue(),
                  v -> SoundPrintConfig.set(SoundPrintConfig.MERGE_WINDOW, Math.round(v)),
                  "%.0f°"
               ),
               new SoundPrintSettingsScreen.SliderControl(
                  "draginventory.soundprint.cfg.stagger_spacing",
                  1.0F,
                  6.0F,
                  1.0F,
                  () -> (float)((Integer)SoundPrintConfig.STAGGER_SPACING.get()).intValue(),
                  v -> SoundPrintConfig.set(SoundPrintConfig.STAGGER_SPACING, Math.round(v)),
                  "%.0f px"
               )
            )
         );
      this.tabs
         .add(
            List.of(
               new SoundPrintSettingsScreen.SliderControl(
                  "draginventory.soundprint.cfg.bar_count",
                  5.0F,
                  21.0F,
                  2.0F,
                  () -> (float)((Integer)SoundPrintConfig.BAR_COUNT.get()).intValue(),
                  v -> SoundPrintConfig.set(SoundPrintConfig.BAR_COUNT, Math.round(v)),
                  "%.0f"
               ),
               new SoundPrintSettingsScreen.SliderControl(
                  "draginventory.soundprint.cfg.bar_width",
                  1.0F,
                  4.0F,
                  1.0F,
                  () -> (float)((Integer)SoundPrintConfig.BAR_WIDTH.get()).intValue(),
                  v -> SoundPrintConfig.set(SoundPrintConfig.BAR_WIDTH, Math.round(v)),
                  "%.0f px"
               ),
               new SoundPrintSettingsScreen.SliderControl(
                  "draginventory.soundprint.cfg.bar_gap",
                  0.0F,
                  3.0F,
                  1.0F,
                  () -> (float)((Integer)SoundPrintConfig.BAR_GAP.get()).intValue(),
                  v -> SoundPrintConfig.set(SoundPrintConfig.BAR_GAP, Math.round(v)),
                  "%.0f px"
               ),
               new SoundPrintSettingsScreen.SliderControl(
                  "draginventory.soundprint.cfg.max_height",
                  8.0F,
                  56.0F,
                  2.0F,
                  () -> (float)((Integer)SoundPrintConfig.MAX_HEIGHT.get()).intValue(),
                  v -> SoundPrintConfig.set(SoundPrintConfig.MAX_HEIGHT, Math.round(v)),
                  "%.0f px"
               ),
               new SoundPrintSettingsScreen.BoolControl(
                  "draginventory.soundprint.cfg.baseline", SoundPrintConfig.BASELINE::get, v -> SoundPrintConfig.set(SoundPrintConfig.BASELINE, v)
               ),
               new SoundPrintSettingsScreen.BoolControl(
                  "draginventory.soundprint.cfg.arrows", SoundPrintConfig.ARROWS::get, v -> SoundPrintConfig.set(SoundPrintConfig.ARROWS, v)
               ),
               new SoundPrintSettingsScreen.BoolControl(
                  "draginventory.soundprint.cfg.vertical_offset",
                  SoundPrintConfig.VERTICAL_OFFSET::get,
                  v -> SoundPrintConfig.set(SoundPrintConfig.VERTICAL_OFFSET, v)
               ),
               new SoundPrintSettingsScreen.CyclerControl(
                  "draginventory.soundprint.cfg.color_footstep",
                  colorOptions(),
                  () -> colorOptionKey((Integer)SoundPrintConfig.COLOR_FOOTSTEP.get(), SoundPrintType.FOOTSTEP.defaultRgb()),
                  v -> SoundPrintConfig.set(SoundPrintConfig.COLOR_FOOTSTEP, colorValue(v, SoundPrintType.FOOTSTEP.defaultRgb()))
               ),
               new SoundPrintSettingsScreen.CyclerControl(
                  "draginventory.soundprint.cfg.color_gunshot",
                  colorOptions(),
                  () -> colorOptionKey((Integer)SoundPrintConfig.COLOR_GUNSHOT.get(), SoundPrintType.GUNSHOT.defaultRgb()),
                  v -> SoundPrintConfig.set(SoundPrintConfig.COLOR_GUNSHOT, colorValue(v, SoundPrintType.GUNSHOT.defaultRgb()))
               )
            )
         );
      this.tabs
         .add(
            List.of(
               new SoundPrintSettingsScreen.BoolControl(
                  "draginventory.soundprint.cfg.distance_decay",
                  SoundPrintConfig.DISTANCE_DECAY::get,
                  v -> SoundPrintConfig.set(SoundPrintConfig.DISTANCE_DECAY, v)
               ),
               new SoundPrintSettingsScreen.SliderControl(
                  "draginventory.soundprint.cfg.min_decay",
                  0.0F,
                  0.6F,
                  0.05F,
                  () -> ((Double)SoundPrintConfig.MIN_DECAY.get()).floatValue(),
                  v -> SoundPrintConfig.set(SoundPrintConfig.MIN_DECAY, round2((double)v.floatValue())),
                  "%.2f"
               ),
               new SoundPrintSettingsScreen.SliderControl(
                  "draginventory.soundprint.cfg.linger",
                  100.0F,
                  1000.0F,
                  20.0F,
                  () -> (float)((Integer)SoundPrintConfig.LINGER_MS.get()).intValue(),
                  v -> SoundPrintConfig.set(SoundPrintConfig.LINGER_MS, Math.round(v)),
                  "%.0f ms"
               ),
               new SoundPrintSettingsScreen.SliderControl(
                  "draginventory.soundprint.cfg.attack",
                  4.0F,
                  40.0F,
                  1.0F,
                  () -> ((Double)SoundPrintConfig.ATTACK.get()).floatValue(),
                  v -> SoundPrintConfig.set(SoundPrintConfig.ATTACK, (double)Math.round(v)),
                  "%.0f"
               ),
               new SoundPrintSettingsScreen.SliderControl(
                  "draginventory.soundprint.cfg.release",
                  1.0F,
                  20.0F,
                  0.5F,
                  () -> ((Double)SoundPrintConfig.RELEASE.get()).floatValue(),
                  v -> SoundPrintConfig.set(SoundPrintConfig.RELEASE, round2((double)v.floatValue())),
                  "%.1f"
               ),
               new SoundPrintSettingsScreen.BoolControl(
                  "draginventory.soundprint.cfg.oscillate", SoundPrintConfig.OSCILLATE::get, v -> SoundPrintConfig.set(SoundPrintConfig.OSCILLATE, v)
               ),
               new SoundPrintSettingsScreen.SliderControl(
                  "draginventory.soundprint.cfg.osc_speed",
                  0.5F,
                  8.0F,
                  0.1F,
                  () -> ((Double)SoundPrintConfig.OSC_SPEED.get()).floatValue(),
                  v -> SoundPrintConfig.set(SoundPrintConfig.OSC_SPEED, round2((double)v.floatValue())),
                  "%.1f Hz"
               ),
               new SoundPrintSettingsScreen.SliderControl(
                  "draginventory.soundprint.cfg.osc_amp_min",
                  0.0F,
                  8.0F,
                  1.0F,
                  () -> (float)((Integer)SoundPrintConfig.OSC_AMP_MIN.get()).intValue(),
                  v -> SoundPrintConfig.set(SoundPrintConfig.OSC_AMP_MIN, Math.round(v)),
                  "%.0f px"
               ),
               new SoundPrintSettingsScreen.SliderControl(
                  "draginventory.soundprint.cfg.osc_amp_max",
                  0.0F,
                  24.0F,
                  1.0F,
                  () -> (float)((Integer)SoundPrintConfig.OSC_AMP_MAX.get()).intValue(),
                  v -> SoundPrintConfig.set(SoundPrintConfig.OSC_AMP_MAX, Math.round(v)),
                  "%.0f px"
               ),
               new SoundPrintSettingsScreen.SliderControl(
                  "draginventory.soundprint.cfg.stack_amp_gain",
                  0.0F,
                  100.0F,
                  5.0F,
                  () -> (float)(Math.round((Double)SoundPrintConfig.STACK_AMP_GAIN.get() * 100.0)),
                  v -> SoundPrintConfig.set(SoundPrintConfig.STACK_AMP_GAIN, round2((double)(v.floatValue() / 100.0F))),
                  "%.0f%%"
               ),
               new SoundPrintSettingsScreen.SliderControl(
                  "draginventory.soundprint.cfg.stack_alpha_gain",
                  0.0F,
                  100.0F,
                  5.0F,
                  () -> (float)(Math.round((Double)SoundPrintConfig.STACK_ALPHA_GAIN.get() * 100.0)),
                  v -> SoundPrintConfig.set(SoundPrintConfig.STACK_ALPHA_GAIN, round2((double)(v.floatValue() / 100.0F))),
                  "%.0f%%"
               )
            )
         );
      this.tabs
         .add(
            List.of(
               new SoundPrintSettingsScreen.BoolControl(
                  "draginventory.soundprint.cfg.sim_enabled", SoundPrintEvents.SIMULATOR::isEnabled, v -> SoundPrintEvents.SIMULATOR.setEnabled(v)
               ),
               new SoundPrintSettingsScreen.CyclerControl(
                  "draginventory.soundprint.cfg.sim_type",
                  List.of("footstep", "gunshot"),
                  () -> SoundPrintEvents.SIMULATOR.type() == SoundPrintType.GUNSHOT ? "gunshot" : "footstep",
                  v -> SoundPrintEvents.SIMULATOR.setType("gunshot".equals(v) ? SoundPrintType.GUNSHOT : SoundPrintType.FOOTSTEP)
               ),
               new SoundPrintSettingsScreen.SliderControl(
                  "draginventory.soundprint.cfg.sim_bearing",
                  0.0F,
                  359.0F,
                  5.0F,
                  () -> SoundPrintEvents.SIMULATOR.bearing(),
                  v -> SoundPrintEvents.SIMULATOR.setBearing((float)Math.round(v)),
                  "%.0f°"
               ),
               new SoundPrintSettingsScreen.SliderControl(
                  "draginventory.soundprint.cfg.sim_distance",
                  2.0F,
                  20.0F,
                  1.0F,
                  () -> SoundPrintEvents.SIMULATOR.distance(),
                  v -> SoundPrintEvents.SIMULATOR.setDistance((float)Math.round(v)),
                  "%.0f m"
               ),
               new SoundPrintSettingsScreen.CyclerControl(
                  "draginventory.soundprint.cfg.sim_mode",
                  List.of("static", "orbit", "random"),
                  () -> SoundPrintEvents.SIMULATOR.movement().name().toLowerCase(Locale.ROOT),
                  v -> SoundPrintEvents.SIMULATOR.setMovement(SoundPrintSimulator.Movement.valueOf(v.toUpperCase(Locale.ROOT)))
               ),
               new SoundPrintSettingsScreen.BoolControl(
                  "draginventory.soundprint.cfg.capture_log",
                  SoundPrintEvents::captureLog,
                  v -> SoundPrintEvents.setCaptureLog(v)
               )
            )
         );
   }

   private static void playClick() {
      Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
   }

   private static double round2(double v) {
      return (double)Math.round(v * 100.0) / 100.0;
   }

   private static List<String> colorOptions() {
      return COLOR_KEYS;
   }

   private static String colorOptionKey(int configured, int defaultRgb) {
      if (configured < 0) {
         return "default";
      } else {
         int i = COLOR_VALUES.indexOf(configured);
         return i > 0 ? COLOR_KEYS.get(i) : String.format(Locale.ROOT, "0x%06X", configured);
      }
   }

   private static int colorValue(String key, int defaultRgb) {
      int i = COLOR_KEYS.indexOf(key);
      return i >= 0 ? COLOR_VALUES.get(i) : -1;
   }

   protected void init() {
      this.panelW = Math.min(336, this.width - 8);
      int rows = 6;
      int idealH = 98 + 17 * rows + 16 + 22;
      this.panelH = Math.min(idealH, this.height - 8);
      this.panelX = (this.width - this.panelW) / 2;
      this.panelY = (this.height - this.panelH) / 2;
      this.previewBoxY = this.panelY + 26;
      this.previewBoxH = 64;
      this.tabBarY = this.previewBoxY + this.previewBoxH + 4;
      this.contentY = this.tabBarY + 18;
      this.footerY = this.panelY + this.panelH - 18;
      this.contentH = this.footerY - this.contentY - 4;
      if (this.activeTab < this.tabScroll.length) {
         this.tabScroll[this.activeTab] = 0;
      }
   }

   public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      this.mouseX = mouseX;
      this.mouseY = mouseY;
      long now = System.currentTimeMillis();
      float closeT = this.closeProgress(now);
      g.pose().pushPose();

      try {
         if (closeT > 0.0F) {
            float s = 1.0F - 0.04F * closeT;
            g.pose().translate((float)this.panelX + (float)this.panelW / 2.0F, (float)this.panelY + (float)this.panelH / 2.0F, 0.0F);
            g.pose().scale(s, s, 1.0F);
            g.pose().translate(-((float)this.panelX + (float)this.panelW / 2.0F), -((float)this.panelY + (float)this.panelH / 2.0F), 0.0F);
         }

         super.render(g, mouseX, mouseY, partialTick);
         this.renderPanel(g, now, closeT);
      } finally {
         g.pose().popPose();
      }

      if (closeT >= 1.0F) {
         this.forceClose();
      }
   }

   private float closeProgress(long now) {
      return this.closeStart == 0L ? 0.0F : Mth.clamp((float)(now - this.closeStart) / 130.0F, 0.0F, 1.0F);
   }

   private float openProgress(long now) {
      float p = (float)(now - this.openTime) / 130.0F;
      return Mth.clamp(p, 0.0F, 1.0F);
   }

   private void renderPanel(GuiGraphics g, long now, float closeT) {
      SoundPrintSettingsScreen.MenuTheme t = theme();
      float open = this.openProgress(now);
      int alpha = Math.round((closeT > 0.0F ? 1.0F - closeT : open) * 255.0F);
      pixelRect(g, this.panelX, this.panelY, this.panelW, this.panelH, withAlpha(t.panelBg(), alpha), withAlpha(t.panelBorder(), alpha));
      g.fill(this.panelX + 2, this.panelY + 2, this.panelX + this.panelW - 2, this.panelY + this.panelH - 2, withAlpha(t.panelInner(), alpha));
      String title = Component.translatable("draginventory.soundprint.title").getString();
      String sub = "SOUNDPRINT";
      g.drawString(this.font, title, this.panelX + 12, this.panelY + 6, withAlpha(-2038035, alpha), false);
      int subW = this.font.width(sub);
      g.drawString(this.font, sub, this.panelX + this.panelW - 12 - subW, this.panelY + 8, withAlpha(t.accent(), alpha), false);
      g.fill(this.panelX + 12, this.panelY + 22, this.panelX + this.panelW - 12, this.panelY + 23, withAlpha(t.panelBorder(), alpha));
      this.renderPreviewBox(g, now, alpha);
      this.renderTabBar(g, alpha);
      this.renderContent(g, alpha);
      this.renderFooter(g, alpha);
   }

   private void renderPreviewBox(GuiGraphics g, long now, int alpha) {
      SoundPrintSettingsScreen.MenuTheme t = theme();
      int x = this.panelX + 12;
      int w = this.panelW - 24;
      g.fill(x, this.previewBoxY, x + w, this.previewBoxY + this.previewBoxH, withAlpha(-166718184, alpha));
      g.fill(x, this.previewBoxY, x + 1, this.previewBoxY + this.previewBoxH, withAlpha(t.panelBorder(), alpha));
      g.fill(x + w - 1, this.previewBoxY, x + w, this.previewBoxY + this.previewBoxH, withAlpha(t.panelBorder(), alpha));
      g.fill(x, this.previewBoxY, x + w, this.previewBoxY + 1, withAlpha(t.panelBorder(), alpha));
      g.fill(x, this.previewBoxY + this.previewBoxH - 1, x + w, this.previewBoxY + this.previewBoxH, withAlpha(t.panelBorder(), alpha));
      float px = (float)(x + 8);
      float pw = (float)(w - 16);
      float py = (float)(this.previewBoxY + this.previewBoxH / 2 + 8);
      this.preview.setSway(this.swayOn);
      this.preview.renderStandalone(g, px, py, pw, now, this.previewScale(px, pw));
      String sway = "SWAY";
      int sw = this.font.width(sway);
      int sx = x + w - sw - 8;
      int sy = this.previewBoxY + 3;
      boolean swayHot = this.mouseX >= sx && this.mouseX < sx + sw + 6 && this.mouseY >= sy - 2 && this.mouseY < sy + 10;
      if (swayHot) {
         g.fill(sx - 4, sy - 2, sx + sw + 4, sy + 10, withAlpha(352321535, alpha));
      }

      g.drawString(this.font, sway, sx, sy, this.swayOn ? -1 : withAlpha(-8418163, alpha), false);
   }

   private float previewScale(float px, float pw) {
      float compassWidth = (float)((Integer)CompassConfig.BAR_WIDTH.get()).intValue();
      float fit = Math.min(1.0F, pw / Math.max(1.0F, compassWidth));
      return (float)((Double)SoundPrintConfig.SCALE.get()).doubleValue() * fit;
   }

   private void renderTabBar(GuiGraphics g, int alpha) {
      SoundPrintSettingsScreen.MenuTheme t = theme();
      String[] names = new String[]{"general", "detect", "multi", "shape", "motion", "sim"};
      int x = this.panelX + 12;

      for (int i = 0; i < names.length && i < this.tabs.size(); i++) {
         String label = Component.translatable("draginventory.soundprint.tab." + names[i]).getString();
         int w = this.font.width(label) + 10;
         boolean active = i == this.activeTab;
         if (active) {
            g.fill(x, this.tabBarY, x + w, this.tabBarY + 15, withAlpha(t.panelBorder(), alpha));
            g.fill(x + 1, this.tabBarY + 1, x + w - 1, this.tabBarY + 14, withAlpha(t.panelInner(), alpha));
         }

         g.drawString(this.font, label, x + 5, this.tabBarY + 4, active ? withAlpha(t.accentBright(), alpha) : withAlpha(-8418163, alpha), false);
         x += w + 2;
      }
   }

   private void renderContent(GuiGraphics g, int alpha) {
      SoundPrintSettingsScreen.MenuTheme t = theme();
      List<SoundPrintSettingsScreen.Control> tab = this.tabs.get(this.activeTab);
      int clipTop = this.contentY;
      int clipBottom = this.contentY + this.contentH;
      int y = clipTop - this.tabScroll[this.activeTab];

      for (int i = 0; i < tab.size(); i++) {
         SoundPrintSettingsScreen.Control control = tab.get(i);
         if (y + control.height() >= clipTop && y <= clipBottom) {
            boolean hot = !this.dragging()
               && this.mouseX >= this.panelX + 12
               && this.mouseX < this.panelX + this.panelW - 12
               && this.mouseY >= y
               && this.mouseY < y + control.height();
            this.renderControl(g, control, this.panelX + 12, y, this.panelW - 24, hot, alpha, t);
            y += control.height() + 1;
         } else {
            y += control.height() + 1;
         }
      }
   }

   private boolean dragging() {
      return this.draggingSlider != null;
   }

   private void renderControl(
      GuiGraphics g, SoundPrintSettingsScreen.Control control, int x, int y, int w, boolean hot, int alpha, SoundPrintSettingsScreen.MenuTheme t
   ) {
      String label = Component.translatable(control.labelKey()).getString();
      if (hot) {
         g.fill(x, y, x + w, y + control.height(), withAlpha(352321535, alpha));
      }

      g.drawString(this.font, label, x, y + 5, withAlpha(-4340793, alpha), false);
      int cw = Math.min(132, w / 2);
      int cx = x + w - cw;
      if (control instanceof SoundPrintSettingsScreen.BoolControl c) {
         this.renderToggle(g, cx, y, cw, c.get.get(), hot, alpha);
      } else if (control instanceof SoundPrintSettingsScreen.SliderControl c) {
         this.renderSlider(g, cx, y, cw, c, alpha);
      } else if (control instanceof SoundPrintSettingsScreen.CyclerControl c) {
         this.renderCycler(g, cx, y, cw, c, hot, alpha);
      } else if (control instanceof SoundPrintSettingsScreen.ButtonControl c) {
         this.renderSmallButton(g, cx, y, cw, Component.translatable(c.labelKey()).getString(), hot, alpha, t, c.action());
      }
   }

   private void renderToggle(GuiGraphics g, int x, int y, int w, boolean on, boolean hot, int alpha) {
      int tx = x + w - 30;
      int ty = y + 3;
      g.fill(tx, ty, tx + 28, ty + 11, withAlpha(-15328995, alpha));
      g.fill(tx + 1, ty + 1, tx + 27, ty + 10, withAlpha(on ? -13730510 : -14275277, alpha));
      if (hot) {
         g.fill(tx + 1, ty + 1, tx + 27, ty + 10, withAlpha(-13419710, alpha));
      }

      int kx = on ? tx + 18 : tx + 3;
      g.fill(kx, ty + 2, kx + 8, ty + 9, on ? -1 : -2038035);
   }

   private void renderSlider(GuiGraphics g, int x, int y, int w, SoundPrintSettingsScreen.SliderControl c, int alpha) {
      int tx = x + 4;
      int tw = w - 8;
      int ty = y + 7;
      g.fill(tx, ty, tx + tw, ty + 4, withAlpha(-15328995, alpha));
      float frac = c.fraction();
      g.fill(tx, ty, tx + Math.round((float)tw * frac), ty + 4, withAlpha(theme().accent(), alpha));
      int kx = tx + Math.round((float)(tw - 6) * frac);
      int ky = ty - 2;
      g.fill(kx, ky, kx + 6, ky + 8, -2038035);
      g.fill(kx, ky, kx + 6, ky + 1, -1);
      String value = String.format(Locale.ROOT, c.fmt(), c.get.get());
      int vw = this.font.width(value);
      g.drawString(this.font, value, x + w - vw - 4, y + 4, withAlpha(-8418163, alpha), false);
   }

   private void renderCycler(GuiGraphics g, int x, int y, int w, SoundPrintSettingsScreen.CyclerControl c, boolean hot, int alpha) {
      String value = c.current();
      int vw = this.font.width(value) + 12;
      int cx = x + w - vw - 4;
      int cy = y + 2;
      g.fill(cx, cy, cx + vw, cy + 12, withAlpha(hot ? -13419710 : -14275277, alpha));
      g.fill(cx, cy, cx + vw, cy + 1, withAlpha(-15328995, alpha));
      g.drawString(this.font, value, cx + 6, cy + 2, withAlpha(-4340793, alpha), false);
      g.drawString(this.font, "<", cx, cy + 2, withAlpha(-8418163, alpha), false);
      g.drawString(this.font, ">", cx + vw - 5, cy + 2, withAlpha(-8418163, alpha), false);
   }

   private void renderSmallButton(
      GuiGraphics g, int x, int y, int w, String label, boolean hot, int alpha, SoundPrintSettingsScreen.MenuTheme t, Runnable action
   ) {
      int bh = 12;
      g.fill(x, y + 2, x + w, y + 2 + bh, withAlpha(hot ? -13419710 : -14275277, alpha));
      g.fill(x, y + 2, x + w, y + 3, withAlpha(-15328995, alpha));
      int lw = this.font.width(label);
      g.drawString(this.font, label, x + (w - lw) / 2, y + 5, withAlpha(-4340793, alpha), false);
   }

   private void renderFooter(GuiGraphics g, int alpha) {
      SoundPrintSettingsScreen.MenuTheme t = theme();
      String reset = Component.translatable("draginventory.soundprint.cfg.reset").getString();
      int rw = this.font.width(reset) + 12;
      int rx = this.panelX + 12;
      boolean rHot = !this.dragging() && this.mouseX >= rx && this.mouseX < rx + rw && this.mouseY >= this.footerY && this.mouseY < this.footerY + 13;
      g.fill(rx, this.footerY, rx + rw, this.footerY + 13, withAlpha(rHot ? -12175044 : -14275277, alpha));
      g.drawString(this.font, reset, rx + 6, this.footerY + 3, rHot ? -2062971 : withAlpha(-8418163, alpha), false);
      String done = Component.translatable("draginventory.soundprint.cfg.done").getString();
      int dw = this.font.width(done) + 14;
      int dx = this.panelX + this.panelW - 12 - dw;
      boolean dHot = !this.dragging() && this.mouseX >= dx && this.mouseX < dx + dw && this.mouseY >= this.footerY && this.mouseY < this.footerY + 13;
      g.fill(dx, this.footerY, dx + dw, this.footerY + 13, withAlpha(t.accent(), alpha));
      g.drawString(this.font, done, dx + 7, this.footerY + 3, -15460323, false);
      this.doneHot = dHot;
      this.resetHot = rHot;
      this.resetRectX = rx;
      this.resetRectW = rw;
   }

   public boolean mouseClicked(double mx, double my, int button) {
      if (this.closeStart != 0L) {
         return true;
      } else {
         int x = (int)mx;
         int y = (int)my;
         if (y >= this.footerY && y < this.footerY + 13) {
            if (this.doneHot) {
               playClick();
               this.onClose();
               return true;
            }

            if (this.resetHot) {
               playClick();
               // Global factory reset: every control re-reads the config live,
               // so the whole panel snaps to the new defaults in place.
               SoundPrintConfig.resetToDefaults();
               return true;
            }
         }

         int bx = this.panelX + this.panelW - 12 - 8 - this.font.width("SWAY");
         if (x >= bx - 4 && x < bx + this.font.width("SWAY") + 4 && y >= this.previewBoxY + 1 && y < this.previewBoxY + 13) {
            this.swayOn = !this.swayOn;
            this.preview.setSway(this.swayOn);
            return true;
         } else {
            String[] names = new String[]{"general", "detect", "multi", "shape", "motion", "sim"};
            int tx = this.panelX + 12;

            for (int i = 0; i < names.length && i < this.tabs.size(); i++) {
               int w = this.font.width(Component.translatable("draginventory.soundprint.tab." + names[i]).getString()) + 10;
               if (x >= tx && x < tx + w && y >= this.tabBarY && y < this.tabBarY + 15) {
                  this.activeTab = i;
                  return true;
               }

               tx += w + 2;
            }

            if (x >= this.panelX + 12 && x < this.panelX + this.panelW - 12 && y >= this.contentY && y < this.contentY + this.contentH) {
               List<SoundPrintSettingsScreen.Control> tab = this.tabs.get(this.activeTab);
               tx = this.contentY - this.tabScroll[this.activeTab];

               for (SoundPrintSettingsScreen.Control control : tab) {
                  if (y >= tx && y < tx + control.height()) {
                     int w = this.panelW - 24;
                     int cw = Math.min(132, w / 2);
                     int cx = this.panelX + 12 + w - cw;
                     if (control instanceof SoundPrintSettingsScreen.BoolControl c) {
                        c.set.accept(!c.get.get());
                     } else if (control instanceof SoundPrintSettingsScreen.CyclerControl c) {
                        c.next();
                     } else if (control instanceof SoundPrintSettingsScreen.SliderControl c) {
                        this.draggingSlider = c;
                        this.dragSliderTo(x, cx, cw);
                     } else if (control instanceof SoundPrintSettingsScreen.ButtonControl c) {
                        c.action().run();
                     }

                     return true;
                  }

                  tx += control.height() + 1;
               }
            }

            return super.mouseClicked(mx, my, button);
         }
      }
   }

   public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
      if (this.draggingSlider != null) {
         int w = this.panelW - 24;
         int cw = Math.min(132, w / 2);
         int cx = this.panelX + 12 + w - cw;
         this.dragSliderTo((int)mx, cx, cw);
         return true;
      } else {
         return super.mouseDragged(mx, my, button, dragX, dragY);
      }
   }

   private void dragSliderTo(int mouseX, int cx, int cw) {
      SoundPrintSettingsScreen.SliderControl c = this.draggingSlider;
      float frac = Mth.clamp((float)(mouseX - cx - 4) / (float)Math.max(1, cw - 8), 0.0F, 1.0F);
      float raw = c.min + frac * (c.max - c.min);
      float stepped = (float)Math.round(raw / c.step) * c.step;
      c.set.accept(Mth.clamp(stepped, c.min, c.max));
   }

   public boolean mouseReleased(double mx, double my, int button) {
      this.draggingSlider = null;
      return super.mouseReleased(mx, my, button);
   }

   public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
      if (mx >= (double)this.panelX && mx < (double)(this.panelX + this.panelW) && my >= (double)this.contentY && my < (double)(this.contentY + this.contentH)) {
         int max = Math.max(0, this.contentHeight(this.tabs.get(this.activeTab)) - this.contentH);
         this.tabScroll[this.activeTab] = (int)Mth.clamp((float)this.tabScroll[this.activeTab] - (float)scrollY * 18.0F, 0.0F, (float)max);
         return true;
      } else {
         return super.mouseScrolled(mx, my, scrollX, scrollY);
      }
   }

   private int contentHeight(List<SoundPrintSettingsScreen.Control> tab) {
      int h = 0;

      for (SoundPrintSettingsScreen.Control control : tab) {
         h += control.height() + 1;
      }

      return Math.max(0, h - 1);
   }

   private static void pixelRect(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
      g.fill(x, y, x + w, y + h, border);
      g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
   }

   private static int withAlpha(int color, int alpha) {
      if (alpha >= 255) {
         return color;
      } else if (alpha <= 0) {
         return 0;
      } else {
         int a = (color >>> 24) * alpha / 255;
         return a << 24 | color & 16777215;
      }
   }

   private static record BoolControl(String labelKey, Supplier<Boolean> get, Consumer<Boolean> set) implements SoundPrintSettingsScreen.Control {
      @Override
      public int height() {
         return 17;
      }
   }

   private static record ButtonControl(String labelKey, Runnable action) implements SoundPrintSettingsScreen.Control {
      @Override
      public int height() {
         return 17;
      }
   }

   private sealed interface Control
      permits SoundPrintSettingsScreen.BoolControl,
      SoundPrintSettingsScreen.SliderControl,
      SoundPrintSettingsScreen.CyclerControl,
      SoundPrintSettingsScreen.ButtonControl {
      String labelKey();

      int height();
   }

   private static record CyclerControl(String labelKey, List<String> options, Supplier<String> get, Consumer<String> set)
      implements SoundPrintSettingsScreen.Control {
      @Override
      public int height() {
         return 17;
      }

      String current() {
         String v = this.get.get();
         return this.options.contains(v) ? v : this.options.get(0);
      }

      void next() {
         int i = (this.options.indexOf(this.current()) + 1) % this.options.size();
         this.set.accept(this.options.get(i));
      }
   }

   private static record MenuTheme(int accent, int accentBright, int panelBg, int panelInner, int panelBorder) {
   }

   private static record SliderControl(String labelKey, float min, float max, float step, Supplier<Float> get, Consumer<Float> set, String fmt)
      implements SoundPrintSettingsScreen.Control {
      @Override
      public int height() {
         return 17;
      }

      float fraction() {
         return Mth.clamp((this.get.get() - this.min) / Math.max(1.0E-4F, this.max - this.min), 0.0F, 1.0F);
      }
   }
}
