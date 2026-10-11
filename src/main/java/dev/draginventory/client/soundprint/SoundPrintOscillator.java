package dev.draginventory.client.soundprint;

/**
 * Pure-JVM vibration math for the soundprint bars.
 *
 * Amplitude model (v2.9.0):
 * - intensity = energy x distanceFactor x stackBoost, clamped to [0, 1]
 * - ampPx interpolates from min to max with that intensity
 *   (v2.9.2: px amplitude only drives the vertical arrow reach)
 *
 * Persistent wobble (v2.9.2):
 * - every live bar wobbles continuously and independently - a slow body
 *   drift plus a fast micro flutter, both smooth random value noise
 * - amplitude is a FRACTION of the bar's current height and scales with
 *   the detected loudness: louder & closer = wider wobble
 * - bars only settle when the sound itself dies (energy -> 0)
 * - fully stateless: a pure function of time + seeds, immune to cluster
 *   reshuffles and identical in HUD, simulator and settings preview
 */
public final class SoundPrintOscillator {
   /**
    * Persistent-wobble amplitude floor: share of the bar height at zero
    * intensity. v2.9.4: 0.10 -> 0.18 - user feedback said even the resting
    * wobble of a live cluster was barely visible; a fifth of the bar height
    * keeps the print audibly "breathing" at all times.
    */
   static final float WOBBLE_AMP_MIN = 0.18F;
   /**
    * Persistent-wobble amplitude ceiling: share of the bar height at full
    * intensity. v2.9.4: 0.26 -> 0.42 - at full intensity a bar now swings
    * between (1-amp)/(1+amp) = 41% and 100% of its ceiling height, roughly
    * a 2.4x peak-to-trough ratio (v2.9.3 was 1.7x) while the v2.9.3 hard
    * ceiling still guarantees it never exceeds the configured maximum.
    */
   static final float WOBBLE_AMP_MAX = 0.42F;
   /** v2.9.4 stacking height gain per extra co-located source (bounded). */
   static final float STACK_HEIGHT_GAIN = 0.18F;
   /** Maximum extra co-located sources that still count towards the boost. */
   static final int STACK_HEIGHT_CAP_SOURCES = 3;
   /** Wobble retarget periods in seconds: slow body drift and fast micro flutter. */
   static final float WOBBLE_SLOW_CELL_SEC = 0.16F;
   static final float WOBBLE_FAST_CELL_SEC = 0.065F;
   /** Wobble octave mix - must keep |mix| bounded by 1. */
   static final float WOBBLE_SLOW_MIX = 0.68F;

   private SoundPrintOscillator() {
   }

   /**
    * Vibration amplitude in pixels for one cluster (v2.9.2: arrow reach only).
    *
    * @param energy       cluster energy in [0, 1]
    * @param distFactor   distance factor in [0, 1] (1 = right next to the player)
    * @param sourceCount  number of sources merged into this cluster (>= 1)
    * @param stackAmpGain extra amplitude per additional co-located source (0 = off)
    * @param minPx        amplitude floor in pixels
    * @param maxPx        amplitude ceiling in pixels
    */
   public static float amplitude(float energy, float distFactor, int sourceCount, float stackAmpGain, float minPx, float maxPx) {
      float boost = 1.0F + Math.max(0, sourceCount - 1) * Math.max(0.0F, stackAmpGain);
      float intensity = SoundPrintEnvelope.clamp(energy * SoundPrintEnvelope.clamp(distFactor, 0.0F, 1.0F) * boost, 0.0F, 1.0F);
      float lo = Math.min(minPx, maxPx);
      float hi = Math.max(minPx, maxPx);
      return lo + (hi - lo) * intensity;
   }

   /**
    * Persistent-wobble amplitude as a fraction of a bar's current height.
    *
    * A live cluster always keeps at least {@link #WOBBLE_AMP_MIN} of wobble so
    * the print never freezes while sound is present, and reaches
    * {@link #WOBBLE_AMP_MAX} at full near loud intensity.
    */
   public static float wobbleAmplitude(float energy, float distFactor, int sourceCount, float stackAmpGain) {
      float boost = 1.0F + Math.max(0, sourceCount - 1) * Math.max(0.0F, stackAmpGain);
      float intensity = SoundPrintEnvelope.clamp(energy * SoundPrintEnvelope.clamp(distFactor, 0.0F, 1.0F) * boost, 0.0F, 1.0F);
      return WOBBLE_AMP_MIN + (WOBBLE_AMP_MAX - WOBBLE_AMP_MIN) * intensity;
   }

