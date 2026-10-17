package dev.jasper.app.presentation;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * A font file a kit names: its jar path (relative to the kit's folder), its bytes, its full name (name ID 4, by which a
 * registered static face resolves) and its typographic family (name ID 16, else ID 1), read from the name table without
 * AWT. A file whose name table cannot be read has empty names. Compared by content.
 *
 * @param path the path as the kit file names it
 * @param bytes the file's bytes
 * @param fullName the Windows English full name, or empty
 * @param family the Windows English typographic family, or empty
 */
public record FontFile(String path, byte[] bytes, String fullName, String family) {
    private static final int TRUE_TYPE = 0x00010000, OPEN_TYPE = 0x4F54544F, APPLE = 0x74727565, NAME = 0x6E616D65;

    /** Copies the bytes. */
    public FontFile {
        Objects.requireNonNull(path, "path"); Objects.requireNonNull(fullName, "fullName"); Objects.requireNonNull(family, "family");
        bytes = bytes.clone();
    }

    /** A copy of the bytes. */
    @Override public byte[] bytes() { return bytes.clone(); }

    /** Whether the name table gave a full name. */
    public boolean named() { return !fullName.isEmpty(); }

    /** {@code bytes} as the font at {@code path}; never throws. */
    public static FontFile read(String path, byte[] bytes) {
        try {
            var data = ByteBuffer.wrap(bytes);
            int version = data.getInt(0);
            if (version != TRUE_TYPE && version != OPEN_TYPE && version != APPLE) return new FontFile(path, bytes, "", "");
            int tables = Short.toUnsignedInt(data.getShort(4));
            for (int i = 0; i < tables; i++) {
                int record = 12 + 16 * i;
                if (data.getInt(record) != NAME) continue;
                int offset = data.getInt(record + 8);
                int count = Short.toUnsignedInt(data.getShort(offset + 2));
                int strings = offset + Short.toUnsignedInt(data.getShort(offset + 4));
                String family = "", full = "", typographic = "";
                for (int n = 0; n < count; n++) {
                    int at = offset + 6 + 12 * n;
                    int platform = Short.toUnsignedInt(data.getShort(at)), encoding = Short.toUnsignedInt(data.getShort(at + 2));
                    int language = Short.toUnsignedInt(data.getShort(at + 4)), id = Short.toUnsignedInt(data.getShort(at + 6));
                    int length = Short.toUnsignedInt(data.getShort(at + 8)), start = strings + Short.toUnsignedInt(data.getShort(at + 10));
                    if (platform != 3 || encoding != 1 || language != 0x409) continue;
                    if (start + length > bytes.length) throw new IndexOutOfBoundsException(start + length);
                    String value = new String(bytes, start, length, StandardCharsets.UTF_16BE);
                    switch (id) {
                        case 1 -> family = value;
                        case 4 -> full = value;
                        case 16 -> typographic = value;
                        default -> { }
                    }
                }
                return new FontFile(path, bytes, full, typographic.isEmpty() ? family : typographic);
            }
        } catch (IndexOutOfBoundsException malformed) {
            // Falls through: an unreadable name table names nothing.
        }
        return new FontFile(path, bytes, "", "");
    }

    @Override public boolean equals(Object other) {
        return other instanceof FontFile file && path.equals(file.path) && Arrays.equals(bytes, file.bytes)
            && fullName.equals(file.fullName) && family.equals(file.family);
    }

    @Override public int hashCode() { return Objects.hash(path, Arrays.hashCode(bytes), fullName, family); }

    @Override public String toString() { return "FontFile[" + path + ", " + bytes.length + " bytes, " + fullName + ", " + family + "]"; }
}
