package dev.draginventory.client.soundprint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SoundPrintAggregator {
   static final int MAX_CLUSTERS = 16;

   private SoundPrintAggregator() {
   }

   public static List<SoundPrintCluster> aggregate(List<SoundPrintAggregator.Hit> hits, boolean stagger, float mergeWindowDeg, float staggerSpacing) {
      List<SoundPrintCluster> out = new ArrayList<>();
      if (hits.isEmpty()) {
         return out;
      } else {
         List<SoundPrintAggregator.Hit> sorted = new ArrayList<>(hits);
         sorted.sort(Comparator.comparingDouble(SoundPrintAggregator.Hit::bearing));
         List<List<SoundPrintAggregator.Hit>> groups = new ArrayList<>();
         List<SoundPrintAggregator.Hit> current = new ArrayList<>();
         current.add(sorted.get(0));

         for (int i = 1; i < sorted.size(); i++) {
            SoundPrintAggregator.Hit hit = sorted.get(i);
            if (hit.bearing() - current.get(current.size() - 1).bearing() <= mergeWindowDeg) {
               current.add(hit);
            } else {
               groups.add(current);
               current = new ArrayList<>();
               current.add(hit);
            }
         }

         groups.add(current);
         if (groups.size() > 1) {
            List<SoundPrintAggregator.Hit> first = groups.get(0);
            List<SoundPrintAggregator.Hit> last = groups.get(groups.size() - 1);
            float wrapGap = 360.0F - last.get(last.size() - 1).bearing() + first.get(0).bearing();
            if (wrapGap <= mergeWindowDeg) {
               last.addAll(first);
               groups.remove(0);
            }
         }

         for (List<SoundPrintAggregator.Hit> group : groups) {
            out.add(stagger ? buildStagger(group, staggerSpacing) : buildMerged(group));
         }

         if (out.size() > 16) {
            out.sort(Comparator.<SoundPrintCluster>comparingDouble(c -> (double)c.energy()).reversed());
            out = new ArrayList<>(out.subList(0, 16));
         }

         return out;
      }
   }

   private static SoundPrintCluster buildMerged(List<SoundPrintAggregator.Hit> group) {
      int n = group.size();
      float[] bearings = new float[n];
      float[] energies = new float[n];
      float energy = 0.0F;
      float verticalWeighted = 0.0F;
      float energySum = 0.0F;
      SoundPrintType type = SoundPrintType.FOOTSTEP;
      float nearest = Float.MAX_VALUE;

      for (int i = 0; i < n; i++) {
         SoundPrintAggregator.Hit hit = group.get(i);
         bearings[i] = hit.bearing();
         energies[i] = hit.energy();
         energy = SoundPrintEnvelope.mergeEnergy(energy, hit.energy());
         verticalWeighted += hit.verticalOffset() * Math.max(0.05F, hit.energy());
         energySum += Math.max(0.05F, hit.energy());
         if (hit.type() == SoundPrintType.GUNSHOT) {
            type = SoundPrintType.GUNSHOT;
         }

         nearest = Math.min(nearest, hit.distance());
      }

      float bearing = SoundPrintEnvelope.weightedBearing(bearings, energies);
      float vertical = energySum > 0.0F ? verticalWeighted / energySum : 0.0F;
      return new SoundPrintCluster(bearing, energy, type, vertical, nearest, n, false, List.of());
   }

   private static SoundPrintCluster buildStagger(List<SoundPrintAggregator.Hit> group, float staggerSpacing) {
      List<SoundPrintAggregator.Hit> sorted = new ArrayList<>(group);
      sorted.sort(Comparator.<SoundPrintAggregator.Hit>comparingDouble(h -> (double)h.energy()).reversed());
      int n = sorted.size();
      float[] bearings = new float[n];
      float[] energies = new float[n];
      List<SoundPrintCluster.Member> members = new ArrayList<>(n);
      float maxEnergy = 0.0F;
      SoundPrintType type = SoundPrintType.FOOTSTEP;
      float nearest = Float.MAX_VALUE;

      for (int i = 0; i < n; i++) {
         SoundPrintAggregator.Hit hit = sorted.get(i);
         bearings[i] = hit.bearing();
         energies[i] = hit.energy();
         maxEnergy = Math.max(maxEnergy, hit.energy());
         if (hit.type() == SoundPrintType.GUNSHOT) {
            type = SoundPrintType.GUNSHOT;
         }

         nearest = Math.min(nearest, hit.distance());
         int slot = (i + 1) / 2 * (i % 2 == 0 ? -1 : 1);
         members.add(new SoundPrintCluster.Member(hit.bearing(), hit.energy(), hit.type(), hit.verticalOffset(), hit.distance(), (float)slot * staggerSpacing));
      }

      float bearing = SoundPrintEnvelope.weightedBearing(bearings, energies);
      return new SoundPrintCluster(bearing, maxEnergy, type, 0.0F, nearest, n, true, List.copyOf(members));
   }

   public static record Hit(float bearing, float energy, SoundPrintType type, float verticalOffset, float distance) {
   }
}