   /**
    * v2.9.4 height boost for stacked co-located sources: every extra source
    * merged into the cluster makes the whole print taller (and therefore
    * louder-wobbling, since wobble is a fraction of height), capped after
    * {@link #STACK_HEIGHT_CAP_SOURCES} extra sources so a horde cannot
    * outgrow the reserved HUD band.
    *
    * @param sourceCount sources merged into the cluster (>= 1)
    * @return multiplier in [1, 1 + 3 x gain] = [1, 1.54] at default gain
    */
   public static float stackHeightBoost(int sourceCount) {
      int extra = Math.min(Math.max(sourceCount - 1, 0), STACK_HEIGHT_CAP_SOURCES);
      return 1.0F + extra * STACK_HEIGHT_GAIN;
   }

   /**
    * Organic per-bar wobble multiplier for a moment in time.
    *
    * @param timeSec      wall-clock seconds
    * @param speedHz      wobble tempo (Hz); 3.5 Hz is the identity tempo
    * @param clusterPhase stable per-cluster phase offset (radians)
    * @param barIndex     index of the bar inside the cluster
    * @return smooth random value in [-1, 1]; negative shrinks the bar below
    *         its base height, positive stretches it above
    */
   public static float wobble(float timeSec, float speedHz, float clusterPhase, int barIndex) {
      float tempo = Math.max(0.05F, speedHz) / 3.5F;
      float t = Math.max(0.0F, timeSec) * tempo;
      float slow = noise1D(clusterPhase, barIndex, 1.0F / WOBBLE_SLOW_CELL_SEC, t);
      float fast = noise1D(clusterPhase * 1.618F + 0.5F, barIndex + 101, 1.0F / WOBBLE_FAST_CELL_SEC, t);
      return WOBBLE_SLOW_MIX * slow + (1.0F - WOBBLE_SLOW_MIX) * fast;
   }

   /**
    * Smooth 1D value noise in [-1, 1]: hashed lattice values with a
    * smoothstep blend in between, so the signal drifts continuously and
    * never teleports.
    */
   private static float noise1D(float seed, int barIndex, float cellsPerSec, float t) {
      float x = Math.max(0.0F, t) * cellsPerSec;
      int cell = (int)Math.floor(x);
      float u = x - cell;
      float a = hash01(seed, barIndex, cell) * 2.0F - 1.0F;
      float b = hash01(seed, barIndex, cell + 1) * 2.0F - 1.0F;
      float s = u * u * (3.0F - 2.0F * u);
      return a + (b - a) * s;
   }

   /** Deterministic hash of (seed, bar, cell) into [0, 1). */
   private static float hash01(float seed, int barIndex, int cell) {
      int h = Float.floatToIntBits(seed);
      h = Integer.rotateLeft(h ^ barIndex * 0x9E3779B1, 13);
      h *= 0x85EBCA6B;
      h ^= cell * 0xC2B2AE3D;
      h ^= h >>> 15;
      h *= 0x27D4EB2F;
      h ^= h >>> 16;
      return (float)(h >>> 8) * (1.0F / 16777216.0F);
   }

   /**
    * Stable per-cluster phase derived from the bearing so neighbouring
    * clusters do not wobble in lockstep.
    */
   public static float clusterPhase(float bearingDeg) {
      return (float)Math.toRadians((double)bearingDeg) * 3.0F;
   }

   /**
    * Extra alpha for stacked co-located sources: more sources = more opaque.
    *
    * @param baseAlpha       base cluster alpha
    * @param sourceCount     sources merged into the cluster
    * @param stackAlphaGain  opacity gained per additional source (0..1)
    */
   public static float stackedAlpha(float baseAlpha, int sourceCount, float stackAlphaGain) {
      float extra = Math.max(0, sourceCount - 1) * Math.max(0.0F, stackAlphaGain);
      return SoundPrintEnvelope.clamp(baseAlpha + extra, 0.0F, 1.0F);
   }
}
