package dev.jasper.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.FlatSystemProperties;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.platform.TitleBarStyle;
import dev.jasper.app.plugins.PluginSelection;
import dev.jasper.app.presentation.DesktopFlags;
import dev.jasper.app.presentation.Presentation;
import dev.jasper.app.presentation.Projections;
import dev.jasper.app.testsupport.LookFixtures;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
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

        var standard = Presentation.standard(new Projections.Host(true, true));
        assertThat(ApplicationBootstrap.applyDesktop(standard, ConfigSnapshot.defaults(), true)).isEqualTo(TitleBarStyle.MAC_UNIFIED);
        assertThat(System.getProperty(MENU)).isEqualTo("true");
        assertThat(ApplicationBootstrap.applyDesktop(standard, ConfigSnapshot.defaults(), false)).isEqualTo(TitleBarStyle.CUSTOM);
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
