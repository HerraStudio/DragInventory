package dev.draginventory.client.soundprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * v2.9.1 live-loudness model tests.
 */
class SoundPrintTrackerTest {
   private static final float RADIUS = 20.0F;
   private static final float ATTACK = 18.0F;
   private static final float RELEASE = 7.0F;
   private static final long TAIL = 280L;

   private static void step(SoundPrintTracker tracker, long t) {
      tracker.tick(t, 0.0, 1.62, 0.0, RADIUS, 0.2F, true, ATTACK, RELEASE, TAIL);
   }

   @Test
   void footstepsBreathButNeverBlackOut() {
      SoundPrintTracker tracker = new SoundPrintTracker();
      // 450 ms cadence with the live tail: energy must visibly dip between steps
      // (the "alive" breathing) yet never collapse to nothing mid-walk.
      float minMidEnergy = 1.0F;
      float maxMidEnergy = 0.0F;
      float minStepEnergy = 1.0F;
      for (int i = 0; i < 24; i++) {
         long t = i * 450L;
         tracker.offer(new SoundSample(0.0, 0.0, 3.0, SoundPrintType.FOOTSTEP, 0.6F, t, "test:step"));
         step(tracker, t);
         float stepE = lastDisplayEnergy(tracker);
         // sample half-way to the next step: the tail has decayed for 225 ms
         step(tracker, t + 225L);
         float midE = lastDisplayEnergy(tracker);
         if (i >= 2) {
            minStepEnergy = Math.min(minStepEnergy, stepE);
            minMidEnergy = Math.min(minMidEnergy, midE);
            maxMidEnergy = Math.max(maxMidEnergy, midE);
         }
      }
      assertTrue(minStepEnergy > 0.5F, "step energy should pop high, got " + minStepEnergy);
      assertTrue(minMidEnergy > 0.10F, "mid-gap energy must not black out (flicker), got " + minMidEnergy);
      assertTrue(maxMidEnergy < minStepEnergy - 0.10F, "mid-gap energy should sag below the step peak (breathing)");
      assertEquals(1, tracker.activeCount());
   }

   @Test
   void louderSamplesReachHigherEnergy() {
      SoundPrintTracker quiet = new SoundPrintTracker();
      SoundPrintTracker loud = new SoundPrintTracker();
      quiet.offer(new SoundSample(0.0, 0.0, 5.0, SoundPrintType.FOOTSTEP, 0.1F, 0L, "test:q"));
      loud.offer(new SoundSample(0.0, 0.0, 5.0, SoundPrintType.FOOTSTEP, 0.5F, 0L, "test:l"));
      step(quiet, 0L);
      step(loud, 0L);
      step(quiet, 60L);
      step(loud, 60L);
      assertTrue(
         lastDisplayEnergy(loud) > lastDisplayEnergy(quiet),
         "louder sample must scale the bars up (amplitude follows volume)"
      );
   }

   @Test
   void tailConstantControlsCollapseSpeed() {
      SoundPrintTracker shortTail = new SoundPrintTracker();
      SoundPrintTracker longTail = new SoundPrintTracker();
      shortTail.offer(new SoundSample(0.0, 0.0, 5.0, SoundPrintType.FOOTSTEP, 0.6F, 0L, "test:s"));
      longTail.offer(new SoundSample(0.0, 0.0, 5.0, SoundPrintType.FOOTSTEP, 0.6F, 0L, "test:l"));
      shortTail.tick(0L, 0.0, 1.62, 0.0, RADIUS, 0.2F, true, ATTACK, RELEASE, 100L);
      longTail.tick(0L, 0.0, 1.62, 0.0, RADIUS, 0.2F, true, ATTACK, RELEASE, 1000L);
      for (long t = 50L; t <= 500L; t += 50L) {
         shortTail.tick(t, 0.0, 1.62, 0.0, RADIUS, 0.2F, true, ATTACK, RELEASE, 100L);
         longTail.tick(t, 0.0, 1.62, 0.0, RADIUS, 0.2F, true, ATTACK, RELEASE, 1000L);
      }
      assertTrue(
         lastDisplayEnergy(shortTail) < lastDisplayEnergy(longTail),
         "a shorter tail must collapse the print faster"
      );
   }

   @Test
   void motionTickRenewsAndSlidesSource() {
      SoundPrintTracker tracker = new SoundPrintTracker();
      tracker.offer(new SoundSample(0.0, 0.0, 5.0, SoundPrintType.FOOTSTEP, 0.6F, 0L, "test:step"));
      step(tracker, 0L);
      assertTrue(tracker.activeCount() == 1);

      // walker keeps moving every 450 ms; no sound events at all after the first
      for (int i = 1; i <= 8; i++) {
         long t = i * 450L;
         double z = 5.0 + i * 0.3;
         tracker.motionTick(t, List.of(new SoundPrintTracker.Mover(0.0, 0.0, z, 0.15)));
         step(tracker, t);
         assertEquals(1, tracker.activeCount(), "walking source must stay alive (renewal), tick " + i);
         assertTrue(lastDisplayEnergy(tracker) > 0.2F, "walking source went dark at tick " + i);
      }
   }

