package dev.draginventory.client.map;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.mojang.math.Axis;
import dev.draginventory.client.TacticalMarker;
import dev.draginventory.client.TacticalMarkerLogic;
import dev.draginventory.client.TacticalMarkerManager;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class FactoryMapScreen extends ModularUIScreen {
    private static final int VIEW_MARGIN = 8;
    private static final int HEADER_HEIGHT = 38;
    private static final int FOOTER_HEIGHT = 30;
    private static final int MAX_CELLS_X = 260;
    private static final int MAX_CELLS_Y = 160;
    private static final FactoryMapSampler SAMPLER = new FactoryMapSampler();

    private final FactoryMapSession session;
    private final FactoryMapUI.View view;
    private boolean dragging;

    private FactoryMapScreen(FactoryMapSession session, FactoryMapUI.View view) {
        super(view.ui(), Component.translatable("draginventory.map.title"));
        this.session = session;
        this.view = view;
    }

    public static FactoryMapScreen create() {
        FactoryMapSession session = new FactoryMapSession(Minecraft.getInstance());
        return new FactoryMapScreen(session, FactoryMapUI.create(session));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        session.tick(mc);

        graphics.fill(0, 0, width, height, 0xF20A0D0F);
        Viewport vp = viewport();
        if (mc.level != null && mc.player != null && vp.width > 0 && vp.height > 0) {
            MapCoordinateTransform transform = session.transform(vp.x, vp.y, vp.width, vp.height);
            if (transform.contains(mouseX, mouseY)) {
                var world = transform.screenToWorld(mouseX, mouseY);
                session.setHover(world.x(), world.z());
            }
            drawMap(graphics, mc, transform, partialTick);
        } else {
            graphics.fill(vp.x, vp.y, vp.x + vp.width, vp.y + vp.height, FactoryMapSampler.COLOR_VOID);
        }

        updateLabels();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawMap(GuiGraphics g, Minecraft mc, MapCoordinateTransform t, float partialTick) {
        int left = t.viewportX();
        int top = t.viewportY();
        int right = left + t.viewportWidth();
        int bottom = top + t.viewportHeight();
        g.fill(left, top, right, bottom, 0xFF101518);

        var min = t.screenToWorld(left, top);
        var max = t.screenToWorld(right, bottom);
        double worldWidth = Math.abs(max.x() - min.x());
        double worldHeight = Math.abs(max.z() - min.z());
        int step = Math.max(1, (int) Math.ceil(Math.max(worldWidth / MAX_CELLS_X, worldHeight / MAX_CELLS_Y)));

        int minX = Mth.floor(Math.min(min.x(), max.x()));
        int maxX = Mth.ceil(Math.max(min.x(), max.x()));
        int minZ = Mth.floor(Math.min(min.z(), max.z()));
        int maxZ = Mth.ceil(Math.max(min.z(), max.z()));
        int startX = Math.floorDiv(minX, step) * step;
        int startZ = Math.floorDiv(minZ, step) * step;
        int layer = session.selectedFloorY();

        for (int z = startZ; z <= maxZ; z += step) {
            int sy0 = Mth.floor(t.worldToScreenY(z));
            int sy1 = Mth.ceil(t.worldToScreenY(z + step));
            if (sy1 < top || sy0 > bottom) continue;
            for (int x = startX; x <= maxX; x += step) {
                int sx0 = Mth.floor(t.worldToScreenX(x));
                int sx1 = Mth.ceil(t.worldToScreenX(x + step));
                if (sx1 < left || sx0 > right) continue;
                int color = SAMPLER.sample(mc.level, x + step / 2, z + step / 2, layer);
                g.fill(Math.max(left, sx0), Math.max(top, sy0),
                        Math.min(right, Math.max(sx0 + 1, sx1)),
                        Math.min(bottom, Math.max(sy0 + 1, sy1)), color);
            }
        }

        drawGrid(g, t, minX, maxX, minZ, maxZ);
        drawMarkers(g, mc, t, partialTick);
        drawPlayer(g, mc, t);

        // Thin tactical viewport frame.
        g.fill(left, top, right, top + 1, 0xFF77858A);
        g.fill(left, bottom - 1, right, bottom, 0xFF77858A);
        g.fill(left, top, left + 1, bottom, 0xFF77858A);
        g.fill(right - 1, top, right, bottom, 0xFF77858A);
    }

    private static void drawGrid(GuiGraphics g, MapCoordinateTransform t,
                                 int minX, int maxX, int minZ, int maxZ) {
        int left = t.viewportX();
        int top = t.viewportY();
        int right = left + t.viewportWidth();
        int bottom = top + t.viewportHeight();

        int gx = Math.floorDiv(minX, 16) * 16;
        for (; gx <= maxX; gx += 16) {
            int sx = Mth.floor(t.worldToScreenX(gx));
            if (sx < left || sx >= right) continue;
            int color = Math.floorMod(gx, 64) == 0 ? 0x6A8DA0A8 : 0x345F6A6F;
            g.fill(sx, top, sx + 1, bottom, color);
        }
        int gz = Math.floorDiv(minZ, 16) * 16;
        for (; gz <= maxZ; gz += 16) {
            int sy = Mth.floor(t.worldToScreenY(gz));
            if (sy < top || sy >= bottom) continue;
            int color = Math.floorMod(gz, 64) == 0 ? 0x6A8DA0A8 : 0x345F6A6F;
            g.fill(left, sy, right, sy + 1, color);
        }
    }

    private void drawMarkers(GuiGraphics g, Minecraft mc, MapCoordinateTransform t, float partialTick) {
        List<TacticalMarker> markers = TacticalMarkerManager.snapshot(partialTick);
        if (markers.isEmpty()) return;

        for (TacticalMarker marker : markers) {
            Vec3 pos = marker.position(partialTick);
            int floor = FactoryMapLayerResolver.findWalkableAt(
                    mc.level, Mth.floor(pos.x), Mth.floor(pos.z), Mth.floor(pos.y) - 1, 7);
            if (floor != FactoryMapLayerResolver.NO_FLOOR && Math.abs(floor - session.selectedFloorY()) > 4) continue;

            int sx = Mth.floor(t.worldToScreenX(pos.x));
            int sy = Mth.floor(t.worldToScreenY(pos.z));
            if (!t.contains(sx, sy)) continue;

            int color = switch (marker.type()) {
                case ENEMY -> 0xFFFF4949;
                case ITEM -> 0xFFFFD36A;
                case LOCATION -> 0xFFFFFFFF;
            };
            drawMarkerGlyph(g, sx, sy, marker.type(), color);

            if (mc.player != null) {
                int meters = TacticalMarkerLogic.distanceMeters(
                        pos.x - mc.player.getX(), pos.y - mc.player.getY(), pos.z - mc.player.getZ());
                String distance = meters + "m";
                g.drawString(mc.font, distance, sx - mc.font.width(distance) / 2, sy + 7, color, true);
            }
        }
    }

    private static void drawMarkerGlyph(GuiGraphics g, int x, int y, TacticalMarker.Type type, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 40);
        if (type == TacticalMarker.Type.LOCATION) {
            g.pose().mulPose(Axis.ZP.rotationDegrees(45));
            g.fill(-4, -4, 4, 4, color);
        } else if (type == TacticalMarker.Type.ENEMY) {
            g.fill(-2, -7, 2, 3, color);
            g.fill(-2, 5, 2, 8, color);
        } else {
            g.fill(-5, -5, 5, 5, 0xCC111416);
            g.fill(-3, -3, 3, 3, color);
        }
        g.pose().popPose();
    }

    private void drawPlayer(GuiGraphics g, Minecraft mc, MapCoordinateTransform t) {
        int playerFloor = FactoryMapLayerResolver.resolvePlayerFloor(mc.level, mc.player);
        if (Math.abs(playerFloor - session.selectedFloorY()) > 4) return;

        int x = Mth.floor(t.worldToScreenX(mc.player.getX()));
        int y = Mth.floor(t.worldToScreenY(mc.player.getZ()));
        if (!t.contains(x, y)) return;

        g.pose().pushPose();
        g.pose().translate(x, y, 50);
        // Minecraft yaw 0 points +Z (map down). The glyph's native direction is up.
        g.pose().mulPose(Axis.ZP.rotationDegrees(180.0f - mc.player.getYRot()));
        g.fill(-2, -9, 2, 4, 0xFFFFFFFF);
        g.fill(-5, -3, 6, 3, 0xFF85D7FF);
        g.fill(-2, -7, 2, -3, 0xFFFFFFFF);
        g.pose().popPose();
    }

    private void updateLabels() {
        view.floorLabel().setText(Component.translatable(
                session.autoFloor() ? "draginventory.map.floor_auto" : "draginventory.map.floor",
                session.selectedFloorY()));
        view.statusLabel().setText(Component.translatable("draginventory.map.footer",
                session.hoverBlockX(), session.hoverBlockZ(), session.zoomText()));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Viewport vp = viewport();
        MapCoordinateTransform transform = session.transform(vp.x, vp.y, vp.width, vp.height);
        if (transform.contains(mouseX, mouseY)) {
            if (button == 0) {
                dragging = true;
                return true;
            }
            if (button == 1) {
                placeMapPing(transform, mouseX, mouseY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && dragging) {
            session.panPixels(dragX, dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Viewport vp = viewport();
        MapCoordinateTransform transform = session.transform(vp.x, vp.y, vp.width, vp.height);
        if (transform.contains(mouseX, mouseY) && scrollY != 0) {
            session.zoomAt(mouseX, mouseY, Math.pow(1.18, scrollY), vp.x, vp.y, vp.width, vp.height);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (FactoryMapKeyBindings.OPEN_MAP.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void placeMapPing(MapCoordinateTransform transform, double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var world = transform.screenToWorld(mouseX, mouseY);
        int blockX = Mth.floor(world.x());
        int blockZ = Mth.floor(world.z());
        int floor = FactoryMapLayerResolver.findWalkableAt(
                mc.level, blockX, blockZ, session.selectedFloorY(), 6);
        if (floor == FactoryMapLayerResolver.NO_FLOOR) floor = session.selectedFloorY();
        TacticalMarkerManager.placeMapLocation(new Vec3(world.x(), floor + 1.05, world.z()));
    }

    private Viewport viewport() {
        return new Viewport(
                VIEW_MARGIN,
                HEADER_HEIGHT,
                Math.max(1, width - VIEW_MARGIN * 2),
                Math.max(1, height - HEADER_HEIGHT - FOOTER_HEIGHT));
    }

    private record Viewport(int x, int y, int width, int height) {}
}
