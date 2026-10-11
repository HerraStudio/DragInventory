package dev.draginventory.client.soundprint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SoundClassifier {
   private SoundClassifier() {
   }

   public static SoundPrintType classify(String soundId, List<String> footstepKeywords, List<String> gunshotKeywords) {
      if (soundId != null && !soundId.isEmpty()) {
         String id = soundId.toLowerCase(Locale.ROOT);

         for (String keyword : footstepKeywords) {
            if (keyword != null && !keyword.isEmpty() && id.contains(keyword)) {
               return SoundPrintType.FOOTSTEP;
            }
         }

         for (String keywordx : gunshotKeywords) {
            if (keywordx != null && !keywordx.isEmpty() && id.contains(keywordx)) {
               return SoundPrintType.GUNSHOT;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public static List<String> parseKeywords(String csv) {
      if (csv != null && !csv.isBlank()) {
         String lower = csv.toLowerCase(Locale.ROOT);
         ArrayList<String> out = new ArrayList<>();

         for (String part : lower.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
               out.add(trimmed);
            }
         }

         return out;
      } else {
         return List.of();
      }
   }

   public static boolean mayRelate(String soundId, List<String> footstepKeywords, List<String> gunshotKeywords) {
      return soundId != null && !soundId.isEmpty() ? classify(soundId, footstepKeywords, gunshotKeywords) != null : false;
   }
}
