package dev.jasper.app.vmoptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class VmOptionsSyntaxTest {
    private static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot", "..")).toAbsolutePath().normalize();

    private static VmOptionsSyntax.Parsed parse(String text) { return VmOptionsSyntax.parse(text.getBytes(StandardCharsets.UTF_8)); }

    /** Parses raw bytes given as ints (chars widen to int; an invocation never narrows constants to byte). */
    private static VmOptionsSyntax.Parsed parseBytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) bytes[i] = (byte) values[i];
        return VmOptionsSyntax.parse(bytes);
    }

    @Test void linesAreTrimmedAndCommentsAndBlankLinesSkipped() {
        var parsed = parse("# heap\r\n\r\n  -Xmx1g  \r\n\t-Dfoo=a b\t\n#-Xmx2g\n-XX:+UseZGC");
        assertThat(parsed.lines()).containsExactly("-Xmx1g", "-Dfoo=a b", "-XX:+UseZGC");
        assertThat(parsed.ignored()).isEmpty();
        assertThat(parsed.corrupted()).isFalse();
        assertThat(parse("-Dcolor=#fff\n").lines()).containsExactly("-Dcolor=#fff");
        assertThat(parse("-Dv=a\u000B\n").lines()).as("vertical tab is trimmed like the core").containsExactly("-Dv=a");
        assertThat(parse("").lines()).isEmpty();
        assertThat(parse("# only\n\n   \n").lines()).isEmpty();
    }

    @Test void aByteOrderMarkIsSkippedAndUtf8TextKept() {
        assertThat(parseBytes(0xEF, 0xBB, 0xBF, '-', 'X', 'm', 'x', '1', 'g', '\n').lines()).containsExactly("-Xmx1g");
        assertThat(parse("-Dname=Jürgen\n").lines()).containsExactly("-Dname=Jürgen");
    }

    @Test void nulOrInvalidUtf8CorruptsTheWholeFile() {
        assertThat(parseBytes('-', 'X', 'm', 'x', '1', 'g', '\n', 0, '-', 'X', 'm', 's', '\n')).isEqualTo(new VmOptionsSyntax.Parsed(List.of(), List.of(), true));
        assertThat(parseBytes('-', 'D', '=', 0xC3, 0x28).corrupted()).as("invalid continuation").isTrue();
        assertThat(parseBytes('-', 'D', '=', 0xC0, 0xAF).corrupted()).as("overlong").isTrue();
        assertThat(parseBytes('-', 'D', '=', 0xED, 0xA0, 0x80).corrupted()).as("surrogate").isTrue();
        assertThat(parseBytes('-', 'D', '=', 0xE2, 0x82).corrupted()).as("truncated").isTrue();
    }

    @Test void onlyOptionLinesApply() {
        var parsed = parse("Xmx300m\n\"-Dfoo=a b\"\n// heap\n-version\n--add-opens\n-Xmx1g\n"
            + "--add-opens=java.base/java.lang=ALL-UNNAMED\n--class-path=/x\n-Xshare:auto\n");
        assertThat(parsed.lines()).containsExactly("-Xmx1g", "--add-opens=java.base/java.lang=ALL-UNNAMED", "-Xshare:auto");
        assertThat(parsed.ignored()).containsExactly("Xmx300m", "\"-Dfoo=a b\"", "// heap", "-version", "--add-opens", "--class-path=/x");
        assertThat(VmOptionsSyntax.isApplicable("-X")).isFalse();
        assertThat(VmOptionsSyntax.isApplicable("-XshowSettings")).isTrue();
        assertThat(VmOptionsSyntax.isApplicable("-cp")).isFalse();
        assertThat(VmOptionsSyntax.isApplicable("--module-path=/x")).isTrue();
        assertThat(VmOptionsSyntax.isApplicable("--module-path")).isFalse();
        assertThat(VmOptionsSyntax.isApplicable("--enable-native-access=ALL-UNNAMED")).isTrue();
        assertThat(VmOptionsSyntax.isApplicable("")).isFalse();
        assertThat(VmOptionsSyntax.isApplicable("-")).isTrue();
    }

    @Test void mergeAppendsAndAppliesIntelliJsFilters() {
        assertThat(VmOptionsSyntax.merge(List.of("-a", "-b"), List.of("-c"))).containsExactly("-a", "-b", "-c");
        assertThat(VmOptionsSyntax.merge(List.of("-XX:+UseG1GC", "-Xmx2048m"), List.of("-XX:+UseZGC"))).containsExactly("-Xmx2048m", "-XX:+UseZGC");
        assertThat(VmOptionsSyntax.merge(List.of("-XX:+UseG1GC"), List.of("-XX:-UseG1GC"))).containsExactly("-XX:+UseG1GC", "-XX:-UseG1GC");
        assertThat(VmOptionsSyntax.merge(List.of("-Xmx2048m", "-Xms128m"), List.of("-XX:MaxRAMPercentage=50"))).containsExactly("-Xms128m", "-XX:MaxRAMPercentage=50");
        assertThat(VmOptionsSyntax.merge(List.of("-Xmx2048m"), List.of("-XX:MinRAMPercentage=5"))).containsExactly("-XX:MinRAMPercentage=5");
        assertThat(VmOptionsSyntax.merge(List.of("-Xmx2048m", "-Xms128m"), List.of("-XX:InitialRAMPercentage=5"))).containsExactly("-Xmx2048m", "-XX:InitialRAMPercentage=5");
        assertThat(VmOptionsSyntax.merge(List.of("-Xmx2048m"), List.of("-Xmx4g"))).containsExactly("-Xmx2048m", "-Xmx4g");
    }

    @Test void digestsMatchTheLaunchersVectors() throws IOException {
        List<String> lines = new ArrayList<>();
        int vectors = 0;
        for (String line : Files.readAllLines(ROOT.resolve("packaging/launcher/test/digest-vectors.txt"), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("= ")) {
                String joined = lines.stream().map(entry -> entry + "\n").reduce("", String::concat);
                assertThat(VmOptionsSyntax.digest(parse(joined).lines())).as("digest of %s", lines).isEqualTo(line.substring(2));
                lines.clear();
                vectors++;
            } else {
                lines.add(line);
            }
        }
        assertThat(vectors).isGreaterThanOrEqualTo(6);
    }

    @Test void heapSizesAreReadInMiB() {
        assertThat(VmOptionsSyntax.maxHeapMiB("-Xmx2048m")).hasValue(2048);
        assertThat(VmOptionsSyntax.maxHeapMiB("-Xmx4g")).hasValue(4096);
        assertThat(VmOptionsSyntax.maxHeapMiB("-Xmx4G")).hasValue(4096);
        assertThat(VmOptionsSyntax.maxHeapMiB("-Xmx1t")).hasValue(1_048_576);
        assertThat(VmOptionsSyntax.maxHeapMiB("-Xmx1048576k")).hasValue(1024);
        assertThat(VmOptionsSyntax.maxHeapMiB("-Xmx2147483648")).hasValue(2048);
        assertThat(VmOptionsSyntax.maxHeapMiB("-Xmx3q")).isEmpty();
        assertThat(VmOptionsSyntax.maxHeapMiB("-Xmx")).isEmpty();
        assertThat(VmOptionsSyntax.maxHeapMiB("-Xms1g")).isEmpty();
        assertThat(VmOptionsSyntax.maxHeapMiB("-Xmx99999999999999t")).as("overflow").isEmpty();
        assertThat(VmOptionsSyntax.lastMaxHeapMiB(List.of("-Xmx1g", "-Dx=1", "-Xmx3q", "-Xmx2g"))).hasValue(2048);
        assertThat(VmOptionsSyntax.lastMaxHeapMiB(List.of("-Dx=1"))).isEqualTo(OptionalLong.empty());
    }
}
