package dev.draginventory.client.compass;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * 方位条客户端事件：
 * <ul>
 *   <li>注册游戏内指令（/herracompass 别名 /compass）</li>
 *   <li>client tick 驱动配置防抖落盘（拖动滑条时静默 500ms 后统一写盘）</li>
 *   <li>退出世界时立即落盘兜底，避免 500ms 窗口内的调整丢失</li>
 * </ul>
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

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        CompassConfig.tickSave();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        CompassConfig.flush();
    }
}
