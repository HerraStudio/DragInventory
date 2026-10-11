package dev.draginventory.client.herra;

import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * HERRA 统一入口公开 API（供其他 HERRA 模组软联动使用）。
 *
 * <p>使用约定与 {@code CompassApi} 一致：调用方通过反射探测本类是否存在
 * （或依赖 soft dependency），避免与 Drag Inventory 产生编译期硬耦合。
 * 模组未加载时这些方法不会被调用。</p>
 *
 * <p>典型接入（外部模组客户端初始化时）：
 * <pre>{@code
 * HerraApi.registerModule(new MyModule());  // 出现在 /herra 主界面与 list 中
 * }</pre></p>
 */
public final class HerraApi {

    private HerraApi() {}

    /** 注册模块（同 id 重复注册保持首次；幂等）。 */
    public static void registerModule(HerraModule module) {
        HerraModuleRegistry.register(module);
    }

    /** 注销模块（模组卸载时调用）。 */
    public static void unregisterModule(String id) {
        HerraModuleRegistry.unregister(id);
    }

    /** 已注册模块快照（调试用；返回副本）。 */
    public static List<HerraModule> modules() {
        return HerraModuleRegistry.modules();
    }

    /** 打开 HERRA 主界面（等价 /herra 指令）。 */
    public static void openHub() {
        Minecraft.getInstance().tell(() ->
                Minecraft.getInstance().setScreen(HerraHubScreen.create()));
    }
}
