import com.youven.quranaccessible.data.FontCoverage;
import com.youven.quranaccessible.data.PageParser;
import com.youven.quranaccessible.data.PageRecovery;
import com.youven.quranaccessible.data.TextPage;
import com.youven.quranaccessible.data.QuranWord;
import java.nio.file.*;
import java.util.*;

/** Run on downloaded files with the same Java cmap validator used by Android. */
public final class FontAudit {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        int pages = 0, words = 0;
        for (int i = 1; i < args.length; i++) {
            int page = Integer.parseInt(args[i]);
            FontCoverage font = FontCoverage.read(Files.readAllBytes(directory.resolve("p" + page + ".ttf")));
            List<String> original = List.of(Files.readString(directory.resolve("page" + page + ".json")));
            TextPage parsed;
            try { parsed = PageParser.INSTANCE.parse(page, original); }
            catch (IllegalArgumentException incomplete) {
                Map<Integer, List<String>> chapters = new LinkedHashMap<>();
                for (int chapter : PageRecovery.INSTANCE.chapters(original)) {
                    chapters.put(chapter, List.of(Files.readString(directory.resolve("chapter" + chapter + ".json"))));
                }
                String restored = PageRecovery.INSTANCE.rebuild(page, original, chapters);
                parsed = PageParser.INSTANCE.parse(page, List.of(restored));
            }
            int checked = 0;
            for (QuranWord token : parsed.getWords()) {
                if (!font.supports(token.getGlyph())) throw new AssertionError("Missing symbol: page " + page + ", " + token.getVerse());
                checked++;
            }
            if (checked == 0) throw new AssertionError("Empty page " + page);
            words += checked;
            pages++;
            System.out.println("Page " + page + ": " + checked + " word/verse tokens covered");
        }
        System.out.println("PASS: " + pages + " pages, " + words + " tokens");
    }
}
