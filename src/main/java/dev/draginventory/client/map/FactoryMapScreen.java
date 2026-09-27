package dev.draginventory.client.map;

import static dev.draginventory.client.map.FactoryMapUI.*;
import dev.draginventory.client.TacticalMarker;
import dev.draginventory.client.TacticalMarkerLogic;
import dev.draginventory.client.TacticalMarkerManager;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** Full-screen operational map with genuine world data and local ping controls. */
public final class FactoryMapScreen extends Screen {
    private final FactoryMapSession session;
    private final FactoryMapTexture terrain = new FactoryMapTexture();
    private final EnumSet<TacticalMarker.Type> visible = EnumSet.allOf(TacticalMarker.Type.class);
    private final List<Button> legendButtons = new ArrayList<>();
    private boolean dragging, legend = true;
    private float uiScale;
    private int canvasW, canvasH, margin, leftW, rightX;
    private final long opened = Util.getMillis();

    private FactoryMapScreen(FactoryMapSession session) {
        super(Component.translatable("draginventory.map.title")); this.session = session;
    }
    public static FactoryMapScreen create() { return new FactoryMapScreen(new FactoryMapSession(Minecraft.getInstance())); }

    @Override protected void init() {
        uiScale = Math.min(1f, Math.min(width / 640f, height / 360f));
        canvasW = Math.round(width / uiScale); canvasH = Math.round(height / uiScale);
        margin = Math.max(18, Math.round(canvasW * .055f));
        leftW = Math.min(160, Math.round(canvasW * .22f)); rightX = canvasW - margin - 128;
        dragging = false; legendButtons.clear();
        addRenderableWidget(button(margin + 8, 231, leftW - 16, tr("clear"), TacticalMarkerManager::clearLocationMarkers));
        int bx = rightX + 8;
        legendButtons.add(addRenderableWidget(button(bx, 201, 112, tr("center"), this::center)));
        addRenderableWidget(button(canvasW - margin - 52, 13, 52, tr("close"), this::onClose));
        legendButtons.forEach(b -> b.visible = legend);
    }
    @Override public void tick() {
        if (minecraft.level == null || minecraft.player == null || !minecraft.player.isAlive()) { onClose(); return; }
        session.tick(minecraft);
    }
    private void center() { if (minecraft.player != null) session.centerOnPlayer(minecraft.player); }
    private MapCoordinateTransform transform() { return session.transform(0, 0, canvasW, canvasH); }
    private boolean overPanel(double x, double y) {
        return y < 42 || y > canvasH - 38 || x < margin || x >= canvasW - margin
                || x >= margin && x < margin + leftW && y >= 52 && y < 259
                || legend && x >= rightX && x < rightX + 128 && y >= 52 && y < 254;
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        if (minecraft.level == null || minecraft.player == null) return;
        int mx = (int) (mouseX / uiScale), my = (int) (mouseY / uiScale);
        g.pose().pushPose(); g.pose().scale(uiScale, uiScale, 1);
        g.fill(0, 0, canvasW, canvasH, 0xFF09151B);
        var t = transform();
        g.enableScissor(0, 0, canvasW, canvasH);
        terrain.draw(g, minecraft, t, session); drawGrid(g, t);
        g.disableScissor();
        g.fillGradient(0, 0, canvasW, 70, 0xF209141A, 0x0009141A);
        g.fillGradient(0, canvasH - 75, canvasW, canvasH, 0x0009141A, 0xF209141A);
        for (int i = 0; i < 24; i++) {
            int alpha = (int) (130 * Math.pow(1 - i / 24.0, 2));
            g.fill(i * 4, 40, i * 4 + 4, canvasH - 38, alpha << 24 | 0x0A2029);
            g.fill(canvasW - i * 4 - 4, 40, canvasW - i * 4, canvasH - 38, alpha << 24 | 0x081319);
        }
        var markers = TacticalMarkerManager.snapshot(partial).stream()
                .filter(marker -> session.activeLayer() != null
                        && Math.abs(marker.position(partial).y - 1 - session.selectedFloorY()) <= 3)
                .toList();
        drawMarkers(g, t, markers, partial);
        int px = Mth.floor(t.worldToScreenX(minecraft.player.getX())), py = Mth.floor(t.worldToScreenY(minecraft.player.getZ()));
        if (px > margin && px < canvasW - margin && py > 42 && py < canvasH - 38
                && session.activeLayer() != null && session.activeLayer().contains(Mth.floor(minecraft.player.getX()), Mth.floor(minecraft.player.getZ()))) {
            box(g, px - 10, py - 10, 21, 21, 0x6035D8A0); player(g, px, py, minecraft.player.getYRot());
        }
        if (!overPanel(mx, my)) {
            var world = t.screenToWorld(mx, my); session.setHover(world.x(), world.z());
            g.fill(mx - 7, my, mx - 2, my + 1, 0x8899B7B4); g.fill(mx + 3, my, mx + 8, my + 1, 0x8899B7B4);
            g.fill(mx, my - 7, mx + 1, my - 2, 0x8899B7B4); g.fill(mx, my + 3, mx + 1, my + 8, 0x8899B7B4);
        }
        drawChrome(g, markers, partial, mx, my);
        long layerFlash = Util.getMillis() - session.layerChangedAt();
        if (session.layerChangedAt() > 0 && layerFlash < 1600) {
            int alpha = (int) (220 * (1.0 - layerFlash / 1600.0));
            String notice = tr("layer_changed") + "  " + session.layerStatus();
            int nw = font.width(notice) + 24;
            int nx = (canvasW - nw) / 2;
            g.fill(nx, 48, nx + nw, 72, alpha << 24 | 0x102C32);
            box(g, nx, 48, nw, 24, alpha << 24 | GREEN);
            text(g, notice, nx + 12, 56, alpha << 24 | TEXT);
        }
        super.render(g, mx, my, partial);
        float fade = 1 - (float) Math.clamp((Util.getMillis() - opened) / 220.0, 0, 1);
        if (fade > 0) g.fill(0, 0, canvasW, canvasH, (int) (fade * fade * 210) << 24 | 0x081217);
        g.pose().popPose();
    }
    private void drawGrid(GuiGraphics g, MapCoordinateTransform t) {
        int spacing = 16; while (spacing * t.zoom() < 64) spacing *= 2;
        var min = t.screenToWorld(0, 0); var max = t.screenToWorld(canvasW, canvasH);
        for (int x = Math.floorDiv(Mth.floor(min.x()), spacing) * spacing; x < max.x(); x += spacing) {
            int sx = Mth.floor(t.worldToScreenX(x)); g.fill(sx, 40, sx + 1, canvasH - 38, 0x123F6B70);
            if (sx > margin && sx < canvasW - margin - 24) text(g, Integer.toString(x), sx + 3, 40, 0xFF58716F);
        }
        for (int z = Math.floorDiv(Mth.floor(min.z()), spacing) * spacing; z < max.z(); z += spacing) {
            int sy = Mth.floor(t.worldToScreenY(z)); g.fill(margin, sy, canvasW - margin, sy + 1, 0x123F6B70);
        }
    }
    private void drawMarkers(GuiGraphics g, MapCoordinateTransform t, List<TacticalMarker> markers, float partial) {
        int index = 0;
        for (var marker : markers) {
            index++; if (!visible.contains(marker.type())) continue;
            Vec3 pos = marker.position(partial);
            if (session.activeLayer() == null || Math.abs(pos.y - 1 - session.selectedFloorY()) > 3) continue;
            int x = Mth.floor(t.worldToScreenX(pos.x)), y = Mth.floor(t.worldToScreenY(pos.z));
            if (x < margin + 8 || x > canvasW - margin - 8 || y < 54 || y > canvasH - 55) continue;
            int color = color(marker.type());
            var animation = TacticalMarkerLogic.appearance(Util.getMillis() - marker.createdAt());
            g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(animation.scale(), animation.scale(), 1);
            glyph(g, 0, 0, marker.type(), color); g.pose().popPose();
            text(g, String.format(java.util.Locale.ROOT, "%02d", index), x + 9, y - 6, color);
            String d = meters(pos) + "m"; text(g, d, x - font.width(d) / 2, y + 12, TEXT);
        }
    }
    private int meters(Vec3 p) { return (int) Math.floor(p.distanceTo(minecraft.player.position())); }
    private void drawChrome(GuiGraphics g, List<TacticalMarker> markers, float partial, int mx, int my) {
        text(g, "HERRA  /  " + tr("title"), margin, 18, TEXT);
        g.fill(margin, 36, canvasW - margin, 37, LINE); g.fill(margin, 36, margin + 38, 37, GREEN);
        text(g, "N", canvasW / 2 - 3, 17, GREEN); g.fill(canvasW / 2, 29, canvasW / 2 + 1, 34, GREEN);
        panel(g, margin, 52, leftW, 207); g.fill(margin, 52, margin + leftW, 76, 0xC51C3333);
        text(g, tr("operations"), margin + 10, 60, GREEN); text(g, markers.size() + " / 5", margin + leftW - 36, 60, DIM);
        g.fill(margin, 75, margin + leftW, 76, GREEN);
        if (markers.isEmpty()) {
            glyph(g, margin + leftW / 2, 113, TacticalMarker.Type.LOCATION, DIM);
            text(g, tr("empty"), margin + 12, 137, TEXT); text(g, tr("empty_hint"), margin + 12, 154, DIM);
        }
        for (int i = 0; i < Math.min(5, markers.size()); i++) {
            var marker = markers.get(i); Vec3 p = marker.position(partial); int y = 83 + i * 28;
            if (mx >= margin + 1 && mx < margin + leftW - 1 && my >= y && my < y + 27)
                g.fill(margin + 1, y, margin + leftW - 1, y + 27, 0xC3203436);
            glyph(g, margin + 14, y + 12, marker.type(), color(marker.type()));
            text(g, String.format(java.util.Locale.ROOT, "%02d  ", i + 1) + tr(marker.type().name().toLowerCase(java.util.Locale.ROOT)), margin + 27, y + 2, TEXT);
            long seconds = Math.max(0, 60 - (Util.getMillis() - marker.createdAt()) / 1000);
            text(g, meters(p) + "m   /   " + seconds + "s", margin + 27, y + 14, DIM);
            g.fill(margin + 9, y + 27, margin + leftW - 9, y + 28, LINE);
        }
        if (legend) {
            panel(g, rightX, 52, 128, 202); text(g, tr("legend"), rightX + 10, 62, TEXT);
            player(g, rightX + 15, 88, 180); text(g, tr("you"), rightX + 30, 85, GREEN);
            int i = 0;
            for (var type : TacticalMarker.Type.values()) {
                int y = 107 + i++ * 19; boolean on = visible.contains(type);
                glyph(g, rightX + 15, y + 3, type, on ? color(type) : DIM);
                text(g, tr(type.name().toLowerCase(java.util.Locale.ROOT)), rightX + 30, y, on ? TEXT : DIM);
                g.fill(rightX + 112, y + 2, rightX + 116, y + 6, on ? GREEN : LINE);
            }
            g.fill(rightX + 8, 163, rightX + 120, 164, LINE);
            text(g, tr("auto_layer") + "  " + session.layerStatus(), rightX + 8, 167, DIM);
            text(g, tr("pre_rendered"), rightX + 8, 183, GREEN);
            text(g, tr("fixed_area"), rightX + 8, 198, DIM);
        }
        int blocks = 16;
        while (blocks * session.zoom() < 36) blocks *= 2;
        while (blocks * session.zoom() > 95 && blocks > 1) blocks /= 2;
        int pixels = (int) (blocks * session.zoom()), by = canvasH - 54;
        g.fill(margin, by, margin + pixels, by + 1, DIM);
        g.fill(margin, by - 3, margin + 1, by + 2, DIM); g.fill(margin + pixels, by - 3, margin + pixels + 1, by + 2, DIM);
        text(g, blocks + "m", margin, by - 14, TEXT);
        String coords = "X " + session.hoverBlockX() + "  Z " + session.hoverBlockZ() + "   /   " + Math.round(session.zoom() / 1.5 * 100) + "%";
        text(g, coords, canvasW - margin - font.width(coords), by - 8, DIM);
        g.fill(margin, canvasH - 37, canvasW - margin, canvasH - 36, LINE);
        text(g, tr("loaded_only"), margin, canvasH - 25, DIM);
        String hints = tr("controls"); text(g, hints, canvasW - margin - font.width(hints), canvasH - 12, TEXT);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        x /= uiScale; y /= uiScale;
        if (super.mouseClicked(x, y, button)) return true;
        if (button == 0 && x >= margin && x < margin + leftW && y >= 83 && y < 223) {
            var markers = TacticalMarkerManager.snapshot(1); int i = (int) ((y - 83) / 28);
            if (i < markers.size()) { Vec3 p = markers.get(i).position(1); session.centerOn(p.x, p.z); } return true;
        }
        if (button == 0 && legend && x >= rightX && x < rightX + 128 && y >= 104 && y < 161) {
            var type = TacticalMarker.Type.values()[(int) ((y - 104) / 19)];
            if (!visible.remove(type)) visible.add(type); return true;
        }
        if (overPanel(x, y)) return false;
        if (button == 0) { dragging = true; return true; }
        if (button == 1 || button == 2) { placeMapPing(transform(), x, y); return true; }
        return false;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && dragging) { dragging = false; return true; }
        return super.mouseReleased(x / uiScale, y / uiScale, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && dragging) { session.panPixels(dx / uiScale, dy / uiScale); return true; }
        return super.mouseDragged(x / uiScale, y / uiScale, button, dx / uiScale, dy / uiScale);
    }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) {
        x /= uiScale; y /= uiScale;
        if (!overPanel(x, y) && sy != 0) { session.zoomAt(x, y, Math.pow(1.18, sy), 0, 0, canvasW, canvasH); return true; }
        return super.mouseScrolled(x, y, sx, sy);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (FactoryMapKeyBindings.OPEN_MAP.matches(key, scan)) { onClose(); return true; }
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            legend = !legend; legendButtons.forEach(b -> b.visible = legend); return true;
        }
        if (key == GLFW.GLFW_KEY_SPACE) { center(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    private void placeMapPing(MapCoordinateTransform t, double x, double y) {
        var world = t.screenToWorld(x, y); int wx = Mth.floor(world.x()), wz = Mth.floor(world.z());
        if (!minecraft.level.hasChunkAt(new BlockPos(wx, 0, wz))) return;
        int floor = session.selectedFloorY();
        if (floor == FactoryMapLayerResolver.NO_FLOOR || floor < minecraft.level.getMinBuildHeight()) return;
        TacticalMarkerManager.placeMapLocation(new Vec3(world.x(), floor + 1.05, world.z()));
    }
    @Override public boolean isPauseScreen() { return false; }
    // The map owns its backdrop; vanilla's menu blur would blur the map and labels just drawn.
    @Override public void renderBackground(GuiGraphics g, int x, int y, float delta) {}
    @Override public void removed() { terrain.close(); super.removed(); }
}
