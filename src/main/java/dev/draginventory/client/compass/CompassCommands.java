package dev.draginventory.client.compass;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 游戏内指令 /herracompass（别名 /compass）：
 * <ul>
 *   <li>不带参数：打开图形化设置界面</li>
 *   <li>子指令覆盖全部常用配置：开关 / 位置 / 缩放 / 透明度 / 风格 / 配色 / 动画 / 范围 / 标点</li>
 *   <li>test 子指令：创建测试标点，验证方位条与标点系统的联动</li>
 *   <li>reset 恢复默认；reload 由配置文件热重载机制自动完成</li>
 * </ul>
 * 所有指令均为客户端指令（配置与 HUD 均在客户端），无需权限。
 */
public final class CompassCommands {

    private CompassCommands() {}

    /** 由 {@code CompassClientEvents} 在 RegisterClientCommandsEvent 时调用。 */
    public static List<LiteralArgumentBuilder<CommandSourceStack>> buildRoots() {
        List<LiteralArgumentBuilder<CommandSourceStack>> roots = new ArrayList<>(2);
        roots.add(build("herracompass"));
        roots.add(build("compass"));
        return roots;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        return Commands.literal(name)
                .executes(ctx -> {
                    openSettings();
                    return 1;
                })
                .then(Commands.literal("toggle").executes(ctx -> {
                    boolean enabled = !CompassConfig.ENABLED.get();
                    CompassConfig.set(CompassConfig.ENABLED, enabled);
                    feedback(ctx, enabled ? "draginventory.compass.cmd.enabled" : "draginventory.compass.cmd.disabled");
                    return 1;
                }))
                .then(Commands.literal("on").executes(ctx -> setBool(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> setBool(ctx, false)))
                .then(Commands.literal("gui").executes(ctx -> {
                    openSettings();
                    return 1;
                }))
                .then(Commands.literal("style")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        CompassStyle.all().stream().map(CompassStyle::id), builder))
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    CompassStyle style = CompassStyle.byId(id);
                                    CompassConfig.set(CompassConfig.STYLE, style.id());
                                    feedback(ctx, "draginventory.compass.cmd.style", Component.literal(style.id()));
                                    return 1;
                                })))
                .then(Commands.literal("palette")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        CompassPalette.all().stream().map(CompassPalette::id), builder))
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    CompassPalette palette = CompassPalette.byId(id);
                                    CompassConfig.set(CompassConfig.PALETTE, palette.id());
                                    feedback(ctx, "draginventory.compass.cmd.palette", Component.literal(palette.id()));
                                    return 1;
                                })))
                .then(Commands.literal("offset")
                        .then(Commands.argument("x", IntegerArgumentType.integer(-640, 640))
                                .then(Commands.argument("y", IntegerArgumentType.integer(-640, 640))
                                        .executes(ctx -> {
                                            int x = IntegerArgumentType.getInteger(ctx, "x");
                                            int y = IntegerArgumentType.getInteger(ctx, "y");
                                            CompassConfig.set(CompassConfig.OFFSET_X, x);
                                            CompassConfig.set(CompassConfig.OFFSET_Y, y);
                                            feedback(ctx, "draginventory.compass.cmd.offset",
                                                    Component.literal(x + ", " + y));
                                            return 1;
                                        }))))
                .then(Commands.literal("scale")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.5, 2.0))
                                .executes(ctx -> {
                                    double v = DoubleArgumentType.getDouble(ctx, "value");
                                    CompassConfig.set(CompassConfig.SCALE, v);
                                    feedback(ctx, "draginventory.compass.cmd.scale", Component.literal(fmt(v)));
                                    return 1;
                                })))
                .then(Commands.literal("opacity")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.15, 1.0))
                                .executes(ctx -> {
                                    double v = DoubleArgumentType.getDouble(ctx, "value");
                                    CompassConfig.set(CompassConfig.OPACITY, v);
                                    feedback(ctx, "draginventory.compass.cmd.opacity", Component.literal(fmt(v)));
                                    return 1;
                                })))
                .then(Commands.literal("width")
                        .then(Commands.argument("px", IntegerArgumentType.integer(120, 520))
                                .executes(ctx -> {
                                    int v = IntegerArgumentType.getInteger(ctx, "px");
                                    CompassConfig.set(CompassConfig.BAR_WIDTH, v);
                                    feedback(ctx, "draginventory.compass.cmd.width", Component.literal(String.valueOf(v)));
                                    return 1;
                                })))
                .then(Commands.literal("range")
                        .then(Commands.argument("degrees", IntegerArgumentType.integer(60, 360))
                                .executes(ctx -> {
                                    int v = IntegerArgumentType.getInteger(ctx, "degrees");
                                    CompassConfig.set(CompassConfig.RANGE, v);
                                    feedback(ctx, "draginventory.compass.cmd.range", Component.literal(v + "\u00B0"));
                                    return 1;
                                })))
                .then(Commands.literal("smooth")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(
                                CompassHeading.OMEGA_MIN, CompassHeading.OMEGA_MAX))
                                .executes(ctx -> {
                                    double v = DoubleArgumentType.getDouble(ctx, "value");
                                    CompassConfig.set(CompassConfig.SMOOTHNESS, v);
                                    feedback(ctx, "draginventory.compass.cmd.smooth", Component.literal(fmt(v)));
                                    return 1;
                                })))
                .then(Commands.literal("markers")
                        .then(Commands.argument("on", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean v = BoolArgumentType.getBool(ctx, "on");
                                    CompassConfig.set(CompassConfig.MARKERS_ENABLED, v);
                                    feedback(ctx, v ? "draginventory.compass.cmd.markers_on"
                                            : "draginventory.compass.cmd.markers_off");
                                    return 1;
                                })))
                .then(Commands.literal("test")
                        .then(Commands.literal("enemy").executes(ctx -> addTestMark(ctx, CompassMark.Kind.ENEMY, 15)))
                        .then(Commands.literal("location").executes(ctx -> addTestMark(ctx, CompassMark.Kind.LOCATION, 0)))
                        .then(Commands.literal("item").executes(ctx -> addTestMark(ctx, CompassMark.Kind.ITEM, -15)))
                        .then(Commands.literal("clear").executes(ctx -> {
                            CompassHub.clearTestMarks();
                            feedback(ctx, "draginventory.compass.cmd.test_cleared");
                            return 1;
                        })))
                .then(Commands.literal("reset").executes(ctx -> {
                    CompassConfig.resetToDefaults();
                    feedback(ctx, "draginventory.compass.cmd.reset");
                    return 1;
                }))
                .then(Commands.literal("info").executes(ctx -> {
                    Player player = Minecraft.getInstance().player;
                    float heading = CompassApi.getSmoothHeading();
                    Component state = Component.literal(
                            (CompassConfig.ENABLED.get() ? "ON" : "OFF")
                                    + " | " + CompassConfig.STYLE.get() + "/" + CompassConfig.PALETTE.get()
                                    + " | " + (heading < 0 ? "-" : Math.round(heading) + "\u00B0")
                                    + " | " + (player == null ? "-" : CompassWidget.cardinalName(
                                            (Math.round(heading) % 360 + 360) % 360 / 90 * 90)));
                    feedback(ctx, "draginventory.compass.cmd.info", state);
                    return 1;
                }));
    }

    private static int setBool(CommandContext<CommandSourceStack> ctx, boolean value) {
        CompassConfig.set(CompassConfig.ENABLED, value);
        feedback(ctx, value ? "draginventory.compass.cmd.enabled" : "draginventory.compass.cmd.disabled");
        return 1;
    }

    private static void openSettings() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(CompassSettingsScreen.create()));
    }

    /** 在玩家当前朝向前方随机 48~144 米、偏转 bearingOffset 度处生成测试标点（指令与设置界面共用）。 */
    public static int spawnTestMark(CompassMark.Kind kind, int bearingOffset) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        double distance = 48 + player.getRandom().nextInt(96);
        double bearing = Math.toRadians(CompassHeading.toHeading(player.getYRot()) + bearingOffset);
        Vec3 position = player.position().add(Math.sin(bearing) * distance, 0, -Math.cos(bearing) * distance);
        CompassPalette palette = CompassWidget.currentPalette();
        int color = switch (kind) {
            case ENEMY -> palette.markerEnemy();
            case ITEM -> palette.markerItem();
            default -> palette.markerLocation();
        };
        String key = "test-" + kind.name().toLowerCase(java.util.Locale.ROOT);
        CompassHub.addTestMark(CompassMark.of(key, kind, position, color,
                Component.translatable("draginventory.compass.marker." + kind.name().toLowerCase(java.util.Locale.ROOT)),
                true));
        return 1;
    }

    private static int addTestMark(CommandContext<CommandSourceStack> ctx, CompassMark.Kind kind, int bearingOffset) {
        if (Minecraft.getInstance().player == null) {
            ctx.getSource().sendFailure(Component.translatable("draginventory.compass.cmd.no_player"));
            return 0;
        }
        int result = spawnTestMark(kind, bearingOffset);
        feedback(ctx, "draginventory.compass.cmd.test_added", Component.literal(""));
        return result;
    }

    private static void feedback(CommandContext<CommandSourceStack> ctx, String key, Component... args) {
        ctx.getSource().sendSuccess(() -> Component.translatable(key, (Object[]) args), false);
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
