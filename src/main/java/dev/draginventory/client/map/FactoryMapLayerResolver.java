package dev.draginventory.client.map;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Resolves "floors" from walkable surfaces instead of fixed Y bands.
 * This keeps ramps/stairs attached to their surrounding plateau and lets the map expose nearby
 * building levels without knowing the factory's floor heights in advance.
 */
public final class FactoryMapLayerResolver {
    public static final int NO_FLOOR = Integer.MIN_VALUE;

    private FactoryMapLayerResolver() {}

    public static int resolvePlayerFloor(ClientLevel level, Player player) {
        int x = Mth.floor(player.getX());
        int z = Mth.floor(player.getZ());
        int around = Mth.floor(player.getY() - 0.05) - 1;
        int direct = findWalkableAt(level, x, z, around, 6);
        if (direct == NO_FLOOR) return Mth.clamp(around, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);

        // Nearby repeated surfaces represent an actual floor better than one stair tread underfoot.
        Map<Integer, Integer> counts = new HashMap<>();
        for (int dx = -5; dx <= 5; dx += 2) {
            for (int dz = -5; dz <= 5; dz += 2) {
                int y = findWalkableAt(level, x + dx, z + dz, direct, 6);
                if (y != NO_FLOOR) counts.merge(y, 1, Integer::sum);
            }
        }
        int bestY = direct;
        int bestScore = Integer.MIN_VALUE;
        for (var entry : counts.entrySet()) {
            int score = entry.getValue() * 12 - Math.abs(entry.getKey() - direct) * 3;
            if (score > bestScore) {
                bestScore = score;
                bestY = entry.getKey();
            }
        }
        return bestY;
    }

    public static int findWalkableAt(ClientLevel level, int x, int z, int aroundY, int radiusY) {
        if (level == null) return NO_FLOOR;
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        int clamped = Mth.clamp(aroundY, minY, maxY);
        BlockPos chunkProbe = new BlockPos(x, clamped, z);
        if (!level.hasChunkAt(chunkProbe)) return NO_FLOOR;

        for (int d = 0; d <= radiusY; d++) {
            int down = clamped - d;
            if (down >= minY && isWalkableFloor(level, new BlockPos(x, down, z))) return down;
            if (d == 0) continue;
            int up = clamped + d;
            if (up <= maxY && isWalkableFloor(level, new BlockPos(x, up, z))) return up;
        }
        return NO_FLOOR;
    }

    public static int findAdjacentFloor(ClientLevel level, Player player, int currentFloor, int direction) {
        if (direction == 0) return currentFloor;
        direction = direction < 0 ? -1 : 1;
        int baseX = Mth.floor(player.getX());
        int baseZ = Mth.floor(player.getZ());
        Map<Integer, Integer> counts = new HashMap<>();

        // Broad X/Z sampling suppresses individual stair treads. Real floors occur repeatedly.
        for (int dx = -8; dx <= 8; dx += 2) {
            for (int dz = -8; dz <= 8; dz += 2) {
                for (int distance = 4; distance <= 28; distance++) {
                    int y = currentFloor + direction * distance;
                    if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) break;
                    if (isWalkableFloor(level, new BlockPos(baseX + dx, y, baseZ + dz))) {
                        counts.merge(y, 1, Integer::sum);
                        break;
                    }
                }
            }
        }

        int best = NO_FLOOR;
        int bestDistance = Integer.MAX_VALUE;
        for (var entry : counts.entrySet()) {
            int delta = entry.getKey() - currentFloor;
            if (Integer.signum(delta) != direction || entry.getValue() < 4) continue;
            int distance = Math.abs(delta);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry.getKey();
            }
        }
        if (best != NO_FLOOR) return best;

        // Sparse catwalks can have fewer samples; fall back to the nearest detected surface.
        for (var entry : counts.entrySet()) {
            int delta = entry.getKey() - currentFloor;
            if (Integer.signum(delta) != direction) continue;
            int distance = Math.abs(delta);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry.getKey();
            }
        }
        return best == NO_FLOOR ? currentFloor : best;
    }

    private static boolean isWalkableFloor(ClientLevel level, BlockPos floor) {
        var floorState = level.getBlockState(floor);
        if (floorState.getCollisionShape(level, floor).isEmpty()) return false;

        BlockPos feet = floor.above();
        BlockPos head = feet.above();
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(head).getCollisionShape(level, head).isEmpty();
    }
}
