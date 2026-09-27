package dev.draginventory.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Pre-renders every discovered floor into an independent low-detail tactical texture. */
final class FactoryMapTexture implements AutoCloseable {
    private static final int SIZE = 512;
    private final FactoryMapSampler sampler = new FactoryMapSampler();
    private final Map<Integer, LayerTexture> textures = new HashMap<>();
    private FactoryMapLayerResolver.LayerCatalog catalog;

    void draw(GuiGraphics g, Minecraft mc, MapCoordinateTransform transform, FactoryMapSession session) {
        FactoryMapLayerResolver.LayerCatalog next = session.catalog();
        if (next != catalog) {
            catalog = next;
            textures.keySet().removeIf(id -> catalog.byId(id) == null);
            for (var layer : catalog.layers()) {
                LayerTexture old = textures.get(layer.id());
                if (old == null || !old.matches(layer)) {
                    if (old != null) old.close();
                    textures.put(layer.id(), new LayerTexture(layer));
                }
            }
        }
        var active = session.activeLayer();
        if (active == null) return;
        sampler.prepare(mc.level);
        int activeId = active.id();
        // Spend most of the frame budget on the visible floor, but continue baking the other
        // floors in the background so changing height never exposes a raw live block scan.
        for (var entry : textures.entrySet()) {
            entry.getValue().bake(mc, sampler, entry.getKey() == activeId ? 12 : 3);
        }
        LayerTexture texture = textures.get(activeId);
        if (texture != null) texture.blit(g, transform);
    }

    @Override public void close() {
        textures.values().forEach(LayerTexture::close);
        textures.clear(); catalog = null;
    }

    private static final class LayerTexture implements AutoCloseable {
        private final FactoryMapLayerResolver.Layer layer;
        private final ResourceLocation id;
        private DynamicTexture texture;
        private int originX, originZ, step, row;
        private Object world;

        LayerTexture(FactoryMapLayerResolver.Layer layer) {
            this.layer = layer;
            this.id = ResourceLocation.fromNamespaceAndPath("draginventory", "factory_map/layer_" + layer.id());
        }

        boolean matches(FactoryMapLayerResolver.Layer other) { return layer.equals(other); }

        void bake(Minecraft mc, FactoryMapSampler sampler, int rows) {
            int spanX = Math.max(1, layer.maxX() - layer.minX() + 1);
            int spanZ = Math.max(1, layer.maxZ() - layer.minZ() + 1);
            int requestedStep = Math.max(1, (int) Math.ceil(Math.max(spanX, spanZ) / (double) (SIZE - 2)));
            if (texture == null || world != mc.level || requestedStep != step) {
                close();
                world = mc.level;
                step = requestedStep;
                originX = layer.minX(); originZ = layer.minZ(); row = 0;
                texture = new DynamicTexture(new NativeImage(SIZE, SIZE, false));
                mc.getTextureManager().register(id, texture);
                texture.setFilter(false, false);
                texture.getPixels().fillRect(0, 0, SIZE, SIZE, rgba(FactoryMapSampler.COLOR_VOID));
            }
            if (row >= SIZE) return;
            int end = Math.min(SIZE, row + rows);
            for (; row < end; row++) {
                for (int x = 0; x < SIZE; x++) {
                    int wx = originX + x * step, wz = originZ + row * step;
                    int color = sampler.sample(mc.level, wx, wz, layer.floorY());
                    texture.getPixels().setPixelRGBA(x, row, rgba(color));
                }
            }
            texture.upload();
        }

        void blit(GuiGraphics g, MapCoordinateTransform t) {
            if (texture == null) return;
            int x = Mth.floor(t.worldToScreenX(originX));
            int y = Mth.floor(t.worldToScreenY(originZ));
            int width = Mth.ceil(SIZE * step * t.zoom());
            g.blit(id, x, y, width, width, 0, 0, SIZE, SIZE, SIZE, SIZE);
        }

        @Override public void close() {
            if (texture != null) Minecraft.getInstance().getTextureManager().release(id);
            texture = null;
        }
    }

    private static int rgba(int argb) { return argb & 0xFF00FF00 | (argb >> 16 & 255) | (argb & 255) << 16; }
}
