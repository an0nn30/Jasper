package dev.jasper.app.appearance;

import java.io.ByteArrayOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.appearance.ThemeFixtures.*;
import static org.assertj.core.api.Assertions.*;

class ThemeArchiveTest {
    @TempDir Path directory;

    @Test void aPluginJarProvidesEachThemeWithItsEditorScheme() {
        Path file = jar(directory.resolve("Pair.jar"), entries(
            "META-INF/plugin.xml", pluginXml("Pair", "/themes/Night.theme.json", "themes/Day.theme.json"),
            "themes/Night.theme.json", theme("Night", true, "/themes/Night.xml", "#101010"),
            "themes/Day.theme.json", theme("Day", false, "Day.xml", "#fafafa"),
            "themes/Night.xml", scheme("Night", "Darcula", "000000", "ff0000"),
            "themes/Day.xml", scheme("Day", "Default", "ffffff", "aa0000"),
            "fix/Pair.class", new byte[]{(byte) 0xca, (byte) 0xfe}));
        var contents = ThemeArchive.read(file);
        assertThat(contents.problems()).isEmpty();
        assertThat(contents.themes()).extracting(ThemeArchive.Found::label)
            .containsExactly("Pair.jar!/themes/Night.theme.json", "Pair.jar!/themes/Day.theme.json");
        var night = contents.themes().getFirst();
        assertThat(night.json()).contains("\"Night\"");
        assertThat(new String(night.schemeXml(), StandardCharsets.UTF_8)).contains("parent_scheme=\"Darcula\"");
        assertThat(night.schemeLabel()).isEqualTo("Pair.jar!/themes/Night.xml");
        assertThat(contents.themes().get(1).schemeLabel()).as("a relative scheme path is beside its theme")
            .isEqualTo("Pair.jar!/themes/Day.xml");
    }

    @Test void aMarketplaceZipIsReadThroughTheJarsInItsLibFolder() {
        byte[] plugin = jarBytes(entries(
            "META-INF/plugin.xml", pluginXml("Dracula Fixture", "/themes/Dracula.theme.json"),
            "themes/Dracula.theme.json", theme("Dracula Fixture", true, "/themes/Dracula.xml", "#282a36"),
            "themes/Dracula.xml", scheme("Dracula Fixture", "Darcula", "282a36", "ff5555")));
        byte[] library = jarBytes(entries("kotlin/Unit.class", new byte[]{1, 2, 3}));
        Path file = jar(directory.resolve("Dracula.zip"), entries(
            "Dracula/lib/kotlin-stdlib.jar", library, "Dracula/lib/dracula.jar", plugin, "Dracula/README.txt", "hello"));
        var contents = ThemeArchive.read(file);
        assertThat(contents.problems()).isEmpty();
        assertThat(contents.themes()).singleElement().satisfies(found -> {
            assertThat(found.label()).isEqualTo("Dracula.zip!/Dracula/lib/dracula.jar!/themes/Dracula.theme.json");
            assertThat(found.schemeXml()).isNotNull();
        });
    }

    @Test void bothFormsOfThemeProviderAreRead() {
        Path plain = jar(directory.resolve("Plain.jar"), entries(
            "META-INF/plugin.xml", "<idea-plugin><name>Plain</name><extensions>"
                + "<com.intellij.themeProvider id=\"a\" path=\"/a.theme.json\"/></extensions></idea-plugin>",
            "a.theme.json", theme("Plain", false, null, "#eeeeee")));
        assertThat(ThemeArchive.read(plain).themes()).singleElement().satisfies(found -> {
            assertThat(found.schemeXml()).isNull();
            assertThat(found.schemeName()).isNull();
        });
        Path real = jar(directory.resolve("Real.jar"), entries(
            "META-INF/plugin.xml", """
                <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude" require-restart="false">
                  <id>com.example.real</id><name>Real Theme</name>
                  <depends>com.intellij.modules.platform</depends>
                  <xi:include href="/META-INF/other.xml" xpointer="xpointer(/idea-plugin/*)"/>
                  <extensions defaultExtensionNs="com.intellij">
                    <themeProvider id="real-dark" path="/real.theme.json" targetUi="classic"/>
                    <bundledColorScheme path="/real"/>
                  </extensions>
                </idea-plugin>""",
            "real.theme.json", theme("Real", true, null, "#123123")));
        var contents = ThemeArchive.read(real);
        assertThat(contents.problems()).isEmpty();
        assertThat(contents.themes()).extracting(ThemeArchive.Found::label).containsExactly("Real.jar!/real.theme.json");
    }

