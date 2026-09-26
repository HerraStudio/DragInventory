package dev.draginventory.client.compass;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * 方位条上的统一标点视图。来源有四类：
 * <ul>
 *   <li>本模组战术标点（中键标记，只读桥接）</li>
 *   <li>指令创建的测试标点（验证联动效果）</li>
 *   <li>外部 HERRA 模组通过 {@link CompassMarkerProvider} 注入</li>
 *   <li>玩家死亡时自动记录的死亡标点（靠近后自动消失）</li>
 * </ul>
 * 颜色为 0xRRGGBB；标点色必须与中心朝向色（accent）明显区分。
 * 形状按类型分型：ENEMY=菱形、LOCATION=上三角、ITEM=方块、DEATH=X 十字、EXTERNAL=菱形。
 */
public record CompassMark(Object key, Kind kind, Vec3 position, int color,
                          @Nullable Component label, boolean showDistance, long createdAtMillis) {

    public enum Kind { ENEMY, LOCATION, ITEM, EXTERNAL, DEATH }

    public static CompassMark of(Object key, Kind kind, Vec3 position, int color,
                                 @Nullable Component label, boolean showDistance) {
        return new CompassMark(key, kind, position, color, label, showDistance, System.currentTimeMillis());
    }
}
