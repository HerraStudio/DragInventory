package dev.draginventory.client.soundprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class SoundClassifierTest {
   @Test
   void matchesFootstepKeywords() {
      assertEquals(
         SoundPrintType.FOOTSTEP,
         SoundClassifier.classify("minecraft:block.grass.step", List.of("step", "footstep"), List.of("gun"))
      );
   }

   @Test
   void matchesGunshotKeywordsAnywhereInId() {
      assertEquals(
         SoundPrintType.GUNSHOT,
         SoundClassifier.classify("gwo:m4_shoot_3d", List.of("step"), List.of("gun", "shoot"))
      );
   }

   @Test
   void unknownSoundsStayUnclassified() {
      assertNull(SoundClassifier.classify("minecraft:music.game", List.of("step"), List.of("gun")));
   }

   @Test
   void footstepKeywordWinsOverGunshot() {
      // footstep list is checked first
      assertEquals(
         SoundPrintType.FOOTSTEP,
         SoundClassifier.classify("mod:some_gun_step_sound", List.of("step"), List.of("gun"))
      );
   }

   @Test
   void keywordsParseFromCsvWithSpaces() {
      List<String> out = SoundClassifier.parseKeywords("Step, footstep ,, GUN");
      assertEquals(List.of("step", "footstep", "gun"), out);
   }

   @Test
   void emptyKeywordListMatchesNothing() {
      assertNull(SoundClassifier.classify("anything:at_all", List.of(), List.of()));
   }

   @Test
   void nullAndEmptyIdsAreSafe() {
      assertNull(SoundClassifier.classify(null, List.of("step"), List.of("gun")));
      assertNull(SoundClassifier.classify("", List.of("step"), List.of("gun")));
   }
}
