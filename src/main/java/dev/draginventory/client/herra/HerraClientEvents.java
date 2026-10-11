package dev.draginventory.client.herra;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * HERRA 统一入口的事件接线（NeoForge 总线）：注册 /herra 主指令。
 * 各模块自身的指令（/com、地图、声纹等）保持独立注册不动——/herra 是聚合层，
 * 不是替代层。
 */
@EventBusSubscriber(modid = "draginventory", value = Dist.CLIENT)
public final class HerraClientEvents {

    private HerraClientEvents() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(HerraCommands.buildRoot());
    }
}
