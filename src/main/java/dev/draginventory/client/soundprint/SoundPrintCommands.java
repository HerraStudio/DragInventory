package dev.draginventory.client.soundprint;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

/**
 * Soundprint client commands. Two equivalent roots:
 *   /soundprint  - primary
 *   /herrasoundprint - HERRA ecosystem alias
 * The short /sp alias was removed in v2.9.0 (clashed with other mods' commands
 * and carried no subcommands in v2.8.0, which is why "/sp sim on" reported
 * "unknown command").
 */
public final class SoundPrintCommands {
   private static final List<String> POSITION_PRESETS = List.of("below_compass", "top_center", "center", "bottom_center");
   private static final List<String> COLOR_PRESETS = List.of("default", "white", "red", "gold", "cyan", "lime");

   private SoundPrintCommands() {
   }

   public static List<LiteralArgumentBuilder<CommandSourceStack>> buildRoots() {
      return List.of(build("soundprint"), build("herrasoundprint"));
   }

   private static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
      LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name).executes(ctx -> {
         openSettings();
         return 1;
      });
      root.then(Commands.literal("gui").executes(ctx -> {
         openSettings();
         return 1;
      }));
      root.then(Commands.literal("toggle").executes(ctx -> setEnabled(ctx, !(Boolean)SoundPrintConfig.ENABLED.get())));
      root.then(Commands.literal("on").executes(ctx -> setEnabled(ctx, true)));
      root.then(Commands.literal("off").executes(ctx -> setEnabled(ctx, false)));
      root.then(Commands.literal("reset").executes(ctx -> {
         SoundPrintConfig.resetToDefaults();
         feedback(ctx, "draginventory.soundprint.cmd.reset");
         return 1;
      }));
      root.then(Commands.literal("status").executes(SoundPrintCommands::dumpStatus));
      // detection
      root.then(intOpt("radius", SoundPrintConfig.RADIUS, 4, 64, "m"));
      root.then(boolOpt("footsteps", SoundPrintConfig.FOOTSTEPS));
      root.then(boolOpt("gunshots", SoundPrintConfig.GUNSHOTS));
      root.then(boolOpt("ignore_own", SoundPrintConfig.IGNORE_OWN));
      root.then(boolOpt("own_gunshots", SoundPrintConfig.OWN_GUNSHOTS));
      root.then(boolOpt("motion_persist", SoundPrintConfig.MOTION_PERSIST));
      root.then(keywordsOpt("footstep_keywords", SoundPrintConfig.FOOTSTEP_KEYWORDS));
      root.then(keywordsOpt("gunshot_keywords", SoundPrintConfig.GUNSHOT_KEYWORDS));
      // multi-source
      root.then(modeOpt());
      root.then(intOpt("merge_window", SoundPrintConfig.MERGE_WINDOW, 2, 20, "\u00b0"));
      root.then(intOpt("stagger_spacing", SoundPrintConfig.STAGGER_SPACING, 1, 6, "px"));
      // shape & colour
      root.then(intOpt("bar_count", SoundPrintConfig.BAR_COUNT, 5, 21, ""));
      root.then(intOpt("bar_width", SoundPrintConfig.BAR_WIDTH, 1, 4, "px"));
      root.then(intOpt("bar_gap", SoundPrintConfig.BAR_GAP, 0, 3, "px"));
      root.then(intOpt("max_height", SoundPrintConfig.MAX_HEIGHT, 8, 56, "px"));
      root.then(boolOpt("baseline", SoundPrintConfig.BASELINE));
      root.then(boolOpt("arrows", SoundPrintConfig.ARROWS));
      root.then(boolOpt("vertical_offset", SoundPrintConfig.VERTICAL_OFFSET));
      root.then(colorOpt("color_footstep", SoundPrintConfig.COLOR_FOOTSTEP));
      root.then(colorOpt("color_gunshot", SoundPrintConfig.COLOR_GUNSHOT));
      // style
      root.then(percentOpt("opacity", SoundPrintConfig.OPACITY, 15, 100));
      root.then(percentOpt("scale", SoundPrintConfig.SCALE, 50, 200));
      root.then(boolOpt("follow_compass", SoundPrintConfig.FOLLOW_COMPASS));
      // animation
      root.then(boolOpt("distance_decay", SoundPrintConfig.DISTANCE_DECAY));
      root.then(percentOpt("min_decay", SoundPrintConfig.MIN_DECAY, 0, 60));
      root.then(doubleOpt("attack", SoundPrintConfig.ATTACK, 4.0, 40.0, ""));
      root.then(doubleOpt("release", SoundPrintConfig.RELEASE, 1.0, 20.0, ""));
      root.then(intOpt("linger", SoundPrintConfig.LINGER_MS, 100, 1000, "ms"));
      root.then(boolOpt("oscillate", SoundPrintConfig.OSCILLATE));
      root.then(doubleOpt("osc_speed", SoundPrintConfig.OSC_SPEED, 0.5, 8.0, "Hz"));
      root.then(intOpt("osc_amp_min", SoundPrintConfig.OSC_AMP_MIN, 0, 8, "px"));
      root.then(intOpt("osc_amp_max", SoundPrintConfig.OSC_AMP_MAX, 0, 24, "px"));
      root.then(percentOpt("stack_amp_gain", SoundPrintConfig.STACK_AMP_GAIN, 0, 100));
      root.then(percentOpt("stack_alpha_gain", SoundPrintConfig.STACK_ALPHA_GAIN, 0, 100));
      // position (all nested under /soundprint position ...)
      root.then(positionOpt());
      // sim & debug
      root.then(buildSim());
      root.then(captureLogOpt());
      return root;
   }

   private static LiteralArgumentBuilder<CommandSourceStack> positionOpt() {
      return Commands.literal("position")
         .then(
            Commands.literal("preset").then(
               Commands.argument("id", StringArgumentType.word())
                  .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(POSITION_PRESETS, builder))
                  .executes(ctx -> {
                     String id = StringArgumentType.getString(ctx, "id");
                     if (!SoundPrintConfig.isPositionPreset(id)) {
                        ctx.getSource().sendFailure(
                           Component.translatable("draginventory.soundprint.cmd.unknown_preset", Component.literal(id), Component.literal(String.join(" | ", POSITION_PRESETS)))
                        );
                        return 0;
                     } else {
                        SoundPrintConfig.set(SoundPrintConfig.POSITION_PRESET, id);
                        feedbackSet(ctx, "position.preset", id);
                        return 1;
                     }
                  })
            )
         )
         .then(intOpt("width", "position.width_px", SoundPrintConfig.POSITION_WIDTH, 60, 480, "px"))
         .then(intOpt("offsetx", "position.offset_x_px", SoundPrintConfig.OFFSET_X, -240, 240, "px"))
         .then(intOpt("offsety", "position.offset_y_px", SoundPrintConfig.OFFSET_Y, -240, 240, "px"))
         .then(intOpt("spacing", "position.spacing", SoundPrintConfig.SPACING, 0, 20, "px"));
   }

   private static LiteralArgumentBuilder<CommandSourceStack> boolOpt(String name, BooleanValue cfg) {
      return Commands.literal(name)
         .then(Commands.literal("on").executes(ctx -> applyBool(ctx, name, cfg, true)))
         .then(Commands.literal("off").executes(ctx -> applyBool(ctx, name, cfg, false)))
         .then(Commands.literal("toggle").executes(ctx -> applyBool(ctx, name, cfg, !cfg.get())));
   }

   private static int applyBool(CommandContext<CommandSourceStack> ctx, String name, BooleanValue cfg, boolean value) {
      SoundPrintConfig.set(cfg, value);
      feedbackSet(ctx, name, value ? "on" : "off");
      return 1;
   }

   private static LiteralArgumentBuilder<CommandSourceStack> intOpt(String name, IntValue cfg, int min, int max, String unit) {
      return intOpt(name, name, cfg, min, max, unit);
   }

   private static LiteralArgumentBuilder<CommandSourceStack> intOpt(String name, String label, IntValue cfg, int min, int max, String unit) {
      return Commands.literal(name).then(
         Commands.argument("value", IntegerArgumentType.integer(min, max)).executes(ctx -> {
            int v = IntegerArgumentType.getInteger(ctx, "value");
            SoundPrintConfig.set(cfg, v);
            feedbackSet(ctx, label, v + unit);
            return 1;
         })
      );
   }

   private static LiteralArgumentBuilder<CommandSourceStack> doubleOpt(String name, DoubleValue cfg, double min, double max, String unit) {
      return Commands.literal(name).then(
         Commands.argument("value", DoubleArgumentType.doubleArg(min, max)).executes(ctx -> {
            double v = DoubleArgumentType.getDouble(ctx, "value");
            SoundPrintConfig.set(cfg, Math.round(v * 100.0) / 100.0);
            feedbackSet(ctx, name, String.format(Locale.ROOT, "%.2f%s", v, unit));
            return 1;
         })
      );
   }

   private static LiteralArgumentBuilder<CommandSourceStack> percentOpt(String name, DoubleValue cfg, int minPercent, int maxPercent) {
      return Commands.literal(name).then(
         Commands.argument("percent", IntegerArgumentType.integer(minPercent, maxPercent)).executes(ctx -> {
            int p = IntegerArgumentType.getInteger(ctx, "percent");
            SoundPrintConfig.set(cfg, Math.round(p / 100.0 * 10000.0) / 10000.0);
            feedbackSet(ctx, name, p + "%");
            return 1;
         })
      );
   }

   private static LiteralArgumentBuilder<CommandSourceStack> modeOpt() {
      return Commands.literal("mode").then(
         Commands.argument("id", StringArgumentType.word())
            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("merge", "stagger"), builder))
            .executes(ctx -> {
               String id = StringArgumentType.getString(ctx, "id");
               if (!"merge".equals(id) && !"stagger".equals(id)) {
                  ctx.getSource().sendFailure(Component.translatable("draginventory.soundprint.cmd.unknown_mode", Component.literal(id)));
                  return 0;
               } else {
                  SoundPrintConfig.set(SoundPrintConfig.MODE, id);
                  feedback(ctx, "draginventory.soundprint.cmd.mode", Component.literal(id));
                  return 1;
               }
            })
      );
   }

   private static LiteralArgumentBuilder<CommandSourceStack> keywordsOpt(String name, ConfigValue<String> cfg) {
      return Commands.literal(name).then(
         Commands.argument("csv", StringArgumentType.string()).executes(ctx -> {
            String csv = StringArgumentType.getString(ctx, "csv");
            SoundPrintConfig.set(cfg, csv);
            feedbackSet(ctx, name, csv);
            return 1;
         })
      );
   }

   private static LiteralArgumentBuilder<CommandSourceStack> colorOpt(String name, IntValue cfg) {
      return Commands.literal(name).then(
         Commands.argument("color", StringArgumentType.word())
            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(COLOR_PRESETS, builder))
            .executes(ctx -> {
               String word = StringArgumentType.getString(ctx, "color").toLowerCase(Locale.ROOT);
               int rgb = switch (word) {
                  case "default" -> -1;
                  case "white" -> 15921906;
                  case "red" -> 16732754;
                  case "gold" -> 15844367;
                  case "cyan" -> 6220500;
                  case "lime" -> 10685430;
                  default -> parseHex(word, Integer.MIN_VALUE);
               };
               if (rgb == Integer.MIN_VALUE) {
                  ctx.getSource().sendFailure(
                     Component.translatable("draginventory.soundprint.cmd.unknown_color", Component.literal(word), Component.literal("default | white | red | gold | cyan | lime | 0xRRGGBB"))
                  );
                  return 0;
               } else {
                  SoundPrintConfig.set(cfg, rgb);
                  feedbackSet(ctx, name, rgb < 0 ? "default" : String.format(Locale.ROOT, "0x%06X", rgb));
                  return 1;
               }
            })
      );
   }

   private static int parseHex(String word, int fallback) {
      try {
         String h = word.startsWith("0x") || word.startsWith("0X") ? word.substring(2) : word;
         if (h.length() != 6) {
            return fallback;
         } else {
            return Integer.parseInt(h, 16);
         }
      } catch (RuntimeException var3) {
         return fallback;
      }
   }

   private static LiteralArgumentBuilder<CommandSourceStack> captureLogOpt() {
      return Commands.literal("debug")
         .then(
            Commands.literal("capturelog")
               .then(Commands.literal("on").executes(ctx -> applyCaptureLog(ctx, true)))
               .then(Commands.literal("off").executes(ctx -> applyCaptureLog(ctx, false)))
               .then(Commands.literal("toggle").executes(ctx -> applyCaptureLog(ctx, !SoundPrintEvents.captureLog())))
               .executes(ctx -> {
                  applyCaptureLog(ctx, SoundPrintEvents.captureLog());
                  return 1;
               })
         );
   }

   private static int applyCaptureLog(CommandContext<CommandSourceStack> ctx, boolean value) {
      SoundPrintEvents.setCaptureLog(value);
      feedbackSet(ctx, "debug.capturelog", value ? "on" : "off");
      if (value) {
         ctx.getSource().sendSuccess(() -> Component.translatable("draginventory.soundprint.cmd.capturelog.hint"), false);
      }

      return 1;
   }

   private static LiteralArgumentBuilder<CommandSourceStack> buildSim() {
      return Commands.literal("sim")
         .executes(ctx -> {
            simStatus(ctx);
            return 1;
         })
         .then(Commands.literal("on").executes(ctx -> {
            SoundPrintEvents.SIMULATOR.setEnabled(true);
            simStatus(ctx);
            return 1;
         }))
         .then(Commands.literal("off").executes(ctx -> {
            SoundPrintEvents.SIMULATOR.setEnabled(false);
            simStatus(ctx);
            return 1;
         }))
         .then(Commands.literal("toggle").executes(ctx -> {
            SoundPrintEvents.SIMULATOR.setEnabled(!SoundPrintEvents.SIMULATOR.isEnabled());
            simStatus(ctx);
            return 1;
         }))
         .then(
            Commands.literal("type").then(
               Commands.argument("id", StringArgumentType.word())
                  .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("footstep", "gunshot"), builder))
                  .executes(ctx -> {
                     String id = StringArgumentType.getString(ctx, "id");
                     SoundPrintType type = "gunshot".equalsIgnoreCase(id) ? SoundPrintType.GUNSHOT : null;
                     if (type == null && !"footstep".equalsIgnoreCase(id)) {
                        ctx.getSource().sendFailure(Component.translatable("draginventory.soundprint.cmd.sim.unknown_type", Component.literal(id)));
                        return 0;
                     } else {
                        SoundPrintEvents.SIMULATOR.setType(type == null ? SoundPrintType.FOOTSTEP : type);
                        simStatus(ctx);
                        return 1;
                     }
                  })
            )
         )
         .then(Commands.literal("bearing").then(Commands.argument("degrees", IntegerArgumentType.integer(0, 359)).executes(ctx -> {
            int deg = IntegerArgumentType.getInteger(ctx, "degrees");
            SoundPrintEvents.SIMULATOR.setBearing((float)deg);
            simStatus(ctx);
            return 1;
         })))
         .then(Commands.literal("distance").then(Commands.argument("meters", IntegerArgumentType.integer(2, 20)).executes(ctx -> {
            int m = IntegerArgumentType.getInteger(ctx, "meters");
            SoundPrintEvents.SIMULATOR.setDistance((float)m);
            simStatus(ctx);
            return 1;
         })))
         .then(
            Commands.literal("mode").then(
               Commands.argument("id", StringArgumentType.word())
                  .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("static", "orbit", "random"), builder))
                  .executes(ctx -> {
                     String id = StringArgumentType.getString(ctx, "id");
                     SoundPrintSimulator.Movement movement = parseMovement(id);
                     if (movement == null) {
                        ctx.getSource().sendFailure(Component.translatable("draginventory.soundprint.cmd.sim.unknown_mode", Component.literal(id)));
                        return 0;
                     } else {
                        SoundPrintEvents.SIMULATOR.setMovement(movement);
                        simStatus(ctx);
                        return 1;
                     }
                  })
            )
         );
   }

   private static SoundPrintSimulator.Movement parseMovement(String id) {
      String var1 = id.toLowerCase(Locale.ROOT);

      return switch (var1) {
         case "static" -> SoundPrintSimulator.Movement.STATIC;
         case "orbit" -> SoundPrintSimulator.Movement.ORBIT;
         case "random" -> SoundPrintSimulator.Movement.RANDOM;
         default -> null;
      };
   }

   private static int dumpStatus(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack src = ctx.getSource();
      src.sendSuccess(() -> Component.translatable("draginventory.soundprint.cmd.status.header"), false);
      for (Supplier<String> line : statusLines()) {
         String text = line.get();
         src.sendSuccess(() -> Component.literal(text), false);
      }
      src.sendSuccess(() -> Component.translatable("draginventory.soundprint.cmd.status.footer"), false);
      return 1;
   }

   private static List<Supplier<String>> statusLines() {
      List<Supplier<String>> lines = new ArrayList<>();
      lines.add(() -> "general.enabled = " + SoundPrintConfig.ENABLED.get());
      lines.add(() -> "general.follow_compass_visibility = " + SoundPrintConfig.FOLLOW_COMPASS.get());
      lines.add(() -> "detection.radius = " + SoundPrintConfig.RADIUS.get() + " m");
      lines.add(() -> "detection.footsteps = " + SoundPrintConfig.FOOTSTEPS.get());
      lines.add(() -> "detection.gunshots = " + SoundPrintConfig.GUNSHOTS.get());
      lines.add(() -> "detection.ignore_own_sounds = " + SoundPrintConfig.IGNORE_OWN.get());
      lines.add(() -> "detection.detect_own_gunshots = " + SoundPrintConfig.OWN_GUNSHOTS.get());
      lines.add(() -> "detection.motion_persist = " + SoundPrintConfig.MOTION_PERSIST.get());
      lines.add(() -> "detection.footstep_keywords = " + SoundPrintConfig.FOOTSTEP_KEYWORDS.get());
      lines.add(() -> "detection.gunshot_keywords = " + SoundPrintConfig.GUNSHOT_KEYWORDS.get());
      lines.add(() -> "multi_source.mode = " + SoundPrintConfig.MODE.get());
      lines.add(() -> "multi_source.merge_window = " + SoundPrintConfig.MERGE_WINDOW.get() + " deg");
      lines.add(() -> "multi_source.stagger_spacing = " + SoundPrintConfig.STAGGER_SPACING.get() + " px");
      lines.add(() -> "style.bar_count = " + SoundPrintConfig.BAR_COUNT.get());
      lines.add(() -> "style.bar_width = " + SoundPrintConfig.BAR_WIDTH.get() + " px");
      lines.add(() -> "style.bar_gap = " + SoundPrintConfig.BAR_GAP.get() + " px");
      lines.add(() -> "style.max_height = " + SoundPrintConfig.MAX_HEIGHT.get() + " px");
      lines.add(() -> "style.baseline = " + SoundPrintConfig.BASELINE.get());
      lines.add(() -> "style.arrows = " + SoundPrintConfig.ARROWS.get());
      lines.add(() -> "style.vertical_offset = " + SoundPrintConfig.VERTICAL_OFFSET.get());
      lines.add(() -> "style.color_footstep = " + fmtColor((Integer)SoundPrintConfig.COLOR_FOOTSTEP.get()));
      lines.add(() -> "style.color_gunshot = " + fmtColor((Integer)SoundPrintConfig.COLOR_GUNSHOT.get()));
      lines.add(() -> "style.opacity = " + fmt2((Double)SoundPrintConfig.OPACITY.get()));
      lines.add(() -> "style.scale = " + fmt2((Double)SoundPrintConfig.SCALE.get()));
      lines.add(() -> "style.distance_decay = " + SoundPrintConfig.DISTANCE_DECAY.get());
      lines.add(() -> "style.min_decay = " + fmt2((Double)SoundPrintConfig.MIN_DECAY.get()));
      lines.add(() -> "animation.attack = " + fmt2((Double)SoundPrintConfig.ATTACK.get()));
      lines.add(() -> "animation.release = " + fmt2((Double)SoundPrintConfig.RELEASE.get()));
      lines.add(() -> "animation.linger_ms = " + SoundPrintConfig.LINGER_MS.get() + " ms");
      lines.add(() -> "animation.oscillate = " + SoundPrintConfig.OSCILLATE.get());
      lines.add(() -> "animation.osc_speed_hz = " + fmt2((Double)SoundPrintConfig.OSC_SPEED.get()) + " Hz");
      lines.add(() -> "animation.osc_amp_min_px = " + SoundPrintConfig.OSC_AMP_MIN.get() + " px");
      lines.add(() -> "animation.osc_amp_max_px = " + SoundPrintConfig.OSC_AMP_MAX.get() + " px");
      lines.add(() -> "animation.stack_amp_gain = " + fmt2((Double)SoundPrintConfig.STACK_AMP_GAIN.get()));
      lines.add(() -> "animation.stack_alpha_gain = " + fmt2((Double)SoundPrintConfig.STACK_ALPHA_GAIN.get()));
      lines.add(() -> "position.preset = " + SoundPrintConfig.POSITION_PRESET.get());
      lines.add(() -> "position.spacing = " + SoundPrintConfig.SPACING.get() + " px");
      lines.add(() -> "position.width_px = " + SoundPrintConfig.POSITION_WIDTH.get() + " px");
      lines.add(() -> "position.offset_x_px = " + SoundPrintConfig.OFFSET_X.get() + " px");
      lines.add(() -> "position.offset_y_px = " + SoundPrintConfig.OFFSET_Y.get() + " px");
      lines.add(() -> "runtime.active_sources = " + SoundPrintEvents.TRACKER.activeCount());
      lines.add(() -> "runtime.capturelog = " + SoundPrintEvents.captureLog());
      lines.add(() -> "runtime.sim = " + SoundPrintEvents.SIMULATOR.isEnabled()
         + " " + (SoundPrintEvents.SIMULATOR.type() == SoundPrintType.GUNSHOT ? "gunshot" : "footstep")
         + " " + String.format(Locale.ROOT, "%.0f", SoundPrintEvents.SIMULATOR.bearing()) + "deg"
         + " " + String.format(Locale.ROOT, "%.0f", SoundPrintEvents.SIMULATOR.distance()) + "m"
         + " " + SoundPrintEvents.SIMULATOR.movement().name().toLowerCase(Locale.ROOT));
      return lines;
   }

   private static String fmt2(double v) {
      return String.format(Locale.ROOT, "%.2f", v);
   }

   private static String fmtColor(int rgb) {
      return rgb < 0 ? "default" : String.format(Locale.ROOT, "0x%06X", rgb);
   }

   private static void simStatus(CommandContext<CommandSourceStack> ctx) {
      SoundPrintSimulator sim = SoundPrintEvents.SIMULATOR;

      feedback(
         ctx,
         "draginventory.soundprint.cmd.sim.status",
         Component.literal(switch (sim.type()) {
            case GUNSHOT -> "gunshot";
            case FOOTSTEP -> "footstep";
         }),
         Component.literal(String.format(Locale.ROOT, "%.0f", sim.bearing())),
         Component.literal(String.format(Locale.ROOT, "%.0f", sim.distance())),
         Component.literal(sim.movement().name().toLowerCase(Locale.ROOT)),
         Component.literal(String.valueOf(sim.isEnabled()))
      );
   }

   private static int setEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
      SoundPrintConfig.set(SoundPrintConfig.ENABLED, enabled);
      feedback(ctx, enabled ? "draginventory.soundprint.cmd.enabled" : "draginventory.soundprint.cmd.disabled");
      return 1;
   }

   private static void openSettings() {
      Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(SoundPrintSettingsScreen.create()));
   }

   private static void feedbackSet(CommandContext<CommandSourceStack> ctx, String key, String value) {
      feedback(ctx, "draginventory.soundprint.cmd.set", Component.literal(key), Component.literal(value));
   }

   private static void feedback(CommandContext<CommandSourceStack> ctx, String key, Object... args) {
      ctx.getSource().sendSuccess(() -> Component.translatable(key, args), false);
   }
}
