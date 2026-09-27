package dev.draginventory.client.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** Discovers walkable factory planes and selects exactly one for the player's current region. */
public final class FactoryMapLayerResolver {
    public static final int NO_FLOOR = Integer.MIN_VALUE;
    public static final int SCAN_RADIUS = 512;
    private static final int SAMPLE_STEP = 8;
    private static final int MAX_LAYERS = 16;

    private FactoryMapLayerResolver() {}

    public static LayerCatalog discover(ClientLevel level, Player player) {
        if (level == null || player == null) return LayerCatalog.empty();
        int centerX = Mth.floor(player.getX()), centerZ = Mth.floor(player.getZ());
        int minX = centerX - SCAN_RADIUS, maxX = centerX + SCAN_RADIUS;
        int minZ = centerZ - SCAN_RADIUS, maxZ = centerZ + SCAN_RADIUS;
        int minY = Math.max(level.getMinBuildHeight(), Mth.floor(player.getY()) - 160);
        int maxY = Math.min(level.getMaxBuildHeight() - 2, Mth.floor(player.getY()) + 160);
        Map<Integer, LayerStats> stats = new HashMap<>();

        // Sparse sampling finds basement rooms and elevated catwalks while avoiding a long UI stall.
        for (int x = minX; x <= maxX; x += SAMPLE_STEP) {
            for (int z = minZ; z <= maxZ; z += SAMPLE_STEP) {
                if (!level.hasChunkAt(new BlockPos(x, Mth.clamp(Mth.floor(player.getY()), minY, maxY), z))) continue;
                for (int y = minY; y <= maxY; y += 2) {
                    if (isWalkableFloor(level, new BlockPos(x, y, z))) {
                        int floorY = y;
                        stats.computeIfAbsent(floorY, ignored -> new LayerStats(floorY)).add(x, z);
                    }
                }
            }
        }

        int playerFloor = resolvePlayerFloor(level, player);
        stats.computeIfAbsent(playerFloor, ignored -> new LayerStats(playerFloor)).add(centerX, centerZ);
        int peak = stats.values().stream().mapToInt(s -> s.samples).max().orElse(1);
        int threshold = Math.max(4, peak / 180);
        List<LayerStats> candidates = stats.values().stream()
                .filter(s -> s.samples >= threshold)
                .sorted(Comparator.comparingInt((LayerStats s) -> s.samples).reversed())
                .toList();

        List<LayerStats> chosen = new ArrayList<>();
        for (LayerStats candidate : candidates) {
            LayerStats merged = chosen.stream().filter(existing -> Math.abs(existing.floorY - candidate.floorY) <= 3)
                    .findFirst().orElse(null);
            if (merged != null) merged.merge(candidate);
            else if (chosen.size() < MAX_LAYERS) chosen.add(candidate);
        }
        chosen.sort(Comparator.comparingInt(s -> s.floorY));
        List<Layer> layers = new ArrayList<>(chosen.size());
        for (int i = 0; i < chosen.size(); i++) {
            LayerStats s = chosen.get(i);
            layers.add(new Layer(i, s.floorY, s.minX - 8, s.maxX + 8, s.minZ - 8, s.maxZ + 8, s.samples));
        }
        return new LayerCatalog(layers, minX, maxX, minZ, maxZ, playerFloor);
    }

    public static int resolvePlayerFloor(ClientLevel level, Player player) {
        int x = Mth.floor(player.getX()), z = Mth.floor(player.getZ());
        int around = Mth.floor(player.getY() - 0.05) - 1;
        int direct = findWalkableAt(level, x, z, around, 8);
        if (direct == NO_FLOOR) return Mth.clamp(around, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        Map<Integer, Integer> counts = new HashMap<>();
        for (int dx = -6; dx <= 6; dx += 2) for (int dz = -6; dz <= 6; dz += 2) {
            int y = findWalkableAt(level, x + dx, z + dz, direct, 7);
            if (y != NO_FLOOR) counts.merge(y, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .max(Comparator.comparingInt((Map.Entry<Integer, Integer> e) -> e.getValue() * 12
                        - Math.abs(e.getKey() - direct) * 3))
                .map(Map.Entry::getKey).orElse(direct);
    }

    public static int findWalkableAt(ClientLevel level, int x, int z, int aroundY, int radiusY) {
        if (level == null) return NO_FLOOR;
        int minY = level.getMinBuildHeight(), maxY = level.getMaxBuildHeight() - 1;
        int clamped = Mth.clamp(aroundY, minY, maxY);
        if (!level.hasChunkAt(new BlockPos(x, clamped, z))) return NO_FLOOR;
        for (int d = 0; d <= radiusY; d++) {
            int down = clamped - d;
            if (down >= minY && isWalkableFloor(level, new BlockPos(x, down, z))) return down;
            if (d == 0) continue;
            int up = clamped + d;
            if (up <= maxY && isWalkableFloor(level, new BlockPos(x, up, z))) return up;
        }
        return NO_FLOOR;
    }

    public static boolean isWalkableFloor(ClientLevel level, BlockPos floor) {
        var floorState = level.getBlockState(floor);
        if (floorState.getCollisionShape(level, floor).isEmpty()) return false;
        return level.getBlockState(floor.above()).getCollisionShape(level, floor.above()).isEmpty()
                && level.getBlockState(floor.above(2)).getCollisionShape(level, floor.above(2)).isEmpty();
    }

    public record Layer(int id, int floorY, int minX, int maxX, int minZ, int maxZ, int samples) {
        public boolean contains(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }
        public String label() { return "Y " + floorY; }
    }

    public record LayerCatalog(List<Layer> layers, int minX, int maxX, int minZ, int maxZ, int playerFloor) {
        static LayerCatalog empty() { return new LayerCatalog(List.of(), 0, 0, 0, 0, 0); }
        public Layer resolve(ClientLevel level, Player player) {
            if (layers.isEmpty()) return new Layer(0, resolvePlayerFloor(level, player), minX, maxX, minZ, maxZ, 1);
            int floor = resolvePlayerFloor(level, player);
            int x = Mth.floor(player.getX()), z = Mth.floor(player.getZ());
            return layers.stream().filter(layer -> layer.contains(x, z))
                    .min(Comparator.comparingInt(layer -> Math.abs(layer.floorY() - floor)))
                    .orElseGet(() -> layers.stream().min(Comparator.comparingInt(layer -> Math.abs(layer.floorY() - floor))).orElse(layers.get(0)));
        }
        public Layer byId(int id) { return layers.stream().filter(l -> l.id() == id).findFirst().orElse(layers.isEmpty() ? null : layers.get(0)); }
    }

    static final class LayerStats {
        final int floorY;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE, samples;
        LayerStats(int floorY) { this.floorY = floorY; }
        void add(int x, int z) { minX = Math.min(minX, x); maxX = Math.max(maxX, x); minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z); samples++; }
        void merge(LayerStats other) { minX = Math.min(minX, other.minX); maxX = Math.max(maxX, other.maxX); minZ = Math.min(minZ, other.minZ); maxZ = Math.max(maxZ, other.maxZ); samples += other.samples; }
    }
}
