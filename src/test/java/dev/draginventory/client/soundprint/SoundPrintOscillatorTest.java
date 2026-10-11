package dev.draginventory.client.soundprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SoundPrintOscillatorTest {
   private static final float EPS = 1.0E-4F;

   @Test
   void amplitudeScalesWithEnergy() {
      float near = SoundPrintOscillator.amplitude(1.0F, 1.0F, 1, 0.0F, 1.0F, 9.0F);
      float weak = SoundPrintOscillator.amplitude(0.25F, 1.0F, 1, 0.0F, 1.0F, 9.0F);
      assertEquals(9.0F, near, EPS);
      assertEquals(1.0F + (9.0F - 1.0F) * 0.25F, weak, EPS);
   }

   @Test
   void amplitudeFallsWithDistance() {
      float closeBy = SoundPrintOscillator.amplitude(1.0F, 1.0F, 1, 0.0F, 1.0F, 9.0F);
      float farAway = SoundPrintOscillator.amplitude(1.0F, 0.3F, 1, 0.0F, 1.0F, 9.0F);
      assertTrue(closeBy > farAway);
      assertEquals(1.0F + (9.0F - 1.0F) * 0.3F, farAway, EPS);
   }

   @Test
   void stackedSourcesBoostAmplitude() {
      float single = SoundPrintOscillator.amplitude(0.4F, 1.0F, 1, 0.5F, 1.0F, 9.0F);
      float triple = SoundPrintOscillator.amplitude(0.4F, 1.0F, 3, 0.5F, 1.0F, 9.0F);
      // boost = 1 + 2*0.5 = 2 -> intensity = 0.8 (clamped below 1)
      assertEquals(1.0F + (9.0F - 1.0F) * 0.8F, triple, EPS);
      assertTrue(triple > single);
   }

   @Test
   void amplitudeClampsAtMax() {
      float boosted = SoundPrintOscillator.amplitude(0.5F, 1.0F, 10, 1.0F, 1.0F, 9.0F);
      assertEquals(9.0F, boosted, EPS);
   }

   @Test
   void amplitudeClampsAtMin() {
      float zeroEnergy = SoundPrintOscillator.amplitude(0.0F, 1.0F, 1, 0.0F, 1.5F, 9.0F);
      assertEquals(1.5F, zeroEnergy, EPS);
   }

   @Test
   void wobbleStaysInUnitRange() {
      for (int i = 0; i < 20; i++) {
         for (long t = 0L; t < 3000L; t += 29L) {
            float w = SoundPrintOscillator.wobble(t / 1000.0F, 3.5F, 0.0F, i);
            assertTrue(w <= 1.0F + EPS, "wobble above 1: " + w);
            assertTrue(w >= -1.0F - EPS, "wobble below -1: " + w);
         }
      }
   }

   @Test
   void wobbleIsDeterministic() {
      assertEquals(
         SoundPrintOscillator.wobble(1.234F, 3.5F, 2.0F, 5),
         SoundPrintOscillator.wobble(1.234F, 3.5F, 2.0F, 5),
         0.0F
      );
   }

   @Test
   void wobbleMovesEveryFrameButNeverTeleports() {
      // persistent life: the same bar must keep changing frame to frame...
      float maxFrameDelta = 0.0F;
      for (long t = 0L; t < 2000L; t += 16L) {
         float prev = SoundPrintOscillator.wobble(t / 1000.0F, 3.5F, 1.0F, 4);
         float next = SoundPrintOscillator.wobble((t + 16L) / 1000.0F, 3.5F, 1.0F, 4);
         maxFrameDelta = Math.max(maxFrameDelta, Math.abs(next - prev));
      }

      assertTrue(maxFrameDelta > 0.0F, "wobble must move between frames");
      // ...yet smoothly - a stepwise random signal would jump by ~2.0 per frame
      assertTrue(maxFrameDelta < 0.55F, "wobble must be smooth, not teleporting: " + maxFrameDelta);
   }

   @Test
   void wobbleDiffersPerBar() {
      int differing = 0;

      for (long t = 0L; t < 2000L; t += 23L) {
         float a = SoundPrintOscillator.wobble(t / 1000.0F, 3.5F, 0.0F, 0);
         float b = SoundPrintOscillator.wobble(t / 1000.0F, 3.5F, 0.0F, 1);
         if (Math.abs(a - b) > 0.02F) {
            differing++;
         }
      }

      assertTrue(differing > 80, "bars should wobble independently, differing frames: " + differing);
   }

   @Test
   void wobbleClustersDecorrelateByPhase() {
      float foreignPhase = SoundPrintOscillator.clusterPhase(120.0F);
      int differing = 0;

      for (long t = 0L; t < 2000L; t += 31L) {
         float a = SoundPrintOscillator.wobble(t / 1000.0F, 3.5F, 0.0F, 3);
         float b = SoundPrintOscillator.wobble(t / 1000.0F, 3.5F, foreignPhase, 3);
         if (Math.abs(a - b) > 0.02F) {
            differing++;
         }
      }

      assertTrue(differing > 40, "neighbouring clusters should not wobble in lockstep, differing frames: " + differing + "/" + 65);
   }

   @Test
   void wobbleAmplitudeScalesWithIntensityAndKeepsBounds() {
      float floor = SoundPrintOscillator.wobbleAmplitude(0.0F, 1.0F, 1, 0.0F);
      float peak = SoundPrintOscillator.wobbleAmplitude(1.0F, 1.0F, 1, 0.0F);
      assertEquals(SoundPrintOscillator.WOBBLE_AMP_MIN, floor, EPS);
      assertEquals(SoundPrintOscillator.WOBBLE_AMP_MAX, peak, EPS);
      float mid = SoundPrintOscillator.wobbleAmplitude(0.5F, 1.0F, 1, 0.0F);
      assertTrue(floor < mid && mid < peak, "amplitude should grow with intensity");
      float farAway = SoundPrintOscillator.wobbleAmplitude(1.0F, 0.3F, 1, 0.0F);
      assertTrue(farAway < peak && farAway >= floor, "distance should reduce the wobble");
      float stacked = SoundPrintOscillator.wobbleAmplitude(0.5F, 1.0F, 3, 0.5F);
      assertTrue(stacked > mid, "stacked sources should widen the wobble");
   }

   @Test
   void clusterPhaseVariesByBearing() {
      assertTrue(Math.abs(SoundPrintOscillator.clusterPhase(10.0F) - SoundPrintOscillator.clusterPhase(120.0F)) > 0.1F);
   }

   @Test
   void stackedAlphaGrowsButClamps() {
      float base = 0.5F;
      assertEquals(0.5F, SoundPrintOscillator.stackedAlpha(base, 1, 0.25F), EPS);
      assertEquals(0.75F, SoundPrintOscillator.stackedAlpha(base, 2, 0.25F), EPS);
      assertEquals(1.0F, SoundPrintOscillator.stackedAlpha(base, 8, 0.25F), EPS);
   }

   @Test
   void stackedAlphaIgnoresNegativeGain() {
      assertEquals(0.5F, SoundPrintOscillator.stackedAlpha(0.5F, 5, -1.0F), EPS);
   }

   @Test
   void stackHeightBoostGrowsPerSourceAndCaps() {
      // v2.9.4: every extra co-located source makes the whole print taller.
      assertEquals(1.0F, SoundPrintOscillator.stackHeightBoost(1), EPS);
      assertEquals(1.18F, SoundPrintOscillator.stackHeightBoost(2), EPS);
      assertEquals(1.36F, SoundPrintOscillator.stackHeightBoost(3), EPS);
      assertEquals(1.54F, SoundPrintOscillator.stackHeightBoost(4), EPS);
      // capped: a horde must not outgrow the reserved HUD band
      assertEquals(1.54F, SoundPrintOscillator.stackHeightBoost(7), EPS);
      assertEquals(1.54F, SoundPrintOscillator.stackHeightBoost(64), EPS);
   }

   @Test
   void stackHeightBoostToleratesBadCounts() {
      assertEquals(1.0F, SoundPrintOscillator.stackHeightBoost(0), EPS);
      assertEquals(1.0F, SoundPrintOscillator.stackHeightBoost(-3), EPS);
   }

   @Test
   void wobbleAmplitudeBoundsAreTheV294RetunedValues() {
      // v2.9.4 amplitude retune: resting wobble is clearly visible and a
      // full-intensity bar swings inside the v2.9.3 ceiling with a ~2.4x
      // peak-to-trough ratio instead of v2.9.3's ~1.7x.
      assertEquals(0.18F, SoundPrintOscillator.WOBBLE_AMP_MIN, EPS);
      assertEquals(0.42F, SoundPrintOscillator.WOBBLE_AMP_MAX, EPS);
      float floor = SoundPrintOscillator.wobbleAmplitude(0.0F, 1.0F, 1, 0.0F);
      float peak = SoundPrintOscillator.wobbleAmplitude(1.0F, 1.0F, 1, 0.0F);
      assertEquals(0.18F, floor, EPS);
      assertEquals(0.42F, peak, EPS);
      assertTrue(floor > 0.15F, "resting wobble must stay clearly visible");
      assertTrue(peak < 0.5F, "wobble must stay a fraction of bar height (ceiling math)");
   }
}
