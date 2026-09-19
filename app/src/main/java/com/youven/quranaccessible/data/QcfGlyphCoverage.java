package com.youven.quranaccessible.data;

import java.util.function.Predicate;

/** Coverage check for QCF word tokens, which may contain several independent glyphs. */
public final class QcfGlyphCoverage {
    private QcfGlyphCoverage() {}

    public static boolean supports(String token, Predicate<String> hasSingleGlyph) {
        if (token.isEmpty()) return false;
        // Paint.hasGlyph(multiCharacterString) asks for ONE ligature, not text coverage.
        // Check code points separately; leave the original token unchanged for drawing.
        for (int offset = 0; offset < token.length();) {
            int codePoint = token.codePointAt(offset);
            if (!hasSingleGlyph.test(new String(Character.toChars(codePoint)))) return false;
            offset += Character.charCount(codePoint);
        }
        return true;
    }
}
