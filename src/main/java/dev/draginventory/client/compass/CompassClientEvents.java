package dev.draginventory.client.compass;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * 方位条客户端事件：注册游戏内指令。
 * 与 HUD 层注册（Mod 总线）分离，各管各的事件通道。
 */
@EventBusSubscriber(modid = "draginventory", value = Dist.CLIENT)
public final class CompassClientEvents {

    private CompassClientEvents() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        for (var root : CompassCommands.buildRoots()) {
            event.getDispatcher().register(root);
        }
    }
}
