package dev.jasper.app.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 5.6 and fact 19: faces are named by a file's full name and checked by its typographic family, without AWT. */
class FontFileTest {
    /** A TrueType file holding only a name table with these Windows English records (name ID to text). */
    static byte[] synthetic(Map<Integer, String> names) {
        try {
            var strings = new ByteArrayOutputStream();
            var records = new ByteArrayOutputStream();
            var record = new DataOutputStream(records);
            for (var name : names.entrySet()) {
                byte[] text = name.getValue().getBytes(StandardCharsets.UTF_16BE);
                record.writeShort(3); record.writeShort(1); record.writeShort(0x409); record.writeShort(name.getKey());
                record.writeShort(text.length); record.writeShort(strings.size());
                strings.write(text);
            }
            var table = new ByteArrayOutputStream();
            var head = new DataOutputStream(table);
            head.writeShort(0); head.writeShort(names.size()); head.writeShort(6 + 12 * names.size());
            head.write(records.toByteArray()); head.write(strings.toByteArray());
            var file = new ByteArrayOutputStream();
            var out = new DataOutputStream(file);
            out.writeInt(0x00010000); out.writeShort(1); out.writeShort(16); out.writeShort(0); out.writeShort(0);
            out.writeInt(0x6E616D65); out.writeInt(0); out.writeInt(12 + 16); out.writeInt(table.size());
            out.write(table.toByteArray());
            return file.toByteArray();
        } catch (IOException impossible) { throw new java.io.UncheckedIOException(impossible); }
    }

    /** A face as IBM Plex's static files name theirs: full name and legacy family per weight, one typographic family. */
    static byte[] face(String fullName, String typographicFamily) {
        var names = new LinkedHashMap<Integer, String>();
        names.put(1, fullName); names.put(4, fullName);
        if (!fullName.equals(typographicFamily)) names.put(16, typographicFamily);
        return synthetic(names);
    }

    @Test void theFullNameNamesTheFaceAndTheTypographicFamilyIsTheFamily() {
        var medium = FontFile.read("fonts/plex-medium.ttf", face("IBM Plex Sans Medm", "IBM Plex Sans"));
        assertThat(medium.fullName()).isEqualTo("IBM Plex Sans Medm");
        assertThat(medium.family()).as("name ID 16 over the legacy family").isEqualTo("IBM Plex Sans");
        assertThat(medium.named()).isTrue();
        var regular = FontFile.read("fonts/plex-regular.ttf", face("IBM Plex Sans", "IBM Plex Sans"));
        assertThat(regular.family()).as("without ID 16, the family is ID 1").isEqualTo("IBM Plex Sans");
    }

    @Test void aFileWithoutAReadableNameTableNamesNothing() {
        for (byte[] bytes : new byte[][] {new byte[0], new byte[] {0, 1, 0, 0, 0, 9}, "not a font at all".getBytes(StandardCharsets.UTF_8),
                java.util.Arrays.copyOf(face("Truncated", "Truncated"), 40)}) {
            var file = FontFile.read("fonts/x.ttf", bytes);
            assertThat(file.named()).isFalse();
            assertThat(file.fullName()).isEmpty();
            assertThat(file.family()).isEmpty();
        }
    }

    @Test void aTableOfManyRecordsDecodesEachNameOnce() {
        // 65,535 full-name records that all share one 64 KiB string, which overlaps the records themselves.
        int count = 0xFFFF, length = 0xFFFE;
        var table = java.nio.ByteBuffer.allocate(6 + 12 * count + length);
        table.putShort((short) 0).putShort((short) count).putShort((short) 6);
        for (int n = 0; n < count; n++)
            table.putShort((short) 3).putShort((short) 1).putShort((short) 0x409).putShort((short) 4).putShort((short) length).putShort((short) 0);
        var file = java.nio.ByteBuffer.allocate(28 + table.capacity());
        file.putInt(0x00010000).putShort((short) 1).putShort((short) 16).putShort((short) 0).putShort((short) 0)
            .putInt(0x6E616D65).putInt(0).putInt(28).putInt(table.capacity()).put(table.array());
        long began = System.nanoTime();
        var read = FontFile.read("fonts/crafted.ttf", file.array());
        assertThat(java.time.Duration.ofNanos(System.nanoTime() - began)).as("one decode, not 65,535").isLessThan(java.time.Duration.ofMillis(500));
        assertThat(read.named()).isTrue();
        assertThat(read.family()).isEmpty();
        var named = FontFile.read("fonts/a.ttf", synthetic(new LinkedHashMap<>(Map.of(4, "First"))));
        assertThat(named.fullName()).isEqualTo("First");
    }

    @Test void filesCompareByContentAndCopyTheirBytes() {
        byte[] bytes = face("A", "A");
        var one = FontFile.read("fonts/a.ttf", bytes);
        bytes[0] = 9;
        assertThat(one).isEqualTo(FontFile.read("fonts/a.ttf", face("A", "A")));
        one.bytes()[0] = 9;
        assertThat(one.bytes()[0]).isZero();
        assertThat(one.toString()).doesNotContain("[B@");
    }
}
