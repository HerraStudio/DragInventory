package dev.draginventory.client.soundprint;

public record SoundSample(double x, double y, double z, SoundPrintType type, float volume, long atMillis, String soundId) {
   public boolean sameSourceAs(SoundSample other, double mergeDistance) {
      if (other != null && this.type == other.type) {
         double dx = this.x - other.x;
         double dy = this.y - other.y;
         double dz = this.z - other.z;
         return dx * dx + dy * dy + dz * dz <= mergeDistance * mergeDistance;
      } else {
         return false;
      }
   }
}