    @Test void anExtensionsElementDeclaringItsNamespaceAsXmlnsIsAccepted() {
        Path file = jar(directory.resolve("Xmlns.jar"), entries(
            "META-INF/plugin.xml", "<idea-plugin><name>Xmlns</name>"
                + "<extensions xmlns=\"com.intellij\"><themeProvider id=\"x\" path=\"/x.theme.json\"/></extensions></idea-plugin>",
            "x.theme.json", theme("X", true, null, "#010101")));
        var contents = ThemeArchive.read(file);
        assertThat(contents.problems()).isEmpty();
        assertThat(contents.themes()).extracting(ThemeArchive.Found::label).containsExactly("Xmlns.jar!/x.theme.json");
    }

    @Test void aSchemeNamedInsteadOfAPathIsPassedOnByName() {
        Path file = jar(directory.resolve("Named.jar"), entries(
            "META-INF/plugin.xml", pluginXml("Named", "/Named.theme.json"),
            "Named.theme.json", theme("Named", true, "Darcula", "#222222")));
        assertThat(ThemeArchive.read(file).themes()).singleElement().satisfies(found -> {
            assertThat(found.schemeName()).isEqualTo("Darcula");
            assertThat(found.schemeXml()).isNull();
        });
    }

    @Test void repeatedProviderPathsAreDeduplicatedToOneTheme() {
        var providers = new StringBuilder();
        for (int i = 0; i < 400; i++) providers.append("<themeProvider id=\"t").append(i).append("\" path=\"/one.theme.json\"/>\n");
        Path file = jar(directory.resolve("Repeat.jar"), entries(
            "META-INF/plugin.xml", "<idea-plugin><name>Repeat</name><extensions defaultExtensionNs=\"com.intellij\">"
                + providers + "</extensions></idea-plugin>",
            "one.theme.json", theme("One", true, null, "#111111")));
        var contents = ThemeArchive.read(file);
        assertThat(contents.problems()).isEmpty();
        assertThat(contents.themes()).hasSize(1);
    }

    @Test void moreThanSixtyFourDistinctProvidersAreCappedWithAProblem() {
        var providers = new StringBuilder();
        var files = new LinkedHashMap<String, byte[]>();
        for (int i = 0; i < 65; i++) {
            providers.append("<themeProvider id=\"t").append(i).append("\" path=\"/t").append(i).append(".theme.json\"/>\n");
            files.put("t" + i + ".theme.json", theme("T" + i, true, null, "#111111").getBytes(StandardCharsets.UTF_8));
        }
        files.put("META-INF/plugin.xml", ("<idea-plugin><name>Many</name><extensions defaultExtensionNs=\"com.intellij\">"
            + providers + "</extensions></idea-plugin>").getBytes(StandardCharsets.UTF_8));
        Path file = jar(directory.resolve("Many65.jar"), files);
        var contents = ThemeArchive.read(file);
        assertThat(contents.themes()).hasSize(64);
        assertThat(contents.problems()).anySatisfy(problem -> assertThat(problem).contains("more than 64 themes"));
    }

    @Test void aPluginWithoutThemeProvidersSaysItDrawsItsThemeInCode() {
        Path file = jar(directory.resolve("Material.jar"), entries("META-INF/plugin.xml", """
            <idea-plugin><name>Material Theme UI</name>
              <extensions defaultExtensionNs="com.intellij"><applicationService serviceImplementation="x.Y"/></extensions>
            </idea-plugin>"""));
        var contents = ThemeArchive.read(file);
        assertThat(contents.themes()).isEmpty();
        assertThat(contents.problems()).containsExactly("Material Theme UI provides no theme files; Jasper can't use themes drawn in code");
    }

    @Test void pathsThatLeaveThePluginAreRejected() {
        Path file = jar(directory.resolve("Escape.jar"), entries(
            "META-INF/plugin.xml", pluginXml("Escape", "/../outside.theme.json", "/ok.theme.json"),
            "ok.theme.json", theme("Ok", true, "../../scheme.xml", "#111111")));
        var contents = ThemeArchive.read(file);
        assertThat(contents.themes()).extracting(ThemeArchive.Found::label).containsExactly("Escape.jar!/ok.theme.json");
        assertThat(contents.themes().getFirst().schemeXml()).isNull();
        assertThat(contents.problems()).hasSize(2).allMatch(problem -> problem.contains("leaves the plugin"));
    }

    @Test void missingThemeAndSchemeFilesAreReported() {
        Path file = jar(directory.resolve("Gaps.jar"), entries(
            "META-INF/plugin.xml", pluginXml("Gaps", "/gone.theme.json", "/here.theme.json"),
            "here.theme.json", theme("Here", false, "/missing.xml", "#ffffff")));
        var contents = ThemeArchive.read(file);
        assertThat(contents.themes()).singleElement().satisfies(found -> assertThat(found.schemeXml()).isNull());
        assertThat(contents.problems()).hasSize(2);
        assertThat(contents.problems().get(0)).contains("Gaps.jar!/gone.theme.json").contains("missing");
        assertThat(contents.problems().get(1)).contains("Gaps.jar!/missing.xml").contains("missing");
    }

