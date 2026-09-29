package dev.jasper.app.platform;

import com.formdev.flatlaf.FlatSystemProperties;
import javax.swing.JDialog;
import javax.swing.JFrame;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TitleBarStyleTest {
    @Test void macOsAlwaysUsesItsOwnTitleRow() {
        assertThat(TitleBarStyle.choose(true, false)).isEqualTo(TitleBarStyle.MAC_UNIFIED);
        assertThat(TitleBarStyle.choose(true, true)).isEqualTo(TitleBarStyle.MAC_UNIFIED);
        assertThat(TitleBarStyle.choose(false, false)).isEqualTo(TitleBarStyle.CUSTOM);
        assertThat(TitleBarStyle.choose(false, true)).isEqualTo(TitleBarStyle.NATIVE);
    }

    @Test void configureSetsTheDecorationDefaultsBeforeAnyWindowExists() {
        String saved = System.getProperty(FlatSystemProperties.USE_WINDOW_DECORATIONS);
        boolean frames = JFrame.isDefaultLookAndFeelDecorated(), dialogs = JDialog.isDefaultLookAndFeelDecorated();
        try {
            TitleBarStyle.CUSTOM.configure();
            assertThat(System.getProperty(FlatSystemProperties.USE_WINDOW_DECORATIONS)).isEqualTo("true");
            assertThat(JFrame.isDefaultLookAndFeelDecorated()).isTrue();
            assertThat(JDialog.isDefaultLookAndFeelDecorated()).isTrue();
            TitleBarStyle.NATIVE.configure();
            assertThat(System.getProperty(FlatSystemProperties.USE_WINDOW_DECORATIONS)).as("Windows defaults to true").isEqualTo("false");
            assertThat(JFrame.isDefaultLookAndFeelDecorated()).isFalse();
            System.clearProperty(FlatSystemProperties.USE_WINDOW_DECORATIONS);
            TitleBarStyle.MAC_UNIFIED.configure();
            assertThat(System.getProperty(FlatSystemProperties.USE_WINDOW_DECORATIONS)).as("macOS is left alone").isNull();
        } finally {
            if (saved == null) System.clearProperty(FlatSystemProperties.USE_WINDOW_DECORATIONS);
            else System.setProperty(FlatSystemProperties.USE_WINDOW_DECORATIONS, saved);
            JFrame.setDefaultLookAndFeelDecorated(frames);
            JDialog.setDefaultLookAndFeelDecorated(dialogs);
        }
    }
}
