package dev.draginventory.client.compass;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.draginventory.client.CompassMarkerBridge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 方位条控件：LDLib2 UIElement 子类，负责
 * <ul>
 *   <li>读取玩家 yaw 并驱动 {@link CompassHeading} 弹簧（帧率无关）</li>
 *   <li>聚合标点（战术标点只读桥接 / 测试标点 / 外部提供者）</li>
 *   <li>计算布局、边缘渐隐、惯性倾斜、入场动画与吸附高亮</li>
 *   <li>把每个元素分发给当前 {@link CompassStyle} 皮肤绘制</li>
 * </ul>
 * 同一个类同时用于 HUD 与设置界面实时预览（预览模式不读玩家状态）。
 */
public final class CompassWidget extends UIElement {
    /** 预览模式的演示标点相对方位（度）。 */
    private static final float[] PREVIEW_BEARINGS = {38f, -22f, 84f};

    /** HUD 实例共享 CompassHub.LIVE（供 API 查询）；预览实例使用独立弹簧。 */
    private final CompassHeading heading;
    private final boolean preview;

    /** 上次可见状态：false -> true 时触发入场动画。 */
    private boolean wasVisible;
    private long entryStart = Long.MIN_VALUE;
    private long lastFrameMillis = Long.MIN_VALUE;
    private Player displayedPlayer;

    /** 预览模式状态。 */
    private float previewTarget = 206f;
    private float previewBase = 206f;
    private boolean sway = true;

    /** 预览自动摆动（设置界面开关）。 */
    public void setSway(boolean sway) {
        this.sway = sway;
    }

    /** 本帧缓存（drawBackgroundTexture 与 drawBackgroundAdditional 共享）。 */
    private CompassStyleContext frame;
    private boolean frameVisible;

    public CompassWidget(boolean preview) {
        this.preview = preview;
        this.heading = preview ? CompassHeading.create() : CompassHub.LIVE;
    }

    public void open() {
        // 布局变化（宽度/位置调整）时调用：下一帧重新播放入场动画。
        wasVisible = false;
    }

    // ==================== 预览控制（设置界面） ====================

    /** 拖拽预览：dx 像素映射为度数。 */
    public void dragPreview(float deltaX) {
        previewTarget = Mth.wrapDegrees(previewTarget + deltaX * 0.55f);
        if (previewTarget < 0) previewTarget += 360f;
    }

    /** 预览自动摆动。 */
    public void swayPreview(long now) {
        previewTarget = Mth.wrapDegrees(previewBase + 34f * (float) Math.sin(now / 3400.0));
        if (previewTarget < 0) previewTarget += 360f;
    }

    public void setPreviewHeading(float degrees) {
        previewTarget = Mth.wrapDegrees(degrees);
        if (previewTarget < 0) previewTarget += 360f;
        previewBase = previewTarget;
        heading.snapTo(previewTarget);
    }

    // ==================== 状态更新 ====================

    private void updateState(GUIContext context) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        float seconds = secondsSinceLastFrame(mc, now);

        boolean visible = isVisible(mc);
        if (visible && !wasVisible) {
            entryStart = CompassConfig.ENTRY_ANIMATION.get() ? now : Long.MIN_VALUE;
            if (!preview) {
                CompassHub.resetCardinalState();
            }
        }
        wasVisible = visible;
        frameVisible = visible;
        if (!visible) {
            lastFrameMillis = now;
            frame = null;
            return;
        }

        // 目标航向：预览用内部值（可自动摆动），实机用玩家视角（带插值）。
        float target;
        if (preview) {
            if (sway) {
                swayPreview(now);
            }
            target = previewTarget;
        } else {
            Player player = mc.player;
            if (player == null) {
                frame = null;
                return;
            }
            if (displayedPlayer != player) {
                displayedPlayer = player;
                heading.snapTo(CompassHeading.toHeading(player.getYRot()));
            }
            target = CompassHeading.toHeading(player.getViewYRot(context.partialTick));
        }

