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
 */
public final class CompassMarkerBridge {
    private CompassMarkerBridge() {}

    /** 把当前战术标点转换为方位条标记视图；无标点时返回空列表。 */
    public static List<CompassMark> collect(CompassPalette palette, long now) {
        if (!dev.draginventory.client.compass.CompassConfig.MARKERS_TACTICAL.get()) return List.of();
        var markers = TacticalMarkerManager.MARKERS;
        if (markers.isEmpty()) return List.of();
        List<CompassMark> result = new ArrayList<>(markers.size());
        for (TacticalMarker marker : markers.values()) {
            // 只读：过期清理由战术标点系统自己的 maintain() 负责。
            Vec3 position = marker.position(1f);
            CompassMark.Kind kind = switch (marker.type()) {
                case ENEMY -> CompassMark.Kind.ENEMY;
                case ITEM -> CompassMark.Kind.ITEM;
                case LOCATION -> CompassMark.Kind.LOCATION;
            };
            int color = switch (kind) {
                case ENEMY -> palette.markerEnemy();
                case ITEM -> palette.markerItem();
                default -> palette.markerLocation();
            };
            result.add(CompassMark.of("tactical:" + System.identityHashCode(marker), kind,
                    position, color, null, true));
        }
        return result;
    }
}
