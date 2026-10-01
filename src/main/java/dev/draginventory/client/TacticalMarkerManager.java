package dev.draginventory.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/** Local-only pings: camera raycast, click disambiguation and world-scoped state. */
@EventBusSubscriber(modid = "draginventory", value = Dist.CLIENT)
public final class TacticalMarkerManager {
    static final double MAX_DISTANCE = 128;
    static final SequencedMap<Object, TacticalMarker> MARKERS = new LinkedHashMap<>();
    private static final TacticalClickGesture<TargetHit> CLICKS = new TacticalClickGesture<>();
    private static TacticalMarkerLogic.MarkerWrite<Object,TacticalMarker> firstWrite;
    private static ClientLevel level;
    private static Player owner;

    private TacticalMarkerManager() {}

    @SubscribeEvent
    public static void input(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE || event.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        maintain(mc, Util.getMillis());
        if (!canInput(mc) || !mc.mouseHandler.isMouseGrabbed()) return;
        // Release still reaches vanilla so a pre-existing held binding cannot stick.
        event.setCanceled(true);
        CLICKS.press(raycast(mc), Util.getMillis(), TacticalMarkerManager::mark);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        maintain(mc, Util.getMillis());
        if (canInput(mc)) {
            CLICKS.flush(Util.getMillis());
            if (!CLICKS.waiting()) firstWrite = null;
        } else cancelClicks();
    }

    private static void cancelClicks() { CLICKS.cancel(); firstWrite = null; }

    @SubscribeEvent
    public static void screenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() != null) cancelClicks();
    }

    static void maintain(Minecraft mc, long now) {
        if (level != mc.level || owner != mc.player) {
            MARKERS.clear();
            cancelClicks();
            level = mc.level;
            owner = mc.player;
            TacticalMarkerHud.invalidate();
        }
        // Also called before rendering so pickups disappear on the next frame, even in menus.
        MARKERS.values().removeIf(marker -> !marker.valid(mc.level, now));
    }

    static boolean canInput(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.player.isAlive() && !mc.player.isSpectator()
                && mc.getCameraEntity() == mc.player && mc.screen == null && mc.getOverlay() == null
                && !mc.isPaused() && !mc.options.hideGui;
    }


    /**
     * Read-only snapshot used by the tactical map. The same marker objects continue to drive the
     * world-space HUD and the compass bridge, so map/HUD/compass can never drift into separate state.
     */
    public static List<TacticalMarker> snapshot(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        maintain(mc, Util.getMillis());
        if (mc.level == null || MARKERS.isEmpty()) return List.of();
        return List.copyOf(MARKERS.values());
    }

    /**
     * Adds a location ping from the full-screen map into the exact same store used by world pings.
     * Closing the map therefore makes the ping immediately visible in TacticalMarkerHud and,
     * when enabled, CompassMarkerBridge.
     */
    public static void placeMapLocation(Vec3 position) {
        Minecraft mc = Minecraft.getInstance();
        maintain(mc, Util.getMillis());
        if (mc.level == null || mc.player == null || position == null) return;
        BlockPos block = BlockPos.containing(position);
        TacticalMarkerLogic.putMarker(MARKERS, new MapLocation(block),
                new TacticalMarker(TacticalMarker.Type.LOCATION, position, null, Util.getMillis()));
    }

    /** Clears user location pings without touching enemy/item marks. */
    public static void clearLocationMarkers() {
        MARKERS.values().removeIf(marker -> marker.type() == TacticalMarker.Type.LOCATION);
    }

    private static void mark(TargetHit hit, boolean doubleClick) {
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        if (doubleClick) {
            if (hit == null) hit = directionEndpoint(mc);
            if (hit != null) {
                Object key = hit.entity != null ? hit.entity.getUUID() : hit.locationKey;
                TacticalMarkerLogic.upgrade(MARKERS, firstWrite, key,
                        new TacticalMarker(TacticalMarker.Type.ENEMY, hit.position, null, now),
                        previous -> previous.valid(mc.level, now));
            }
            firstWrite = null;
            return;
        }
        firstWrite = null;
        if (hit == null) return;
        if (hit.entity != null && (!hit.entity.isAlive() || hit.entity.isRemoved() || hit.entity.level() != mc.level)) return;
        if (hit.entity instanceof ItemEntity item) {
            if (!item.getItem().isEmpty()) firstWrite = TacticalMarkerLogic.writeImmediate(MARKERS, item.getUUID(),
                    new TacticalMarker(TacticalMarker.Type.ITEM, hit.position, item, now));
        } else {
            firstWrite = TacticalMarkerLogic.writeImmediate(MARKERS, hit.locationKey,
                    new TacticalMarker(TacticalMarker.Type.LOCATION, hit.position, null, now));
        }
    }

    private static TargetHit directionEndpoint(Minecraft mc) {
        var camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) return null;
        Vec3[] ray = TacticalMarkerHud.centerRay(camera);
        Vec3 position = ray[0].add(ray[1].scale(MAX_DISTANCE));
        return new TargetHit(position, null, new DirectionalPoint(BlockPos.containing(position)));
    }

    static TargetHit raycast(Minecraft mc) {
        var camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) return null;
        Vec3[] ray = TacticalMarkerHud.centerRay(camera);
        Vec3 start = ray[0], end = start.add(ray[1].scale(MAX_DISTANCE));
        var block = mc.level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        boolean blockHit = block.getType() == HitResult.Type.BLOCK;
        if (blockHit) end = block.getLocation();
        double nearest = start.distanceToSqr(end);
        Entity selected = null;
        Vec3 selectedPoint = null;
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
        // Vanilla excludes ItemEntity from isPickable(), so drops must be included explicitly.
        for (Entity entity : mc.level.getEntities(mc.player, new AABB(start, end).inflate(1),
                e -> e.isAlive() && !e.isSpectator() && (e instanceof ItemEntity || e.isPickable()))) {
            if (entity instanceof ItemEntity item && item.getItem().isEmpty()) continue;
            AABB bounds = entity.getBoundingBox().move(entity.getPosition(partial).subtract(entity.position()))
                    .inflate(entity instanceof ItemEntity ? 0.12 : entity.getPickRadius());
            Vec3 point = bounds.contains(start) ? start : bounds.clip(start, end).orElse(null);
            if (point == null) continue;
            double distance = point.distanceToSqr(start);
            if (distance <= nearest) {
                nearest = distance;
                selected = entity;
                selectedPoint = point;
            }
        }
        if (selected != null) return new TargetHit(selectedPoint, selected, selected.getUUID());
        return blockHit ? new TargetHit(block.getLocation(), null, new Surface(block.getBlockPos(), block.getDirection())) : null;
    }

    record TargetHit(Vec3 position, Entity entity, Object locationKey) {}
    private record Surface(BlockPos pos, Direction face) {}
    private record MapLocation(BlockPos pos) {}
    private record DirectionalPoint(BlockPos pos) {}
}
