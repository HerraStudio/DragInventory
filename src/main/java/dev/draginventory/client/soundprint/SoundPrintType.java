package dev.draginventory.client.soundprint;

public enum SoundPrintType {
   FOOTSTEP(15921906),
   GUNSHOT(16732754);

   private final int defaultRgb;

   private SoundPrintType(int defaultRgb) {
      this.defaultRgb = defaultRgb;
   }

   public int defaultRgb() {
      return this.defaultRgb;
   }

   public float heightBoost() {
      return this == GUNSHOT ? 1.35F : 1.0F;
   }

   public float baseVolume() {
      return this == GUNSHOT ? 0.85F : 0.28F;
   }

   /** Multiplier on the live-decay tail: gunshots snap away sharper than footsteps. */
   public float tailScale() {
      return this == GUNSHOT ? 0.6F : 1.0F;
   }
}
