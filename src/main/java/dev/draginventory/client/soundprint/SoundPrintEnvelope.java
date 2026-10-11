package dev.draginventory.client.soundprint;

public final class SoundPrintEnvelope {
   private SoundPrintEnvelope() {
   }

   public static float bell(int i, int count) {
      if (count <= 1) {
         return 1.0F;
      } else {
         float center = (float)(count - 1) / 2.0F;
         float sigma = Math.max(0.8F, center / 1.6F);
         float d = ((float)i - center) / sigma;
         return (float)Math.exp((double)(-d * d));
      }
   }

   public static float[] asymmetryFactors(float asymmetry) {
      float a = clamp(asymmetry, -1.0F, 1.0F);
      float up = 1.0F + Math.max(0.0F, a) * 0.75F - Math.max(0.0F, -a) * 0.72F;
      float down = 1.0F + Math.max(0.0F, -a) * 0.75F - Math.max(0.0F, a) * 0.72F;
      return new float[]{Math.max(0.12F, up), Math.max(0.12F, down)};
   }

   public static float distanceDecay(float dist, float radius, float minDecay, boolean enabled) {
      if (!enabled) {
         return 1.0F;
      } else if (radius <= 0.01F) {
         return minDecay;
      } else {
         float t = clamp(dist / radius, 0.0F, 1.0F);
         return 1.0F - (1.0F - minDecay) * (float)Math.sqrt((double)t);
      }
   }

   public static float verticalOffset(double sourceY, double eyeY, boolean enabled) {
      return !enabled ? 0.0F : clamp((float)((sourceY - eyeY) / 4.0), -1.0F, 1.0F);
   }

   public static float arrowStrength(float verticalOffset) {
      float a = Math.abs(clamp(verticalOffset, -1.0F, 1.0F));
      // v2.9.5: dead zone 0.22 -> 0.15 - the old threshold needed the source
      // ~0.9 blocks above/below eye level before an arrow even started to
      // fade in, so shallow elevation differences (a platform one block up)
      // produced no arrow at all; full strength still at 0.65 (~2.6 blocks).
      if (a <= 0.15F) {
         return 0.0F;
      } else {
         return a >= 0.65F ? 1.0F : (a - 0.15F) / 0.5F;
      }
   }

   /**
    * v2.9.5: normalized arm spread for one row of a vertical arrow.
    *
    * Rows run top (row 0) to bottom (row rows-1); t = (row + 0.5) / rows in
    * (0, 1). An UP arrow is narrow at its top tip and widest at the bottom
    * base (spread grows with t); a DOWN arrow mirrors it (spread shrinks
    * with t). The v2.9.2 renderer had this ternary inverted, so every
    * arrow pointed the OPPOSITE way of the sound source - user-reported
    * in v2.9.4 field testing (source below -> bottom arrow pointed up).
    */
   static float arrowRowSpread(boolean up, float t) {
      float c = clamp(t, 0.0F, 1.0F);
      return up ? c : 1.0F - c;
   }

   public static float mergeEnergy(float e1, float e2) {
      float merged = (float)Math.sqrt((double)(e1 * e1 + e2 * e2));
      return Math.min(1.0F, merged);
   }

   public static float weightedBearing(float[] bearings, float[] energies) {
      float sin = 0.0F;
      float cos = 0.0F;

      for (int i = 0; i < bearings.length; i++) {
         double rad = Math.toRadians((double)bearings[i]);
         sin += (float)Math.sin(rad) * energies[i];
         cos += (float)Math.cos(rad) * energies[i];
      }

      if (sin == 0.0F && cos == 0.0F) {
         return 0.0F;
      } else {
         float bearing = (float)Math.toDegrees(Math.atan2((double)sin, (double)cos));
         return bearing < 0.0F ? bearing + 360.0F : bearing;
      }
   }

   /**
    * v2.9.3 hard ceiling for a single bar's rendered height.
    *
    * Before this, the bar height stacked every multiplier - max_height x
    * gunshot boost x vertical asymmetry (up to 1.75 for sounds above/below)
    * x wobble (up to 1.26) - so a "max_height = 24" config could render bars
    * ~3x taller than configured, pinning whole clusters over the screen in
    * vertical GWO firefights.
    *
    * The pre-wobble base height is clamped to cap / (1 + wobbleAmp) first and
    * the wobble multiplier is applied afterwards, so:
    * - the final height can NEVER exceed cap, for any asymmetry or wobble;
    * - a full-energy bar still wobbles between (1-amp)/(1+amp) x cap and cap
    *   instead of freezing against a hard clamp (the rhythm survives).
    *
    * @param rawBase   pre-wobble height: heightBase x bell x asymmetry (>= 0 expected)
    * @param cap       per-side ceiling = max_height x height boost x size scale
    * @param wobbleAmp wobble amplitude as a fraction (0 when oscillation is off)
    * @param wobble    per-bar wobble signal in [-1, 1]
    * @return rendered bar height in [0, cap]
    */
   static float cappedBarHeight(float rawBase, float cap, float wobbleAmp, float wobble) {
      if (cap <= 0.0F) {
         return 0.0F;
      } else {
         float amp = clamp(wobbleAmp, 0.0F, 1.0F);
         float ceiling = amp > 0.0F ? cap / (1.0F + amp) : cap;
         float clamped = clamp(rawBase, 0.0F, ceiling);
         float wobbled = clamped * (1.0F + amp * clamp(wobble, -1.0F, 1.0F));
         return clamp(wobbled, 0.0F, cap);
      }
   }

   static float smoothstep(float t) {
      t = clamp(t, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   static float clamp(float v, float min, float max) {
      return v < min ? min : Math.min(v, max);
   }
}
