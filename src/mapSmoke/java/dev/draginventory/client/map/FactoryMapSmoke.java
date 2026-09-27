package dev.draginventory.client.map;

import dev.draginventory.client.TacticalMarker;
import dev.draginventory.client.TacticalMarkerManager;
import java.nio.file.Path;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

/** Opt-in isolated UI fixture. The factory is a generated QA scene, not a shipped map. */
@EventBusSubscriber(modid = "draginventory", value = Dist.CLIENT)
public final class FactoryMapSmoke {
    private static boolean started, built;
    private static long deadline, last;
    private static int tick;
    private static String capture;
    private static FactoryMapSession state;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        var mc = Minecraft.getInstance();
        if (!started && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            started = true; deadline = Util.getMillis() + 180000;
            mc.getWindow().setWindowed(1600, 900);
            mc.options.guiScale().set(2); mc.options.renderDistance().set(8);
            mc.options.pauseOnLostFocus = false; mc.resizeDisplay();
            var rules = new GameRules(); rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("map-ui-" + System.currentTimeMillis(),
                    new LevelSettings("Factory map UI QA", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(1, false, false), r -> r.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
        }
        if (!started) return;
        if (Util.getMillis() > deadline) throw new AssertionError("Map smoke timed out");
        if (mc.level == null || mc.player == null || mc.getOverlay() != null) return;
        if (!built && mc.screen == null) {
            built = true; var uuid = mc.player.getUUID();
            mc.getSingleplayerServer().submit(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
                var level = player.serverLevel(); player.teleportTo(0, -60, 0);
                for (int x = -95; x < 96; x++) for (int z = -80; z < 81; z++) {
                    BlockState ground = (Math.abs(x) < 6 || Math.abs(z) < 6 || Math.abs(x) > 86 || Math.abs(z) > 72)
                            ? Blocks.GRAY_CONCRETE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                    level.setBlock(new BlockPos(x, -61, z), ground, 2);
                    if ((x == 0 && z % 7 < 3) || (z == 0 && x % 7 < 3))
                        level.setBlock(new BlockPos(x, -61, z), Blocks.YELLOW_CONCRETE.defaultBlockState(), 2);
                }
                for (int bx : new int[] {-76, -35, 16, 57}) for (int bz : new int[] {-61, 19}) {
                    for (int x = bx; x < bx + 27; x++) for (int z = bz; z < bz + 36; z++) {
                        level.setBlock(new BlockPos(x, -61, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                        for (int y = -60; y < -51; y++) {
                            boolean wall = x == bx || x == bx + 26 || z == bz || z == bz + 35;
                            boolean door = z == bz + 35 && x > bx + 10 && x < bx + 16 && y < -56;
                            if (wall && !door || y == -52)
                                level.setBlock(new BlockPos(x, y, z), (y == -52 && x % 4 == 0 ? Blocks.POLISHED_ANDESITE : Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState(), 2);
                        }
                    }
                }
                for (int x = -80; x < -12; x += 10) for (int z = -17; z < -7; z++) for (int a = 0; a < 7; a++)
                    for (int y = -60; y < -57; y++) level.setBlock(new BlockPos(x + a, y, z), Blocks.CYAN_TERRACOTTA.defaultBlockState(), 2);
            }).join();
        }
        if (!built || Util.getMillis() - last < 50) return;
        last = Util.getMillis(); tick++;
        if (tick == 80) {
            mc.setScreen(FactoryMapScreen.create());
            var field = FactoryMapScreen.class.getDeclaredField("session"); field.setAccessible(true);
            state = (FactoryMapSession) field.get(mc.screen);
            state.zoomAt(400, 225, 2, 0, 0, 800, 450);
            TacticalMarkerManager.placeMapLocation(new Vec3(-24, -51, -38));
            TacticalMarkerManager.placeMapLocation(new Vec3(32, -51, 38));
            // Fixture-only enemy and item symbols exercise all legend colours.
            var mapField = TacticalMarkerManager.class.getDeclaredField("MARKERS"); mapField.setAccessible(true);
            @SuppressWarnings("unchecked") var markers = (java.util.Map<Object, TacticalMarker>) mapField.get(null);
            markers.put("qa-enemy", new TacticalMarker(TacticalMarker.Type.ENEMY, new Vec3(28, -51, -44), null, Util.getMillis()));
            markers.put("qa-item", new TacticalMarker(TacticalMarker.Type.ITEM, new Vec3(-28, -57, -12), null, Util.getMillis()));
        }
        if (!(mc.screen instanceof FactoryMapScreen screen)) return;
        if (tick == 125) {
            capture = "map-surface.png";
            check(TacticalMarkerManager.snapshot(1).size() == 4, "fixture markers");
        }
        if (tick == 130) {
            double before = state.zoom();
            screen.mouseScrolled(400, 240, 0, 1);
            check(state.zoom() > before, "zoom");
            var beforeWorld = state.transform(0, 0, 800, 450).screenToWorld(400, 240);
            screen.mouseClicked(400, 240, 0); screen.mouseDragged(415, 250, 0, 15, 10); screen.mouseReleased(415, 250, 0);
            var after = state.transform(0, 0, 800, 450).screenToWorld(415, 250);
            check(Math.abs(after.x() - beforeWorld.x()) < .001 && Math.abs(after.z() - beforeWorld.z()) < .001, "pan anchor");
            int count = TacticalMarkerManager.snapshot(1).size();
            screen.mouseClicked(70, 110, 1); check(TacticalMarkerManager.snapshot(1).size() == count, "panel intercept");
            screen.mouseClicked(400, 280, 1); check(TacticalMarkerManager.snapshot(1).size() == count + 1, "map ping");
            screen.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0);
            screen.mouseClicked(690, 185, 0); check(!state.surface(), "surface toggle");
        }
        if (tick == 170) capture = "map-floor.png";
        if (tick == 175) {
            screen.keyPressed(GLFW.GLFW_KEY_LEFT_SHIFT, 0, 0);
            capture = "map-legend-hidden.png";
        }
        if (tick == 180) {
            mc.options.guiScale().set(4); mc.resizeDisplay();
            screen.keyPressed(GLFW.GLFW_KEY_LEFT_SHIFT, 0, 0);
        }
        if (tick == 215) capture = "map-scale4.png";
        if (tick == 220) {
            // Rescaled controls still receive the same logical click.
            float s = Math.min(1f, Math.min(screen.width / 640f, screen.height / 360f));
            int cw = Math.round(screen.width / s);
            int rx = cw - Math.max(18, Math.round(cw * .055f)) - 128;
            screen.mouseClicked((rx + 50) * s, 185 * s, 0); check(state.surface(), "scaled button hit");
            screen.onClose(); mc.setScreen(FactoryMapScreen.create());
        }
        if (tick == 255) {
            check(mc.screen instanceof FactoryMapScreen, "reopen after texture release");
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0); check(mc.screen == null, "escape closes");
            LoggerFactory.getLogger("FactoryMapSmoke").info("FACTORY_MAP_SMOKE_PASS: overview, slice, zoom, drag, panel hit, ping, scale4, legend, reopen");
            mc.stop();
        }
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    @SubscribeEvent public static void rendered(ScreenEvent.Render.Post event) throws Exception {
        if (capture == null || !(event.getScreen() instanceof FactoryMapScreen)) return;
        event.getGuiGraphics().flush();
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) { image.writeToFile(Path.of(capture)); }
        capture = null;
    }
}
