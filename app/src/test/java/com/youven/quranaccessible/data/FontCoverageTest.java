package com.youven.quranaccessible.data;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class FontCoverageTest {
    private byte[] font(byte[] subtable, boolean cff) {
        ByteBuffer b = ByteBuffer.allocate(62 + subtable.length);
        b.putInt(cff ? 0x4F54544F : 0x00010000).putShort((short) 2);
        b.position(12);
        b.putInt(0x6D617870).putInt(0).putInt(44).putInt(6);
        b.putInt(0x636D6170).putInt(0).putInt(50).putInt(12 + subtable.length);
        b.putInt(0x00010000).putShort((short) 8);
        b.putShort((short) 0).putShort((short) 1);
        b.putShort((short) 3).putShort((short) (subtable[1] == 12 ? 10 : 1)).putInt(12);
        b.put(subtable);
        return b.array();
    }

    private byte[] format4(boolean indexed) {
        ByteBuffer b = ByteBuffer.allocate(indexed ? 36 : 32);
        b.putShort((short) 4).putShort((short) b.capacity()).putShort((short) 0);
        b.putShort((short) 4).putShort((short) 4).putShort((short) 1).putShort((short) 0);
        b.putShort((short) 0xFC42).putShort((short) 0xFFFF).putShort((short) 0);
        b.putShort((short) 0xFC41).putShort((short) 0xFFFF);
        b.putShort((short) (indexed ? 1 : 1 - 0xFC41)).putShort((short) 1);
        b.putShort((short) (indexed ? 4 : 0)).putShort((short) 0);
        if (indexed) b.putShort((short) 1).putShort((short) 0);
        return b.array();
    }

    @Test public void supportsSeparateGlyphsAndRejectsMissingSymbols() throws Exception {
        FontCoverage f = FontCoverage.read(font(format4(false), false));
        assertTrue(f.supports("\uFC41\uFC42"));
        assertFalse(f.supports("\uFC41\uFC43"));
        assertFalse(f.supports(""));
        assertFalse(f.supports("\uFFFF")); // Sentinel maps to .notdef, not a valid glyph.
    }

    @Test public void indexedMappingsApplyDeltaButKeepNotdefMissing() throws Exception {
        FontCoverage f = FontCoverage.read(font(format4(true), false));
        assertTrue(f.supports("\uFC41"));
        assertFalse(f.supports("\uFC42"));
    }

    @Test public void acceptsOrnamentSeparatorWithoutAcceptingMissingVisibleGlyphs() throws Exception {
        FontCoverage f = FontCoverage.read(font(format4(false), false));
        assertTrue(f.supports("\uFC41 \uFC42"));
        assertFalse(f.supports("\uFC41 \uFC43"));
        assertFalse(f.supports(" "));
        assertFalse(f.supports("\uFC41\t\uFC42"));
    }

    @Test public void acceptsCffOpenTypeContainerWithUnicodeCmap() throws Exception {
        assertTrue(FontCoverage.read(font(format4(false), true)).supports("\uFC41"));
    }

    @Test public void supportsSupplementaryCodePointsWithFormat12() throws Exception {
        ByteBuffer b = ByteBuffer.allocate(40);
        b.putShort((short) 12).putShort((short) 0).putInt(40).putInt(0).putInt(2);
        b.putInt(0xFC41).putInt(0xFC42).putInt(1);
        b.putInt(0x1F600).putInt(0x1F601).putInt(3);
        FontCoverage f = FontCoverage.read(font(b.array(), false));
        assertTrue(f.supports("\uD83D\uDE00\uFC41"));
        assertFalse(f.supports("\uD83D\uDE02"));
    }

    @Test public void everyTruncatedPrefixIsRejected() throws Exception {
        byte[] valid = font(format4(true), false);
        for (int length = 0; length < valid.length; length++) {
            try {
                FontCoverage.read(Arrays.copyOf(valid, length));
                fail("Accepted truncated file of length " + length);
            } catch (IOException expected) { /* Fail closed on corrupt cache data. */ }
        }
    }

    @Test(expected = IOException.class) public void rejectsOutOfBoundsGlyphArray() throws Exception {
        byte[] sub = format4(true);
        ByteBuffer.wrap(sub).putShort(28, (short) 0xFFFE);
        FontCoverage.read(font(sub, false));
    }

    @Test(expected = IOException.class) public void rejectsOutOfRangeGlyphIndex() throws Exception {
        byte[] sub = format4(true);
        ByteBuffer.wrap(sub).putShort(32, (short) 100);
        FontCoverage.read(font(sub, false));
    }

    @Test public void sanitizeRenamesHdmxTableTagToXdmx() {
        ByteBuffer b = ByteBuffer.allocate(60);
        b.putInt(0x00010000).putShort((short) 2);
        b.position(12);
        b.putInt(0x6D617870).putInt(0).putInt(44).putInt(6); // maxp
        b.putInt(0x68646D78).putInt(0).putInt(50).putInt(10); // hdmx
        byte[] original = b.array();
        byte[] sanitized = FontCoverage.sanitize(original);
        assertNotSame(original, sanitized);
        assertEquals(0x78646D78, ByteBuffer.wrap(sanitized).getInt(28)); // xdmx
        assertEquals(0x6D617870, ByteBuffer.wrap(sanitized).getInt(12)); // maxp unchanged
    }

    @Test public void sanitizePreservesFontsWithoutHdmx() {
        byte[] valid = font(format4(false), false);
        assertSame(valid, FontCoverage.sanitize(valid));
    }

    @Test public void sanitizeIgnoresNonFontOrTruncatedData() {
        assertNull(FontCoverage.sanitize(null));
        byte[] shortBytes = new byte[]{1, 2, 3};
        assertSame(shortBytes, FontCoverage.sanitize(shortBytes));
        byte[] notFont = new byte[20];
        assertSame(notFont, FontCoverage.sanitize(notFont));
    }
}
