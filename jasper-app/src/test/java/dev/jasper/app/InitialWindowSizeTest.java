package dev.jasper.app;

import dev.jasper.terminal.FontSet;
import java.awt.*;
import java.util.ArrayDeque;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static dev.jasper.app.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class InitialWindowSizeTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }
    static ConfigSnapshot withGridAndFont(int columns, int lines, FontConfig font) {
        var d = ConfigSnapshot.defaults();
        return new ConfigSnapshot(d.tabHeight(), d.toolbar(), d.statusBar(), font, d.variant(), d.keybindings(), columns, lines, d.terminal());
    }

    @Test void initialAreaUsesActualSharedFontMetricsAndRequestedGrid() {
        var f = FontConfig.defaults();
        var natural = new FontSet(f.family(), f.size(), f.fallback(), f.ligatures());
        var defaults = InitialWindowSize.terminalArea(ConfigSnapshot.defaults());
        assertThat(defaults).isEqualTo(new Dimension(150 * natural.cellWidth() + 8, 45 * natural.cellHeight() + 8));
        var large = new FontConfig("Monospaced", 24, java.util.List.of(), false, 1.8f);
        var fonts = new FontSet(large.family(), large.size(), large.fallback(), large.ligatures(), large.lineHeight());
        assertThat(InitialWindowSize.terminalArea(withGridAndFont(80, 30, large)))
            .isEqualTo(new Dimension(80 * fonts.cellWidth() + 8, 30 * fonts.cellHeight() + 8));
        assertThat(InitialWindowSize.terminalArea(withGridAndFont(151, 46, f)))
            .isEqualTo(new Dimension(defaults.width + natural.cellWidth(), defaults.height + natural.cellHeight()));
    }

    @Test void packedSizeRespectsMinimumAndUsableScreenWithScreenWinningWhenTooSmall() {
        Rectangle bounds = new Rectangle(-1600, 30, 1600, 900);
        assertThat(InitialWindowSize.fit(new Dimension(800, 600), new Dimension(200, 100), bounds)).isEqualTo(new Dimension(800, 600));
        assertThat(InitialWindowSize.fit(new Dimension(100, 50), new Dimension(200, 100), bounds)).isEqualTo(new Dimension(200, 100));
        assertThat(InitialWindowSize.fit(new Dimension(2000, 1000), new Dimension(200, 100), bounds)).isEqualTo(new Dimension(1600, 900));
        assertThat(InitialWindowSize.fit(new Dimension(800, 600), new Dimension(1800, 950), bounds)).isEqualTo(new Dimension(1600, 900));
    }

    @Test @DisabledOnOs(OS.WINDOWS)
    void firstPaneAreaFlowsThroughRootAndStaysFixedAcrossPendingCompletionAndReload() throws Exception {
        var pending = new ArrayDeque<Runnable>(); var owner = new WindowContent[1]; var root = new JRootPane[1];
        var area = InitialWindowSize.terminalArea(withGridAndFont(95, 31, FontConfig.defaults()));
        var packed = new Dimension[1];
        edt(() -> {
            owner[0] = content(launcher(pending));
            assertThat(owner[0].currentPane().getPreferredSize()).isEqualTo(new Dimension(958, 821));
            owner[0].currentPane().setPreferredSize(area);
            root[0] = new JRootPane(); root[0].setContentPane(owner[0]); root[0].setJMenuBar(owner[0].menuBar());
            packed[0] = root[0].getPreferredSize();
            assertThat(packed[0].width).isEqualTo(area.width);
            assertThat(packed[0].height).isGreaterThan(area.height);
            root[0].setSize(packed[0]); root[0].doLayout(); owner[0].doLayout();
            owner[0].applyConfiguration(withGridAndFont(200, 100, FontConfig.defaults().withSize(30)), false);
        });
        pending.remove().run(); edt(() -> {
            assertThat(owner[0].currentPane().view()).isNotNull();
            assertThat(owner[0].currentPane().getPreferredSize()).isEqualTo(area);
            assertThat(root[0].getPreferredSize()).isEqualTo(packed[0]);
            assertThat(root[0].getSize()).isEqualTo(packed[0]);
        });
    }
}