    @Test void unreadableArchivesAndPluginXmlAreReported() throws Exception {
        Files.writeString(directory.resolve("text.jar"), "not a zip");
        assertThat(ThemeArchive.read(directory.resolve("text.jar")).problems()).singleElement().asString()
            .contains("text.jar").contains("cannot be read as a plugin archive");
        Path noPlugin = jar(directory.resolve("NoPlugin.zip"), entries("Thing/lib/other.jar", jarBytes(entries("a.txt", "a"))));
        assertThat(ThemeArchive.read(noPlugin).problems()).singleElement().asString().contains("no META-INF/plugin.xml");
        Path broken = jar(directory.resolve("Broken.jar"), entries("META-INF/plugin.xml", "<idea-plugin"));
        assertThat(ThemeArchive.read(broken).problems()).singleElement().asString()
            .contains("Broken.jar!/META-INF/plugin.xml").contains("not a readable XML document");
    }

    @Test void aDoctypeIsRejectedWithoutReadingItsEntities() throws Exception {
        Path secret = Files.writeString(directory.resolve("secret.txt"), "top secret");
        Path file = jar(directory.resolve("Xxe.jar"), entries("META-INF/plugin.xml",
            "<?xml version=\"1.0\"?><!DOCTYPE idea-plugin [<!ENTITY leak SYSTEM \"" + secret.toUri() + "\">]>"
                + "<idea-plugin><name>&leak;</name><extensions defaultExtensionNs=\"com.intellij\">"
                + "<themeProvider id=\"x\" path=\"/x.theme.json\"/></extensions></idea-plugin>",
            "x.theme.json", theme("X", true, null, "#000000")));
        var contents = ThemeArchive.read(file);
        assertThat(contents.themes()).isEmpty();
        assertThat(contents.problems()).singleElement().asString().contains("DOCTYPE").doesNotContain("top secret");
    }

    @Test void everyReadIsCapped() throws Exception {
        Path huge = directory.resolve("Huge.jar");
        try (var file = new RandomAccessFile(huge.toFile(), "rw")) { file.setLength(ThemeArchive.MAX_ARCHIVE + 1); }
        assertThat(ThemeArchive.read(huge).problems()).singleElement().asString().contains("larger than 64 MiB");

        Path bigTheme = jar(directory.resolve("BigTheme.jar"), entries(
            "META-INF/plugin.xml", pluginXml("Big", "/big.theme.json"),
            "big.theme.json", new byte[ThemeArchive.MAX_FILE + 1]));
        assertThat(ThemeArchive.read(bigTheme).problems()).singleElement().asString()
            .contains("BigTheme.jar!/big.theme.json").contains("larger than 4 MiB");

        var many = new LinkedHashMap<String, byte[]>();
        for (int i = 0; i <= ThemeArchive.MAX_ENTRIES; i++) many.put("e" + i, new byte[0]);
        assertThat(ThemeArchive.read(jar(directory.resolve("Many.jar"), many)).problems()).singleElement().asString()
            .contains("more than 10,000 entries");

        var libs = new LinkedHashMap<String, byte[]>();
        for (int i = 0; i <= ThemeArchive.MAX_NESTED_JARS; i++) libs.put(String.format("P/lib/l%02d.jar", i), jarBytes(entries("a.txt", "a")));
        libs.put("P/lib/zz-theme.jar", jarBytes(entries(
            "META-INF/plugin.xml", pluginXml("Late", "/late.theme.json"), "late.theme.json", theme("Late", true, null, "#000000"))));
        var crowded = ThemeArchive.read(jar(directory.resolve("Crowded.zip"), libs));
        assertThat(crowded.themes()).as("only the first 16 jars are read").isEmpty();
        assertThat(crowded.problems()).anySatisfy(problem -> assertThat(problem).contains("more than 16 jars"));

        Path bigLib = jar(directory.resolve("BigLib.zip"), entries("P/lib/big.jar", storedJar("blob.bin", ThemeArchive.MAX_NESTED + 1)));
        assertThat(ThemeArchive.read(bigLib).problems()).anySatisfy(problem ->
            assertThat(problem).contains("BigLib.zip!/P/lib/big.jar").contains("larger than 32 MiB"));
    }

    @Test void pathWithNoNameElementsDoesNotThrow() {
        assertThat(ThemeArchive.read(directory.getRoot()).problems()).isNotEmpty();
    }

