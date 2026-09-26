package dev.draginventory;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
@Mod("draginventory")
public final class DragInventory {
    public DragInventory(IEventBus modBus) {
        PlayerStamina.ATTACHMENTS.register(modBus);
    }
}
