package dev.draginventory.client.soundprint;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * v2.9.1 live-loudness model ("活的振动动画"):
 *
 * Every source tracks a LIVE loudness that jumps to each incoming sample's
 * volume and decays exponentially between samples (time constant = tailMs).
 * The displayed energy springs towards live x distance factor, so the bars
 * visibly breathe with the rhythm of the sound instead of standing at a
 * static height, and collapse smoothly to nothing once it goes quiet.
 *
 * Replaced from v2.9.0: the old model held peak energy at 100% for the whole
 * linger window and then faded it linearly, which read as a static white bar
 * that finally popped away ~2s later.
 */
public final class SoundPrintTracker {
   static final double SAME_SOURCE_METERS = 1.6;
   static final int MAX_SOURCES = 16;
   /** Live loudness below this counts as silence for expiry purposes. */
   static final float LIVE_SILENCE = 0.02F;
   /** Grace after silence before the (already invisible) source is dropped. */
   static final long EXPIRY_GRACE_MS = 200L;
   private final ConcurrentLinkedQueue<SoundSample> inbox = new ConcurrentLinkedQueue<>();
   private final List<SoundPrintTracker.ActiveSource> sources = new ArrayList<>();
   private long lastTickMillis = Long.MIN_VALUE;

   public void offer(SoundSample sample) {
      if (sample != null) {
         this.inbox.add(sample);
      }
   }

   public void tick(
      long nowMillis, double px, double py, double pz, float radius, float minDecay, boolean distanceDecay, float attack, float release, long tailMs
   ) {
      float dt = this.stepDeltaSeconds(nowMillis);

      SoundSample sample;
      while ((sample = this.inbox.poll()) != null) {
         if (nowMillis - sample.atMillis() <= 2000L) {
            this.mergeOrAdd(sample, nowMillis, px, py, pz, radius, minDecay, distanceDecay);
         }
      }

      for (int i = this.sources.size() - 1; i >= 0; i--) {
         SoundPrintTracker.ActiveSource source = this.sources.get(i);
         // Exponential live decay measured from the last refresh of THIS source,
         // so a sample that just arrived is not decayed for the whole gap before
         // it. Loud sounds keep the bars tall, then they sink away smoothly
         // instead of freezing at a static height.
         float elapsedSec = (float)(nowMillis - source.lastLiveMillis) / 1000.0F;
         if (elapsedSec > 0.0F) {
            float tailSec = Math.max(0.05F, (float)tailMs / 1000.0F) * source.type.tailScale();
            source.live *= (float)Math.exp((double)(-elapsedSec / tailSec));
            if (source.live < LIVE_SILENCE) {
               source.live = 0.0F;
            }

            source.lastLiveMillis = nowMillis;
         }

         float dist = distance(px, py, pz, source.x, source.y, source.z);
         float target = source.live * SoundPrintEnvelope.distanceDecay(dist, radius, minDecay, distanceDecay);
         boolean rising = target > source.displayEnergy;
         source.displayEnergy = springStep(source.displayEnergy, target, dt, rising ? attack : Math.max(release, 1.5F));
         boolean expired = source.live <= 0.0F
            && source.displayEnergy < 0.02F
            && nowMillis - source.lastHeard > tailMs + EXPIRY_GRACE_MS;
         if (expired) {
            this.sources.remove(i);
         }
      }
   }

   static float motionEnergy(double speedBlocksPerTick) {
      return SoundPrintEnvelope.clamp((float)(0.35 + speedBlocksPerTick * 1.8), 0.35F, 0.9F);
   }

   public void motionTick(long nowMillis, List<SoundPrintTracker.Mover> movers) {
      if (!movers.isEmpty()) {
         for (SoundPrintTracker.Mover mover : movers) {
            boolean matched = false;

            for (SoundPrintTracker.ActiveSource source : this.sources) {
               if (source.type == SoundPrintType.FOOTSTEP && samePosition(source, mover.x(), mover.y(), mover.z())) {
                  source.x = mover.x();
                  source.y = mover.y();
                  source.z = mover.z();
                  source.lastHeard = Math.max(source.lastHeard, nowMillis);
                  // A walking entity is a CONTINUOUS sound: keep the live
                  // loudness up so its print keeps vibrating instead of sagging.
                  source.live = Math.max(source.live, motionEnergy(mover.speed()) * 0.9F);
                  source.lastLiveMillis = Math.max(source.lastLiveMillis, nowMillis);
                  matched = true;
                  break;
               }
            }

            if (!matched && this.sources.size() < 16) {
               float energy = motionEnergy(mover.speed());
               this.sources
                  .add(
                     new SoundPrintTracker.ActiveSource(
                        new SoundSample(mover.x(), mover.y(), mover.z(), SoundPrintType.FOOTSTEP, energy, nowMillis, "draginventory:motion_walk"),
                        energy,
                        nowMillis
                     )
                  );
            }
         }
      }
   }

   private float stepDeltaSeconds(long nowMillis) {
      float dt;
      if (this.lastTickMillis == Long.MIN_VALUE) {
         dt = 0.05F;
      } else {
         dt = Math.min(0.25F, Math.max(0.001F, (float)(nowMillis - this.lastTickMillis) / 1000.0F));
      }

      this.lastTickMillis = nowMillis;
      return dt;
   }

