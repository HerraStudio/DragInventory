package dev.draginventory.client.soundprint;

import java.util.List;

public record SoundPrintCluster(
   float bearing,
   float energy,
   SoundPrintType type,
   float verticalOffset,
   float nearestDistance,
   int sourceCount,
   boolean stagger,
   List<SoundPrintCluster.Member> members
) {
   public static record Member(float bearing, float energy, SoundPrintType type, float verticalOffset, float distance, float staggerOffset) {
   }
}
