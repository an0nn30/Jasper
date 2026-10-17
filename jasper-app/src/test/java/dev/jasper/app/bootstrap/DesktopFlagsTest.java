package dev.jasper.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.presentation.DesktopFlags;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 9.1 and 10.1: the committed desktop flags reach the properties AWT reads, before any window. */
class DesktopFlagsTest {
    static final String MENU = "apple.laf.useScreenMenuBar";
    private final Map<String, String> saved = new HashMap<>();

    @BeforeEach void save() { for (String key : new String[] {MENU, "apple.awt.application.appearance", "apple.awt.enableTemplateImages"})
        saved.put(key, System.getProperty(key)); }
    @AfterEach void restore() { saved.forEach((key, value) -> { if (value == null) System.clearProperty(key); else System.setProperty(key, value); }); }

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
