package dev.draginventory.client.soundprint;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(
   modid = "draginventory",
   value = {Dist.CLIENT},
   bus = Bus.MOD
)
public final class SoundPrintHudRegistration {
   private static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath("draginventory", "soundprint");
   private static final SoundPrintHud LAYER = new SoundPrintHud();

   private SoundPrintHudRegistration() {
   }

   @SubscribeEvent
   public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
      event.registerAboveAll(LAYER_ID, LAYER);
   }
}