        float snapRange = CompassConfig.SNAP_ASSIST.get()
                ? CompassConfig.SNAP_RANGE.get().floatValue()
                : 0f;
        heading.step(target, seconds, (float) CompassConfig.SMOOTHNESS.get().doubleValue(), snapRange);

        if (!preview && heading.cardinalGlow() > 0.5f) {
            CompassHub.fireCardinalIfChanged(heading.nearestCardinal());
        }

        float entryProgress = 1f;
        float entryAlpha = 1f;
        if (entryStart != Long.MIN_VALUE) {
            float p = Mth.clamp((now - entryStart) / 450f, 0f, 1f);
            entryProgress = p;
            entryAlpha = 1f - (1f - p) * (1f - p); // ease-out
            if (p >= 1f) entryStart = Long.MIN_VALUE;
        }

        CompassPalette palette = currentPalette();
        CompassStyle style = currentStyle();
        float alpha = (float) CompassConfig.OPACITY.get().doubleValue() * entryAlpha;

        float width = getSizeWidth();
        float height = Mth.clamp(style.widgetHeight(), 40f, 72f);
        frame = new CompassStyleContext(getPositionX(), getPositionY(), width, height,
                heading.smooth(), heading.velocityDegPerSec(), heading.cardinalGlow(),
                heading.nearestCardinal(), alpha, entryProgress, now, palette, style);
    }

    private float secondsSinceLastFrame(Minecraft mc, long now) {
        if (lastFrameMillis == Long.MIN_VALUE) {
            lastFrameMillis = now;
            return 0.016f;
        }
        float seconds = (now - lastFrameMillis) / 1000f;
        lastFrameMillis = now;
        return Mth.clamp(seconds, 0f, 0.1f);
    }

    private boolean isVisible(Minecraft mc) {
        if (preview) return true;
        if (!CompassConfig.ENABLED.get()) return false;
        if (mc.options.hideGui || mc.level == null) return false;
        if (!(mc.getCameraEntity() instanceof Player player) || player.isSpectator()) return false;
        if (CompassConfig.HIDE_WITH_DEBUG.get() && isDebugScreenOpen(mc)) return false;
        return true;
    }

    private static boolean isDebugScreenOpen(Minecraft mc) {
        try {
            return mc.gui.getDebugOverlay().showDebugScreen();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static CompassPalette currentPalette() {
        CompassPalette palette = CompassPalette.byId(CompassConfig.PALETTE.get());
        return new CompassPalette(palette.id(),
                CompassPalette.resolve(CompassConfig.COLOR_ACCENT.get(), palette.accent()),
                CompassPalette.resolve(CompassConfig.COLOR_TEXT.get(), palette.text()),
                CompassPalette.resolve(CompassConfig.COLOR_DIM.get(), palette.dim()),
                CompassPalette.resolve(CompassConfig.COLOR_TICK.get(), palette.tick()),
                CompassPalette.resolve(CompassConfig.COLOR_BACKGROUND.get(), palette.background()),
                palette.markerEnemy(), palette.markerLocation(), palette.markerItem());
    }

    public static CompassStyle currentStyle() {
        return CompassStyle.byId(CompassConfig.STYLE.get());
    }

    // ==================== 绘制 ====================

    @Override
    public void drawBackgroundTexture(GUIContext context) {
        updateState(context);
        if (frame == null || !frameVisible || frame.alpha <= 0.01f) return;
        GuiGraphics g = context.graphics;
        g.pose().pushPose();
        try {
            applyScale(g);
            currentStyle().drawBackground(frame, g);
        } finally {
            g.pose().popPose();
        }
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        if (frame == null) return;
        GuiGraphics g = context.graphics;
        Font font = com.lowdragmc.lowdraglib2.gui.LDLibFonts.font();
        g.pose().pushPose();
        try {
            applyScale(g);
            drawTicksAndLabels(g, font);
            drawMarkers(g, font);
            currentStyle().drawCenter(frame, font, g);
            currentStyle().drawForeground(frame, g);
        } finally {
            g.pose().popPose();
        }
    }

    /** 以控件水平中心为锚点应用整体缩放。 */
    private void applyScale(GuiGraphics g) {
        float scale = (float) CompassConfig.SCALE.get().doubleValue();
        if (Math.abs(scale - 1f) < 0.001f) return;
        float cx = frame != null ? frame.centerX : getPositionX() + getSizeWidth() / 2f;
        g.pose().translate(cx, 0, 0);
        g.pose().scale(scale, scale, 1);
        g.pose().translate(-cx, 0, 0);
    }

    private void drawTicksAndLabels(GuiGraphics g, Font font) {
        CompassStyle style = currentStyle();
        int minorStep = Math.max(5, CompassConfig.MINOR_STEP.get());
        int numberStep = Math.max(minorStep, CompassConfig.NUMBER_STEP.get());
        float half = frame.range() / 2f;
        int first = (int) Math.floor((frame.heading - half) / minorStep) * minorStep;

        for (int deg = first; deg <= frame.heading + half + minorStep; deg += minorStep) {
            int wrapped = Math.floorMod(deg, 360);
            float x = frame.degreesToX(deg);
            if (x < frame.originX - 8f || x > frame.originX + frame.width + 8f) continue;
            float alpha = frame.alpha * frame.edgeFade(x) * stagger(x);

            boolean cardinal = wrapped % 90 == 0;
            boolean inter = wrapped % 45 == 0;
            boolean numbered = wrapped % numberStep == 0;

            if (cardinal) {
                if (CompassConfig.SHOW_CARDINALS.get()) {
                    boolean nearest = wrapped == frame.nearestCardinal;
                    style.drawCardinal(frame, font, g, x, cardinalName(wrapped), nearest, alpha);
                }
                style.drawTick(frame, g, x, CompassStyle.TickKind.CARDINAL, alpha);
            } else if (inter) {
                if (CompassConfig.SHOW_INTERCARDINALS.get()) {
                    style.drawIntercardinal(frame, font, g, x, intercardinalName(wrapped), alpha);
                }
                style.drawTick(frame, g, x, CompassStyle.TickKind.MAJOR, alpha);
            } else if (numbered) {
                if (CompassConfig.SHOW_NUMBERS.get()) {
                    style.drawNumber(frame, font, g, x, wrapped, alpha);
                }
                style.drawTick(frame, g, x, CompassStyle.TickKind.MAJOR, alpha);
            } else {
                style.drawTick(frame, g, x, CompassStyle.TickKind.MINOR, alpha * 0.9f);
            }
        }
    }

    /** 入场动画的错峰系数：中央元素先出现。 */
    private float stagger(float x) {
        if (frame.entryProgress >= 1f) return 1f;
        float t = Math.abs(x - frame.centerX) / (frame.width / 2f);
        return Mth.clamp(frame.entryProgress * 1.7f - t * 0.7f, 0f, 1f);
    }

    private void drawMarkers(GuiGraphics g, Font font) {
        if (!CompassConfig.MARKERS_ENABLED.get()) return;
        List<CompassMark> marks = preview ? previewMarks() : collectLiveMarks();
        if (marks.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 eye = preview ? Vec3.ZERO : mc.player.getEyePosition();
        for (CompassMark mark : marks) {
            float bearing = preview ? previewBearingOf(mark) : bearingBetween(eye, mark.position());
            float x = frame.degreesToX(bearing);
            if (x < frame.originX - 6f || x > frame.originX + frame.width + 6f) continue;
            float alpha = frame.alpha * frame.edgeFade(x);
            String dist = null;
            if (mark.showDistance() && CompassConfig.MARKERS_DISTANCE.get()) {
                if (preview) {
                    dist = previewDistanceOf(mark);
                } else {
                    int meters = (int) Math.floor(eye.distanceTo(mark.position()));
                    dist = meters + "m";
                }
            }
            currentStyle().drawMarker(frame, font, g, mark, x, alpha, dist);
        }
    }

    private List<CompassMark> collectLiveMarks() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.player instanceof LocalPlayer player) || mc.level == null) return List.of();
        List<CompassMark> result = null;
        // 1) 战术标点（只读桥接）
        if (CompassConfig.MARKERS_TACTICAL.get()) {
            List<CompassMark> tactical = CompassMarkerBridge.collect(frame.palette, frame.now);
            if (!tactical.isEmpty()) {
                result = new ArrayList<>(tactical);
            }
        }
        // 2) 测试标点
        if (CompassConfig.MARKERS_TEST.get() && !CompassHub.testMarks().isEmpty()) {
            if (result == null) result = new ArrayList<>();
            result.addAll(CompassHub.testMarks());
        }
        // 3) 外部提供者
        var providers = CompassHub.providers();
        if (!providers.isEmpty()) {
            if (result == null) result = new ArrayList<>();
            var context = new CompassMarkerProvider.Context(mc.level, player, frame.now);
            for (CompassMarkerProvider provider : providers) {
                try {
                    provider.collectMarkers(context, result::add);
                } catch (RuntimeException ignored) {
                }
            }
        }
        return result != null ? result : List.of();
    }

    // ==================== 预览演示数据 ====================

    private List<CompassMark> previewMarks;
    private String previewPaletteId = "";

    private List<CompassMark> previewMarks() {
        if (previewMarks == null || !previewPaletteId.equals(CompassConfig.PALETTE.get())) {
            previewPaletteId = CompassConfig.PALETTE.get();
            previewMarks = List.of(
                    CompassMark.of("preview-enemy", CompassMark.Kind.ENEMY, Vec3.ZERO,
                            currentPalette().markerEnemy(), null, true),
                    CompassMark.of("preview-location", CompassMark.Kind.LOCATION, Vec3.ZERO,
                            currentPalette().markerLocation(), null, true),
                    CompassMark.of("preview-item", CompassMark.Kind.ITEM, Vec3.ZERO,
                            currentPalette().markerItem(), null, true));
        }
        return previewMarks;
    }

    private float previewBearingOf(CompassMark mark) {
        int index = switch (mark.key().toString()) {
            case "preview-enemy" -> 0;
            case "preview-location" -> 1;
            default -> 2;
        };
        return Mth.wrapDegrees(previewBase + PREVIEW_BEARINGS[index]);
    }

    private String previewDistanceOf(CompassMark mark) {
        return switch (mark.key().toString()) {
            case "preview-enemy" -> "37m";
            case "preview-location" -> "124m";
            default -> "58m";
        };
    }

    // ==================== 文本 ====================

    static String cardinalName(int degrees) {
        boolean cjk = CompassConfig.CJK_LABELS.get();
        return switch (degrees) {
            case 0 -> cjk ? "北" : "N";
            case 90 -> cjk ? "东" : "E";
            case 180 -> cjk ? "南" : "S";
            case 270 -> cjk ? "西" : "W";
            default -> String.valueOf(degrees);
        };
    }

    static String intercardinalName(int degrees) {
        boolean cjk = CompassConfig.CJK_LABELS.get();
        return switch (degrees) {
            case 45 -> cjk ? "东北" : "NE";
            case 135 -> cjk ? "东南" : "SE";
            case 225 -> cjk ? "西南" : "SW";
            case 315 -> cjk ? "西北" : "NW";
            default -> String.valueOf(degrees);
        };
    }

    /** 玩家眼睛位置 -> 目标位置的罗盘方位（0 = 北，顺时针）。 */
    static float bearingBetween(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        float bearing = (float) Math.toDegrees(Math.atan2(dx, -dz));
        return bearing < 0 ? bearing + 360f : bearing;
    }
}
