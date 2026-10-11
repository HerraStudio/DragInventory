package dev.draginventory.client.soundprint;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Guards the language files: en/zh key symmetry and that every lang key referenced
 * by the soundprint settings GUI / commands exists in both files.
 */
class LangKeysTest {
   private static final Path LANG_DIR = Path.of("src/main/resources/assets/draginventory/lang");
   private static final Pattern KEY_PATTERN = Pattern.compile("\"(draginventory\\.soundprint\\.[a-z0-9_.]+)\"");

   private static Set<String> keys(String file) throws IOException {
      String text = Files.readString(LANG_DIR.resolve(file));
      Matcher m = KEY_PATTERN.matcher(text);
      Set<String> out = new HashSet<>();
      while (m.find()) {
         String key = m.group(1);
         if (!key.endsWith(".")) {
            out.add(key);
         }
      }
      return out;
   }

   @Test
   void enAndZhShareTheSameKeySet() throws IOException {
      Set<String> en = keys("en_us.json");
      Set<String> zh = keys("zh_cn.json");
      Set<String> onlyEn = new HashSet<>(en);
      onlyEn.removeAll(zh);
      Set<String> onlyZh = new HashSet<>(zh);
      onlyZh.removeAll(en);
      assertTrue(onlyEn.isEmpty(), "keys missing in zh_cn.json: " + onlyEn);
      assertTrue(onlyZh.isEmpty(), "keys missing in en_us.json: " + onlyZh);
   }

   @Test
   void settingsScreenLangKeysAllExist() throws IOException {
      Set<String> en = keys("en_us.json");
      List<String> missing = new ArrayList<>();
      Path screen = Path.of("src/main/java/dev/draginventory/client/soundprint/SoundPrintSettingsScreen.java");
      Matcher m = KEY_PATTERN.matcher(Files.readString(screen));
      while (m.find()) {
         String key = m.group(1);
         if (!key.endsWith(".") && !en.contains(key)) {
            missing.add(key);
         }
      }
      assertTrue(missing.isEmpty(), "lang keys referenced by the GUI but absent from en_us.json: " + missing);
   }

   @Test
   void commandLangKeysAllExist() throws IOException {
      Set<String> en = keys("en_us.json");
      List<String> missing = new ArrayList<>();
      Path commands = Path.of("src/main/java/dev/draginventory/client/soundprint/SoundPrintCommands.java");
      Matcher m = KEY_PATTERN.matcher(Files.readString(commands));
      while (m.find()) {
         String key = m.group(1);
         if (!key.endsWith(".") && !en.contains(key)) {
            missing.add(key);
         }
      }
      assertTrue(missing.isEmpty(), "lang keys referenced by commands but absent from en_us.json: " + missing);
   }
}
