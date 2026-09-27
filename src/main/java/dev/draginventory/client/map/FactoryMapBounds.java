package dev.draginventory.client.map;

import net.minecraft.util.Mth;

/** Fixed factory footprint measured from the 工厂 save, not from the player's current position. */
public final class FactoryMapBounds {
    public static final int MIN_X = -48;
    public static final int MAX_X = 352;
    public static final int MIN_Z = -176;
    public static final int MAX_Z = 64;
    public static final int WIDTH = MAX_X - MIN_X;
    public static final int HEIGHT = MAX_Z - MIN_Z;

    private FactoryMapBounds() {}

    public static double clampCenterX(double x, double visibleWidth) {
        double half = Math.min(WIDTH * 0.5, visibleWidth * 0.5);
        return Mth.clamp(x, MIN_X + half, MAX_X - half);
    }

    public static double clampCenterZ(double z, double visibleHeight) {
        double half = Math.min(HEIGHT * 0.5, visibleHeight * 0.5);
        return Mth.clamp(z, MIN_Z + half, MAX_Z - half);
    }

    public static boolean contains(double x, double z) {
        return x >= MIN_X && x <= MAX_X && z >= MIN_Z && z <= MAX_Z;
    }
}
