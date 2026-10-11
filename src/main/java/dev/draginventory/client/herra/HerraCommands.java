package dev.draginventory.client.herra;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * HERRA 统一主指令 /herra：
 * <ul>
 *   <li>无参数：打开 HERRA 主界面（全部模块的统一设置入口）</li>
 *   <li>list：列出已注册模块与启用状态</li>
 *   <li>open &lt;module&gt;：打开某模块的设置界面</li>
 *   <li>toggle &lt;module&gt; [on|off]：开关某模块</li>
 *   <li>modules：仅列 id（供脚本 / 外部模组探测）</li>
 * </ul>
 * 模块列表来自 {@link HerraModuleRegistry}（内置七模块 + 外部经 HerraApi 注册），
 * 本指令零修改即可服务未来新模块。全部客户端指令，无需权限。
 */
public final class HerraCommands {

    private HerraCommands() {}

    /** 由事件类在 RegisterClientCommandsEvent 时调用（见 HerraClientEvents）。 */
    public static LiteralArgumentBuilder<CommandSourceStack> buildRoot() {
        return
                Commands.literal("herra")
                        .executes(ctx -> {
                            openHub();
                            return 1;
                        })
                        .then(Commands.literal("gui").executes(ctx -> {
                            openHub();
                            return 1;
                        }))
                        .then(Commands.literal("list").executes(HerraCommands::listModules))
                        .then(Commands.literal("modules").executes(HerraCommands::listIds))
                        .then(Commands.literal("open")
                                .then(Commands.argument("module", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                HerraModuleRegistry.modules().stream()
                                                        .filter(HerraModule::hasSettings)
                                                        .map(HerraModule::id),
                                                builder))
                                        .executes(ctx -> openModule(ctx,
                                                StringArgumentType.getString(ctx, "module")))))
                        .then(Commands.literal("toggle")
                                .then(Commands.argument("module", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                HerraModuleRegistry.modules().stream()
                                                        .filter(HerraModule::toggleable)
                                                        .map(HerraModule::id),
                                                builder))
                                        .executes(ctx -> toggleModule(ctx,
                                                StringArgumentType.getString(ctx, "module"), null))
                                        .then(Commands.argument("state", BoolArgumentType.bool())
                                                .executes(ctx -> toggleModule(ctx,
                                                        StringArgumentType.getString(ctx, "module"),
                                                        BoolArgumentType.getBool(ctx, "state"))))));
    }

    private static void openHub() {
        Minecraft.getInstance().tell(() ->
                Minecraft.getInstance().setScreen(HerraHubScreen.create()));
    }

    private static int listModules(CommandContext<CommandSourceStack> ctx) {
        List<HerraModule> modules = HerraModuleRegistry.modules();
        if (modules.isEmpty()) {
            ctx.getSource().sendSuccess(
                    () -> Component.translatable("draginventory.herra.cmd.empty"), false);
            return 0;
        }
        ctx.getSource().sendSuccess(
                () -> Component.translatable("draginventory.herra.cmd.header", modules.size()), false);
        for (HerraModule module : modules) {
            String state = module.isEnabled() ? "ON" : "OFF";
            String settings = module.hasSettings() ? " *" : "";
            ctx.getSource().sendSuccess(() -> Component.literal(
                    String.format("  %-12s [%s]%s  %s", module.id(), state, settings,
                            module.title().getString())), false);
        }
        return modules.size();
    }

    private static int listIds(CommandContext<CommandSourceStack> ctx) {
        List<String> ids = HerraModuleRegistry.modules().stream().map(HerraModule::id).toList();
        ctx.getSource().sendSuccess(
                () -> Component.literal(String.join(",", ids)), false);
        return ids.size();
    }

    private static int openModule(CommandContext<CommandSourceStack> ctx, String id) {
        var found = HerraModuleRegistry.byId(id);
        if (found.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable(
                    "draginventory.herra.cmd.unknown_module", Component.literal(id)));
            return 0;
        }
        HerraModule module = found.get();
        if (!module.hasSettings()) {
            ctx.getSource().sendFailure(Component.translatable(
                    "draginventory.herra.cmd.no_settings",
                    Component.literal(module.id())));
            return 0;
        }
        module.openSettings();
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "draginventory.herra.cmd.opened", module.title()), false);
        return 1;
    }

    private static int toggleModule(CommandContext<CommandSourceStack> ctx, String id, Boolean state) {
        var found = HerraModuleRegistry.byId(id);
        if (found.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable(
                    "draginventory.herra.cmd.unknown_module", Component.literal(id)));
            return 0;
        }
        HerraModule module = found.get();
        if (!module.toggleable()) {
            ctx.getSource().sendFailure(Component.translatable(
                    "draginventory.herra.cmd.not_toggleable", module.title()));
            return 0;
        }
        boolean next = state != null ? state : !module.isEnabled();
        module.setEnabled(next);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                next ? "draginventory.herra.cmd.module_on" : "draginventory.herra.cmd.module_off",
                module.title()), false);
        return 1;
    }
}
