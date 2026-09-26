package dev.draginventory;
import dev.draginventory.client.compass.CompassConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.bus.api.IEventBus;
@Mod("draginventory")
public final class DragInventory {
    public DragInventory(IEventBus modBus, ModContainer modContainer) {
        PlayerStamina.ATTACHMENTS.register(modBus);
        // HERRA 方位条：独立客户端配置（draginventory-compass-client.toml），
        // 注册入口集中在此，模块内部不再依赖主类。
        modContainer.registerConfig(CompassConfig.type(), CompassConfig.SPEC, CompassConfig.fileName());
    }
}
