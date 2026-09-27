package dev.draginventory.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public final class FactoryMapSession {
    public static final double MIN_ZOOM = 0.35;
    public static final double MAX_ZOOM = 8.0;

    private double centerX;
    private double centerZ;
    private double zoom = 1.5;
    private int selectedFloorY;
    private boolean autoFloor = true;
    private double hoverX;
    private double hoverZ;

    public FactoryMapSession(Minecraft mc) {
        if (mc.player != null && mc.level != null) {
            centerOnPlayer(mc.player);
            selectedFloorY = FactoryMapLayerResolver.resolvePlayerFloor(mc.level, mc.player);
        }
    }

    public void tick(Minecraft mc) {
        if (autoFloor && mc.level != null && mc.player != null) {
            selectedFloorY = FactoryMapLayerResolver.resolvePlayerFloor(mc.level, mc.player);
        }
    }

    public MapCoordinateTransform transform(int x, int y, int width, int height) {
        return new MapCoordinateTransform(centerX, centerZ, zoom, x, y, width, height);
    }

    public void panPixels(double dx, double dy) {
        centerX -= dx / zoom;
        centerZ -= dy / zoom;
    }

    public void zoomAt(double screenX, double screenY, double multiplier,
                       int viewportX, int viewportY, int viewportWidth, int viewportHeight) {
        MapCoordinateTransform before = transform(viewportX, viewportY, viewportWidth, viewportHeight);
        var anchored = before.screenToWorld(screenX, screenY);
        double next = Math.clamp(zoom * multiplier, MIN_ZOOM, MAX_ZOOM);
        if (Math.abs(next - zoom) < 1.0e-6) return;
        zoom = next;
        centerX = anchored.x() - (screenX - (viewportX + viewportWidth * 0.5)) / zoom;
        centerZ = anchored.z() - (screenY - (viewportY + viewportHeight * 0.5)) / zoom;
    }

    public void centerOnPlayer(Player player) {
        centerX = player.getX();
        centerZ = player.getZ();
    }

    public void stepFloor(ClientLevel level, Player player, int direction) {
        if (level == null || player == null) return;
        selectedFloorY = FactoryMapLayerResolver.findAdjacentFloor(level, player, selectedFloorY, direction);
        autoFloor = false;
    }

    public void toggleAutoFloor(ClientLevel level, Player player) {
        autoFloor = !autoFloor;
        if (autoFloor && level != null && player != null) {
            selectedFloorY = FactoryMapLayerResolver.resolvePlayerFloor(level, player);
        }
    }

    public void setHover(double x, double z) {
        hoverX = x;
        hoverZ = z;
    }

    public int selectedFloorY() { return selectedFloorY; }
    public boolean autoFloor() { return autoFloor; }
    public double zoom() { return zoom; }
    public double hoverX() { return hoverX; }
    public double hoverZ() { return hoverZ; }

    public String zoomText() { return String.format(java.util.Locale.ROOT, "%.2f", zoom); }
    public int hoverBlockX() { return Mth.floor(hoverX); }
    public int hoverBlockZ() { return Mth.floor(hoverZ); }
}
