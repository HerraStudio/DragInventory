package dev.draginventory.client.map;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/**
 * Samples the immutable factory geometry into a tactical plan view.
 * Results are cached because this project deliberately does not support player building/breaking.
 */
public final class FactoryMapSampler {
    public static final int COLOR_VOID = 0xFF0A0E10;
    public static final int COLOR_WALL = 0xFF343D40;
    public static final int COLOR_WATER = 0xFF244754;
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

        // At the selected walking plane, occupied feet/head space reads as a wall/solid obstacle.
        if (solidAt(level, x, y + 1, z) || solidAt(level, x, y + 2, z)) return COLOR_WALL;

        for (int fy = Math.max(level.getMinBuildHeight(), y - 2);
             fy <= Math.min(level.getMaxBuildHeight() - 1, y + 3); fy++) {
            var state = level.getBlockState(new BlockPos(x, fy, z));
            if (!state.getFluidState().isEmpty()) return COLOR_WATER;
        }

        int floorY = FactoryMapLayerResolver.findWalkableAt(level, x, z, y, 5);
        if (floorY == FactoryMapLayerResolver.NO_FLOOR) return COLOR_VOID;

        BlockPos floor = new BlockPos(x, floorY, z);
        int rgb = level.getBlockState(floor).getMapColor(level, floor).col;
        if ((rgb & 0x00FFFFFF) == 0) rgb = 0x6C7370;
        return stylize(rgb, floorY - y);
    }

    private static boolean solidAt(ClientLevel level, int x, int y, int z) {
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) return false;
        BlockPos pos = new BlockPos(x, y, z);
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static int stylize(int rgb, int heightDelta) {
        int r = rgb >> 16 & 255;
        int g = rgb >> 8 & 255;
        int b = rgb & 255;
        int gray = (r * 30 + g * 59 + b * 11) / 100;

        // Desaturate hard, then tint slightly cool to get a readable tactical-terminal map.
        r = (r + gray * 3) / 4;
        g = (g + gray * 3) / 4;
        b = (b + gray * 3) / 4;

        double light = Math.clamp(0.64 + heightDelta * 0.035, 0.45, 0.88);
        r = Mth.clamp((int) (r * light * 0.94), 18, 210);
        g = Mth.clamp((int) (g * light), 20, 220);
        b = Mth.clamp((int) (b * light * 1.03), 22, 225);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private record CellKey(int x, int y, int z) {}
}