    @Test void aCorruptJarInLibDoesNotHideTheOthers() {
        byte[] good = jarBytes(entries("META-INF/plugin.xml", pluginXml("Good", "/g.theme.json"),
            "g.theme.json", theme("Good", true, null, "#000000")));
        // Truncating the jar to half its length corrupts it
        byte[] corrupt = java.util.Arrays.copyOf(good, good.length / 2);
        var contents = ThemeArchive.read(jar(directory.resolve("Mixed.zip"), entries(
            "P/lib/a-corrupt.jar", corrupt, "P/lib/b-good.jar", good)));
        assertThat(contents.themes()).extracting(ThemeArchive.Found::label)
            .containsExactly("Mixed.zip!/P/lib/b-good.jar!/g.theme.json");
        assertThat(contents.problems()).singleElement().asString()
            .contains("Mixed.zip!/P/lib/a-corrupt.jar").contains("cannot be read");
    }

    @Test void aNestedJarWithTooManyEntriesIsReportedAndTheZipStillRead() {
        var many = new LinkedHashMap<String, byte[]>();
        for (int i = 0; i <= ThemeArchive.MAX_ENTRIES; i++) many.put("e" + i, new byte[0]);
        byte[] good = jarBytes(entries("META-INF/plugin.xml", pluginXml("Good", "/g.theme.json"),
            "g.theme.json", theme("Good", true, null, "#000000")));
        var contents = ThemeArchive.read(jar(directory.resolve("Deep.zip"), entries("P/lib/a-many.jar", jarBytes(many), "P/lib/b-good.jar", good)));
        assertThat(contents.themes()).hasSize(1);
        assertThat(contents.problems()).singleElement().asString()
            .contains("Deep.zip!/P/lib/a-many.jar").contains("more than 10,000 entries");
    }

    @Test void aLibJarEntryThatInflatesPastTheBudgetIsReportedWithoutReadingItAll() {
        // 300 MiB of zeros deflates to roughly 300 KiB; placed before plugin.xml so the plugin-probe pass
        // (which streams every entry in order) meets it first and must not silently skip-inflate it.
        var libEntries = new LinkedHashMap<String, byte[]>();
        libEntries.put("big.bin", new byte[300 << 20]);
        libEntries.put("META-INF/plugin.xml", pluginXml("Bomb", "/small.theme.json").getBytes(StandardCharsets.UTF_8));
        libEntries.put("small.theme.json", theme("Small", true, null, "#111111").getBytes(StandardCharsets.UTF_8));
        byte[] lib = jarBytes(libEntries);
        Path file = jar(directory.resolve("Bomb.zip"), entries("P/lib/big.jar", lib));
        var contents = ThemeArchive.read(file);
        assertThat(contents.themes()).isEmpty();
        assertThat(contents.problems()).anySatisfy(problem ->
            assertThat(problem).contains("Bomb.zip!/P/lib/big.jar").contains("inflates to more than 256 MiB"));
    }

    @Test void aPlainJarWhoseThemesInflatePastTheBudgetIsReported() {
        // 64 providers (the cap), each theme at the 4 MiB file cap: the themes alone fill the 256 MiB budget,
        // so plugin.xml's bytes take the read past it. The filler deflates to a jar well under 1 MiB.
        String head = "{\"name\": \"Budget\", \"dark\": true, \"filler\": \"", tail = "\"}";
        byte[] theme = (head + "a".repeat(ThemeArchive.MAX_FILE - head.length() - tail.length()) + tail)
            .getBytes(StandardCharsets.UTF_8);
        var paths = new String[ThemeArchive.MAX_PROVIDERS];
        var files = new LinkedHashMap<String, byte[]>();
        for (int i = 0; i < paths.length; i++) {
            paths[i] = "/t" + i + ".theme.json";
            files.put("t" + i + ".theme.json", theme);
        }
        files.put("META-INF/plugin.xml", pluginXml("Budget", paths).getBytes(StandardCharsets.UTF_8));
        var contents = ThemeArchive.read(jar(directory.resolve("Budget.jar"), files));
        assertThat(contents.themes()).isEmpty();
        assertThat(contents.problems()).singleElement().asString()
            .contains("Budget.jar").contains("inflates to more than 256 MiB").doesNotContain("Exception");
    }

    /** A jar whose one entry is stored uncompressed, so the jar itself is at least {@code size} bytes. */
    private static byte[] storedJar(String name, int size) throws Exception {
        byte[] data = new byte[size];
        var crc = new CRC32();
        crc.update(data);
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            var entry = new ZipEntry(name);
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(size);
            entry.setCompressedSize(size);
            entry.setCrc(crc.getValue());
            zip.putNextEntry(entry);
            zip.write(data);
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
