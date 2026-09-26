package dev.draginventory.client;

import dev.draginventory.client.compass.CompassMark;
import dev.draginventory.client.compass.CompassPalette;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * 方位条 &lt;-&gt; 战术标点 的只读桥接。
 * 与 {@code TacticalMarkerManager} 同包以访问其包级私有 MARKERS 表，
 * 绝不写入、不清除、不改变战术标点的任何状态（满足“只读不写”约束）。
 *
 * <p>单个标点的任何异常都被隔离，不会拖垮方位条或其它 HUD 的渲染；
 * 战术标点系统未来新增类型时按 LOCATION 兜底显示。</p>
 */
public final class CompassMarkerBridge {
    private CompassMarkerBridge() {}

    /** 把当前战术标点转换为方位条标记视图；无标点时返回空列表。partialTick 用于移动目标的平滑插值。 */
    public static List<CompassMark> collect(CompassPalette palette, float partialTick) {
        if (!dev.draginventory.client.compass.CompassConfig.MARKERS_TACTICAL.get()) return List.of();
        var markers = TacticalMarkerManager.MARKERS;
        if (markers.isEmpty()) return List.of();
        List<CompassMark> result = new ArrayList<>(markers.size());
        for (TacticalMarker marker : markers.values()) {
            // 只读：过期清理由战术标点系统自己的 maintain() 负责。
            try {
                Vec3 position = marker.position(partialTick);
                CompassMark.Kind kind = switch (marker.type()) {
                    case ENEMY -> CompassMark.Kind.ENEMY;
                    case ITEM -> CompassMark.Kind.ITEM;
                    case LOCATION -> CompassMark.Kind.LOCATION;
                    // 未来战术标点新增类型时的兜底，避免穷举 switch 直接抛异常。
                    default -> CompassMark.Kind.LOCATION;
                };
                int color = switch (kind) {
                    case ENEMY -> palette.markerEnemy();
                    case ITEM -> palette.markerItem();
                    default -> palette.markerLocation();
                };
                // 创建时间用战术标点自身的创建时刻：CompassMark 是逐帧重建的临时视图，
                // 若用“现在”会导致脉冲动画相位每帧重置、随帧时长随机抖动。
                result.add(new CompassMark("tactical:" + System.identityHashCode(marker), kind,
                        position, color, null, true, marker.createdAt()));
            } catch (RuntimeException ignored) {
                // 单个标点状态异常（目标正在失效等）不影响其余标点与整个 HUD。
            }
        }
        return result;
    }
}
