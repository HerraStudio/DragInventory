package dev.draginventory.client.map;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "draginventory", value = Dist.CLIENT)
public final class FactoryMapClientEvents {
    private FactoryMapClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (FactoryMapKeyBindings.OPEN_MAP.consumeClick()) {
            if (mc.screen != null) continue;
            if (mc.player == null || mc.level == null || !mc.player.isAlive() || mc.player.isSpectator()) continue;
            mc.setScreen(FactoryMapScreen.create());
        }
    }
}
