package dev.draginventory.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Pre-renders every discovered floor into a fixed high-resolution tactical texture. */
final class FactoryMapTexture implements AutoCloseable {
    private static final int SIZE = 1024;
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
            entry.getValue().bake(mc, sampler, entry.getKey() == activeId ? 64 : 4);
        }
        LayerTexture texture = textures.get(activeId);
        if (texture != null) {
            texture.blit(g, transform);
            texture.drawConnectors(g, transform, active.connectors());
        }
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
            int requestedStep = 1;
            if (texture == null || world != mc.level || requestedStep != step) {
                close();
                world = mc.level;
                step = requestedStep;
                originX = FactoryMapBounds.MIN_X; originZ = FactoryMapBounds.MIN_Z; row = 0;
                texture = new DynamicTexture(new NativeImage(SIZE, SIZE, false));
                mc.getTextureManager().register(id, texture);
                texture.setFilter(true, false);
                texture.getPixels().fillRect(0, 0, SIZE, SIZE, rgba(FactoryMapSampler.COLOR_VOID));
            }
            if (row >= SIZE) return;
            int end = Math.min(SIZE, row + rows);
            for (; row < end; row++) {
                for (int x = 0; x < SIZE; x++) {
                    int wx = originX + x * step, wz = originZ + row * step;
                    int color = (wx > FactoryMapBounds.MAX_X || wz > FactoryMapBounds.MAX_Z)
                            ? FactoryMapSampler.COLOR_VOID : sampler.sampleSmooth(mc.level, wx, wz, layer.floorY(), 1);
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

        void drawConnectors(GuiGraphics g, MapCoordinateTransform t, java.util.List<FactoryMapLayerResolver.Connector> connectors) {
            for (var connector : connectors) {
                int x = Mth.floor(t.worldToScreenX(connector.x()));
                int y = Mth.floor(t.worldToScreenY(connector.z()));
                int color = connector.kind() == FactoryMapLayerResolver.Connector.Kind.STAIR ? 0xFFE9B75A : 0xFF62D8C6;
                g.fill(x - 3, y - 3, x + 4, y + 4, 0xAA0A1519);
                g.fill(x - 2, y - 2, x + 3, y + 3, color);
                g.fill(x - 1, y - 1, x + 2, y + 2, 0xFF0B171B);
            }
        }

        @Override public void close() {
            if (texture != null) Minecraft.getInstance().getTextureManager().release(id);
            texture = null;
        }
    }

    private static int rgba(int argb) { return argb & 0xFF00FF00 | (argb >> 16 & 255) | (argb & 255) << 16; }
}
