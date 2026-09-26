package dev.draginventory.client.compass;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * 方位条上的统一标点视图。来源有三类：
 * <ul>
 *   <li>本模组战术标点（中键标记，只读桥接）</li>
 *   <li>指令创建的测试标点（验证联动效果）</li>
 *   <li>外部 HERRA 模组通过 {@link CompassMarkerProvider} 注入</li>
 * </ul>
 * 颜色为 0xRRGGBB；标点色必须与中心朝向色（accent）明显区分。
 */
public record CompassMark(Object key, Kind kind, Vec3 position, int color,
                          @Nullable Component label, boolean showDistance, long createdAtMillis) {

    public enum Kind { ENEMY, LOCATION, ITEM, EXTERNAL }

    public static CompassMark of(Object key, Kind kind, Vec3 position, int color,
                                 @Nullable Component label, boolean showDistance) {
        return new CompassMark(key, kind, position, color, label, showDistance, System.currentTimeMillis());
    }
}