   @Test
   void ownPositionMoverKeepsOwnFootstepLit() {
      // the v2.9.0 fix: local player is fed into motionTick when ignore_own is off
      SoundPrintTracker tracker = new SoundPrintTracker();
      tracker.offer(new SoundSample(0.0, 0.0, 0.0, SoundPrintType.FOOTSTEP, 0.6F, 0L, "test:own"));
      step(tracker, 0L);

      for (int i = 1; i <= 10; i++) {
         long t = i * 450L;
         tracker.motionTick(t, List.of(new SoundPrintTracker.Mover(0.0, 0.0, 0.0, 0.12)));
         step(tracker, t);
         assertEquals(1, tracker.activeCount(), "own footstep source must survive while walking, tick " + i);
      }
   }

   @Test
   void sourceExpiresAfterQuiet() {
      SoundPrintTracker tracker = new SoundPrintTracker();
      tracker.offer(new SoundSample(0.0, 0.0, 5.0, SoundPrintType.FOOTSTEP, 0.6F, 0L, "test:step"));
      step(tracker, 0L);
      // keep ticking far past tail + grace without any sound or motion
      for (long t = 450L; t <= 8000L; t += 450L) {
         step(tracker, t);
      }
      assertEquals(0, tracker.activeCount(), "source should have expired after quiet period");
   }

   @Test
   void printCollapsesToInvisibleBeforeExpiry() {
      // The white-bar residue fix: by the time the source is dropped its energy
      // must already be visually zero, so removal can never look like a pop.
      SoundPrintTracker tracker = new SoundPrintTracker();
      tracker.offer(new SoundSample(0.0, 0.0, 5.0, SoundPrintType.FOOTSTEP, 0.6F, 0L, "test:step"));
      step(tracker, 0L);

      float lastEnergy = 1.0F;
      int seen = 0;
      for (long t = 50L; t <= 4000L && tracker.activeCount() > 0; t += 50L) {
         step(tracker, t);
         lastEnergy = lastDisplayEnergy(tracker);
         seen++;
      }

      assertEquals(0, tracker.activeCount(), "source must expire within the window");
      assertTrue(lastEnergy < 0.05F, "energy right before removal must be near zero, got " + lastEnergy);
      assertTrue(seen > 4, "collapse should take several frames, got " + seen);
   }

   @Test
   void gunshotIsNotRenewedByMotion() {
      SoundPrintTracker tracker = new SoundPrintTracker();
      tracker.offer(new SoundSample(0.0, 1.6, 5.0, SoundPrintType.GUNSHOT, 1.0F, 0L, "test:shot"));
      step(tracker, 0L);
      // movers at the gunshot position must NOT renew the GUNSHOT source
      // (they may legitimately spawn their own footstep source, so we assert
      // on the gunshot specifically, not on activeCount)
      for (int i = 1; i <= 12; i++) {
         long t = i * 450L;
         tracker.motionTick(t, List.of(new SoundPrintTracker.Mover(0.0, 1.6, 5.0, 0.15)));
         step(tracker, t);
      }
      for (SoundPrintAggregator.Hit h : tracker.frameHits(0.0, 1.62, 0.0, false)) {
         if (h.type() == SoundPrintType.GUNSHOT) {
            throw new AssertionError("gunshot must fade even with motion at its position");
         }
      }
   }

   @Test
   void frameHitsCarryBearingEnergyAndDistance() {
      SoundPrintTracker tracker = new SoundPrintTracker();
      tracker.offer(new SoundSample(10.0, 0.0, 0.0, SoundPrintType.GUNSHOT, 1.0F, 0L, "test:shot"));
      step(tracker, 0L);
      step(tracker, 50L);
      List<SoundPrintAggregator.Hit> hits = tracker.frameHits(0.0, 1.62, 0.0, true);
      assertEquals(1, hits.size());
      assertEquals(90.0F, hits.get(0).bearing(), 0.5F); // due east
      assertTrue(hits.get(0).energy() > 0.0F);
      assertTrue(hits.get(0).distance() > 8.0F);
   }

   @Test
   void springStepConvergesToTarget() {
      float v = 0.0F;
      for (int i = 0; i < 200; i++) {
         v = SoundPrintTracker.springStep(v, 1.0F, 0.05F, 18.0F);
      }
      assertEquals(1.0F, v, 0.001F);
   }

   private static float lastDisplayEnergy(SoundPrintTracker tracker) {
      float best = 0.0F;
      for (SoundPrintAggregator.Hit h : tracker.frameHits(0.0, 1.62, 0.0, false)) {
         if (h.type() == SoundPrintType.FOOTSTEP) {
            best = Math.max(best, h.energy());
         }
      }
      return best;
   }
}
