package dev.draginventory.client.soundprint;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.config.ModConfigEvent.Loading;
import net.neoforged.fml.event.config.ModConfigEvent.Reloading;
import net.neoforged.fml.event.config.ModConfigEvent.Unloading;

@EventBusSubscriber(
   modid = "draginventory",
   value = {Dist.CLIENT},
   bus = Bus.MOD
)
public final class SoundPrintConfigEvents {
   private SoundPrintConfigEvents() {
   }

   @SubscribeEvent
   public static void onConfigLoading(Loading event) {
      if (isSoundprintConfig(event)) {
         retuneShapeDefaults();
         retuneV294Defaults();
         retuneV295Defaults();
         SoundPrintConfig.onConfigReloaded();
      }
   }

   @SubscribeEvent
   public static void onConfigReloading(Reloading event) {
      if (isSoundprintConfig(event)) {
         SoundPrintConfig.onConfigReloaded();
      }
   }

   @SubscribeEvent
   public static void onConfigUnloading(Unloading event) {
      if (isSoundprintConfig(event)) {
         SoundPrintConfig.onConfigUnloaded();
      }
   }

   private static boolean isSoundprintConfig(ModConfigEvent event) {
      return SoundPrintConfig.fileName().equals(event.getConfig().getFileName());
   }

   private static void retuneShapeDefaults() {
      if (!(Boolean)SoundPrintConfig.SHAPE_RETUNED.get()) {
         if ((Integer)SoundPrintConfig.BAR_COUNT.get() == 9) {
            SoundPrintConfig.set(SoundPrintConfig.BAR_COUNT, 14);
         }

         if ((Integer)SoundPrintConfig.BAR_GAP.get() == 1) {
            SoundPrintConfig.set(SoundPrintConfig.BAR_GAP, 3);
         }

         SoundPrintConfig.set(SoundPrintConfig.SHAPE_RETUNED, true);
         SoundPrintConfig.flush();
      }
   }

   /**
    * v2.9.4 one-time default retune (same discipline as shape_retuned):
    * max_height 24 -> 30 and stack_alpha_gain 0.22 -> 0.30. Only values
    * still sitting at the OLD defaults are bumped, so any deliberate
    * customization survives; after this single pass the marker guards
    * future manual changes permanently.
    */
   private static void retuneV294Defaults() {
      if (!(Boolean)SoundPrintConfig.V294_RETUNED.get()) {
         if ((Integer)SoundPrintConfig.MAX_HEIGHT.get() == 24) {
            SoundPrintConfig.set(SoundPrintConfig.MAX_HEIGHT, 30);
         }

         if ((Double)(SoundPrintConfig.STACK_ALPHA_GAIN.get()) == 0.22D) {
            SoundPrintConfig.set(SoundPrintConfig.STACK_ALPHA_GAIN, 0.30D);
         }

         SoundPrintConfig.set(SoundPrintConfig.V294_RETUNED, true);
         SoundPrintConfig.flush();
      }
   }

   /**
    * v2.9.5 one-time default retune (user's field-tested preset, same
    * discipline as shape_retuned / v294_retuned): bar_count 14 -> 21,
    * bar_width 2 -> 1, detect_own_gunshots true -> false, osc_speed_hz
    * 3.5 -> 5.6, osc_amp_max_px 9 -> 24, offset_y_px -174 -> -143.
    * Only values still sitting at the OLD defaults are bumped, so any
    * deliberate customization survives; after this single pass the marker
    * guards future manual changes permanently.
    */
   private static void retuneV295Defaults() {
      if (!(Boolean)SoundPrintConfig.V295_RETUNED.get()) {
         if ((Integer)SoundPrintConfig.BAR_COUNT.get() == 14) {
            SoundPrintConfig.set(SoundPrintConfig.BAR_COUNT, 21);
         }

         if ((Integer)SoundPrintConfig.BAR_WIDTH.get() == 2) {
            SoundPrintConfig.set(SoundPrintConfig.BAR_WIDTH, 1);
         }

         if (SoundPrintConfig.OWN_GUNSHOTS.get()) {
            SoundPrintConfig.set(SoundPrintConfig.OWN_GUNSHOTS, false);
         }

         if ((Double)SoundPrintConfig.OSC_SPEED.get() == 3.5D) {
            SoundPrintConfig.set(SoundPrintConfig.OSC_SPEED, 5.6D);
         }

         if ((Integer)SoundPrintConfig.OSC_AMP_MAX.get() == 9) {
            SoundPrintConfig.set(SoundPrintConfig.OSC_AMP_MAX, 24);
         }

         if ((Integer)SoundPrintConfig.OFFSET_Y.get() == -174) {
            SoundPrintConfig.set(SoundPrintConfig.OFFSET_Y, -143);
         }

         SoundPrintConfig.set(SoundPrintConfig.V295_RETUNED, true);
         SoundPrintConfig.flush();
      }
   }
}
