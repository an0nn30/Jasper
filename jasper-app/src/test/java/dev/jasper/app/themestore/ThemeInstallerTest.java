package dev.jasper.app.themestore;

import static dev.jasper.app.appearance.ThemeFixtures.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.appearance.ThemeCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThemeInstallerTest {
    @TempDir Path home;
    private final Provenance.Entry dracula = new Provenance.Entry(12275, "com.vermouthx.idea", "Dracula Theme", "2.3.1");

    private Path themes() { return home.resolve("themes"); }

    private Path download(ThemeInstaller installer, long updateId, String theme) throws Exception {
        Path part = installer.downloadTarget(new MarketplaceClient.Release(updateId, "x", updateId + "/x.jar"));
        plugin(part, theme, true, "#282a36", "282a36");
        return part;
    }

    @Test void aMarketplaceDownloadWithAThemeLandsUnderItsPluginIdAndVersionAndIsRecorded() throws Exception {
        var installer = new ThemeInstaller(themes());
        var installed = installer.installDownload(download(installer, 1, "Dracula"), ".jar", dracula);
        assertThat(installed.file()).isEqualTo(themes().resolve("com.vermouthx.idea-2.3.1.jar"));
        assertThat(installed.themes()).containsExactly("Dracula");
        assertThat(installer.sources()).containsExactly(entry("com.vermouthx.idea-2.3.1.jar", dracula));
        assertThat(themes().resolve(".downloads")).isEmptyDirectory();
        assertThat(ThemeCatalog.beside(home.resolve("config.toml")).scan().find("Dracula")).isPresent();
    }

    @Test void aDownloadWithoutThemesIsRefusedAndLeavesNothingBehind() throws Exception {
        var installer = new ThemeInstaller(themes());
        Path part = installer.downloadTarget(new MarketplaceClient.Release(8, "1.0", "8/material.zip"));
        jar(part, entries("META-INF/plugin.xml", pluginXml("Material")));
        var material = new Provenance.Entry(8006, "com.chrisrm.idea.MaterialThemeUI", "Material Theme UI", "1.0");
        assertThatThrownBy(() -> installer.installDownload(part, ".zip", material))
            .isInstanceOf(ThemeInstaller.InstallException.class).hasMessageStartingWith("Material Theme UI has no themes Jasper can use.");
        try (var files = Files.list(themes())) {
            assertThat(files.map(file -> file.getFileName().toString())).containsExactly(".downloads");
        }
        assertThat(themes().resolve(".downloads")).isEmptyDirectory();
        assertThat(installer.sources()).isEmpty();
    }

    @Test void anUpdateReplacesTheOlderFileOfTheSamePlugin() throws Exception {
        var installer = new ThemeInstaller(themes());
        installer.installDownload(download(installer, 1, "Dracula"), ".jar", new Provenance.Entry(12275, "com.vermouthx.idea", "Dracula Theme", "2.3.0"));
        installer.installDownload(download(installer, 2, "Dracula"), ".jar", dracula);
        assertThat(themes().resolve("com.vermouthx.idea-2.3.0.jar")).doesNotExist();
        assertThat(themes().resolve("com.vermouthx.idea-2.3.1.jar")).isRegularFile();
        assertThat(installer.sources()).containsOnlyKeys("com.vermouthx.idea-2.3.1.jar");
    }

    @Test void aDiskInstallKeepsItsNameAndAsksBeforeReplacing() throws Exception {
        var installer = new ThemeInstaller(themes());
        Path nord = plugin(home.resolve("Downloads/nord.jar"), "Nord", true, "#2e3440", "2e3440");
        assertThat(installer.installFromDisk(nord, false).themes()).containsExactly("Nord");
        assertThat(themes().resolve("nord.jar")).isRegularFile();
        assertThat(installer.sources()).isEmpty();
        assertThatThrownBy(() -> installer.installFromDisk(nord, false)).isInstanceOfSatisfying(ThemeInstaller.Conflict.class,
            conflict -> assertThat(conflict.existing()).isEqualTo(themes().resolve("nord.jar")));
        assertThat(installer.installFromDisk(nord, true).file()).isEqualTo(themes().resolve("nord.jar"));
        Path notes = Files.writeString(home.resolve("Downloads/notes.txt"), "hi");
        assertThatThrownBy(() -> installer.installFromDisk(notes, false)).isInstanceOf(ThemeInstaller.InstallException.class)
            .hasMessage("notes.txt is not a .jar or .zip theme plugin.");
    }

    @Test void aDiskInstallOverAMarketplaceFileForgetsWhereTheOldOneCameFrom() throws Exception {
        var installer = new ThemeInstaller(themes());
        installer.installDownload(download(installer, 1, "Dracula"), ".jar", dracula);
        Path mine = plugin(home.resolve("Downloads/com.vermouthx.idea-2.3.1.jar"), "Dracula", true, "#000000", "000000");
        installer.installFromDisk(mine, true);
        assertThat(installer.sources()).isEmpty();
    }

    @Test void uninstallRemovesTheFileAndItsRecordAndOnlyInsideTheFolder() throws Exception {
        var installer = new ThemeInstaller(themes());
        var installed = installer.installDownload(download(installer, 1, "Dracula"), ".jar", dracula);
        installer.uninstall(installed.file());
        assertThat(installed.file()).doesNotExist();
        assertThat(installer.sources()).isEmpty();
        Path outside = plugin(home.resolve("Downloads/nord.jar"), "Nord", true, "#2e3440", "2e3440");
        assertThatThrownBy(() -> installer.uninstall(outside)).isInstanceOf(ThemeInstaller.InstallException.class);
        assertThat(outside).isRegularFile();
    }

    @Test void staleRecordsAndLeftoverDownloadsAreDropped() throws Exception {
        var installer = new ThemeInstaller(themes());
        Files.createDirectories(themes().resolve(".downloads"));
        Files.writeString(themes().resolve(".downloads/1.part"), "partial");
        Files.writeString(themes().resolve(".sources.toml"),
            "[\"gone-1.0.zip\"]\nmarketplace_id = 1\nxml_id = \"gone\"\nname = \"Gone\"\nversion = \"1.0\"\n");
        assertThat(installer.sources()).isEmpty();
        installer.clearDownloads();
        assertThat(themes().resolve(".downloads")).isEmptyDirectory();
        Files.writeString(themes().resolve(".sources.toml"), "this is = not [ toml");
        assertThat(installer.sources()).isEmpty();
    }

    @Test void anUnknownExtensionIsRefusedAndNothingLandsOutside() throws Exception {
        var installer = new ThemeInstaller(themes());
        Path part = download(installer, 1, "Dracula");
        assertThatThrownBy(() -> installer.installDownload(part, "/../x", dracula)).isInstanceOf(ThemeInstaller.InstallException.class);
        assertThat(home.resolve("x")).doesNotExist();
        assertThat(installer.sources()).isEmpty();
    }

    @Test void uninstallRefusesDotFilesAndNonArchives() throws Exception {
        var installer = new ThemeInstaller(themes());
        installer.installDownload(download(installer, 1, "Dracula"), ".jar", dracula);
        assertThatThrownBy(() -> installer.uninstall(themes().resolve(".sources.toml"))).isInstanceOf(ThemeInstaller.InstallException.class);
        assertThat(themes().resolve(".sources.toml")).isRegularFile();
    }

    @Test void aReplacingDiskInstallLeavesNoTemporaryFile() throws Exception {
        var installer = new ThemeInstaller(themes());
        Path nord = plugin(home.resolve("Downloads/nord.jar"), "Nord", true, "#2e3440", "2e3440");
        installer.installFromDisk(nord, false);
        installer.installFromDisk(nord, true);
        assertThat(themes().resolve(".downloads")).isEmptyDirectory();
        assertThat(themes().resolve("nord.jar")).isRegularFile();
    }

    @Test void anUpdateDeletesOnlyArchivesInTheFolderWhateverTheRecordsSay() throws Exception {
        var installer = new ThemeInstaller(themes());
        Files.createDirectories(themes());
        Path outside = Files.writeString(home.resolve("x.jar"), "outside");
        Path backslashed = Files.writeString(themes().resolve("a\\..\\..\\x.jar"), "odd name");
        Path notes = Files.writeString(themes().resolve("notes.txt"), "not an archive");
        var record = "\nmarketplace_id = 12275\nxml_id = \"com.vermouthx.idea\"\nname = \"Dracula Theme\"\nversion = \"1.0\"\n";
        Files.writeString(themes().resolve(Provenance.FILE), "['../x.jar']" + record + "['a\\..\\..\\x.jar']" + record
            + "['notes.txt']" + record);
        assertThat(installer.sources()).containsOnlyKeys("notes.txt");
        installer.installDownload(download(installer, 1, "Dracula"), ".jar", dracula);
        assertThat(outside).hasContent("outside");
        assertThat(backslashed).hasContent("odd name");
        assertThat(notes).hasContent("not an archive");
        assertThat(installer.sources()).containsOnlyKeys("com.vermouthx.idea-2.3.1.jar");
    }
}
