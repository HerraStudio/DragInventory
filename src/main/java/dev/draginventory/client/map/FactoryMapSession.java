package dev.draginventory.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;

/** Runtime state for a north-up map whose visible layer always follows the player. */
public final class FactoryMapSession {
    public static final double MIN_ZOOM = 0.35;
    public static final double MAX_ZOOM = 8.0;

    private double centerX;
    private double centerZ;
    private double zoom = 1.5;
    private double hoverX;
    private double hoverZ;
    private FactoryMapLayerResolver.LayerCatalog catalog;
    private FactoryMapLayerResolver.Layer activeLayer;
    private long lastScan;

    public FactoryMapSession(Minecraft mc) {
        if (mc.player != null && mc.level != null) {
            centerOnPlayer(mc.player);
            rebuild(mc);
        } else {
            catalog = FactoryMapLayerResolver.LayerCatalog.empty();
        }
    }

    public void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        if (catalog == null || activeLayer == null || mc.player.tickCount % 40 == 0
                || mc.player.distanceToSqr(centerX, mc.player.getY(), centerZ) > 4096) {
            // Re-scan as new chunks around the factory become available. The active map changes
            // only after a complete catalog is ready, so the screen never flashes another floor.
            if (net.minecraft.Util.getMillis() - lastScan > 900) rebuild(mc);
        }
        if (activeLayer != null) activeLayer = catalog.resolve(mc.level, mc.player);
    }

    private void rebuild(Minecraft mc) {
        catalog = FactoryMapLayerResolver.discover(mc.level, mc.player);
        activeLayer = catalog.resolve(mc.level, mc.player);
        lastScan = net.minecraft.Util.getMillis();
    }

    public MapCoordinateTransform transform(int x, int y, int width, int height) {
        return new MapCoordinateTransform(centerX, centerZ, zoom, x, y, width, height);
    }
    public void panPixels(double dx, double dy) { centerX -= dx / zoom; centerZ -= dy / zoom; }
    public void zoomAt(double screenX, double screenY, double multiplier, int viewportX, int viewportY, int viewportWidth, int viewportHeight) {
        MapCoordinateTransform before = transform(viewportX, viewportY, viewportWidth, viewportHeight);
        var anchored = before.screenToWorld(screenX, screenY);
        double next = Math.clamp(zoom * multiplier, MIN_ZOOM, MAX_ZOOM);
        if (Math.abs(next - zoom) < 1.0e-6) return;
        zoom = next;
        centerX = anchored.x() - (screenX - (viewportX + viewportWidth * 0.5)) / zoom;
        centerZ = anchored.z() - (screenY - (viewportY + viewportHeight * 0.5)) / zoom;
    }
    public void centerOnPlayer(Player player) { centerX = player.getX(); centerZ = player.getZ(); }
    public void centerOn(double x, double z) { centerX = x; centerZ = z; }
    public void setHover(double x, double z) { hoverX = x; hoverZ = z; }
    public double zoom() { return zoom; }
    public double hoverX() { return hoverX; }
    public double hoverZ() { return hoverZ; }
    public int hoverBlockX() { return Mth.floor(hoverX); }
    public int hoverBlockZ() { return Mth.floor(hoverZ); }
    public FactoryMapLayerResolver.LayerCatalog catalog() { return catalog; }
    public FactoryMapLayerResolver.Layer activeLayer() { return activeLayer; }
    public int selectedFloorY() { return activeLayer == null ? 0 : activeLayer.floorY(); }
    public String layerStatus() {
        if (catalog == null || catalog.layers().isEmpty()) return "SCANNING";
        return (activeLayer == null ? "Y ?" : activeLayer.label()) + "  /  " + catalog.layers().size();
    }
}
