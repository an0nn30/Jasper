package dev.jasper.app.vmoptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AotCacheRecordTest {
    private static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot", "..")).toAbsolutePath().normalize();
    private static final String CACHE = "/Applications/Jasper.app/Contents/app/jasper.aot";
    private static final String DIGEST = "c758526d21c369d0";
    private static final AotCacheRecord.CacheFile FILE = new AotCacheRecord.CacheFile(57_016_320, 1_791_966_245);

    @TempDir Path home;

    private static String text(String... lines) {
        var text = new StringBuilder();
        for (String line : lines) text.append(line).append('\n');
        return text.toString();
    }

    private static String valid(String options) {
        return text(AotCacheRecord.HEADER, "cache=" + CACHE, "size=57016320", "modified=1791966245", "options=" + options);
    }

    @Test void aRecordFormatsAsTheSpecSaysAndReadsBack() {
        var record = new AotCacheRecord(CACHE, 57_016_320, 1_791_966_245, Optional.of(DIGEST));
        assertThat(record.format()).isEqualTo(
            "# Jasper skips its start-up cache while this file matches it. Delete this file to try the cache again.\n"
                + "cache=/Applications/Jasper.app/Contents/app/jasper.aot\n"
                + "size=57016320\n"
                + "modified=1791966245\n"
                + "options=c758526d21c369d0\n");
        assertThat(AotCacheRecord.parse(record.format())).contains(record);
        var bundled = new AotCacheRecord(CACHE, 1, 2, Optional.empty());
        assertThat(bundled.format()).endsWith("\noptions=\n");
        assertThat(AotCacheRecord.parse(bundled.format())).contains(bundled);
    }

    @Test void commentsEmptyLinesAndCarriageReturnsAreSkippedAndOrderIsFree() {
        String crlf = "# note\r\n\r\noptions=\r\nmodified=1791966245\r\n#cache=/elsewhere\r\nsize=57016320\r\ncache=" + CACHE + "\r\n";
        assertThat(AotCacheRecord.parse(crlf)).contains(new AotCacheRecord(CACHE, 57_016_320, 1_791_966_245, Optional.empty()));
        assertThat(AotCacheRecord.parse(valid("").replace("cache=" + CACHE, "cache=/a\r\r")))
            .as("only one carriage return is removed").hasValueSatisfying(record -> assertThat(record.cache()).isEqualTo("/a\r"));
        assertThat(AotCacheRecord.parse(valid("").replace("cache=" + CACHE, "cache= /my app/x=y.aot")))
            .as("values are verbatim and split at the first =").hasValueSatisfying(record -> assertThat(record.cache()).isEqualTo(" /my app/x=y.aot"));
        assertThat(AotCacheRecord.parse(valid("").replace("size=57016320", "size=0057016320")))
            .as("leading zeros are digits").hasValueSatisfying(record -> assertThat(record.size()).isEqualTo(57_016_320));
        assertThat(AotCacheRecord.parse(valid("").replace("size=57016320", "size=9999999999999999999")))
            .as("19 digits always fit").hasValueSatisfying(record -> assertThat(Long.toUnsignedString(record.size())).isEqualTo("9999999999999999999"));
    }

    @Test void aMalformedRecordReadsAsNothing() {
        assertThat(AotCacheRecord.parse("")).isEmpty();
        assertThat(AotCacheRecord.parse(text(AotCacheRecord.HEADER))).isEmpty();
        assertThat(AotCacheRecord.parse(valid("").replace("options=\n", ""))).as("a missing key").isEmpty();
        assertThat(AotCacheRecord.parse(valid("") + "size=1\n")).as("a repeated key").isEmpty();
        assertThat(AotCacheRecord.parse(valid("") + "extra=1\n")).as("an unknown key").isEmpty();
        assertThat(AotCacheRecord.parse(valid("") + "no equals sign\n")).as("a line without =").isEmpty();
        assertThat(AotCacheRecord.parse(valid("") + "   \n")).as("spaces are not an empty line").isEmpty();
        assertThat(AotCacheRecord.parse(valid("").replace("cache=", " cache="))).as("keys are exact").isEmpty();
        assertThat(AotCacheRecord.parse(valid("").replace("cache=" + CACHE, "cache="))).as("an empty cache").isEmpty();
        assertThat(AotCacheRecord.parse(valid("").replace("size=57016320", "size="))).isEmpty();
        assertThat(AotCacheRecord.parse(valid("").replace("size=57016320", "size=57a"))).isEmpty();
        assertThat(AotCacheRecord.parse(valid("").replace("size=57016320", "size=+5"))).isEmpty();
        assertThat(AotCacheRecord.parse(valid("").replace("size=57016320", "size=-5"))).isEmpty();
        assertThat(AotCacheRecord.parse(valid("").replace("size=57016320", "size=12345678901234567890"))).as("20 digits").isEmpty();
        assertThat(AotCacheRecord.parse(valid("").replace("modified=1791966245", "modified=1.5"))).isEmpty();
        assertThat(AotCacheRecord.parse(valid("C758526D21C369D0"))).as("uppercase hex").isEmpty();
        assertThat(AotCacheRecord.parse(valid("c758526d21c369d"))).as("15 digits").isEmpty();
        assertThat(AotCacheRecord.parse(valid("c758526d21c369d0 "))).as("a trailing space").isEmpty();
        assertThat(AotCacheRecord.parse("\uFEFF" + valid(""))).as("a byte order mark is not a comment").isEmpty();
    }

    @Test void aRecordMatchesOnlyTheSameCacheFileAndOptions() {
        var bundled = new AotCacheRecord(CACHE, FILE.size(), FILE.modified(), Optional.empty());
        assertThat(bundled.matches(Optional.of(FILE), Optional.empty())).isTrue();
        assertThat(bundled.matches(Optional.of(FILE), Optional.of(DIGEST))).as("empty options match any start").isTrue();
        assertThat(bundled.matches(Optional.empty(), Optional.empty())).as("no file there").isFalse();
        assertThat(bundled.matches(Optional.of(new AotCacheRecord.CacheFile(FILE.size() + 1, FILE.modified())), Optional.empty()))
            .as("a new build: another size").isFalse();
        assertThat(bundled.matches(Optional.of(new AotCacheRecord.CacheFile(FILE.size(), FILE.modified() + 1)), Optional.empty()))
            .as("a new build: another time").isFalse();
        assertThat(bundled.matches(Optional.of(new AotCacheRecord.CacheFile(FILE.size(), -FILE.modified())), Optional.empty()))
            .as("a file before 1970 never matches").isFalse();
        var farFuture = new AotCacheRecord(CACHE, FILE.size(), Long.parseUnsignedLong("9999999999999999999"), Optional.empty());
        assertThat(farFuture.matches(Optional.of(new AotCacheRecord.CacheFile(FILE.size(), farFuture.modified())), Optional.empty()))
            .as("a negative time never matches, even as the same 64 bits").isFalse();
        var custom = new AotCacheRecord(CACHE, FILE.size(), FILE.modified(), Optional.of(DIGEST));
        assertThat(custom.matches(Optional.of(FILE), Optional.of(DIGEST))).isTrue();
        assertThat(custom.matches(Optional.of(FILE), Optional.of("0000000000000000"))).as("other options").isFalse();
        assertThat(custom.matches(Optional.of(FILE), Optional.empty())).as("a start without custom options").isFalse();
        assertThat(AotCacheRecord.matches(valid(""), Optional.of(FILE), Optional.empty())).isTrue();
        assertThat(AotCacheRecord.matches(valid("") + "junk\n", Optional.of(FILE), Optional.empty())).as("malformed").isFalse();
    }

    /**
     * The launcher's shared cases. A record line ending with the two characters backslash and r stands for that line
     * ending with a carriage return; the path at the end of an "actual" line is the expanded cache path, which matching
     * ignores.
     */
    @Test void theLaunchersVectorsAgree() throws IOException {
        String vectors = Files.readString(ROOT.resolve("packaging/launcher/test/aot-record-vectors.txt"), StandardCharsets.UTF_8);
        StringBuilder record = null;
        String actual = null;
        int matches = 0;
        int misses = 0;
        for (String line : vectors.split("\n", -1)) {
            if (line.startsWith("##")) continue;
            if (record == null) {
                if (line.isEmpty()) continue;
                assertThat(line).as("a vector starts with a line \"record\"").isEqualTo("record");
                record = new StringBuilder();
            } else if (actual == null) {
                if (line.startsWith("actual ")) actual = line;
                else record.append(line.endsWith("\\r") ? line.substring(0, line.length() - 2) + "\r" : line).append('\n');
            } else {
                if (line.isEmpty()) continue;
                assertThat(line).as("after %s", actual).isIn("= match", "= no match");
                String[] facts = actual.split(" ", 5);
                assertThat(facts).as("actual <size> <modified> <digest> <path>: %s", actual).hasSize(5);
                Optional<AotCacheRecord.CacheFile> file = facts[1].equals("absent") ? Optional.empty()
                    : Optional.of(new AotCacheRecord.CacheFile(number(facts[1]), number(facts[2])));
                Optional<String> digest = facts[3].equals("-") ? Optional.empty() : Optional.of(facts[3]);
                boolean expected = line.equals("= match");
                assertThat(AotCacheRecord.matches(record.toString(), file, digest)).as("record %s against %s", record, actual)
                    .isEqualTo(expected);
                if (expected) matches++;
                else misses++;
                record = null;
                actual = null;
            }
        }
        assertThat(record).as("the last vector is complete").isNull();
        assertThat(matches).as("vectors that match").isPositive();
        assertThat(misses).as("vectors that do not match").isPositive();
    }

    /** A vector's number: unsigned, or negative with a sign (a file modified before 1970). */
    private static long number(String value) { return value.startsWith("-") ? Long.parseLong(value) : Long.parseUnsignedLong(value); }

    @Test void theCachePathIsTheLastAotCacheOption() {
        assertThat(AotCacheRecord.cachePath(List.of("-Xmx2048m", "-XX:AOTCache=/a/jasper.aot", "-XX:AOTCache=/b.aot", "-Dx=1")))
            .contains("/b.aot");
        assertThat(AotCacheRecord.cachePath(List.of("-XX:AOTCache=/a.aot", "-XX:AOTCache="))).as("an empty value names nothing").contains("/a.aot");
        assertThat(AotCacheRecord.cachePath(List.of("-Xmx2048m"))).isEmpty();
    }

    @Test void captureReadsTheCacheFileAsTheLauncherSeesIt() throws IOException {
        Path cache = Files.writeString(home.resolve("jasper.aot"), "cache");
        Files.setLastModifiedTime(cache, FileTime.from(Instant.parse("2026-10-14T08:24:05.900Z")));
        var record = AotCacheRecord.capture(List.of("-Xmx2048m", "-XX:AOTCache=" + cache), Optional.of(DIGEST));
        assertThat(record).isEqualTo(new AotCacheRecord(cache.toString(), 5, 1_791_966_245, Optional.of(DIGEST)));
        assertThat(record.matches(AotCacheRecord.CacheFile.at(cache), Optional.of(DIGEST))).isTrue();
        Path moved = Files.copy(cache, Files.createDirectory(home.resolve("Moved.app")).resolve("jasper.aot"), StandardCopyOption.COPY_ATTRIBUTES);
        assertThat(record.matches(AotCacheRecord.CacheFile.at(moved), Optional.of(DIGEST))).as("the same build moved elsewhere").isTrue();
        assertThat(AotCacheRecord.CacheFile.at(home)).as("a directory").isEmpty();
        assertThat(AotCacheRecord.CacheFile.at(home.resolve("missing.aot"))).isEmpty();
        assertThatThrownBy(() -> AotCacheRecord.capture(List.of("-XX:AOTCache=" + home.resolve("missing.aot")), Optional.empty()))
            .isInstanceOf(NoSuchFileException.class);
        assertThatThrownBy(() -> AotCacheRecord.capture(List.of("-Xmx2048m"), Optional.empty())).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> AotCacheRecord.capture(List.of("-XX:AOTCache=/a.aot\r"), Optional.empty()))
            .as("a path that would not read back").isInstanceOf(IOException.class);
    }

    @Test void writingReplacesTheFileAtomicallyAndReadingFindsIt() throws IOException {
        Path file = home.resolve("jasper-aot.failed");
        var first = new AotCacheRecord(CACHE, 1, 2, Optional.empty());
        first.write(file);
        assertThat(Files.readString(file)).isEqualTo(first.format());
        var second = new AotCacheRecord("/Users/me/My Apps/J\u00e4sper.app/Contents/app/jasper.aot", 3, 4, Optional.of(DIGEST));
        second.write(file);
        assertThat(AotCacheRecord.read(file)).as("a space and a non-ASCII character").contains(second);
        assertThat(Files.readAllBytes(file)).as("UTF-8").containsSequence((byte) 'J', (byte) 0xC3, (byte) 0xA4, (byte) 's');
        try (Stream<Path> entries = Files.list(home)) {
            assertThat(entries.map(path -> path.getFileName().toString())).as("no temporary file").containsExactly("jasper-aot.failed");
        }
    }

    @Test void aMissingUnreadableOrNonUtf8FileReadsAsNothing() throws IOException {
        assertThat(AotCacheRecord.read(home.resolve("jasper-aot.failed"))).isEmpty();
        assertThat(AotCacheRecord.read(home)).as("a directory").isEmpty();
        Path invalid = home.resolve("invalid");
        byte[] bytes = valid("").replace("cache=" + CACHE, "cache=/a").getBytes(StandardCharsets.UTF_8);
        byte[] broken = new byte[bytes.length + 1];
        System.arraycopy(bytes, 0, broken, 0, bytes.length);
        broken[bytes.length] = (byte) 0xC3;
        Files.write(invalid, broken);
        assertThat(AotCacheRecord.read(invalid)).isEmpty();
    }

    @Test void onlyRecordsThatReadBackCanBeMade() {
        assertThatThrownBy(() -> new AotCacheRecord("", 1, 2, Optional.empty())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AotCacheRecord("/a\n/b", 1, 2, Optional.empty())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AotCacheRecord(CACHE, 1, 2, Optional.of("XYZ"))).isInstanceOf(IllegalArgumentException.class);
    }
}
