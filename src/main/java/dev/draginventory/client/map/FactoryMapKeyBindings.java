package dev.draginventory.client.map;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class FactoryMapKeyBindings {
    public static final KeyMapping OPEN_MAP = new KeyMapping(
            "key.draginventory.map",
            GLFW.GLFW_KEY_M,
            "key.categories.draginventory");

    private FactoryMapKeyBindings() {}

    @EventBusSubscriber(modid = "draginventory", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registrar {
        private Registrar() {}

        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(OPEN_MAP);
        }
    }
}
