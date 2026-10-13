package dev.jasper.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Spec 3.1 and 11: no colour value lives in production code or resources outside slate-steel.json. */
class ColourLiteralScanTest {
    static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot"));
    static final Path THEME = ROOT.resolve("jasper-ui/src/main/resources/dev/jasper/ui/theme/slate-steel.json");
    static final Pattern STRING_HEX = Pattern.compile("\"[^\"\\n]*#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?(?![0-9A-Za-z])[^\"\\n]*\"");
    // A constructor or decode call, simple or fully qualified (new java.awt.Color(0x…)), given a hex literal.
    static final Pattern CONSTRUCTED = Pattern.compile("(?:\\bColor(?:UIResource)?|\\bColor\\.decode)\\(\\s*\"?(?:0x|#)[0-9A-Fa-f]{6}");
    static final Pattern RESOURCE_HEX = Pattern.compile("#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?(?![0-9A-Za-z])");
    static final Set<String> TEXT_RESOURCES = Set.of("json", "toml", "properties", "xml", "css", "html", "tsv");

    /** The constructed forms the scan matches, simple or fully qualified; decimal components are not hex literals. */
    @Test void theConstructedPatternMatchesQualifiedAndSimpleNames() {
        for (String line : List.of("g.setColor(new Color(0x112233));", "var c = new java.awt.Color(0xC45A53);",
                "defaults.put(key, new javax.swing.plaf.ColorUIResource(0x112233));", "new ColorUIResource(0xabcdef)",
                "Color.decode(\"#112233\")", "java.awt.Color.decode(\"0x112233\")"))
            assertThat(CONSTRUCTED.matcher(line).find()).as(line).isTrue();
        for (String line : List.of("new Color(12, 34, 56)", "new java.awt.Color(0, 0, 0, alpha)", "ColorMath.opacity(Color.BLACK, .5)"))
            assertThat(CONSTRUCTED.matcher(line).find()).as(line).isFalse();
    }

    @Test void noProductionColourLiteralOutsideTheThemeFile() throws IOException {
        var offenders = new ArrayList<String>();
        var roots = new ArrayList<>(List.of(ROOT.resolve("jasper-ui/src/main"), ROOT.resolve("jasper-app/src/main")));
        try (var plugins = Files.list(ROOT.resolve("plugins"))) {
            plugins.map(plugin -> plugin.resolve("src/main")).filter(Files::isDirectory).forEach(roots::add);
        }
        for (Path root : roots) try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString(), extension = name.substring(name.lastIndexOf('.') + 1);
                if (file.equals(THEME)) continue;
                String text;
                if (extension.equals("java")) text = Files.readString(file);
                else if (TEXT_RESOURCES.contains(extension)) text = Files.readString(file);
                else continue;
                var lines = text.split("\n", -1);
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];
                    boolean hit = extension.equals("java")
                        ? STRING_HEX.matcher(line).find() || CONSTRUCTED.matcher(line).find()
                        : RESOURCE_HEX.matcher(line).find();
                    if (hit) offenders.add(ROOT.relativize(file) + ":" + (i + 1) + ": " + line.strip());
                }
            }
        }
        assertThat(offenders).as("colour literals outside slate-steel.json").isEmpty();
    }
}
