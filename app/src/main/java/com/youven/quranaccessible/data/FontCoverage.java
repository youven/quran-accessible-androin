package com.youven.quranaccessible.data;

import java.io.IOException;
import java.util.BitSet;

/** Reads the downloaded font's Unicode cmap, without Android shaping or fallback fonts. */
public final class FontCoverage {
    private final BitSet characters;
    private FontCoverage(BitSet characters) { this.characters = characters; }

    public boolean supports(String token) {
        // The API separates a rub-el-hizb ornament and its word with U+0020.
        // QCF page fonts need not map this layout separator. Keep the original
        // token for rendering, but require every visible code point in this font.
        return token.codePoints().anyMatch(cp -> cp != 0x20)
            && token.codePoints().allMatch(cp -> cp == 0x20 || characters.get(cp));
    }

    public static FontCoverage read(byte[] bytes) throws IOException {
        Data data = new Data(bytes, 0, bytes.length);
        long signature = data.u32(0);
        if (signature != 0x00010000L && signature != 0x4F54544FL) throw invalid();
        int tables = data.u16(4);
        data.check(12, tables * 16);
        Data cmap = null;
        int glyphCount = 0;
        for (int i = 0; i < tables; i++) {
            int record = 12 + i * 16;
            long tag = data.u32(record);
            Data table = data.slice(data.u32(record + 8), data.u32(record + 12));
            if (tag == 0x636D6170L) cmap = table;
            if (tag == 0x6D617870L) glyphCount = table.u16(4);
        }
        if (cmap == null || glyphCount == 0 || cmap.u16(0) != 0) throw invalid();
        int count = cmap.u16(2);
        cmap.check(4, count * 8);
        Data selected = null;
        int selectedScore = -1;
        for (int i = 0; i < count; i++) {
            int record = 4 + 8 * i;
            int platform = cmap.u16(record), encoding = cmap.u16(record + 2);
            if (!(platform == 0 || (platform == 3 && (encoding == 1 || encoding == 10)))) continue;
            long offset = cmap.u32(record + 4);
            Data sub = cmap.slice(offset, cmap.length - offset);
            int format = sub.u16(0);
            if (format != 4 && format != 12) continue;
            int score = (format == 12 ? 2 : 0) + (platform == 3 ? 1 : 0);
            if (score > selectedScore) {
                selected = sub.slice(0, format == 4 ? sub.u16(2) : sub.u32(4));
                selectedScore = score;
            }
        }
        if (selected == null) throw invalid();
        BitSet chars = new BitSet();
        if (selected.u16(0) == 4) read4(selected, glyphCount, chars);
        else read12(selected, glyphCount, chars);
        return new FontCoverage(chars);
    }

    /**
     * Neutralizes the optional 'hdmx' table in TrueType fonts by changing its tag to 'xdmx'.
     *
     * Some QCF V2 fonts contain a buggy 'hdmx' table where zero-width combining waqf marks
     * have device advance widths of 255 at certain pixel sizes (e.g. 32px). This causes
     * Android's FreeType rasterizer and HarfBuzz shaper to inflate line measurements and
     * shift base words off-screen during RTL drawing. Disabling the 'hdmx' table causes the
     * rasterizer to use the accurate, linearly-scaled 'hmtx' metrics instead.
     */
    public static byte[] sanitize(byte[] bytes) {
        if (bytes == null || bytes.length < 12) return bytes;
        int b0 = bytes[0] & 0xFF, b1 = bytes[1] & 0xFF, b2 = bytes[2] & 0xFF, b3 = bytes[3] & 0xFF;
        long signature = ((long) b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
        if (signature != 0x00010000L && signature != 0x4F54544FL && signature != 0x74727565L) return bytes;
        int tables = ((bytes[4] & 0xFF) << 8) | (bytes[5] & 0xFF);
        if (tables <= 0 || 12 + tables * 16 > bytes.length) return bytes;
        for (int i = 0; i < tables; i++) {
            int record = 12 + i * 16;
            if (bytes[record] == 'h' && bytes[record + 1] == 'd' && bytes[record + 2] == 'm' && bytes[record + 3] == 'x') {
                byte[] copy = bytes.clone();
                copy[record] = 'x';
                return copy;
            }
        }
        return bytes;
    }

    private static void read4(Data table, int glyphCount, BitSet chars) throws IOException {
        int doubled = table.u16(6);
        if (doubled == 0 || doubled % 2 != 0) throw invalid();
        int count = doubled / 2;
        table.check(0, 16 + 8 * count);
        int previous = -1;
        for (int i = 0; i < count; i++) {
            int end = table.u16(14 + 2 * i);
            int start = table.u16(16 + 2 * count + 2 * i);
            int delta = table.u16(16 + 4 * count + 2 * i);
            int rangeAddress = 16 + 6 * count + 2 * i;
            int range = table.u16(rangeAddress);
            if (start > end || start <= previous || range % 2 != 0) throw invalid();
            previous = end;
            for (int cp = start; cp <= end; cp++) {
                int glyph;
                if (range == 0) glyph = (cp + delta) & 0xFFFF;
                else {
                    int address = rangeAddress + range + 2 * (cp - start);
                    if (address < 16 + 8 * count) throw invalid();
                    glyph = table.u16(address);
                    if (glyph != 0) glyph = (glyph + delta) & 0xFFFF;
                }
                if (glyph >= glyphCount) throw invalid();
                if (glyph != 0) chars.set(cp);
            }
        }
    }

    private static void read12(Data table, int glyphCount, BitSet chars) throws IOException {
        long count = table.u32(12);
        if (count > (table.length - 16) / 12) throw invalid();
        long previous = -1;
        for (int i = 0; i < count; i++) {
            int group = 16 + 12 * i;
            long start = table.u32(group), end = table.u32(group + 4);
            long glyph = table.u32(group + 8);
            if (start > end || start <= previous || end > 0x10FFFFL ||
                glyph + end - start >= glyphCount) throw invalid();
            previous = end;
            for (int cp = (int) start; cp <= end; cp++, glyph++) {
                if (glyph != 0) chars.set(cp);
            }
        }
    }

    private static IOException invalid() { return new IOException("Invalid or unsupported font cmap"); }

    private static final class Data {
        final byte[] bytes;
        final int base, length;
        Data(byte[] bytes, int base, int length) { this.bytes = bytes; this.base = base; this.length = length; }
        void check(int offset, int size) throws IOException {
            if (offset < 0 || size < 0 || offset > length - size) throw invalid();
        }
        int u16(int offset) throws IOException {
            check(offset, 2);
            return ((bytes[base + offset] & 255) << 8) | (bytes[base + offset + 1] & 255);
        }
        long u32(int offset) throws IOException { return ((long) u16(offset) << 16) | u16(offset + 2); }
        Data slice(long offset, long size) throws IOException {
            if (offset < 0 || size < 0 || offset > length || size > length - offset) throw invalid();
            return new Data(bytes, base + (int) offset, (int) size);
        }
    }
}
