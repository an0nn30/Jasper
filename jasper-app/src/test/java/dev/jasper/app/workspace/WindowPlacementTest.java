package dev.jasper.app.workspace;

import dev.jasper.app.persistence.UiState;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Rectangle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class WindowPlacementTest {
    @TempDir Path dir;

    @Test void closeSavesExactBoundsAndTheLastWindowWinsAcrossReloads() {
        Path file = dir.resolve("ui-state.toml");
        UiState state = UiState.load(file);
        var first = new WindowPlacement(state);
        var last = new WindowPlacement(state);
        last.observe(new Rectangle(-1200, 75, 1103, 807), Frame.NORMAL);
        first.observe(new Rectangle(90, 80, 800, 600), Frame.NORMAL);
        first.save(Frame.NORMAL); last.save(Frame.NORMAL);
        var restored = new WindowPlacement(UiState.load(file));
        assertThat(restored.restore(new Dimension(300, 200), List.of(new Rectangle(-1600, 30, 1600, 1000))))
            .hasValue(new Rectangle(-1200, 75, 1103, 807));
        assertThat(restored.extendedState()).isEqualTo(Frame.NORMAL);
    }

    @Test void maximizingAndMinimizingKeepTheNormalBoundsAndRestoreUnminimized() {
        Path file = dir.resolve("ui-state.toml");
        var placement = new WindowPlacement(UiState.load(file));
        placement.observe(new Rectangle(100, 90, 1103, 807), Frame.NORMAL);
        placement.observe(new Rectangle(0, 0, 1600, 1000), Frame.MAXIMIZED_BOTH);
        placement.observe(new Rectangle(0, 0, 1, 1), Frame.MAXIMIZED_BOTH | Frame.ICONIFIED);
        placement.save(Frame.MAXIMIZED_BOTH | Frame.ICONIFIED);
        var restored = new WindowPlacement(UiState.load(file));
        assertThat(restored.restore(new Dimension(300, 200), List.of(new Rectangle(0, 0, 1600, 1000))))
            .hasValue(new Rectangle(100, 90, 1103, 807));
        assertThat(restored.extendedState()).isEqualTo(Frame.MAXIMIZED_BOTH);
    }

    @Test void aWindowSpanningUnchangedDisplaysKeepsItsExactBounds() {
        var state = UiState.inMemory();
        var placement = new WindowPlacement(state);
        placement.observe(new Rectangle(1200, 100, 1000, 700), Frame.NORMAL);
        placement.save(Frame.NORMAL);
        assertThat(new WindowPlacement(state).restore(new Dimension(300, 200),
            List.of(new Rectangle(0, 0, 1600, 1000), new Rectangle(1600, 0, 1600, 1000))))
            .hasValue(new Rectangle(1200, 100, 1000, 700));
    }

    @Test void aRemovedMonitorFitsTheWindowOntoTheRemainingScreen() {
        var state = UiState.inMemory();
        var placement = new WindowPlacement(state);
        placement.observe(new Rectangle(-1700, 80, 1500, 1100), Frame.NORMAL);
        placement.save(Frame.NORMAL);
        assertThat(new WindowPlacement(state).restore(new Dimension(300, 200), List.of(new Rectangle(0, 25, 1280, 775))))
            .hasValue(new Rectangle(0, 25, 1280, 775));
    }

    @Test void malformedMainWindowFallsBackWithoutLosingTheDock() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, "version = 2\n[dock]\nleft_width = 321\n[main_window]\nx = 10\ny = 20\nwidth = -1\nheight = 700\n");
        UiState state = UiState.load(file);
        assertThat(state.dock().leftWidth()).isEqualTo(321);
        assertThat(new WindowPlacement(state).restore(new Dimension(300, 200), List.of(new Rectangle(0, 0, 1600, 1000))))
            .isEmpty();
    }
}
