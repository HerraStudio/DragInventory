package dev.draginventory.client.soundprint;

import java.util.List;
import java.util.Random;

public final class SoundPrintSimulator {
   public static final String ID_FOOTSTEP = "draginventory:sim_footstep";
   public static final String ID_GUNSHOT = "draginventory:sim_gunshot";
   static final long FOOTSTEP_INTERVAL_MS = 420L;
   static final long GUNSHOT_INTERVAL_MS = 2200L;
   static final float ORBIT_DEG_PER_SEC = 18.0F;
   static final long RETARGET_MIN_MS = 1200L;
   static final long RETARGET_SPAN_MS = 1200L;
   static final float RANDOM_BEARING_SWING = 50.0F;
   static final float RANDOM_DISTANCE_SWING = 2.5F;
   static final float RANDOM_APPROACH_RATE = 3.5F;
   private final Random random;
   private boolean enabled;
   private SoundPrintType type = SoundPrintType.FOOTSTEP;
   private float bearing = 90.0F;
   private float distance = 12.0F;
   private SoundPrintSimulator.Movement movement = SoundPrintSimulator.Movement.STATIC;
   private long lastEmitMillis = Long.MIN_VALUE;
   private long lastTickMillis = Long.MIN_VALUE;
   private long retargetAtMillis = Long.MIN_VALUE;
   private long retargetIntervalMillis = 1200L;
   private float targetBearing = 90.0F;
   private float targetDistance = 12.0F;

   public SoundPrintSimulator() {
      this(new Random());
   }

   SoundPrintSimulator(Random random) {
      this.random = random;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public void setEnabled(boolean enabled) {
      if (this.enabled != enabled) {
         this.enabled = enabled;
         if (enabled) {
            this.lastEmitMillis = Long.MIN_VALUE;
            this.lastTickMillis = Long.MIN_VALUE;
            this.retargetAtMillis = Long.MIN_VALUE;
         }
      }
   }

   public SoundPrintType type() {
      return this.type;
   }

   public void setType(SoundPrintType type) {
      this.type = type == null ? SoundPrintType.FOOTSTEP : type;
      this.lastEmitMillis = Long.MIN_VALUE;
   }

   public float bearing() {
      return this.bearing;
   }

   public void setBearing(float bearing) {
      this.bearing = wrap360(bearing);
      this.targetBearing = this.bearing;
   }

   public float distance() {
      return this.distance;
   }

   public void setDistance(float distance) {
      this.distance = clamp(distance, 2.0F, 20.0F);
      this.targetDistance = this.distance;
   }

   public SoundPrintSimulator.Movement movement() {
      return this.movement;
   }

   public void setMovement(SoundPrintSimulator.Movement movement) {
      this.movement = movement == null ? SoundPrintSimulator.Movement.STATIC : movement;
   }

   public List<SoundSample> tick(long nowMillis, double eyeX, double eyeY, double eyeZ) {
      if (!this.enabled) {
         this.lastTickMillis = nowMillis;
         return List.of();
      } else {
         float dt = this.stepSeconds(nowMillis);
         this.advanceMovement(nowMillis, dt);
         long interval = this.type == SoundPrintType.GUNSHOT ? 2200L : 420L;
         if (this.lastEmitMillis != Long.MIN_VALUE && nowMillis - this.lastEmitMillis < interval) {
            return List.of();
         } else {
            this.lastEmitMillis = nowMillis;
            double rad = Math.toRadians((double)this.bearing);
            double x = eyeX + Math.sin(rad) * (double)this.distance;
            double z = eyeZ - Math.cos(rad) * (double)this.distance;
            double y = this.type == SoundPrintType.GUNSHOT ? eyeY : eyeY - 1.62;
            float volume = this.type == SoundPrintType.GUNSHOT ? 1.0F : 0.6F;
            return List.of(
               new SoundSample(
                  x, y, z, this.type, volume, nowMillis, this.type == SoundPrintType.GUNSHOT ? "draginventory:sim_gunshot" : "draginventory:sim_footstep"
               )
            );
         }
      }
   }

   private float stepSeconds(long nowMillis) {
      float dt;
      if (this.lastTickMillis == Long.MIN_VALUE) {
         dt = 0.05F;
      } else {
         dt = Math.min(0.25F, Math.max(0.001F, (float)(nowMillis - this.lastTickMillis) / 1000.0F));
      }

      this.lastTickMillis = nowMillis;
      return dt;
   }

   private void advanceMovement(long nowMillis, float dt) {
      switch (this.movement) {
         case STATIC:
         default:
            break;
         case ORBIT:
            this.bearing = wrap360(this.bearing + 18.0F * dt);
            break;
         case RANDOM:
            if (this.retargetAtMillis == Long.MIN_VALUE || nowMillis - this.retargetAtMillis >= this.retargetIntervalMillis) {
               this.retargetAtMillis = nowMillis;
               this.retargetIntervalMillis = 1200L + (long)(this.random.nextFloat() * 1200.0F);
               this.targetBearing = wrap360(this.bearing + (this.random.nextFloat() - 0.5F) * 2.0F * 50.0F);
               this.targetDistance = clamp(this.distance + (this.random.nextFloat() - 0.5F) * 2.0F * 2.5F, 3.0F, 20.0F);
            }

            float k = 1.0F - (float)Math.exp((double)(-3.5F * dt));
            this.bearing = wrap360(this.bearing + wrapDelta(this.targetBearing - this.bearing) * k);
            this.distance = this.distance + (this.targetDistance - this.distance) * k;
      }
   }

   public void reset() {
      this.lastEmitMillis = Long.MIN_VALUE;
      this.lastTickMillis = Long.MIN_VALUE;
      this.retargetAtMillis = Long.MIN_VALUE;
   }

   static float wrap360(float v) {
      float r = v % 360.0F;
      return r < 0.0F ? r + 360.0F : r;
   }

   static float wrapDelta(float d) {
      return (d + 540.0F) % 360.0F - 180.0F;
   }

   private static float clamp(float v, float min, float max) {
      return v < min ? min : Math.min(max, v);
   }

   public static enum Movement {
      STATIC,
      ORBIT,
      RANDOM;
   }
}
