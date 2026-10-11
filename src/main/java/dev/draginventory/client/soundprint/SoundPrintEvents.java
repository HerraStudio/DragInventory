package dev.draginventory.client.soundprint;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(
   modid = "draginventory",
   value = {Dist.CLIENT}
)
public final class SoundPrintEvents {
   private static final Logger LOGGER = LoggerFactory.getLogger("DragInventory/SoundPrint");
   static final SoundPrintTracker TRACKER = new SoundPrintTracker();
   static final SoundPrintSimulator SIMULATOR = new SoundPrintSimulator();
   private static final double WALK_SPEED_MIN = 0.06;
   private static final AtomicBoolean CAPTURE_FAULT_REPORTED = new AtomicBoolean(false);
   private static final AtomicBoolean CAPTURE_LOG = new AtomicBoolean(false);

   /** Debug aid: log every sound event that reaches the capture chain. */
   public static void setCaptureLog(boolean on) {
      CAPTURE_LOG.set(on);
   }

   public static boolean captureLog() {
      return CAPTURE_LOG.get();
   }

   private static boolean relevantCategory(SoundSource source, SoundPrintType type) {
      if (source == SoundSource.NEUTRAL || source == SoundSource.HOSTILE || source == SoundSource.PLAYERS || source == SoundSource.BLOCKS) {
         return true;
      } else {
         // Gun mods commonly fire in MASTER; accept it for gunshots only so music/UI stays out.
         return source == SoundSource.MASTER && type == SoundPrintType.GUNSHOT;
      }
   }

   @SubscribeEvent
   public static void onPlaySound(PlaySoundEvent event) {
      try {
         capture(event.getSound());
      } catch (RuntimeException var2) {
         if (CAPTURE_FAULT_REPORTED.compareAndSet(false, true)) {
            LOGGER.warn("声纹采集异常（同类异常只记录一次，之后静默丢弃）", var2);
         }
      }
   }

