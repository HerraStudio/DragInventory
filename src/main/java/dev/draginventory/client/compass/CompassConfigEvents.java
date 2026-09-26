package dev.draginventory.client.compass;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * 方位条配置的 MOD 总线事件：与 FML 配置系统的加载/重载/卸载对账。
 *
 * <p>关键背景（v1.5.2 修复的保存竞态）：{@code SPEC.save()} 写盘后，FML 的
 * 文件监视线程会异步重读文件并<b>整体替换</b>内存配置映射。若用户在
 * “落盘 → 监视线程重载完成”的窗口内继续修改配置，新值会被旧文件内容覆盖，
 * 表现为设置界面“开关点了没反应 / 配色跳过去又跳回来 / 连跳两下”。</p>
 *
 * <p>对策见 {@link CompassConfig#onConfigReloaded}：写前日志 + 重载对账重放。
 * 注意 Reloading 事件可能出现在两个时机：save() 内同步触发（主线程）与
 * 监视线程异步触发——对账逻辑对两者都幂等。</p>
 */
@EventBusSubscriber(modid = "draginventory", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CompassConfigEvents {

    private CompassConfigEvents() {}

    @SubscribeEvent
    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (isCompassConfig(event)) {
            CompassConfig.onConfigReloaded();
        }
    }

    @SubscribeEvent
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (isCompassConfig(event)) {
            CompassConfig.onConfigReloaded();
        }
    }

    @SubscribeEvent
    public static void onConfigUnloading(ModConfigEvent.Unloading event) {
        if (isCompassConfig(event)) {
            CompassConfig.onConfigUnloaded();
        }
    }

    private static boolean isCompassConfig(ModConfigEvent event) {
        return CompassConfig.fileName().equals(event.getConfig().getFileName());
    }
}
