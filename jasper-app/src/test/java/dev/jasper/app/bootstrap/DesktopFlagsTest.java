package dev.jasper.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.FlatSystemProperties;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.DefaultSource;
import dev.jasper.app.config.ViewMode;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.platform.TitleBarStyle;
import dev.jasper.app.plugins.PluginSelection;
import dev.jasper.app.presentation.Branding;
import dev.jasper.app.presentation.ChromeSpec;
import dev.jasper.app.presentation.DesktopFlags;
import dev.jasper.app.presentation.KitParser;
import dev.jasper.app.presentation.Presentation;
import dev.jasper.app.presentation.Projections;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.LookFixtures;
import dev.jasper.ui.kit.Kit;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 9.1 and 10.1: the committed desktop flags reach the properties AWT reads, before any window. */
class DesktopFlagsTest {
    static final String MENU = "apple.laf.useScreenMenuBar";
    private final Map<String, String> saved = new HashMap<>();
    private boolean framesDecorated, dialogsDecorated;
    @TempDir Path home;

    @BeforeEach void save() {
        for (String key : new String[] {MENU, "apple.awt.application.appearance", "apple.awt.enableTemplateImages",
                FlatSystemProperties.USE_WINDOW_DECORATIONS})
            saved.put(key, System.getProperty(key));
        framesDecorated = javax.swing.JFrame.isDefaultLookAndFeelDecorated();
        dialogsDecorated = javax.swing.JDialog.isDefaultLookAndFeelDecorated();
    }
    @AfterEach void restore() {
        saved.forEach((key, value) -> { if (value == null) System.clearProperty(key); else System.setProperty(key, value); });
        javax.swing.JFrame.setDefaultLookAndFeelDecorated(framesDecorated);
        javax.swing.JDialog.setDefaultLookAndFeelDecorated(dialogsDecorated);
    }

    /** The bootstrap's one step: the presentation's committed flags reach both the menu bar property and the title bar style. */
    @Test void theBootstrapAppliesThePresentationsCommittedFlags() throws Exception {
        Path dev = LookFixtures.plugin(home.resolve("dev"), "dev.example.look", LookFixtures.descriptor("dev.example.look", "Example")
            + LookFixtures.provider("look.kit.toml"), Map.of("look.kit.toml",
                LookFixtures.KIT + "[chrome]\ntitle_bar = \"native\"\nmenu_bar = \"window\"\n"));
        var presentation = PluginSelection.of(new AppDirs(home, home.resolve("config.toml"), home.resolve("logs")), null, "", dev, false)
            .presentation();
        assertThat(presentation.committedDesktop()).isEqualTo(new DesktopFlags(false, true));
        System.setProperty(MENU, "true");
        assertThat(ApplicationBootstrap.applyDesktop(presentation, ConfigSnapshot.defaults(), true)).isEqualTo(TitleBarStyle.NATIVE);
        assertThat(System.getProperty(MENU)).isEqualTo("false");
        assertThat(System.getProperty(FlatSystemProperties.USE_WINDOW_DECORATIONS)).as("the system title bar").isEqualTo("false");
        assertThat(ApplicationBootstrap.applyDesktop(presentation, ConfigSnapshot.defaults(), false)).isEqualTo(TitleBarStyle.NATIVE);

        var standard = Presentation.standard(new Projections.Host(Projections.Platform.MAC, true));
        assertThat(ApplicationBootstrap.applyDesktop(standard, ConfigSnapshot.defaults(), true)).isEqualTo(TitleBarStyle.MAC_UNIFIED);
        assertThat(System.getProperty(MENU)).isEqualTo("true");
        assertThat(ApplicationBootstrap.applyDesktop(standard, ConfigSnapshot.defaults(), false)).isEqualTo(TitleBarStyle.CUSTOM);
    }

    static Presentation classic(Projections.Host host) {
        var chrome = EnterpriseLook.titled(ChromeSpec.CLASSIC, ChromeSpec.TitleBar.CLASSIC);
        return new Presentation(Optional.of(new Presentation.Provider("dev.example.look", "Look", "0.1.0", "d1")), Kit.DEFAULT, chrome, true,
            KitParser.Faces.NONE, DefaultSource.CORE, Projections.of(chrome, true, false, "dev.example.look", host), List.of(), Branding.NONE);
    }

    /** Plan 14 (Rulings 5, 17 and 18): macOS and Windows keep the default look's desktop; Linux keeps the system title. */
    @Test void aClassicTitleCommitsTheDefaultLooksFlagsOnMacOsAndWindowsAndTheSystemTitleOnLinux() {
        var mac = classic(new Projections.Host(Projections.Platform.MAC, true));
        assertThat(mac.committedDesktop()).isEqualTo(DesktopFlags.STANDARD);
        System.setProperty(MENU, "false");
        assertThat(ApplicationBootstrap.applyDesktop(mac, ConfigSnapshot.defaults(), true)).isEqualTo(TitleBarStyle.MAC_UNIFIED);
        assertThat(System.getProperty(MENU)).as("the screen menu bar").isEqualTo("true");
        var windows = classic(new Projections.Host(Projections.Platform.WINDOWS, false));
        assertThat(windows.committedDesktop()).as("the default look's flags, so the fallback is the default look").isEqualTo(DesktopFlags.STANDARD);
        assertThat(ApplicationBootstrap.applyDesktop(windows, ConfigSnapshot.defaults(), false)).isEqualTo(TitleBarStyle.CUSTOM);
        assertThat(System.getProperty(FlatSystemProperties.USE_WINDOW_DECORATIONS)).as("FlatLaf's title pane").isEqualTo("true");
        assertThat(ApplicationBootstrap.applyDesktop(windows, ConfigSnapshot.defaults().toBuilder().nativeDecorations(true).build(), false))
            .as("window.native_decorations").isEqualTo(TitleBarStyle.NATIVE);
        assertThat(ApplicationBootstrap.applyDesktop(windows, ConfigSnapshot.defaults().toBuilder().viewMode(ViewMode.COMPACT).build(), false))
            .as("Compact").isEqualTo(TitleBarStyle.NATIVE);
        var linux = classic(new Projections.Host(Projections.Platform.LINUX, false));
        assertThat(linux.committedDesktop()).isEqualTo(new DesktopFlags(false, true));
        assertThat(ApplicationBootstrap.applyDesktop(linux, ConfigSnapshot.defaults(), false)).isEqualTo(TitleBarStyle.NATIVE);
    }

    @Test void aNativeTitledLookTurnsTheScreenMenuBarOffBeforeAwt() {
        ApplicationBootstrap.configureDesktopProperties(new DesktopFlags(false, true));
        assertThat(System.getProperty(MENU)).isEqualTo("false");
        assertThat(System.getProperty("apple.awt.application.appearance")).as("the rest is unchanged").isEqualTo("system");
        assertThat(System.getProperty("apple.awt.enableTemplateImages")).isEqualTo("true");
    }

    @Test void theDefaultLookKeepsTheScreenMenuBarAndOverridesAnInheritedSetting() {
        System.setProperty(MENU, "false");
        ApplicationBootstrap.configureDesktopProperties(DesktopFlags.STANDARD);
        assertThat(System.getProperty(MENU)).isEqualTo("true");
        System.setProperty(MENU, "false");
        ApplicationBootstrap.configureDesktopProperties();
        assertThat(System.getProperty(MENU)).as("the no-argument form is the default look's").isEqualTo("true");
    }
}
