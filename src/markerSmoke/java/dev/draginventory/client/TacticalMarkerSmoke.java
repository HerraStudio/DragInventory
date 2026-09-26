package dev.draginventory.client;

import java.nio.file.Path;
import java.util.concurrent.Future;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.slf4j.LoggerFactory;

/** Opt-in integration QA. Uses real mouse callbacks, entities, packets and native rendering. */
@EventBusSubscriber(modid = "draginventory", value = Dist.CLIENT)
public final class TacticalMarkerSmoke {
    private static boolean started;
    private static int tick;
    private static Future<?> setup;
    private static Future<?> change;
    private static int zombieId, itemId, cowId;
    private static String screenshot;
    private static long deadline, enemyTime;
    private static Vec3 previousEnemy;
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static long secondClickAt;
    private static long lastStep;
    private static final java.util.List<Object> fifoKeys = new java.util.ArrayList<>();
    private static final java.util.Set<TacticalMarker.Type> recordedTypes = java.util.EnumSet.noneOf(TacticalMarker.Type.class);
    private static TacticalMarker animating;
    private static long lastFrameAge = -100;
    private static int frameIndex;
    private static final StringBuilder animationLog = new StringBuilder("type,frame,age_ms,scale,offset_y,opacity\n");

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        if (!started && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            started = true;
            deadline = Util.getMillis() + 180_000;
            mc.getWindow().setWindowed(1600, 900);
            mc.options.guiScale().set(2);
            mc.options.renderDistance().set(3);
            mc.options.simulationDistance().set(5);
            mc.options.bobView().set(false);
            mc.options.fov().set(80);
            mc.options.pauseOnLostFocus = false;
            mc.resizeDisplay();
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            var settings = new LevelSettings("Tactical marker QA", GameType.CREATIVE, false,
                    Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("marker-qa-" + System.currentTimeMillis(), settings,
                    new WorldOptions(1, false, false), registry -> registry.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
        }
        if (!started) return;
        if (Util.getMillis() > deadline) throw new AssertionError("Marker QA timed out");
        if (mc.player == null || mc.level == null || mc.getOverlay() != null) return;
        if (setup == null && mc.screen == null) {
            var uuid = mc.player.getUUID();
            setup = mc.getSingleplayerServer().submit(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
                var level = player.serverLevel();
                player.teleportTo(0.5, -60, 0.5);
                level.setDayTime(6000);
                var zombie = new Zombie(EntityType.ZOMBIE, level);
                zombie.setPos(-3.5, -60, 10.5);
                zombie.setNoAi(true);
                zombie.setInvulnerable(true);
                zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                level.addFreshEntity(zombie);
                zombieId = zombie.getId();
                ItemStack drop = sampleItem();
                if (GwoHudBridge.installed()) {
                    player.getInventory().setItem(0, drop.copy());
                    player.getInventory().selected = 0;
                    player.inventoryMenu.broadcastChanges();
                }
                var item = new ItemEntity(level, 3.5, -59.85, 8.5, drop);
                item.setDeltaMovement(Vec3.ZERO);
                item.setNoGravity(true);
                item.setNeverPickUp();
                level.addFreshEntity(item);
                itemId = item.getId();
                var cow = new Cow(EntityType.COW, level);
                cow.setPos(7.5, -60, 12.5);
                cow.setNoAi(true);
                level.addFreshEntity(cow);
                cowId = cow.getId();
                level.setBlockAndUpdate(new BlockPos(0, -60, 12), Blocks.STONE.defaultBlockState());
            });
            return;
        }
        if (setup == null || !setup.isDone()) return;
        setup.get();
        if (screenshot != null || reload != null && !reload.isDone()) return;
        if (secondClickAt != 0) {
            if (Util.getMillis() < secondClickAt + 400) return;
            long elapsed = Util.getMillis() - secondClickAt;
            check(elapsed <= 450, "QA second-click callback exceeded the intended window: " + elapsed);
            click(mc);
            LoggerFactory.getLogger("TacticalMarkerSmoke").info("MARKER_RELAXED_DOUBLE_PASS interval_ms={}", elapsed);
            secondClickAt = 0;
        }
        // Client catch-up ticks may occur in one rendered frame; UI gestures use wall time.
        if (Util.getMillis() - lastStep < 50) return;
        lastStep = Util.getMillis();
        tick++;
        if (tick == 35) aim(mc, new Vec3(0.5, -59.5, 12));
        if (tick == 50) {
            check(TacticalMarkerManager.raycast(mc) != null, "First location ray hits a surface");
            click(mc);
        }
        if (tick == 62) {
            expectCount(1);
            check(count(TacticalMarker.Type.LOCATION) == 1, "Single click is location");
            screenshot = "marker-location-center.png";
        }
        if (tick == 65) aim(mc, target(mc, zombieId));
        if (tick == 68) click(mc);
        if (tick == 79) {
            expectCount(2);
            check(count(TacticalMarker.Type.ENEMY) == 0, "Single click on enemy is only a location");
        }
        if (tick == 80) { click(mc); secondClickAt = Util.getMillis(); }
        if (tick == 83) {
            expectCount(2);
            check(count(TacticalMarker.Type.ENEMY) == 1, "Double click is enemy without extra location");
            enemyTime = enemy().createdAt();
        }
        if (tick == 86) { click(mc); click(mc); }
        if (tick == 89) {
            expectCount(2);
            check(enemy().createdAt() > enemyTime, "Repeat enemy refreshes time");
        }
        if (tick == 95) aim(mc, target(mc, itemId));
        if (tick == 110) {
            check(TacticalMarkerManager.raycast(mc).entity().getId() == itemId, "Ray includes non-pickable drop");
            click(mc);
        }
        if (tick == 122) {
            expectCount(3);
            check(count(TacticalMarker.Type.ITEM) == 1, "Item takes priority over ground");
            screenshot = "marker-item-center.png";
        }
        if (tick == 125) { click(mc); click(mc); }
        if (tick == 128) expectCount(3);
        if (tick == 130) aim(mc, new Vec3(0.5, -59, 14));
        if (tick == 145) screenshot = "marker-all-types.png";
        if (tick == 150) { mc.options.fov().set(35); }
        if (tick == 165) screenshot = "marker-zoom.png";
        if (tick == 170) {
            mc.options.fov().set(80);
            mc.options.guiScale().set(3);
            mc.resizeDisplay();
        }
        if (tick == 183) screenshot = "marker-scale3.png";
        if (tick == 188) {
            mc.options.guiScale().set(4);
            mc.getWindow().setWindowed(1280, 960);
            mc.resizeDisplay();
        }
        if (tick == 201) screenshot = "marker-scale4.png";
        if (tick == 205) {
            mc.getWindow().setWindowed(1600, 900);
            mc.options.guiScale().set(2);
            mc.resizeDisplay();
            aim(mc, new Vec3(0.5, -59, -14));
        }
        if (tick == 220) screenshot = "marker-edge-behind.png";
        if (tick == 225) {
            aim(mc, new Vec3(0.5, -59, 14));
            previousEnemy = enemy().position(1);
            change = mc.getSingleplayerServer().submit(() -> mc.getSingleplayerServer().overworld().getEntity(zombieId).teleportTo(-6.5, -60, 11.5));
        }
        if (tick == 245) {
            change.get();
            check(target(mc, zombieId).distanceTo(previousEnemy) > 2, "Enemy actually moved in client world");
            check(enemy().position(1).equals(previousEnemy), "Enemy ping stays at the original location");
            check(enemy().target() == null, "Fixed enemy ping must not retain entity tracking");
            screenshot = "marker-enemy-moved.png";
        }
        if (tick == 247) screenshot = "marker-icon-alpha-check.png";
        if (tick == 250) aim(mc, target(mc, cowId));
        if (tick == 265) { click(mc); click(mc); }
        if (tick == 268) {
            check(count(TacticalMarker.Type.ENEMY) == 1, "Passive animal is not an enemy");
            check(count(TacticalMarker.Type.LOCATION) == 2, "Non-enemy double falls back to location");
        }
        if (tick == 272) {
            mc.setScreen(new InventoryScreen(mc.player));
            click(mc);
            expectCount(4);
        }
        if (tick == 280) { mc.setScreen(null); mc.mouseHandler.grabMouse(); aim(mc, target(mc, itemId)); }
        if (tick == 295) {
            change = mc.getSingleplayerServer().submit(() -> {
                var level = mc.getSingleplayerServer().overworld();
                for (int x = 0; x <= 4; x++) for (int y = -60; y <= -56; y++)
                    level.setBlockAndUpdate(new BlockPos(x, y, 5), Blocks.STONE.defaultBlockState());
            });
        }
        if (tick == 315) {
            change.get();
            var hit = TacticalMarkerManager.raycast(mc);
            check(hit != null && hit.entity() == null, "Solid wall blocks item raycast");
            change = mc.getSingleplayerServer().submit(() -> {
                var level = mc.getSingleplayerServer().overworld();
                var item = (ItemEntity) level.getEntity(itemId);
                item.setNoPickUpDelay();
                item.playerTouch(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()));
                level.getEntity(zombieId).discard();
            });
        }
        if (tick == 335) {
            change.get();
            check(count(TacticalMarker.Type.ITEM) == 0, "Actual pickup removes marker via client packet");
            check(count(TacticalMarker.Type.ENEMY) == 1, "Enemy removal does not remove its fixed location ping");
            reload = mc.reloadResourcePacks();
        }
        if (tick == 340) {
            reload.join();
            screenshot = "marker-after-reload.png";
        }
        if (tick == 345) {
            long now = Util.getMillis();
            TacticalMarkerManager.MARKERS.replaceAll((key, marker) -> new TacticalMarker(marker.type(), marker.position(), marker.target(), now - 60_000));
            TacticalMarkerManager.maintain(mc, now);
            expectCount(0);
        }
        if (tick >= 350 && tick < 490) {
            int phase = (tick - 350) % 20;
            int index = (tick - 350) / 20;
            if (phase == 0) aim(mc, new Vec3(-6.5 + index * 2, -60, 4.5));
            if (phase == 4) {
                var hit = TacticalMarkerManager.raycast(mc);
                check(hit != null && hit.entity() == null, "FIFO test ray hits a distinct ground surface");
                check(!fifoKeys.contains(hit.locationKey()), "FIFO target is new");
                fifoKeys.add(hit.locationKey());
                click(mc);
            }
            if (phase == 17) {
                expectCount(Math.min(index + 1, 5));
                check(java.util.List.copyOf(TacticalMarkerManager.MARKERS.keySet()).equals(
                        fifoKeys.subList(Math.max(0, fifoKeys.size() - 5), fifoKeys.size())), "FIFO evicts earliest on each overflow");
                if (index == 4 || index == 5 || index == 6) screenshot = "marker-fifo-" + (index + 1) + ".png";
            }
        }
        if (tick == 492) {
            click(mc); click(mc);
            expectCount(5);
            check(java.util.List.copyOf(TacticalMarkerManager.MARKERS.keySet()).equals(fifoKeys.subList(2, 7)), "Refresh at capacity neither reorders nor evicts");
        }
        if (tick == 500) {
            java.nio.file.Files.writeString(Path.of("marker-animation.csv"), animationLog);
            check(recordedTypes.size() == 3, "Recorded appearance of all three types");
            LoggerFactory.getLogger("TacticalMarkerSmoke").info("TACTICAL_MARKER_149_SMOKE_PASS: 450ms clicks, fixed enemy location, FIFO five-marker cap, nonlinear appearance, pickup, expiry, GUI/FOV/reload");
            mc.stop();
        }
    }

    private static TacticalMarker enemy() { return TacticalMarkerManager.MARKERS.values().stream().filter(m -> m.type() == TacticalMarker.Type.ENEMY).findFirst().orElseThrow(); }
    private static ItemStack sampleItem() {
        if (!GwoHudBridge.installed()) return new ItemStack(Items.DIAMOND);
        try {
            var id = net.minecraft.resources.ResourceLocation.parse("gwo:m4a1");
            var registry = Class.forName("com.sgr792.gwo.content.WeaponContentRegistry");
            var definitions = (java.util.Map<?, ?>) registry.getMethod("definitions").invoke(null);
            var entry = definitions.entrySet().stream().filter(e -> e.getKey().toString().contains("m4")).findFirst().orElseThrow();
            id = (net.minecraft.resources.ResourceLocation) entry.getKey();
            var stack = (ItemStack) Class.forName("com.sgr792.gwo.GwoMod").getMethod("weaponStack", net.minecraft.resources.ResourceLocation.class).invoke(null, id);
            var data = Class.forName("com.sgr792.gwo.item.GunData");
            data.getMethod("initialize", ItemStack.class, net.minecraft.resources.ResourceLocation.class, entry.getValue().getClass()).invoke(null, stack, id, entry.getValue());
            data.getMethod("setAmmo", ItemStack.class, int.class).invoke(null, stack, 30);
            return stack;
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
    private static long count(TacticalMarker.Type type) { return TacticalMarkerManager.MARKERS.values().stream().filter(m -> m.type() == type).count(); }
    private static void expectCount(int expected) { check(TacticalMarkerManager.MARKERS.size() == expected, "Expected " + expected + " markers, got " + TacticalMarkerManager.MARKERS); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static Vec3 target(Minecraft mc, int id) { return mc.level.getEntity(id).getBoundingBox().getCenter(); }
    private static void aim(Minecraft mc, Vec3 target) {
        Vec3 direction = target.subtract(mc.player.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()));
        mc.player.setYRot(yaw); mc.player.yRotO = yaw;
        mc.player.setXRot(pitch); mc.player.xRotO = pitch;
    }
    private static void click(Minecraft mc) throws Exception {
        if (mc.screen == null) {
            mc.mouseHandler.grabMouse();
            check(TacticalMarkerManager.canInput(mc) && mc.mouseHandler.isMouseGrabbed(), "Game view accepts marker input");
        }
        var method = mc.mouseHandler.getClass().getDeclaredMethod("onPress", long.class, int.class, int.class, int.class);
        method.setAccessible(true);
        method.invoke(mc.mouseHandler, mc.getWindow().getWindow(), 2, 1, 0);
        method.invoke(mc.mouseHandler, mc.getWindow().getWindow(), 2, 0, 0);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void screenshot(RenderGuiEvent.Post event) throws Exception {
        recordAppearance(event);
        if (screenshot == null) return;
        boolean alphaCheck = screenshot.equals("marker-icon-alpha-check.png");
        var mc = Minecraft.getInstance();
        if (alphaCheck) {
            var graphics = event.getGuiGraphics();
            ItemStack[] samples = { new ItemStack(Items.DIAMOND), new ItemStack(Items.STONE), sampleItem() };
            for (int row = 0; row < samples.length; row++) {
                int y = 30 + row * 30;
                graphics.fill(15, y - 5, 95, y + 22, 0xFF303030);
                TacticalItemIcon.draw(graphics, mc, samples[row], 20, y, 1);
                TacticalItemIcon.draw(graphics, mc, samples[row], 65, y, 0.2f);
            }
        }
        event.getGuiGraphics().flush();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(Path.of(screenshot));
            if (alphaCheck) {
                int scale = (int) mc.getWindow().getGuiScale();
                for (int row = 0; row < 3; row++) {
                    long full = 0, faded = 0;
                    for (int y = 0; y < 16 * scale; y++) for (int x = 0; x < 16 * scale; x++) {
                        int a = image.getPixelRGBA(20 * scale + x, (30 + row * 30) * scale + y);
                        int b = image.getPixelRGBA(65 * scale + x, (30 + row * 30) * scale + y);
                        for (int channel = 0; channel < 3; channel++) {
                            full += Math.abs((a >>> (channel * 8) & 255) - 48);
                            faded += Math.abs((b >>> (channel * 8) & 255) - 48);
                        }
                    }
                    check(full > 1000, "Native icon must contain visible pixels at row " + row);
                    double ratio = faded / (double) full;
                    check(ratio > 0.15 && ratio < 0.25, "Full native icon fades to 20%, row=" + row + " ratio=" + ratio);
                    LoggerFactory.getLogger("TacticalMarkerSmoke").info("MARKER_ICON_ALPHA_PASS row={} ratio={}", row, ratio);
                }
            }
        }
        screenshot = null;
    }

    private static void recordAppearance(RenderGuiEvent.Post event) throws Exception {
        long now = Util.getMillis();
        if (animating == null) {
            for (var marker : TacticalMarkerManager.MARKERS.values()) {
                if (!recordedTypes.contains(marker.type()) && now - marker.createdAt() < 70) {
                    animating = marker;
                    recordedTypes.add(marker.type());
                    lastFrameAge = -100;
                    frameIndex = 0;
                    java.nio.file.Files.createDirectories(Path.of("marker-animation"));
                    break;
                }
            }
        }
        if (animating == null) return;
        long age = now - animating.createdAt();
        if (age > 400) { animating = null; return; }
        if (age - lastFrameAge < 30) return;
        lastFrameAge = age;
        var pose = TacticalMarkerLogic.appearance(age);
        animationLog.append(animating.type()).append(',').append(frameIndex).append(',').append(age).append(',')
                .append(pose.scale()).append(',').append(pose.offsetY()).append(',').append(pose.opacity()).append('\n');
        event.getGuiGraphics().flush();
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(Path.of("marker-animation", animating.type() + "-" + String.format("%03d", frameIndex++) + ".png"));
        }
    }
}
