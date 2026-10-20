package dev.jasper.app.appearance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.GeneratedTheme;
import dev.jasper.ui.theme.JasperTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Spec 4.7: the four steps of the fallback at start, and which of them blame the kit. */
class ThemeControllerFallbackTest {
    static final Kit CLASSIC = new Kit("classic-test", Kit.DEFAULT.metrics().with(DesignMetrics.Role.BUTTON_HEIGHT, 22), Kit.DEFAULT.type(),
        Kit.DEFAULT.faces(), Kit.DEFAULT.controls(), Kit.DEFAULT.colors(), Kit.DEFAULT.uiKeys());
    /** Light, so the fallback theme (Slate Cobalt Dark) is a different variant. */
    static final ThemeSelection LIGHT = ThemeSelection.slateCobalt(TerminalSchemeChoice.MATCH);

    record Run(ThemeController.Started started, List<ThemeController.InstallationFailure> failures) { }

    /** Starts with installers that fail where {@code fails} says (kit, theme), and otherwise install nothing. */
    static Run start(Kit kit, BiPredicate<Kit, GeneratedTheme> fails) throws Exception {
        var failures = new ArrayList<ThemeController.InstallationFailure>();
        var started = new ThemeController.Started[1];
        Function<Kit, Predicate<GeneratedTheme>> installers = chosen -> theme -> {
            if (fails.test(chosen, theme)) throw new IllegalStateException("cannot install " + theme.name() + " under " + chosen.id());
            return true;
        };
        SwingUtilities.invokeAndWait(() -> started[0] = ThemeController.start(false, LIGHT, kit, installers, failures::add));
        return new Run(started[0], failures);
    }

    @Test void theSelectionUnderTheChosenKitIsTheFirstStep() throws Exception {
        var run = start(CLASSIC, (kit, theme) -> false);
        assertThat(run.started().step()).isEqualTo(ThemeController.Step.CHOSEN);
        assertThat(run.started().step().blamesKit()).isFalse();
        assertThat(run.started().controller().kit()).isSameAs(CLASSIC);
        assertThat(run.failures()).isEmpty();
    }

    @Test void theSelectionUnderTheDefaultKitBlamesTheKit() throws Exception {
        var run = start(CLASSIC, (kit, theme) -> kit == CLASSIC);
        assertThat(run.started().step()).isEqualTo(ThemeController.Step.DEFAULT_KIT);
        assertThat(run.started().step().blamesKit()).isTrue();
        assertThat(run.started().controller().kit()).isSameAs(Kit.DEFAULT);
        SwingUtilities.invokeAndWait(() -> assertThat(run.started().controller().current().chrome().dark()).as("the user's Light").isFalse());
        assertThat(run.failures()).hasSize(1);
    }

    @Test void slateCobaltDarkUnderTheChosenKitBlamesTheTheme() throws Exception {
        var run = start(CLASSIC, (kit, theme) -> !theme.dark());
        assertThat(run.started().step()).isEqualTo(ThemeController.Step.FALLBACK_THEME);
        assertThat(run.started().step().blamesKit()).isFalse();
        assertThat(run.started().controller().kit()).isSameAs(CLASSIC);
        SwingUtilities.invokeAndWait(() -> assertThat(run.started().controller().current().chrome()).isSameAs(JasperTheme.bundled().generated(true)));
        assertThat(run.failures()).hasSize(2);
    }

    @Test void slateCobaltDarkUnderTheDefaultKitIsTheLastStepAndItsFailurePropagates() throws Exception {
        var run = start(CLASSIC, (kit, theme) -> kit == CLASSIC || !theme.dark());
        assertThat(run.started().step()).isEqualTo(ThemeController.Step.FALLBACK_THEME_DEFAULT_KIT);
        assertThat(run.started().step().blamesKit()).isTrue();
        assertThat(run.failures()).hasSize(3);
        assertThatThrownBy(() -> start(CLASSIC, (kit, theme) -> true)).hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test void theDefaultKitHasOnlyTodaysTwoSteps() throws Exception {
        var run = start(Kit.DEFAULT, (kit, theme) -> !theme.dark());
        assertThat(run.started().step()).isEqualTo(ThemeController.Step.FALLBACK_THEME);
        assertThat(run.failures()).as("no default-kit retry of the same kit").hasSize(1);
        assertThatThrownBy(() -> start(Kit.DEFAULT, (kit, theme) -> true)).hasRootCauseInstanceOf(IllegalStateException.class);
    }
}
