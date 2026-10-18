package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Spec 5.3 and 5.4 through today's windows (plan Ruling 12). */
class OpenPlanTest {
    private static final Path A = Path.of("/work/a"), B = Path.of("/work/b"), NOTES = Path.of("/work/notes.txt"),
        GONE = Path.of("/work/gone");
    private static final Set<Path> DIRECTORIES = Set.of(A, B), FILES = Set.of(NOTES);

    private static OpenPlan plan(boolean windowOpen, Path... paths) {
        return OpenPlan.of(List.of(paths), windowOpen, DIRECTORIES::contains, FILES::contains);
    }

    @Test void aRaiseOpensAWindowOnlyWhenNoneIsOpen() {
        assertThat(plan(true)).isEqualTo(new OpenPlan(false, null, List.of(), List.of()));
        assertThat(plan(false)).isEqualTo(new OpenPlan(true, null, List.of(), List.of()));
    }

    @Test void directoriesBecomeTerminalTabsAndFilesOpenInTheSameWindow() {
        assertThat(plan(true, NOTES, A, B)).isEqualTo(new OpenPlan(false, null, List.of(A, B), List.of(NOTES)));
    }

    @Test void withNoWindowTheFirstDirectoryIsTheNewWindowsTab() {
        assertThat(plan(false, NOTES, A, B)).isEqualTo(new OpenPlan(true, A, List.of(B), List.of(NOTES)));
        assertThat(plan(false, NOTES)).isEqualTo(new OpenPlan(true, null, List.of(), List.of(NOTES)));
    }

    @Test void aMissingPathIsSkippedAndARepeatOpensOnce() {
        assertThat(plan(true, GONE, A, A, NOTES, NOTES)).isEqualTo(new OpenPlan(false, null, List.of(A), List.of(NOTES)));
        assertThat(plan(false, GONE)).as("nothing to open: a window still opens").isEqualTo(new OpenPlan(true, null, List.of(), List.of()));
    }
}
