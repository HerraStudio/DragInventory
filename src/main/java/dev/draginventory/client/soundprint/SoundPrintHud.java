package dev.draginventory.client.soundprint;

import com.lowdragmc.lowdraglib2.gui.hud.ModularHudLayer;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.draginventory.client.compass.CompassConfig;
import dev.draginventory.client.compass.CompassWidget;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

final class SoundPrintHud implements ModularHudLayer {
   private static final float BOTTOM_MARGIN_PX = 44.0F;
   private ModularUI ui;
   private SoundPrintWidget widget;
   private int cachedWidth = -1;
   private int cachedOffsetX = Integer.MIN_VALUE;
   private int cachedOffsetY = Integer.MIN_VALUE;
   private int cachedHeight = -1;
   private String cachedPreset = "";
   private int cachedScreenW = -1;
   private int cachedScreenH = -1;
   private static volatile boolean reflectionBroken;
   private static volatile Field compassLayerField;
   private static volatile Field compassWidgetField;

   private static CompassWidget liveCompassWidget() {
      if (reflectionBroken) {
         return null;
      } else {
         try {
            if (compassLayerField == null || compassWidgetField == null) {
               Field layerField = Class.forName("dev.draginventory.client.compass.CompassHudRegistration").getDeclaredField("LAYER");
               layerField.setAccessible(true);
               Field widgetField = Class.forName("dev.draginventory.client.compass.CompassHud").getDeclaredField("widget");
               widgetField.setAccessible(true);
               compassLayerField = layerField;
               compassWidgetField = widgetField;
            }

            Object layer = compassLayerField.get(null);
            if (layer == null) {
               return null;
            } else {
               return compassWidgetField.get(layer) instanceof CompassWidget cw ? cw : null;
            }
         } catch (RuntimeException | ReflectiveOperationException var3) {
            reflectionBroken = true;
            return null;
         }
      }
   }

   private static float[] compassLayoutGeometry() {
      CompassWidget live = liveCompassWidget();
      if (live != null) {
         float w = live.getSizeWidth();
         float h = live.getSizeHeight();
         if (w > 0.0F && h > 0.0F) {
            return new float[]{live.getPositionY(), h, w};
         }
      }

      float width = (float)((Integer)CompassConfig.BAR_WIDTH.get()).intValue();
      float height = (float)Mth.clamp(Math.round(CompassWidget.currentStyle().widgetHeight()), 40, 72);
      return new float[]{(float)((Integer)CompassConfig.OFFSET_Y.get()).intValue(), height, width};
   }

   public ModularUI getModularUI() {
      if (this.ui == null) {
         UIElement root = new UIElement().layout(l -> l.widthPercent(100.0F).heightPercent(100.0F));
         this.widget = new SoundPrintWidget(false);
         root.addChild(this.widget);
         this.ui = ModularUI.of(UI.of(root));
      }

      this.syncLayout();
      return this.ui;
   }

   private void syncLayout() {
      String preset = (String)SoundPrintConfig.POSITION_PRESET.get();
      boolean belowCompass = "below_compass".equals(preset);
      float ownScale = Math.max(0.05F, (float)((Double)SoundPrintConfig.SCALE.get()).doubleValue());
      float compassScale = (float)((Double)CompassConfig.SCALE.get()).doubleValue();
      int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
      int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();

      // halfBand in unscaled draw units: bars extend symmetrically around the
      // baseline which sits at the widget's vertical centre.
      float maxHeight = (float)((Integer)SoundPrintConfig.MAX_HEIGHT.get()).intValue();
      float ampMax = (float)((Integer)SoundPrintConfig.OSC_AMP_MAX.get()).intValue();
      float halfBand = maxHeight * 1.35F * 1.8F + ampMax + 3.0F;
      int height = Math.round(halfBand * 2.0F + 2.0F);

      // All user-facing position values are VISUAL (scaled) pixels.
      int offsetXVisual = (Integer)SoundPrintConfig.OFFSET_X.get();
      int offsetYVisual = (Integer)SoundPrintConfig.OFFSET_Y.get();
      int spacingVisual = (Integer)SoundPrintConfig.SPACING.get();
      int widthVisual;
      int topVisual;
      switch (preset) {
         case "top_center" -> {
            widthVisual = (Integer)SoundPrintConfig.POSITION_WIDTH.get();
            topVisual = Math.max(0, offsetYVisual);
         }
         case "center" -> {
            widthVisual = (Integer)SoundPrintConfig.POSITION_WIDTH.get();
            topVisual = Math.round((float)screenH / 2.0F - halfBand * ownScale) + offsetYVisual;
         }
         case "bottom_center" -> {
            widthVisual = (Integer)SoundPrintConfig.POSITION_WIDTH.get();
            topVisual = Math.round((float)screenH - BOTTOM_MARGIN_PX - halfBand * 2.0F * ownScale) + offsetYVisual;
         }
         default -> {
            // below_compass: band top hugs the compass' visual bottom edge.
            float[] compass = compassLayoutGeometry();
            float compassTop = compass[0];
            float compassHeight = compass[1];
            float compassWidth = compass[2];
            float compassBottomVisual = (compassTop + compassHeight) * compassScale;
            widthVisual = Math.round(compassWidth * compassScale);
            topVisual = Math.round(compassBottomVisual) + spacingVisual + offsetYVisual;
            // Follow the compass' horizontal offset as well (visual px).
            offsetXVisual = Math.round(((Integer)CompassConfig.OFFSET_X.get()).intValue() * compassScale) + offsetXVisual;
         }
      }

      int width = Math.max(60, Math.round((float)widthVisual / ownScale));
      int offsetY = Math.round((float)Math.max(-400, topVisual) / ownScale);
      int offsetX = Math.round((float)offsetXVisual / ownScale);
      if (width != this.cachedWidth
         || offsetX != this.cachedOffsetX
         || offsetY != this.cachedOffsetY
         || height != this.cachedHeight
         || !preset.equals(this.cachedPreset)
         || screenW != this.cachedScreenW
         || screenH != this.cachedScreenH) {
         this.cachedWidth = width;
         this.cachedOffsetX = offsetX;
         this.cachedOffsetY = offsetY;
         this.cachedHeight = height;
         this.cachedPreset = preset;
         this.cachedScreenW = screenW;
         this.cachedScreenH = screenH;
         this.widget
            .layout(
               l -> l.positionType(TaffyPosition.ABSOLUTE)
                     .leftPercent(50.0F)
                     .marginLeft((float)(-width) / 2.0F + (float)offsetX)
                     .top((float)offsetY)
                     .width((float)width)
                     .height((float)height)
            );
      }
   }
}
