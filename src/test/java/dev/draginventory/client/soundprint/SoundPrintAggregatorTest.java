package dev.draginventory.client.soundprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SoundPrintAggregatorTest {
   @Test
   void sameBearingSourcesStackIntoOneClusterWithCount() {
      List<SoundPrintCluster> out = SoundPrintAggregator.aggregate(
         List.of(
            new SoundPrintAggregator.Hit(90.0F, 0.6F, SoundPrintType.FOOTSTEP, 0.0F, 6.0F),
            new SoundPrintAggregator.Hit(91.0F, 0.5F, SoundPrintType.FOOTSTEP, 0.0F, 7.0F),
            new SoundPrintAggregator.Hit(92.0F, 0.7F, SoundPrintType.FOOTSTEP, 0.0F, 8.0F)
         ),
         false,
         6.0F,
         2.0F
      );
      assertEquals(1, out.size());
      assertEquals(3, out.get(0).sourceCount(), "stack count must reach the renderer for opacity/amp gain");
      assertEquals(90.0F + 2.0F, out.get(0).bearing(), 1.0F);
   }

   @Test
   void distantBearingsStaySeparate() {
      List<SoundPrintCluster> out = SoundPrintAggregator.aggregate(
         List.of(
            new SoundPrintAggregator.Hit(10.0F, 0.6F, SoundPrintType.FOOTSTEP, 0.0F, 5.0F),
            new SoundPrintAggregator.Hit(200.0F, 0.6F, SoundPrintType.FOOTSTEP, 0.0F, 5.0F)
         ),
         false,
         6.0F,
         2.0F
      );
      assertEquals(2, out.size());
   }

   @Test
   void mergedEnergyFollowsRootSumOfSquares() {
      List<SoundPrintCluster> out = SoundPrintAggregator.aggregate(
         List.of(
            new SoundPrintAggregator.Hit(90.0F, 0.6F, SoundPrintType.FOOTSTEP, 0.0F, 5.0F),
            new SoundPrintAggregator.Hit(91.0F, 0.6F, SoundPrintType.FOOTSTEP, 0.0F, 5.0F)
         ),
         false,
         6.0F,
         2.0F
      );
      assertEquals(1, out.size());
      assertEquals((float)Math.sqrt(0.6 * 0.6 + 0.6 * 0.6), out.get(0).energy(), 1.0E-4F);
   }

   @Test
   void gunshotWinsTypeWhenMergedWithFootstep() {
      List<SoundPrintCluster> out = SoundPrintAggregator.aggregate(
         List.of(
            new SoundPrintAggregator.Hit(90.0F, 0.6F, SoundPrintType.FOOTSTEP, 0.0F, 5.0F),
            new SoundPrintAggregator.Hit(91.0F, 0.6F, SoundPrintType.GUNSHOT, 0.0F, 5.0F)
         ),
         false,
         6.0F,
         2.0F
      );
      assertEquals(SoundPrintType.GUNSHOT, out.get(0).type());
   }

   @Test
   void wrapAroundBearingStillMerges() {
      List<SoundPrintCluster> out = SoundPrintAggregator.aggregate(
         List.of(
            new SoundPrintAggregator.Hit(359.0F, 0.6F, SoundPrintType.FOOTSTEP, 0.0F, 5.0F),
            new SoundPrintAggregator.Hit(1.0F, 0.6F, SoundPrintType.FOOTSTEP, 0.0F, 5.0F)
         ),
         false,
         6.0F,
         2.0F
      );
      assertEquals(1, out.size(), "clusters across north (0/360) must merge");
   }

   @Test
   void staggerKeepsMembersIndependent() {
      List<SoundPrintCluster> out = SoundPrintAggregator.aggregate(
         List.of(
            new SoundPrintAggregator.Hit(90.0F, 0.6F, SoundPrintType.FOOTSTEP, 0.0F, 5.0F),
            new SoundPrintAggregator.Hit(91.0F, 0.5F, SoundPrintType.FOOTSTEP, 0.0F, 5.0F)
         ),
         true,
         6.0F,
         3.0F
      );
      assertEquals(1, out.size());
      assertEquals(2, out.get(0).members().size());
      assertTrue(out.get(0).stagger());
      // members fan out around the centre with stagger spacing
      float off0 = out.get(0).members().get(0).staggerOffset();
      float off1 = out.get(0).members().get(1).staggerOffset();
      assertTrue(Math.abs(off0 - off1) > 0.0F);
   }
}
