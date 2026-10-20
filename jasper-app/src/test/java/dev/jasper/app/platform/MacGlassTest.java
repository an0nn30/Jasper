package dev.jasper.app.platform;

import java.awt.Color;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.plaf.ColorUIResource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MacGlassTest {
    @Test void anExplicitLibraryWins() {
        assertThat(MacGlass.library("/tmp/libjasper-glass.dylib", "/Applications/Jasper.app/Contents/MacOS/Jasper"))
            .isEqualTo(Path.of("/tmp/libjasper-glass.dylib"));
    }

    @Test void aPackagedAppLoadsItFromContentsApp() {
        assertThat(MacGlass.library(null, "/Applications/Jasper.app/Contents/MacOS/Jasper"))
            .isEqualTo(Path.of("/Applications/Jasper.app/Contents/app/libjasper-glass.dylib").toAbsolutePath());
    }

    @Test void anUnpackagedJvmHasNoLibrary() {
        assertThat(MacGlass.library(null, null)).isNull();
        assertThat(MacGlass.library(" ", "/usr/bin/java")).isNull();
    }

    @Test void headlessRuntimesAreUnsupported() {
        assertThat(WindowGlass.Platform.current().supported()).isFalse();
    }

    @Test void aRootPaneWithoutAWindowIsNeverMadeTranslucent() {
        assertThat(MacGlass.translucent(new JRootPane())).isFalse();
        assertThat(MacGlass.blur(new JRootPane(), WindowGlass.Blur.DARK)).isFalse();
        assertThat(MacGlass.blur(new JRootPane(), WindowGlass.Blur.NONE)).isFalse();
    }

    @Test void aRootPaneWithoutAWindowHasNothingToWatch() {
        var resets = new AtomicInteger();
        MacGlass.watch(new JRootPane(), resets::incrementAndGet).run();
        assertThat(resets).hasValue(0);
    }

    @Test void onlyANotifiedOpaqueBackgroundCountsAsAReset() {
        var window = new JPanel();
        var resets = new AtomicInteger();
        Runnable stop = MacGlass.watchBackground(window, resets::incrementAndGet);
        window.setBackground(new Color(0, 0, 0, 0x80));
        assertThat(resets).as("a translucent background").hasValue(0);
        window.setBackground(new ColorUIResource(0x3C3F41));
        assertThat(resets).as("an opaque background").hasValue(1);
        window.setBackground(null);
        assertThat(resets).as("no background is opaque").hasValue(2);
        stop.run();
        window.setBackground(Color.RED);
        assertThat(resets).as("after watching stops").hasValue(2);
    }

    @Test void aStaleLookAndFeelBackgroundGivesWayToTheCurrentOne() {
        var stale = new ColorUIResource(0xF2F2F2);
        var current = new ColorUIResource(0x3C3F41);
        assertThat(MacGlass.restored(stale, current)).isSameAs(current);
        assertThat(MacGlass.restored(stale, null)).as("no current colour").isSameAs(stale);
        assertThat(MacGlass.restored(Color.RED, current)).as("an application colour is kept").isEqualTo(Color.RED);
        assertThat(MacGlass.restored(null, current)).as("nothing recorded").isNull();
    }
}