   private static void capture(SoundInstance sound) {
      if ((Boolean)SoundPrintConfig.ENABLED.get()) {
         if (sound != null) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player != null && mc.level != null) {
               SoundPrintType type = SoundClassifier.classify(
                  sound.getLocation().toString(), SoundPrintConfig.footstepKeywords(), SoundPrintConfig.gunshotKeywords()
               );
               if (CAPTURE_LOG.get()) {
                  LOGGER.info(
                     "[SoundPrint-Capture] id={} category={} relative={} looping={} pos=({},{},{}) -> {}",
                     sound.getLocation(),
                     sound.getSource(),
                     sound.isRelative(),
                     sound.isLooping(),
                     sound.getX(),
                     sound.getY(),
                     sound.getZ(),
                     type
                  );
               }

               if (type != null) {
                  if (type != SoundPrintType.FOOTSTEP || (Boolean)SoundPrintConfig.FOOTSTEPS.get()) {
                     if (type != SoundPrintType.GUNSHOT || (Boolean)SoundPrintConfig.GUNSHOTS.get()) {
                        if (relevantCategory(sound.getSource(), type)) {
                           if (!sound.isLooping()) {
                              double x = sound.getX();
                              double y = sound.getY();
                              double z = sound.getZ();
                              if (sound.isRelative()) {
                                 // 2D/local sound (common for own gunfire): anchor it on the player.
                                 x = player.getX();
                                 y = player.getY();
                                 z = player.getZ();
                              }

                              double ex = player.getX();
                              double ey = player.getEyeY();
                              double ez = player.getZ();
                              boolean own = SoundPrintCaptureRules.isOwnSound(player.getX(), player.getY(), player.getZ(), x, y, z);
                              if (own && type == SoundPrintType.GUNSHOT && (Boolean)SoundPrintConfig.OWN_GUNSHOTS.get()) {
                                 Vec3 view = player.getViewVector(1.0F);
                                 Vec3 eye = player.getEyePosition(1.0F);
                                 double[] ahead = SoundPrintCaptureRules.projectAhead(eye.x, eye.y, eye.z, view.x, view.y, view.z, 4.0);
                                 x = ahead[0];
                                 y = ahead[1];
                                 z = ahead[2];
                                 own = false;
                              }

                              if (!own || !(Boolean)SoundPrintConfig.IGNORE_OWN.get()) {
                                 float dist = SoundPrintTracker.distance(x, y, z, ex, ey, ez);
                                 if (!(dist > (float)((Integer)SoundPrintConfig.RADIUS.get()).intValue())) {
                                    float loudness = loudness(sound, mc.getSoundManager());
                                    if (!Float.isNaN(loudness)) {
                                       TRACKER.offer(
                                          new SoundSample(
                                             x,
                                             y,
                                             z,
                                             type,
                                             Math.max(0.05F, Math.min(1.0F, loudness)),
                                             System.currentTimeMillis(),
                                             sound.getLocation().toString()
                                          )
                                       );
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   static float loudness(final SoundInstance sound, final SoundManager manager) {
      return SoundPrintCaptureRules.safeLoudness(new SoundPrintCaptureRules.SoundView() {
         @Override
         public Object resolvedSound() {
            return sound.getSound();
         }

         @Override
         public void resolveOnce() {
            sound.resolve(manager);
         }

         @Override
         public boolean isUnplayable(Object resolved) {
            return resolved == SoundManager.EMPTY_SOUND || resolved == SoundManager.INTENTIONALLY_EMPTY_SOUND;
         }

         @Override
         public float rawVolume() {
            return sound.getVolume();
         }
      });
   }

   @SubscribeEvent
   public static void onClientTick(Post event) {
      SoundPrintConfig.tickSave();
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null) {
         if ((Boolean)SoundPrintConfig.ENABLED.get()) {
            Vec3 eye = mc.player.getEyePosition(1.0F);
            long now = System.currentTimeMillis();
            if (SIMULATOR.isEnabled()) {
               for (SoundSample sample : SIMULATOR.tick(now, eye.x, eye.y, eye.z)) {
                  TRACKER.offer(sample);
               }
            }

            if ((Boolean)SoundPrintConfig.MOTION_PERSIST.get()) {
               TRACKER.motionTick(now, collectMovers(mc, eye, ((Integer)SoundPrintConfig.RADIUS.get()).floatValue()));
            }

            TRACKER.tick(
               now,
               eye.x,
               eye.y,
               eye.z,
               (float)((Integer)SoundPrintConfig.RADIUS.get()).intValue(),
               ((Double)SoundPrintConfig.MIN_DECAY.get()).floatValue(),
               (Boolean)SoundPrintConfig.DISTANCE_DECAY.get(),
               ((Double)SoundPrintConfig.ATTACK.get()).floatValue(),
               ((Double)SoundPrintConfig.RELEASE.get()).floatValue(),
               (long)((Integer)SoundPrintConfig.LINGER_MS.get()).intValue()
            );
         }
      } else {
         if (TRACKER.activeCount() > 0) {
            TRACKER.clear();
         }

         SIMULATOR.reset();
      }
   }

   private static List<SoundPrintTracker.Mover> collectMovers(Minecraft mc, Vec3 eye, float radius) {
      List<SoundPrintTracker.Mover> movers = new ArrayList<>(4);
      double r2 = (double)radius * (double)radius;

      for (Entity entity : mc.level.entitiesForRendering()) {
         if (entity != mc.player && entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity)entity;
            if (living.onGround()) {
               Vec3 dm = living.getDeltaMovement();
               double h2 = dm.x * dm.x + dm.z * dm.z;
               if (!(h2 < 0.0036)) {
                  double dx = living.getX() - eye.x;
                  double dy = living.getY() - eye.y;
                  double dz = living.getZ() - eye.z;
                  if (!(dx * dx + dy * dy + dz * dz > r2)) {
                     movers.add(new SoundPrintTracker.Mover(living.getX(), living.getY(), living.getZ(), Math.sqrt(h2)));
                  }
               }
            }
         }
      }

      // Own footsteps only get motion persistence when the player actually wants to see them.
      if (!(Boolean)SoundPrintConfig.IGNORE_OWN.get() && mc.player.onGround()) {
         Vec3 dm = mc.player.getDeltaMovement();
         double h2 = dm.x * dm.x + dm.z * dm.z;
         if (!(h2 < 0.0036)) {
            movers.add(new SoundPrintTracker.Mover(mc.player.getX(), mc.player.getY(), mc.player.getZ(), Math.sqrt(h2)));
         }
      }

      return movers;
   }

   @SubscribeEvent
   public static void onLoggingOut(LoggingOut event) {
      SoundPrintConfig.flush();
      TRACKER.clear();
      SIMULATOR.reset();
   }

   @SubscribeEvent
   public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
      for (LiteralArgumentBuilder<CommandSourceStack> root : SoundPrintCommands.buildRoots()) {
         event.getDispatcher().register(root);
      }
   }

   public static List<String> debugSummary() {
      return List.of("active_sources=" + TRACKER.activeCount());
   }
}
