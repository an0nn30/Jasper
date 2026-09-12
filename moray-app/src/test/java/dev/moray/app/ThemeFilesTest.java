package dev.moray.app;

import dev.moray.terminal.Palette;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ThemeFilesTest {
    @TempDir Path directory;

    @Test void fileRepairSucceedsWithoutChangingTheSelector() throws Exception {
        var file = directory.resolve("night.toml");
        var files = new ThemeFiles(directory);
        var colors = new ColorsConfig(Appearance.SYSTEM, "night");
        Files.writeString(file, "[colors.primary]\nbackground = '#101820'\n");
        var good = files.refresh(colors, false);
        Files.writeString(file, "[colors.primary]\nbackground = 'broken'\n");
        assertThat(files.refresh(colors, true).palette()).isEqualTo(good.palette());
        Files.delete(file);
        assertThat(files.refresh(colors, false).diagnostics()).isNotEmpty();
        Files.writeString(file, "[colors.primary]\nbackground = '#fafafa'\n");
        var repaired = files.refresh(colors, false);
        assertThat(repaired.palette().background()).isEqualTo(new Color(0xfafafa));
        assertThat(repaired.diagnostics()).isEmpty();
    }

    @Test void builtinSelectionResetsLastGoodAndDoesNotReadCustomFiles() throws Exception {
        Files.writeString(directory.resolve("moray-dark.toml"), "[colors.primary]\nbackground='#ffffff'");
        var files = new ThemeFiles(directory);
        assertThat(files.refresh(new ColorsConfig(Appearance.SYSTEM, "moray-light"), false).palette())
            .isEqualTo(Palette.morayLight());
        assertThat(files.refresh(new ColorsConfig(Appearance.SYSTEM, "moray-dark"), false).palette())
            .isEqualTo(Palette.morayDark());
    }

    @Test void unicodeSelectorAndOptionalExtensionResolveWithinThemeDirectory() throws Exception {
        Files.writeString(directory.resolve("夜.toml"), "[colors.primary]\nbackground='#101820'");
        var files = new ThemeFiles(directory);
        var bare = files.refresh(new ColorsConfig(Appearance.SYSTEM, "夜"), false);
        var extension = files.refresh(new ColorsConfig(Appearance.SYSTEM, "夜.toml"), false);
        assertThat(bare.palette().background()).isEqualTo(new Color(0x101820));
        assertThat(extension).isEqualTo(bare);
    }

    @Test void acceptsExactLimitAndRejectsExtraByteWithoutLosingLastGood() throws Exception {
        var file = directory.resolve("night.toml");
        String prefix = "[colors.primary]\nbackground='#101820'\n#";
        Files.writeString(file, prefix + "x".repeat(256 * 1024 - prefix.length()));
        var files = new ThemeFiles(directory);
        var colors = new ColorsConfig(Appearance.SYSTEM, "night");
        var good = files.refresh(colors, false);
        assertThat(good.diagnostics()).isEmpty();
        Files.writeString(file, prefix + "x".repeat(256 * 1024 + 1 - prefix.length()));
        var failed = files.refresh(colors, false);
        assertThat(failed.palette()).isEqualTo(good.palette());
        assertThat(failed.diagnostics()).singleElement().satisfies(d -> assertThat(d.message()).contains("256 KiB"));
    }

    @Test void invalidUtf8NonregularTargetAndMissingDirectoryAreDiagnosed() throws Exception {
        var colors = new ColorsConfig(Appearance.SYSTEM, "night");
        Files.write(directory.resolve("night.toml"), new byte[]{'[', (byte) 0xc3, (byte) 0x28});
        assertThat(new ThemeFiles(directory).refresh(colors, false).diagnostics()).isNotEmpty();
        Files.delete(directory.resolve("night.toml"));
        Files.createDirectory(directory.resolve("night.toml"));
        assertThat(new ThemeFiles(directory).refresh(colors, false).diagnostics()).isNotEmpty();
        assertThat(new ThemeFiles(directory.resolve("missing")).refresh(colors, false).diagnostics()).isNotEmpty();
    }

    @Test void permitsInRootSymlinkAndRejectsEscapingSymlink() throws Exception {
        Path target = directory.resolve("target.toml");
        Files.writeString(target, "[colors.primary]\nbackground='#101820'");
        Path inside = directory.resolve("inside.toml");
        try { Files.createSymbolicLink(inside, target.getFileName()); }
        catch (UnsupportedOperationException | java.io.IOException failure) { assumeTrue(false, "symbolic links unavailable"); }
        var files = new ThemeFiles(directory);
        assertThat(files.refresh(new ColorsConfig(Appearance.SYSTEM, "inside"), false).diagnostics()).isEmpty();

        Path external = Files.createTempFile("moray-external-theme", ".toml");
        try {
            Files.writeString(external, "[colors.primary]\nbackground='#ffffff'");
            Files.createSymbolicLink(directory.resolve("outside.toml"), external);
            var rejected = files.refresh(new ColorsConfig(Appearance.SYSTEM, "outside"), false);
            assertThat(rejected.diagnostics()).isNotEmpty();
            assertThat(rejected.palette().background()).isEqualTo(new Color(0x101820));
        } finally { Files.deleteIfExists(external); }
    }

    @Test void forceBypassesEqualMetadataCache() throws Exception {
        Path file = directory.resolve("night.toml");
        Files.writeString(file, "[colors.primary]\nbackground='#101820'");
        FileTime modified = Files.getLastModifiedTime(file);
        var files = new ThemeFiles(directory);
        var colors = new ColorsConfig(Appearance.SYSTEM, "night");
        files.refresh(colors, false);
        Files.writeString(file, "[colors.primary]\nbackground='#fafafa'");
        Files.setLastModifiedTime(file, modified);
        assertThat(files.refresh(colors, false).palette().background()).isEqualTo(new Color(0x101820));
        assertThat(files.refresh(colors, true).palette().background()).isEqualTo(new Color(0xfafafa));
    }
}
