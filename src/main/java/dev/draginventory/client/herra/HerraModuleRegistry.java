package dev.draginventory.client.herra;

import dev.draginventory.client.compass.CompassConfig;
import dev.draginventory.client.compass.CompassSettingsScreen;
import dev.draginventory.client.map.MapConfig;
import dev.draginventory.client.map.MapSettingsScreen;
import dev.draginventory.client.soundprint.SoundPrintConfig;
import dev.draginventory.client.soundprint.SoundPrintSettingsScreen;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * HERRA 模块注册表：统一设置入口的数据源。
 *
 * <p>内置七模块在类加载时静态注册（方位条 / 战术地图 / 声纹三个可配置模块
 * 接真实配置与设置界面；体力条 / 快捷对齐 / 枪械 HUD / 口袋轮盘当前无配置
 * 系统与设置 GUI，作为占位条目接入——<b>不改这些模块的任何代码</b>，仅由
 * 本注册表用只读描述接入；未来它们获得配置时替换占位实现即可）。</p>
 *
 * <p>外部 HERRA 模组经 {@link HerraApi} 注册（CopyOnWrite，注册时机任意）。
 * 同 id 重复注册保持首次（先到先得，防止覆盖内置模块）。</p>
 */
public final class HerraModuleRegistry {

    private static final CopyOnWriteArrayList<HerraModule> MODULES = new CopyOnWriteArrayList<>();

    static {
        // ==================== 可配置模块（真实开关 + 设置界面） ====================
        register(new SimpleModule(
                "compass",
                () -> Component.translatable("draginventory.herra.module.compass"),
                () -> Component.translatable("draginventory.herra.module.compass.desc"),
                true,
                () -> open(CompassSettingsScreen.create()),
                () -> CompassConfig.ENABLED.get(),
                v -> CompassConfig.set(CompassConfig.ENABLED, v)));
        register(new SimpleModule(
                "map",
                () -> Component.translatable("draginventory.herra.module.map"),
                () -> Component.translatable("draginventory.herra.module.map.desc"),
                true,
                () -> open(MapSettingsScreen.create()),
                () -> MapConfig.ENABLED.get(),
                v -> MapConfig.set(MapConfig.ENABLED, v)));
        register(new SimpleModule(
                "soundprint",
                () -> Component.translatable("draginventory.herra.module.soundprint"),
                () -> Component.translatable("draginventory.herra.module.soundprint.desc"),
                true,
                () -> open(SoundPrintSettingsScreen.create()),
                () -> SoundPrintConfig.ENABLED.get(),
                v -> SoundPrintConfig.set(SoundPrintConfig.ENABLED, v)));

        // ==================== 占位模块（无配置系统：只展示，不可开关） ====================
        register(placeholder("stamina",
                "draginventory.herra.module.stamina", "draginventory.herra.module.stamina.desc"));
        register(placeholder("hotbar",
                "draginventory.herra.module.hotbar", "draginventory.herra.module.hotbar.desc"));
        register(placeholder("weapon",
                "draginventory.herra.module.weapon", "draginventory.herra.module.weapon.desc"));
        register(placeholder("wheel",
                "draginventory.herra.module.wheel", "draginventory.herra.module.wheel.desc"));
    }

    private HerraModuleRegistry() {}

    /** 注册模块（同 id 静默忽略，保持首次注册）。 */
    public static void register(HerraModule module) {
        if (module == null || module.id() == null || module.id().isBlank()) return;
        for (HerraModule existing : MODULES) {
            if (existing.id().equals(module.id())) return;
        }
        MODULES.add(module);
    }

    /** 注销模块（外部模组卸载场景）。 */
    public static void unregister(String id) {
        if (id == null) return;
        MODULES.removeIf(m -> id.equals(m.id()));
    }

    /** 模块快照（界面与指令遍历用，顺序稳定：注册序）。 */
    public static List<HerraModule> modules() {
        return List.copyOf(MODULES);
    }

    /** 按 id 查找。 */
    public static Optional<HerraModule> byId(String id) {
        if (id == null) return Optional.empty();
        for (HerraModule module : MODULES) {
            if (module.id().equals(id)) return Optional.of(module);
        }
        return Optional.empty();
    }

    private static HerraModule placeholder(String id, String titleKey, String descKey) {
        return new SimpleModule(
                id,
                () -> Component.translatable(titleKey),
                () -> Component.translatable(descKey),
                false,
                () -> {},
                () -> true,
                SimpleModule.NO_OP);
    }

    private static void open(net.minecraft.client.gui.screens.Screen screen) {
        Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(screen));
    }

    /** 通用实现：全部行为由 lambda 提供（record 的字段名与接口方法同名冲突，用普通类）。 */
    private static final class SimpleModule implements HerraModule {
        private static final SetEnabledConsumer NO_OP = v -> {};

        private final String id;
        private final TitleSupplier title;
        private final TitleSupplier description;
        private final boolean hasSettings;
        private final Runnable openSettings;
        private final EnabledSupplier enabled;
        private final SetEnabledConsumer setEnabled;

        SimpleModule(String id, TitleSupplier title, TitleSupplier description,
                     boolean hasSettings, Runnable openSettings,
                     EnabledSupplier enabled, SetEnabledConsumer setEnabled) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.hasSettings = hasSettings;
            this.openSettings = openSettings;
            this.enabled = enabled;
            this.setEnabled = setEnabled;
        }

        @Override public String id() { return id; }

        @Override public Component title() { return title.get(); }

        @Override public Component description() { return description.get(); }

        @Override public boolean hasSettings() { return hasSettings; }

        @Override public void openSettings() { openSettings.run(); }

        @Override public boolean isEnabled() { return enabled.get(); }

        @Override public boolean toggleable() { return setEnabled != NO_OP; }

        @Override public void setEnabled(boolean value) { setEnabled.accept(value); }
    }

    @FunctionalInterface private interface TitleSupplier { Component get(); }

    @FunctionalInterface private interface EnabledSupplier { boolean get(); }

    @FunctionalInterface private interface SetEnabledConsumer { void accept(boolean value); }
}
