package dev.draginventory.client.herra;

import net.minecraft.network.chat.Component;

/**
 * HERRA 模块描述：统一设置入口（/herra 指令与 HERRA 主界面）的接入单元。
 *
 * <p>本模组内置的七个模块（方位条 / 战术地图 / 声纹 / 体力条 / 快捷对齐 /
 * 枪械 HUD / 口袋轮盘）在 {@link HerraModuleRegistry} 静态注册；未来其他
 * HERRA 模组经 {@link HerraApi#registerModule} 接入同一入口，无需改动本模组。</p>
 *
 * <p>实现约束：所有方法必须可在客户端任意线程安全调用（界面渲染线程 / 指令
 * 线程均为客户端主线程，实际无并发要求，但实现不应阻塞）；{@link #openSettings}
 * 内部自行 {@code Minecraft.tell} 切换到主线程。</p>
 */
public interface HerraModule {

    /** 模块 id（稳定标识，用于指令参数与注册去重，小写字母与下划线）。 */
    String id();

    /** 显示名（界面列表 / 详情标题）。 */
    Component title();

    /** 一句话描述（详情页）。 */
    Component description();

    /** 是否提供设置界面（决定“打开设置”按钮与 /herra open 是否可用）。 */
    boolean hasSettings();

    /** 打开设置界面（仅在 {@link #hasSettings} 为 true 时被调用）。 */
    void openSettings();

    /** 当前启用状态（不可开关的模块恒 true）。 */
    boolean isEnabled();

    /** 是否可开关（无配置的内置模块为 false，仅展示状态）。 */
    boolean toggleable();

    /** 切换启用状态（不可开关的模块为 no-op）。 */
    void setEnabled(boolean value);
}
