package dev.draginventory.client.map;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Samples the immutable factory geometry into a tactical plan view.
 * Results are cached because this project deliberately does not support player building/breaking.
 */
public final class FactoryMapSampler {
    public static final int COLOR_VOID = 0xFF081015;
    public static final int COLOR_WALL = 0xFF1B272C;
    public static final int COLOR_FLOOR = 0xFF64747A;
    public static final int COLOR_EDGE = 0xFFB1C0BC;
    public static final int COLOR_STAIR = 0xFFC0A66E;
    public static final int COLOR_BLOCKED = 0xFF10191E;
    public static final int COLOR_WATER = 0xFF245463;
    private static final int MAX_CACHED_CELLS = 120_000;

    private ClientLevel cachedLevel;
    private final Map<CellKey, Integer> colors = new LinkedHashMap<>(4096, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CellKey, Integer> eldest) {
            return size() > MAX_CACHED_CELLS;
        }
    };

    public void prepare(ClientLevel level) {
        if (cachedLevel != level) {
            cachedLevel = level;
            colors.clear();
        }
    }

    public int sample(ClientLevel level, int x, int z, int layerY) {
        prepare(level);
        if (!level.hasChunkAt(new BlockPos(x, layerY, z))) return COLOR_VOID;
        CellKey key = new CellKey(x, layerY, z);
        Integer cached = colors.get(key);
        if (cached != null) return cached;

        int color = compute(level, x, z, layerY);
        colors.put(key, color);
        return color;
    }

    private static int compute(ClientLevel level, int x, int z, int layerY) {
        int y = Mth.clamp(layerY, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        BlockPos probe = new BlockPos(x, y, z);
        if (!level.hasChunkAt(probe)) return COLOR_VOID;

        // At the selected walking plane, occupied feet/head space is an opaque tactical wall.
        if (solidAt(level, x, y + 1, z) || solidAt(level, x, y + 2, z)) return COLOR_WALL;

        for (int fy = Math.max(level.getMinBuildHeight(), y - 2);
             fy <= Math.min(level.getMaxBuildHeight() - 1, y + 3); fy++) {
            var state = level.getBlockState(new BlockPos(x, fy, z));
            if (!state.getFluidState().isEmpty()) return COLOR_WATER;
        }

        BlockPos floor = new BlockPos(x, y, z);
        if (!FactoryMapLayerResolver.isWalkableFloor(level, floor)) return COLOR_BLOCKED;
        var floorState = level.getBlockState(floor);
        String blockName = floorState.getBlock().builtInRegistryHolder().key().location().getPath();
        if (blockName.contains("stairs") || blockName.contains("ladder") || blockName.contains("scaffold")) return COLOR_STAIR;
        boolean edge = !isWalkable(level, x + 1, y, z) || !isWalkable(level, x - 1, y, z)
                || !isWalkable(level, x, y, z + 1) || !isWalkable(level, x, y, z - 1);
        return edge ? COLOR_EDGE : COLOR_FLOOR;
    }

    private static boolean solidAt(ClientLevel level, int x, int y, int z) {
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) return false;
        BlockPos pos = new BlockPos(x, y, z);
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static boolean isWalkable(ClientLevel level, int x, int y, int z) {
        return level.hasChunkAt(new BlockPos(x, y, z))
                && FactoryMapLayerResolver.isWalkableFloor(level, new BlockPos(x, y, z));
    }

    /** Roof/terrain overview with directional relief, restricted to loaded client chunks. */
    public int surface(ClientLevel level, int x, int z) {
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) return COLOR_VOID;
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        if (y < level.getMinBuildHeight()) return COLOR_VOID;
        BlockPos p = new BlockPos(x, y, z);
        var state = level.getBlockState(p);
        if (!state.getFluidState().isEmpty()) return 0xFF1A3038;
        int rgb = state.getMapColor(level, p).col;
        int north = level.hasChunkAt(p.north()) ? level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - 1) - 1 : y;
        int west = level.hasChunkAt(p.west()) ? level.getHeight(Heightmap.Types.WORLD_SURFACE, x - 1, z) - 1 : y;
        int relief = Mth.clamp((y - north) * 9 + (y - west) * 6, -26, 32);
        int gray = ((rgb >> 16 & 255) * 30 + (rgb >> 8 & 255) * 59 + (rgb & 255) * 11) / 100;
        int value = Mth.clamp(34 + gray * 36 / 100 + relief, 24, 136);
        return 0xFF000000 | (value - 5) << 16 | value << 8 | (value + 3);
    }

    private record CellKey(int x, int y, int z) {}
}
