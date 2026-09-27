package dev.draginventory.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Incrementally sampled texture, rather than tens of thousands of GUI quads every frame. */
final class FactoryMapTexture implements AutoCloseable {
    private static final int SIZE = 384;
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("draginventory", "factory_map/live");
    private final FactoryMapSampler sampler = new FactoryMapSampler();
    private DynamicTexture texture;
    private int originX, originZ, step, floor, row;
    private boolean surface;
    private long refreshed;

    void draw(GuiGraphics g, Minecraft mc, MapCoordinateTransform t, FactoryMapSession session) {
        double span = Math.max(t.viewportWidth(), t.viewportHeight()) / t.zoom();
        int requestedStep = Math.max(1, (int) Math.ceil(span / (SIZE - 64)));
        var a = t.screenToWorld(t.viewportX(), t.viewportY());
        var b = t.screenToWorld(t.viewportX() + t.viewportWidth(), t.viewportY() + t.viewportHeight());
        if (texture == null || requestedStep != step || !surface && floor != session.selectedFloorY() || surface != session.surface()
                || a.x() < originX || a.z() < originZ || b.x() > originX + SIZE * step || b.z() > originZ + SIZE * step) {
            step = requestedStep;
            originX = Mth.floor(t.centerX() - SIZE * step / 2.0);
            originZ = Mth.floor(t.centerZ() - SIZE * step / 2.0);
            floor = session.selectedFloorY(); surface = session.surface(); row = 0;
            if (texture == null) {
                texture = new DynamicTexture(new NativeImage(SIZE, SIZE, false));
                mc.getTextureManager().register(ID, texture);
                texture.setFilter(false, false);
            }
            texture.getPixels().fillRect(0, 0, SIZE, SIZE, rgba(FactoryMapSampler.COLOR_VOID));
        }
        // Newly received chunks appear without reopening the map.
        if (row == SIZE && net.minecraft.Util.getMillis() - refreshed > 2000) row = 0;
        if (row < SIZE) {
            long deadline = System.nanoTime() + 4_000_000;
            int start = row;
            do {
                for (int x = 0; x < SIZE; x++) {
                    int wx = originX + x * step, wz = originZ + row * step;
                    int color = surface ? sampler.surface(mc.level, wx, wz) : sampler.sample(mc.level, wx, wz, floor);
                    texture.getPixels().setPixelRGBA(x, row, rgba(color));
                }
                row++;
            } while (row < SIZE && row - start < 24 && System.nanoTime() < deadline);
            texture.upload(); refreshed = net.minecraft.Util.getMillis();
        }
        int x = Mth.floor(t.worldToScreenX(originX)), y = Mth.floor(t.worldToScreenY(originZ));
        int size = Mth.ceil(SIZE * step * t.zoom());
        g.blit(ID, x, y, size, size, 0, 0, SIZE, SIZE, SIZE, SIZE);
    }
    private static int rgba(int argb) { return argb & 0xFF00FF00 | (argb >> 16 & 255) | (argb & 255) << 16; }
    @Override public void close() {
        if (texture != null) Minecraft.getInstance().getTextureManager().release(ID);
        texture = null;
    }
}
