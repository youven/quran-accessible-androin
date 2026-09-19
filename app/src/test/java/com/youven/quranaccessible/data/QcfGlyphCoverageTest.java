package com.youven.quranaccessible.data;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import static org.junit.Assert.*;

public class QcfGlyphCoverageTest {
    // Models the documented hasGlyph contract for independent (non-ligature) glyphs.
    private final Predicate<String> singleGlyph = text ->
        text.codePointCount(0, text.length()) == 1;

    @Test public void checksBothGlyphsOfActualPageTwoToken() {
        String token = "\uFC46\uFC47";
        List<String> checked = new ArrayList<>();
        assertFalse(singleGlyph.test(token)); // Old call falsely rejected this valid token.
        assertTrue(QcfGlyphCoverage.supports(token, value -> {
            checked.add(value);
            return singleGlyph.test(value);
        }));
        assertEquals(List.of("\uFC46", "\uFC47"), checked);
        assertEquals("\uFC46\uFC47", token);
    }

    @Test public void stillRejectsAMissingGlyph() {
        assertFalse(QcfGlyphCoverage.supports("\uFC46\uFC47", "\uFC46"::equals));
    }

    @Test public void rejectsEmptyTokens() {
        assertFalse(QcfGlyphCoverage.supports("", singleGlyph));
    }

    @Test public void doesNotSplitSurrogatePairs() {
        List<String> checked = new ArrayList<>();
        assertTrue(QcfGlyphCoverage.supports("\uD83D\uDE00\uFC41", value -> {
            checked.add(value);
            return singleGlyph.test(value);
        }));
        assertEquals(List.of("\uD83D\uDE00", "\uFC41"), checked);
    }

    @Test public void providerFixturesExposeTheRegressionOnPageTwoNotPageOne() throws Exception {
        int pageTwoMultiGlyphs = 0;
        for (int page : new int[] {1, 2, 50, 187, 604}) {
            try (InputStream stream = getClass().getResourceAsStream("/page" + page + ".json")) {
                assertNotNull(stream);
                JSONObject response = new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                JSONArray verses = response.getJSONArray("verses");
                for (int i = 0; i < verses.length(); i++) {
                    JSONArray words = verses.getJSONObject(i).getJSONArray("words");
                    for (int j = 0; j < words.length(); j++) {
                        String token = words.getJSONObject(j).getString("code_v2");
                        assertTrue(QcfGlyphCoverage.supports(token, singleGlyph));
                        if (page == 1) assertTrue(singleGlyph.test(token));
                        if (page == 2 && !singleGlyph.test(token)) pageTwoMultiGlyphs++;
                    }
                }
            }
        }
        assertEquals(3, pageTwoMultiGlyphs);
    }
}
