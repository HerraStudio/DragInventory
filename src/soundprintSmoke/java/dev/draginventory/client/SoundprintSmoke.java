package dev.draginventory.client;

import dev.draginventory.client.herra.HerraModuleRegistry;
import dev.draginventory.client.soundprint.SoundPrintConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.LoggerFactory;

/**
 * 声纹 + HERRA 统一入口的启动自检（仅 soundprintSmoke 构建包含，发布 jar 不含）。
 *
 * <p>检验三件事：声纹配置被 FML 接受并落盘（注册链 OK）、HERRA 模块注册表的
 * 内置七模块可用（静态初始化链 OK）、声纹 / HERRA 事件类完成类加载（总线订阅 OK）。
 * 全部通过输出 SOUNDPRINT_SMOKE_PASS。</p>
 */
@EventBusSubscriber(modid = "draginventory", value = Dist.CLIENT)
public final class SoundprintSmoke {
    private static boolean done;

    private SoundprintSmoke() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (done || mc.screen == null || mc.getOverlay() != null) return;
        done = true;
        try {
            // 1) 声纹配置已注册并加载（SPEC 可读、值在范围内）。
            if (!SoundPrintConfig.ENABLED.get()) {
                throw new AssertionError("soundprint config not loaded (enabled should default true)");
            }
            int radius = SoundPrintConfig.RADIUS.get();
            if (radius < 4 || radius > 64) {
                throw new AssertionError("soundprint radius out of range: " + radius);
            }
            // 2) HERRA 注册表：内置七模块 + 可配置三模块有设置界面。
            if (HerraModuleRegistry.modules().size() < 7) {
                throw new AssertionError("expected >=7 built-in herra modules, got "
                        + HerraModuleRegistry.modules().size());
            }
            long configurable = HerraModuleRegistry.modules().stream()
                    .filter(m -> m.hasSettings() && m.toggleable()).count();
            if (configurable < 3) {
                throw new AssertionError("expected >=3 configurable modules, got " + configurable);
            }
            if (HerraModuleRegistry.byId("soundprint").isEmpty()) {
                throw new AssertionError("soundprint module missing from registry");
            }
            // 3) 配置文件已落盘（FML 接受注册）。
            Path file = mc.gameDirectory.toPath()
                    .resolve("config").resolve(SoundPrintConfig.fileName());
            if (!Files.exists(file)) {
                throw new AssertionError("config file not created: " + file);
            }
            LoggerFactory.getLogger("SoundprintSmoke")
                    .info("SOUNDPRINT_SMOKE_PASS: config ok (radius={}), herra modules={}, file created",
                            radius, HerraModuleRegistry.modules().size());
        } finally {
            mc.stop();
        }
    }
}
