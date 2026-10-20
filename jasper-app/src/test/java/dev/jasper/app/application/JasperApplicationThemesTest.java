package dev.jasper.app.application;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.ConfigService;
import dev.jasper.ui.theme.JasperTheme;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

/** spec 5.4: start-up parses the bundled theme once and installs the configured default variant. */
class JasperApplicationThemesTest {
    @TempDir Path home;

    private void start(String config, boolean dark, dev.jasper.ui.theme.TerminalScheme scheme) throws Exception {
        Files.writeString(home.resolve("config.toml"), config);
        var service = new ConfigService(home.resolve("config.toml"), false);
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                try { UIManager.setLookAndFeel(new javax.swing.plaf.metal.MetalLookAndFeel()); }
                catch (javax.swing.UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
                org.assertj.core.api.Assertions.assertThatIllegalStateException().isThrownBy(dev.jasper.ui.swing.FlatLafInstaller::installedTheme);
                assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedKit()).as("no Jasper look and feel yet: the default kit")
                    .isSameAs(dev.jasper.ui.kit.Kit.DEFAULT);
                application[0] = new JasperApplication(service, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
                var expected = (dark ? JasperTheme.bundled() : JasperTheme.builtIn("jasper-meadow").orElseThrow()).generated(dark);
                assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedTheme()).isSameAs(expected);
                assertThat(UIManager.getLookAndFeelDefaults().get("Jasper.installedKit")).as("start-up publishes its kit with the theme")
                    .isSameAs(dev.jasper.ui.kit.Kit.DEFAULT);
                assertThat(dev.jasper.app.platform.ThemeZone.theme(null)).isSameAs(expected);
                // Plan 2 finding 8: the configured scheme reaches the terminal palette.
                assertThat(application[0].themes().current().palette())
                    .isEqualTo(dev.jasper.app.appearance.TerminalPaletteAdapter.toTerminal(scheme));
            });
        } finally {
            if (application[0] != null) { edt(application[0]::quit); assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue(); }
            service.close();
            edt(() -> ThemeTestSupport.controller(true));
        }
    }

    /** From a fresh look and feel (no Jasper snapshot), start-up installs its variant before any chrome reads the snapshot. */
    @Test void aFreshProcessInstallsTheConfiguredVariantBeforeAnyChromeReadsTheSnapshot() throws Exception {
        start("[ui]\nappearance = 'light'\n[terminal]\nscheme = 'slate-cobalt/high-contrast'\n", false, dev.jasper.ui.theme.JasperTheme.bundled().scheme("high-contrast").orElseThrow());
    }

    /** The default follows the operating system; a headless run has none, so it starts Dark. */
    @Test void withNoAppearanceAHeadlessStartIsDark() throws Exception {
        start("", true, dev.jasper.ui.theme.JasperTheme.bundled().matching(true));
    }

    /** Review Focus 1: an older install's theme folders are neither read nor changed; nothing else appears beside them. */
    @Test void anOlderInstallsThemeFoldersAreIgnoredAndLeftAsTheyAre() throws Exception {
        String config = "[ui]\nappearance = 'light'\n";
        java.nio.file.Files.createDirectories(home.resolve("themes/nested"));
        java.nio.file.Files.createDirectories(home.resolve("terminal-themes"));
        java.nio.file.Files.writeString(home.resolve("themes/nord.toml"), "name = \"Nord\"\n");
        java.nio.file.Files.writeString(home.resolve("themes/nested/custom.theme.json"), "{ \"name\": \"Custom\" }\n");
        java.nio.file.Files.writeString(home.resolve("terminal-themes/dark.toml"), "[colors]\n");
        java.nio.file.Files.writeString(home.resolve("config.toml"), config);
        var before = snapshot(home);
        start(config, false, dev.jasper.ui.theme.JasperTheme.builtIn("jasper-meadow").orElseThrow().matching(false));
        assertThat(snapshot(home)).as("every path and byte under the home").isEqualTo(before);
    }

    /** Every path under {@code root} with its bytes (directories map to an empty marker). */
    private static java.util.Map<String, String> snapshot(Path root) throws java.io.IOException {
        var files = new java.util.TreeMap<String, String>();
        try (var walk = java.nio.file.Files.walk(root)) {
            for (Path path : walk.toList()) {
                if (path.equals(root)) continue;
                files.put(root.relativize(path).toString(), java.nio.file.Files.isDirectory(path) ? "<dir>"
                    : java.util.HexFormat.of().formatHex(java.nio.file.Files.readAllBytes(path)));
            }
        }
        return files;
    }
}
