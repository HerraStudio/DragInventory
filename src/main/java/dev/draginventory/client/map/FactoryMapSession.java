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
    private int viewportWidth = 800;
    private int viewportHeight = 450;
    private FactoryMapLayerResolver.LayerCatalog catalog;
    private FactoryMapLayerResolver.Layer activeLayer;
    private long lastScan;
    private long layerChangedAt;

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
        // The footprint is deliberately fixed. Walking does not expand or regenerate the map.
        if (activeLayer != null) {
            var next = catalog.resolve(mc.level, mc.player);
            if (next.id() != activeLayer.id()) layerChangedAt = net.minecraft.Util.getMillis();
            activeLayer = next;
        }
    }

    private void rebuild(Minecraft mc) {
        catalog = FactoryMapLayerResolver.discover(mc.level, mc.player);
        activeLayer = catalog.resolve(mc.level, mc.player);
        lastScan = net.minecraft.Util.getMillis();
    }

    public MapCoordinateTransform transform(int x, int y, int width, int height) {
        viewportWidth = width;
        viewportHeight = height;
        zoom = Math.max(zoom, minimumZoom(width, height));
        clampCenter();
        return new MapCoordinateTransform(centerX, centerZ, zoom, x, y, width, height);
    }
    public void panPixels(double dx, double dy) { centerX -= dx / zoom; centerZ -= dy / zoom; clampCenter(); }
    public void zoomAt(double screenX, double screenY, double multiplier, int viewportX, int viewportY, int viewportWidth, int viewportHeight) {
        MapCoordinateTransform before = transform(viewportX, viewportY, viewportWidth, viewportHeight);
        var anchored = before.screenToWorld(screenX, screenY);
        double next = Math.clamp(zoom * multiplier, minimumZoom(viewportWidth, viewportHeight), MAX_ZOOM);
        if (Math.abs(next - zoom) < 1.0e-6) return;
        zoom = next;
        centerX = anchored.x() - (screenX - (viewportX + viewportWidth * 0.5)) / zoom;
        centerZ = anchored.z() - (screenY - (viewportY + viewportHeight * 0.5)) / zoom;
        clampCenter();
    }
    public void centerOnPlayer(Player player) { centerX = player.getX(); centerZ = player.getZ(); clampCenter(); }
    public void centerOn(double x, double z) { centerX = x; centerZ = z; clampCenter(); }
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
    public long layerChangedAt() { return layerChangedAt; }

    private double minimumZoom(int width, int height) {
        return Math.max(MIN_ZOOM, Math.max(width / (double) FactoryMapBounds.WIDTH, height / (double) FactoryMapBounds.HEIGHT) * 0.75);
    }

    private void clampCenter() {
        centerX = FactoryMapBounds.clampCenterX(centerX, viewportWidth / zoom);
        centerZ = FactoryMapBounds.clampCenterZ(centerZ, viewportHeight / zoom);
    }
}
