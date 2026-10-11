package dev.draginventory.client.soundprint;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.draginventory.client.compass.CompassConfig;
import dev.draginventory.client.compass.CompassHeading;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class SoundPrintWidget extends UIElement {
   private final CompassHeading heading = CompassHeading.create();
   private final boolean preview;
   private float previewHeadingTarget = 30.0F;
   private float previewBase = 30.0F;
   private boolean sway = true;
   private boolean wasVisible;
   private long entryStart = Long.MIN_VALUE;
   private long lastFrameMillis = Long.MIN_VALUE;
   private Player displayedPlayer;
   private boolean frameVisible;
   private float entryProgress = 1.0F;
   private float frameHeading;
   private float halfRange;
   private float edgeHalfWidth;
   private List<SoundPrintCluster> clusters = List.of();
   private float effectiveScale = 1.0F;

   public SoundPrintWidget(boolean preview) {
      this.preview = preview;
   }

   public void setSway(boolean sway) {
      this.sway = sway;
      this.previewBase = this.previewHeadingTarget;
   }

   private void updateState(GUIContext context) {
      Minecraft mc = Minecraft.getInstance();
      long now = System.currentTimeMillis();
      float seconds = this.secondsSinceLastFrame(now);
      boolean visible = this.computeVisible(mc);
      if (visible && !this.wasVisible) {
         this.entryStart = now;
      }

      this.wasVisible = visible;
      this.frameVisible = visible;
      if (!visible) {
         this.clusters = List.of();
      } else {
         this.entryProgress = this.entryProgress(now);
         float target;
         if (this.preview) {
            if (this.sway) {
               this.previewHeadingTarget = Mth.wrapDegrees(this.previewBase + 26.0F * (float)Math.sin((double)now / 3600.0));
               if (this.previewHeadingTarget < 0.0F) {
                  this.previewHeadingTarget += 360.0F;
               }
            }

            target = this.previewHeadingTarget;
         } else {
            Player player = mc.player;
            if (player == null) {
               this.clusters = List.of();
               return;
            }

            if (this.displayedPlayer != player) {
               this.displayedPlayer = player;
               this.heading.snapTo(CompassHeading.toHeading(player.getYRot()));
            }

            target = CompassHeading.toHeading(player.getViewYRot(context.partialTick));
         }

         this.heading.step(target, seconds, (float)((Double)CompassConfig.SMOOTHNESS.get()).doubleValue(), 0.0F);
         this.frameHeading = this.heading.smooth();
         this.halfRange = (float)((Integer)CompassConfig.RANGE.get()).intValue() / 2.0F;
         this.edgeHalfWidth = this.halfRange + 8.0F;
         List<SoundPrintAggregator.Hit> hits;
         if (this.preview) {
            hits = this.previewHits(now);
         } else {
            LocalPlayer playerx = mc.player;
            Vec3 eye = playerx.getEyePosition(context.partialTick);
            hits = SoundPrintEvents.TRACKER.frameHits(eye.x, eye.y, eye.z, (Boolean)SoundPrintConfig.VERTICAL_OFFSET.get());
         }

         this.clusters = SoundPrintAggregator.aggregate(
            hits,
            SoundPrintConfig.staggerMode(),
            (float)((Integer)SoundPrintConfig.MERGE_WINDOW.get()).intValue(),
            (float)((Integer)SoundPrintConfig.STAGGER_SPACING.get()).intValue()
         );
         this.effectiveScale = (float)((Double)SoundPrintConfig.SCALE.get()).doubleValue();
      }
   }

   private float secondsSinceLastFrame(long now) {
      if (this.lastFrameMillis == Long.MIN_VALUE) {
         this.lastFrameMillis = now;
         return 0.016F;
      } else {
         float seconds = (float)(now - this.lastFrameMillis) / 1000.0F;
         this.lastFrameMillis = now;
         return Mth.clamp(seconds, 0.0F, 0.1F);
      }
   }

   private float entryProgress(long now) {
      if (this.entryStart == Long.MIN_VALUE) {
         return 1.0F;
      } else {
         float p = (float)(now - this.entryStart) / 180.0F;
         if (p >= 1.0F) {
            this.entryStart = Long.MIN_VALUE;
            return 1.0F;
         } else {
            return 1.0F - (1.0F - p) * (1.0F - p);
         }
      }
   }

   private boolean computeVisible(Minecraft mc) {
      if (this.preview) {
         return true;
      } else if (!(Boolean)SoundPrintConfig.ENABLED.get()) {
         return false;
      } else if (!mc.options.hideGui && mc.level != null) {
         if (!(mc.getCameraEntity() instanceof Player camera) || camera.isSpectator()) {
            return false;
         }

         if (!(Boolean)SoundPrintConfig.FOLLOW_COMPASS.get()) {
            return true;
         } else {
            boolean debugOpen = isDebugScreenOpen(mc);
            return (Boolean)CompassConfig.ENABLED.get() && (!(Boolean)CompassConfig.HIDE_WITH_DEBUG.get() || !debugOpen);
         }
      } else {
         return false;
      }
   }

   private static boolean isDebugScreenOpen(Minecraft mc) {
      try {
         return mc.gui.getDebugOverlay().showDebugScreen();
      } catch (RuntimeException var2) {
         return false;
      }
   }

   public void drawBackgroundTexture(GUIContext context) {
      if (!this.preview) {
         this.updateState(context);
      }
   }

   public void drawBackgroundAdditional(GUIContext context) {
      if (!this.preview) {
         if (this.frameVisible && !this.clusters.isEmpty()) {
            GuiGraphics g = context.graphics;
            float width = this.getSizeWidth();
            float centerX = this.getPositionX() + width / 2.0F;
            // Baseline sits at the widget's vertical center so bars extend symmetrically
            // and never climb into the compass strip above.
            float baselineY = this.getPositionY() + this.getSizeHeight() / 2.0F + 1.0F;
            long now = System.currentTimeMillis();
            g.pose().pushPose();

            try {
               this.applyScale(g, centerX);
               this.drawSoundprint(g, this.getPositionX(), baselineY, width, centerX, now);
            } finally {
               g.pose().popPose();
            }
         }
      }
   }

   void renderStandalone(GuiGraphics g, float x, float y, float width, long now, float scaleOverride) {
      float seconds = this.lastFrameMillis == Long.MIN_VALUE ? 0.016F : Mth.clamp((float)(now - this.lastFrameMillis) / 1000.0F, 0.0F, 0.1F);
      this.lastFrameMillis = now;
      if (this.sway) {
         this.previewHeadingTarget = Mth.wrapDegrees(this.previewBase + 26.0F * (float)Math.sin((double)now / 3600.0));
         if (this.previewHeadingTarget < 0.0F) {
            this.previewHeadingTarget += 360.0F;
         }
      }

      this.heading.step(this.previewHeadingTarget, seconds, 15.0F, 0.0F);
      this.frameHeading = this.heading.smooth();
      this.halfRange = (float)((Integer)CompassConfig.RANGE.get()).intValue() / 2.0F;
      this.edgeHalfWidth = this.halfRange + 8.0F;
      this.clusters = SoundPrintAggregator.aggregate(
         this.previewHits(now),
         SoundPrintConfig.staggerMode(),
         (float)((Integer)SoundPrintConfig.MERGE_WINDOW.get()).intValue(),
         (float)((Integer)SoundPrintConfig.STAGGER_SPACING.get()).intValue()
      );
      this.entryProgress = 1.0F;
      this.effectiveScale = scaleOverride;
      if (!this.clusters.isEmpty()) {
         g.pose().pushPose();

         try {
            this.applyScale(g, x + width / 2.0F);
            this.drawSoundprint(g, x, y + 0.5F, width, x + width / 2.0F, now);
         } finally {
            g.pose().popPose();
         }
      }
   }

   private void applyScale(GuiGraphics g, float centerX) {
      float scale = this.effectiveScale;
      if (!(Math.abs(scale - 1.0F) < 0.01F)) {
         g.pose().translate(centerX, 0.0F, 0.0F);
         g.pose().scale(scale, scale, 1.0F);
         g.pose().translate(-centerX, 0.0F, 0.0F);
      }
   }

   private void drawSoundprint(GuiGraphics g, float x, float baselineY, float width, float centerX, long now) {
      float opacity = (float)((Double)SoundPrintConfig.OPACITY.get()).doubleValue();
      float alpha = opacity * this.entryProgress;
      if (!(alpha <= 0.01F)) {
         float entryOffsetY = (1.0F - this.entryProgress) * -3.0F;
         float baseline = baselineY + entryOffsetY;
         // The baseline breathes with the loudest live cluster and vanishes with
         // it - no static white line may remain once the sound is over.
         float maxEnergy = 0.0F;
         for (SoundPrintCluster cluster : this.clusters) {
            maxEnergy = Math.max(maxEnergy, cluster.energy());
         }

         if ((Boolean)SoundPrintConfig.BASELINE.get() && maxEnergy > 0.01F) {
            fillH(g, x + 2.0F, x + width - 2.0F, baseline, 1.0F, 15263976, alpha * 0.80F * maxEnergy);
         }

         int barCount = (Integer)SoundPrintConfig.BAR_COUNT.get();
         float barWidth = (float)((Integer)SoundPrintConfig.BAR_WIDTH.get()).intValue();
         float barGap = (float)((Integer)SoundPrintConfig.BAR_GAP.get()).intValue();
         float maxHeight = (float)((Integer)SoundPrintConfig.MAX_HEIGHT.get()).intValue();
         SoundPrintWidget.OscStyle osc = new SoundPrintWidget.OscStyle(
            (Boolean)SoundPrintConfig.OSCILLATE.get(),
            ((Double)SoundPrintConfig.OSC_SPEED.get()).floatValue(),
            (float)((Integer)SoundPrintConfig.OSC_AMP_MIN.get()).intValue(),
            (float)((Integer)SoundPrintConfig.OSC_AMP_MAX.get()).intValue(),
            ((Double)SoundPrintConfig.STACK_AMP_GAIN.get()).floatValue(),
            ((Double)SoundPrintConfig.STACK_ALPHA_GAIN.get()).floatValue(),
            (float)((Integer)SoundPrintConfig.RADIUS.get()).intValue(),
            ((Double)SoundPrintConfig.MIN_DECAY.get()).floatValue(),
            (Boolean)SoundPrintConfig.DISTANCE_DECAY.get(),
            now / 1000.0F
         );

         for (SoundPrintCluster cluster : this.clusters) {
            if (cluster.stagger()) {
               for (SoundPrintCluster.Member member : cluster.members()) {
                  this.drawCluster(
                     g,
                     width,
                     centerX,
                     member.bearing(),
                     member.energy(),
                     member.type(),
                     member.verticalOffset(),
                     member.distance(),
                     1,
                     alpha,
                     barCount,
                     barWidth,
                     barGap,
                     maxHeight,
                     baseline,
                     member.staggerOffset(),
                     0.82F,
                     osc
                  );
               }
            } else {
               this.drawCluster(
                  g,
                  width,
                  centerX,
                  cluster.bearing(),
                  cluster.energy(),
                  cluster.type(),
                  cluster.verticalOffset(),
                  cluster.nearestDistance(),
                  cluster.sourceCount(),
                  alpha,
                  barCount,
                  barWidth,
                  barGap,
                  maxHeight,
                  baseline,
                  0.0F,
                  1.0F,
                  osc
               );
            }
         }
      }
   }

   private void drawCluster(
      GuiGraphics g,
      float width,
      float centerX,
      float bearing,
      float energy,
      SoundPrintType type,
      float verticalOffset,
      float distance,
      int sourceCount,
      float alpha,
      int barCount,
      float barWidth,
      float barGap,
      float maxHeight,
      float baselineY,
      float staggerOffset,
      float sizeScale,
      SoundPrintWidget.OscStyle osc
   ) {
      float diff = CompassHeading.wrapDegrees((double)(bearing - this.frameHeading));
      if (!(Math.abs(diff) > this.edgeHalfWidth)) {
         float x = centerX + diff / this.halfRange * (width / 2.0F - 6.0F) + staggerOffset;
         float edgeFade = 1.0F
            - SoundPrintEnvelope.smoothstep(Mth.clamp((Math.abs(diff) - this.halfRange * 0.7F) / (this.edgeHalfWidth - this.halfRange * 0.7F), 0.0F, 1.0F));
         // v2.9.4: visibility floor raised - faint/distant clusters stay
         // clearly readable instead of dissolving into the background
         // (the v2.9.2 gradient look read as "too transparent" in play).
         float clusterAlpha = alpha * edgeFade * (0.55F + 0.45F * energy);
         // Same-bearing stacking: each extra source makes the cluster more opaque.
         clusterAlpha = SoundPrintOscillator.stackedAlpha(clusterAlpha, sourceCount, osc.stackAlphaGain);

         if (!(clusterAlpha <= 0.015F)) {
            // v2.9.1: no static height floor - bar height follows the live
            // energy straight down to zero, so the print collapses entirely
            // instead of leaving a stub of white bars behind.
            // v2.9.4: stacked co-located sources also make the whole print
            // TALLER - more sources = visibly bigger print, which together
            // with the opacity gain makes multi-source stacking obvious at
            // a glance (it previously only nudged alpha/wobble).
            float stackBoost = SoundPrintOscillator.stackHeightBoost(sourceCount);
            float heightBase = maxHeight * type.heightBoost() * sizeScale * energy * stackBoost;
            float bw = barWidth + (type == SoundPrintType.GUNSHOT ? 1.0F : 0.0F);
            float gap = Math.max(0.0F, barGap - (type == SoundPrintType.GUNSHOT ? 1.0F : 0.0F));
            int rgb = resolveColor(type);
            float[] asym = SoundPrintEnvelope.asymmetryFactors(verticalOffset);
            float totalW = (float)barCount * (bw + gap) - gap;
            float left = x - totalW / 2.0F;
            float distFactor = SoundPrintEnvelope.distanceDecay(distance, osc.radius, osc.minDecay, osc.distanceDecay);
            float ampPx = osc.enabled
               ? SoundPrintOscillator.amplitude(energy, distFactor, sourceCount, osc.stackAmpGain, osc.ampMin, osc.ampMax)
               : 0.0F;
            float clusterPhase = SoundPrintOscillator.clusterPhase(bearing);
            // v2.9.3: the per-side ceiling makes max_height mean what it
            // says - stacked multipliers (boost x asymmetry x wobble)
            // can no longer render bars ~3x past the configured height,
            // and a full-energy bar keeps dancing inside the ceiling
            // instead of pinning against a hard clamp.
            // v2.9.4: the stacking height boost grows the ceiling with the
            // same bounded multiplier (max +54% at 4+ merged sources) so a
            // stacked cluster legitimately reads as bigger, never runaway.
            float capSide = maxHeight * type.heightBoost() * sizeScale * stackBoost;
            // v2.9.2 persistent organic wobble: while the cluster is live every
            // bar keeps randomly stretching and shrinking on its own, and the
            // amplitude scales with the detected loudness - the print never
            // freezes; bars only settle when the sound itself dies.
            float wobbleAmp = osc.enabled
               ? SoundPrintOscillator.wobbleAmplitude(energy, distFactor, sourceCount, osc.stackAmpGain)
               : 0.0F;

            float maxUp = 0.0F;
            float maxDown = 0.0F;

            for (int i = 0; i < barCount; i++) {
               float bell = SoundPrintEnvelope.bell(i, barCount);
               if (!(bell < 0.06F)) {
                  float wobble = SoundPrintOscillator.wobble(osc.timeSec, osc.speedHz, clusterPhase, i);
                  // v2.9.4: outer bars keep more of the cluster alpha - the
                  // bell envelope used to dim them into near-invisibility.
                  float barAlpha = clusterAlpha * (0.62F + 0.38F * bell);
                  float bx = left + (float)i * (bw + gap);
                  float hUp = SoundPrintEnvelope.cappedBarHeight(heightBase * bell * asym[0], capSide, wobbleAmp, wobble);
                  float hDown = SoundPrintEnvelope.cappedBarHeight(heightBase * bell * asym[1], capSide, wobbleAmp, wobble);
                  if (hUp >= 1.0F) {
                     fillV(g, bx, bw, baselineY - hUp, hUp, rgb, barAlpha, true);
                     maxUp = Math.max(maxUp, hUp);
                  }

                  if (hDown >= 1.0F) {
                     fillV(g, bx, bw, baselineY + 1.5F, hDown, rgb, barAlpha, false);
                     maxDown = Math.max(maxDown, hDown);
                  }
               }
            }

            if ((Boolean)SoundPrintConfig.ARROWS.get()) {
               float strength = SoundPrintEnvelope.arrowStrength(verticalOffset) * energy;
               if (strength > 0.05F) {
                  float arrowAlpha = clusterAlpha * strength * 0.95F;
                  if (arrowAlpha > 0.02F) {
                     // v2.9.5: the arrow is anchored to the tallest bar
                     // ACTUALLY drawn on its side (plus a small gap and the
                     // px stretch), instead of a reach estimate derived from
                     // the pre-energy height base. The old estimate could
                     // sit INSIDE the bar stack - vertical-asymmetry boost
                     // stretches the bars on the source side well past
                     // heightBase - so the arrow vanished against the
                     // same-colored bars ("no arrow for sources above").
                     // The stretch (osc_amp px, scaled by intensity) keeps
                     // the louder-closer-reaches-further look.
                     float stretch = ampPx * 0.6F;
                     float halfW = 2.2F + 0.8F * strength;
                     if (verticalOffset > 0.0F) {
                        drawVArrow(g, x, baselineY - maxUp - 2.0F - stretch, halfW, 3.6F, rgb, arrowAlpha, true);
                     } else {
                        drawVArrow(g, x, baselineY + maxDown + 7.1F + stretch, halfW, 3.6F, rgb, arrowAlpha, false);
                     }
                  }
               }
            }
         }
      }
   }

   private static int resolveColor(SoundPrintType type) {
      int override = type == SoundPrintType.GUNSHOT ? (Integer)SoundPrintConfig.COLOR_GUNSHOT.get() : (Integer)SoundPrintConfig.COLOR_FOOTSTEP.get();
      return override >= 0 ? override : type.defaultRgb();
   }

   private static int rgba(int rgb, float alpha) {
      return Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24 | rgb & 16777215;
   }

   /**
    * v2.9.4 modern bar style: sub-pixel geometry + layered soft glow.
    *
    * Sub-pixel: the pose matrix carries fractional positions AND sizes, so
    * the continuous wobble animates on a smooth grid instead of snapping
    * in whole-pixel steps - that 1px stepping was the remaining "pixelated"
    * look of v2.9.2. Glow: a two-layer halo (wide faint outer ring, bright
    * inner ring) fakes a bloom and visually softens the rectangle edges.
    * Tip: a translucent cap row plus a vertical gradient (solid at the
    * baseline, gently translucent towards the tip).
    */
   private static void fillV(GuiGraphics g, float x, float w, float y, float h, int rgb, float alpha, boolean tipUp) {
      if (alpha <= 0.01F || h < 0.5F || w <= 0.0F) {
         return;
      }

      // two-layer soft halo
      fillQuadF(g, x - 1.5F, y - 1.5F, w + 3.0F, h + 3.0F, rgba(rgb, alpha * 0.09F));
      fillQuadF(g, x - 0.5F, y - 0.5F, w + 1.0F, h + 1.0F, rgba(rgb, alpha * 0.22F));

      int base = rgba(rgb, alpha);
      int tip = rgba(rgb, alpha * 0.50F);
      int capRow = rgba(rgb, alpha * 0.62F);
      if (tipUp) {
         // tip at the top: gradient body + translucent cap row
         if (h > 1.5F) {
            fillGradientF(g, x, y + 1.0F, w, h - 1.0F, tip, base);
            fillQuadF(g, x, y, w, 1.0F, capRow);
         } else {
            fillQuadF(g, x, y, w, h, rgba(rgb, alpha * 0.75F));
         }
      } else {
         // tip at the bottom
         if (h > 1.5F) {
            fillGradientF(g, x, y, w, h - 1.0F, base, tip);
            fillQuadF(g, x, y + h - 1.0F, w, 1.0F, capRow);
         } else {
            fillQuadF(g, x, y, w, h, rgba(rgb, alpha * 0.75F));
         }
      }
   }

   /** Solid rectangle at fractional coordinates via the pose matrix. */
   private static void fillQuadF(GuiGraphics g, float x, float y, float w, float h, int color) {
      if (w <= 0.0F || h <= 0.0F) {
         return;
      }

      g.pose().pushPose();
      g.pose().translate(x, y, 0.0F);
      g.pose().scale(w, h, 1.0F);
      g.fill(0, 0, 1, 1, color);
      g.pose().popPose();
   }

   /** Vertical gradient rectangle at fractional coordinates via the pose matrix. */
   private static void fillGradientF(GuiGraphics g, float x, float y, float w, float h, int colorFrom, int colorTo) {
      if (w <= 0.0F || h <= 0.0F) {
         return;
      }

      g.pose().pushPose();
      g.pose().translate(x, y, 0.0F);
      g.pose().scale(w, h, 1.0F);
      g.fillGradient(0, 0, 1, 1, colorFrom, colorTo);
      g.pose().popPose();
   }

   /** v2.9.2 modern baseline: solid core with softly fading ends plus a one-pixel under-shadow. */
   private static void fillH(GuiGraphics g, float x1, float x2, float y, float h, int rgb, float alpha) {
      if (alpha <= 0.01F) {
         return;
      }

      int y1 = Math.round(y);
      int y2 = Math.round(y + h);
      float fade = Math.min(12.0F, (x2 - x1) / 3.0F);
      g.fill(Math.round(x1), y1, Math.round(x1 + fade), y2, rgba(rgb, alpha * 0.30F));
      g.fill(Math.round(x1 + fade), y1, Math.round(x2 - fade), y2, rgba(rgb, alpha));
      g.fill(Math.round(x2 - fade), y1, Math.round(x2), y2, rgba(rgb, alpha * 0.30F));
      if (alpha > 0.1F) {
         int shadow = rgba(rgb, alpha * 0.12F);
         if (shadow != 0) {
            g.fill(Math.round(x1 + fade), y2, Math.round(x2 - fade), y2 + 1, shadow);
         }
      }
   }

   private static void drawVArrow(GuiGraphics g, float cx, float cy, float halfW, float h, int rgb, float alpha, boolean up) {
      int c = rgba(rgb, alpha);
      int rows = Math.max(3, Math.round(h));

      for (int i = 0; i < rows; i++) {
         float t = ((float)i + 0.5F) / (float)rows;
         // v2.9.5: rows run top -> bottom (y = cy - h + i), so an UP arrow
         // opens its arms downward (narrow tip on top, widest base at the
         // bottom) and a DOWN arrow mirrors it. The v2.9.2 ternary was
         // inverted, which made every arrow point the opposite way of the
         // sound source.
         float spread = SoundPrintEnvelope.arrowRowSpread(up, t);
         float armX = halfW * spread;
         float y = cy - h + (float)i;
         int y1 = Math.round(y);
         int y2 = y1 + 1;
         g.fill(Math.round(cx - armX - 0.5F), y1, Math.round(cx - armX + 0.5F), y2, c);
         g.fill(Math.round(cx + armX - 0.5F), y1, Math.round(cx + armX + 0.5F), y2, c);
      }
   }

   private List<SoundPrintAggregator.Hit> previewHits(long now) {
      float h = this.previewHeadingTarget;
      // Preview energies breathe slowly so the settings preview demonstrates
      // the live-vibration look of the real HUD.
      float breath0 = 0.72F * (0.78F + 0.22F * (float)Math.sin((double)now / 650.0));
      float breath1 = 0.45F * (0.78F + 0.22F * (float)Math.sin((double)now / 650.0 + 2.1));
      float breath2 = 0.95F * (0.72F + 0.28F * (float)Math.sin((double)now / 430.0 + 0.9));
      return List.of(
         new SoundPrintAggregator.Hit(wrap360(h + 24.0F), breath0, SoundPrintType.FOOTSTEP, -0.55F, 6.0F),
         new SoundPrintAggregator.Hit(wrap360(h - 38.0F), breath1, SoundPrintType.FOOTSTEP, -0.2F, 17.0F),
         new SoundPrintAggregator.Hit(wrap360(h + 61.0F), breath2, SoundPrintType.GUNSHOT, 0.6F, 11.0F)
      );
   }

   private static final record OscStyle(
      boolean enabled,
      float speedHz,
      float ampMin,
      float ampMax,
      float stackAmpGain,
      float stackAlphaGain,
      float radius,
      float minDecay,
      boolean distanceDecay,
      float timeSec
   ) {
   }

   private static float wrap360(float v) {
      float r = v % 360.0F;
      return r < 0.0F ? r + 360.0F : r;
   }
}