   private void mergeOrAdd(SoundSample sample, long nowMillis, double px, double py, double pz, float radius, float minDecay, boolean distanceDecay) {
      for (SoundPrintTracker.ActiveSource source : this.sources) {
         if (source.type == sample.type() && samePosition(source, sample)) {
            source.x = sample.x();
            source.y = sample.y();
            source.z = sample.z();
            source.lastHeard = nowMillis;
            source.soundId = sample.soundId();
            float energy = normalizedEnergy(sample, px, py, pz, radius, minDecay, distanceDecay);
            // Attack: the live loudness jumps straight to the newest sample so
            // each footstep pops the bars up, then the tail sinks them back.
            source.live = Math.max(source.live, energy);
            source.lastLiveMillis = Math.max(source.lastLiveMillis, sample.atMillis());
            source.peak = Math.max(source.peak * 0.6F, energy);
            return;
         }
      }

      float energy = normalizedEnergy(sample, px, py, pz, radius, minDecay, distanceDecay);
      if (this.sources.size() >= 16) {
         int weakest = 0;

         for (int i = 1; i < this.sources.size(); i++) {
            if (scoreForReplacement(this.sources.get(i), nowMillis) < scoreForReplacement(this.sources.get(weakest), nowMillis)) {
               weakest = i;
            }
         }

         if (energy > scoreForReplacement(this.sources.get(weakest), nowMillis)) {
            this.sources.set(weakest, new SoundPrintTracker.ActiveSource(sample, energy, nowMillis));
         }
      } else {
         this.sources.add(new SoundPrintTracker.ActiveSource(sample, energy, nowMillis));
      }
   }

   private static float scoreForReplacement(SoundPrintTracker.ActiveSource source, long nowMillis) {
      long quiet = Math.max(0L, nowMillis - source.lastHeard - 300L);
      return Math.max(source.live, source.displayEnergy) * (1.0F / (1.0F + (float)quiet / 300.0F));
   }

   private static boolean samePosition(SoundPrintTracker.ActiveSource source, SoundSample sample) {
      return samePosition(source, sample.x(), sample.y(), sample.z());
   }

   private static boolean samePosition(SoundPrintTracker.ActiveSource source, double x, double y, double z) {
      double dx = source.x - x;
      double dy = source.y - y;
      double dz = source.z - z;
      return dx * dx + dy * dy + dz * dz <= 2.5600000000000005;
   }

   private static float normalizedEnergy(SoundSample sample, double px, double py, double pz, float radius, float minDecay, boolean distanceDecay) {
      return SoundPrintEnvelope.clamp(sample.volume() / sample.type().baseVolume(), 0.25F, 1.0F);
   }

   static float distance(double ax, double ay, double az, double bx, double by, double bz) {
      double dx = ax - bx;
      double dy = ay - by;
      double dz = az - bz;
      return (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   static float springStep(float value, float target, float dt, float omega) {
      if (dt <= 0.0F) {
         return value;
      } else {
         double displacement = (double)(value - target);
         double coefficient = (double)omega * displacement;
         double decay = Math.exp((double)(-omega * dt));
         double next = (double)target + (displacement + coefficient * (double)dt) * decay;
         if (displacement > 0.0 && next < (double)target) {
            next = (double)target;
         }

         if (displacement < 0.0 && next > (double)target) {
            next = (double)target;
         }

         return (float)next;
      }
   }

   public List<SoundPrintAggregator.Hit> frameHits(double eyeX, double eyeY, double eyeZ, boolean verticalOffsetEnabled) {
      List<SoundPrintAggregator.Hit> hits = new ArrayList<>(this.sources.size());

      for (SoundPrintTracker.ActiveSource source : this.sources) {
         if (!(source.displayEnergy < 0.01F)) {
            hits.add(
               new SoundPrintAggregator.Hit(
                  bearingFrom(eyeX, eyeZ, source.x, source.z),
                  source.displayEnergy,
                  source.type,
                  SoundPrintEnvelope.verticalOffset(source.y, eyeY, verticalOffsetEnabled),
                  distance(eyeX, eyeY, eyeZ, source.x, source.y, source.z)
               )
            );
         }
      }

      return hits;
   }

   public static float bearingFrom(double eyeX, double eyeZ, double x, double z) {
      double dx = x - eyeX;
      double dz = z - eyeZ;
      float bearing = (float)Math.toDegrees(Math.atan2(dx, -dz));
      return bearing < 0.0F ? bearing + 360.0F : bearing;
   }

   public int activeCount() {
      return this.sources.size();
   }

   public void clear() {
      this.inbox.clear();
      this.sources.clear();
      this.lastTickMillis = Long.MIN_VALUE;
   }

   public static final class ActiveSource {
      final SoundPrintType type;
      double x;
      double y;
      double z;
      float peak;
      /** Instantaneous loudness in [0, 1]: jumps on samples, decays per tick. */
      float live;
      float displayEnergy;
      long lastHeard;
      /** Timestamp of the last live-loudness refresh (sample or motion). */
      long lastLiveMillis;
      String soundId;

      ActiveSource(SoundSample sample, float energy, long now) {
         this.type = sample.type();
         this.x = sample.x();
         this.y = sample.y();
         this.z = sample.z();
         this.peak = energy;
         this.live = energy;
         this.displayEnergy = 0.0F;
         this.lastHeard = now;
         this.lastLiveMillis = sample.atMillis();
         this.soundId = sample.soundId();
      }
   }

   public static record Mover(double x, double y, double z, double speed) {
   }
}
