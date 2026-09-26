package dev.draginventory.client.compass;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;

/**
 * 方位条模块的内部状态中心：
 * <ul>
 *   <li>实时朝向弹簧状态（HUD 控件每帧更新，供 API 查询）</li>
 *   <li>外部标记提供者注册表</li>
 *   <li>测试标点（指令创建，重启失效，不落盘）</li>
 *   <li>基数方位监听器（朝向进入东南西北吸附区的事件回调）</li>
 * </ul>
 */
final class CompassHub {
    /** HUD 实例维护的实时朝向。 */
    static final CompassHeading LIVE = CompassHeading.create();

    /** 外部提供者（CopyOnWrite：注册/注销可能来自任意时机）。 */
    private static final CopyOnWriteArrayList<CompassMarkerProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    /** 指令创建的测试标点。 */
    private static final List<CompassMark> TEST_MARKS = new ArrayList<>();

    /** 基数方位监听器列表。 */
    private static final CopyOnWriteArrayList<CompassApi.CardinalListener> CARDINAL_LISTENERS = new CopyOnWriteArrayList<>();

    private CompassHub() {}

    static void registerProvider(CompassMarkerProvider provider) {
        if (provider != null) PROVIDERS.addIfAbsent(provider);
    }

    static void unregisterProvider(CompassMarkerProvider provider) {
        PROVIDERS.remove(provider);
    }

    static List<CompassMarkerProvider> providers() {
        return PROVIDERS;
    }

    static void addTestMark(CompassMark mark) {
        // 同 key 的测试标点刷新而不累加。
        TEST_MARKS.removeIf(m -> m.key().equals(mark.key()));
        TEST_MARKS.add(mark);
    }

    static void clearTestMarks() {
        TEST_MARKS.clear();
    }

    static List<CompassMark> testMarks() {
        return TEST_MARKS;
    }

    /** 基数方位监听器。 */
    static void addCardinalListener(CompassApi.CardinalListener listener) {
        if (listener != null) CARDINAL_LISTENERS.addIfAbsent(listener);
    }

    static void removeCardinalListener(CompassApi.CardinalListener listener) {
        CARDINAL_LISTENERS.remove(listener);
    }

    /**
     * 由 HUD 控件在“进入基数方位区域”边沿事件时调用（每次进入都触发，
     * 边沿检测与滞回在 {@link CompassHeading} 内，这里不做去重——
     * 旧行“仅当最近方位变化才发”会漏掉“离开后重进同一方位”与“快转扫过”）。
     */
    static void fireCardinal(int cardinal) {
        for (CompassApi.CardinalListener listener : CARDINAL_LISTENERS) {
            try {
                listener.onCardinalReached(cardinal);
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Nullable
    static CompassHeading liveHeading() {
        return LIVE.isInitialized() ? LIVE : null;
    }
}
