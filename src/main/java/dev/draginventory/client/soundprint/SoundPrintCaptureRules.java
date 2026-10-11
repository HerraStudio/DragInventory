package dev.draginventory.client.soundprint;

final class SoundPrintCaptureRules {
   static final double OWN_SOUND_METERS = 0.3;

   private SoundPrintCaptureRules() {
   }

   static float safeLoudness(SoundPrintCaptureRules.SoundView view) {
      Object resolved = quietResolved(view);
      if (resolved == null) {
         try {
            view.resolveOnce();
         } catch (RuntimeException var4) {
         }

         resolved = quietResolved(view);
      }

      if (resolved == null) {
         return 1.0F;
      } else if (view.isUnplayable(resolved)) {
         return Float.NaN;
      } else {
         try {
            return view.rawVolume();
         } catch (RuntimeException var3) {
            return 1.0F;
         }
      }
   }

   private static Object quietResolved(SoundPrintCaptureRules.SoundView view) {
      try {
         return view.resolvedSound();
      } catch (RuntimeException var2) {
         return null;
      }
   }

   static double boxDistanceSqr(double px, double py, double pz, double x, double y, double z) {
      double dx = Math.max(Math.max(px - 0.3 - x, x - (px + 0.3)), 0.0);
      double dy = Math.max(Math.max(py - y, y - (py + 1.8)), 0.0);
      double dz = Math.max(Math.max(pz - 0.3 - z, z - (pz + 0.3)), 0.0);
      return dx * dx + dy * dy + dz * dz;
   }

   static boolean isOwnSound(double px, double py, double pz, double x, double y, double z) {
      return boxDistanceSqr(px, py, pz, x, y, z) < 0.09;
   }

   static double[] projectAhead(double eyeX, double eyeY, double eyeZ, double vx, double vy, double vz, double distance) {
      double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
      if (len < 1.0E-6) {
         return new double[]{eyeX, eyeY, eyeZ};
      } else {
         double k = distance / len;
         return new double[]{eyeX + vx * k, eyeY + vy * k, eyeZ + vz * k};
      }
   }

   interface SoundView {
      Object resolvedSound();

      void resolveOnce();

      boolean isUnplayable(Object var1);

      float rawVolume();
   }
}
