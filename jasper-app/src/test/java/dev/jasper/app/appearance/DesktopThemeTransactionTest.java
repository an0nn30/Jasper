package dev.jasper.app.appearance;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DesktopThemeTransactionTest {
    @Test void equalDesktopSnapshotDoesNotReinstallTheLookAndFeel() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            var installs = new java.util.concurrent.atomic.AtomicInteger();
            var selection = ThemeSelection.of(ThemeLibrary.bundledOnly(), "desktop-colors", "desktop-colors",
                dev.jasper.app.config.TerminalSchemeChoice.MATCH, (key, problem) -> { throw new AssertionError(problem); });
            var started = ThemeController.start(true, selection, dev.jasper.ui.kit.Kit.DEFAULT,
                kit -> theme -> { installs.incrementAndGet(); return true; }, failure -> { throw failure; });
            var controller = started.controller();
            try {
                var first = controller.current().chrome();
                var equalCopy = new dev.jasper.ui.theme.GeneratedTheme(first.name(), first.dark(), first.author(),
                    first.semantic(), first.terminal(), first.editor(), first.syntax(), first.glass(), first.derivedOverrides());
                int before = installs.get();
                controller.configure(true, selection, dev.jasper.app.config.UiFontConfig.defaults(), equalCopy);
                assertThat(installs.get()).isEqualTo(before);
                assertThat(controller.current().chrome()).isEqualTo(first);
            } finally { controller.close(); }
        });
    }
    @Test void changedPalettePublishesOnceToEveryBindingAndAnEqualCopyDoesNothing() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            var installs = new java.util.concurrent.atomic.AtomicInteger();
            var selection = selection();
            try (var controller = ThemeController.start(true, selection, dev.jasper.ui.kit.Kit.DEFAULT,
                    kit -> theme -> { installs.incrementAndGet(); return true; }, failure -> { throw failure; }).controller()) {
                var firstWindow = new java.util.concurrent.atomic.AtomicInteger();
                var secondWindow = new java.util.concurrent.atomic.AtomicInteger();
                controller.appearance().binding().subscribe(theme -> firstWindow.incrementAndGet());
                controller.appearance().binding().subscribe(theme -> secondWindow.incrementAndGet());
                var changed = desktop();
                int before = installs.get();
                controller.configure(true, selection, dev.jasper.app.config.UiFontConfig.defaults(), changed);
                controller.configure(true, selection, dev.jasper.app.config.UiFontConfig.defaults(), desktop());
                assertThat(installs.get()).isEqualTo(before + 1);
                assertThat(firstWindow.get()).isEqualTo(2);
                assertThat(secondWindow.get()).isEqualTo(2);
            }
        });
    }

    @Test void failedTransactionRollsBackAndTheEqualRequestedSnapshotCanRetry() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            var fail = new java.util.concurrent.atomic.AtomicBoolean();
            var selection = selection();
            try (var controller = ThemeController.start(true, selection, dev.jasper.ui.kit.Kit.DEFAULT,
                    kit -> theme -> !fail.get(), failure -> { throw failure; }).controller()) {
                var before = controller.current();
                fail.set(true);
                assertThatThrownBy(() -> controller.configure(true, selection, dev.jasper.app.config.UiFontConfig.defaults(), desktop()))
                    .isInstanceOf(ThemeController.InstallationFailure.class);
                assertThat(controller.current()).isSameAs(before);
                fail.set(false);
                controller.configure(true, selection, dev.jasper.app.config.UiFontConfig.defaults(), desktop());
                assertThat(controller.current().chrome().name()).isEqualTo("Desktop colors");
            }
        });
    }

    @Test void fontAndFixedTerminalSchemeChangesKeepTheDesktopSnapshot() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            var selection = selection();
            var installs = new java.util.concurrent.atomic.AtomicInteger();
            try (var controller = ThemeController.start(true, selection, dev.jasper.ui.kit.Kit.DEFAULT,
                    kit -> theme -> { installs.incrementAndGet(); return true; }, failure -> { throw failure; }).controller()) {
                var desktop = desktop();
                controller.configure(true, selection, dev.jasper.app.config.UiFontConfig.defaults(), desktop);
                int before = installs.get();
                controller.configure(true, selection, new dev.jasper.app.config.UiFontConfig("system", 17));
                assertThat(installs.get()).isEqualTo(before + 1);
                controller.configureScheme(dev.jasper.app.config.TerminalSchemeChoice.parse("slate-cobalt/high-contrast").orElseThrow());
                assertThat(controller.current().chrome()).isSameAs(desktop);
                assertThat(controller.current().palette().background()).isEqualTo(
                    dev.jasper.ui.theme.JasperTheme.bundled().schemes().stream()
                        .filter(scheme -> scheme.id().key().equals("high-contrast")).findFirst().orElseThrow().colors().background());
                assertThat(installs.get()).isEqualTo(before + 1);
            }
        });
    }

    private static ThemeSelection selection() {
        return ThemeSelection.of(ThemeLibrary.bundledOnly(), "desktop-colors", "desktop-colors",
            dev.jasper.app.config.TerminalSchemeChoice.MATCH, (key, problem) -> { throw new AssertionError(problem); });
    }
    private static dev.jasper.ui.theme.GeneratedTheme desktop() {
        var base = dev.jasper.ui.theme.JasperTheme.bundled().generated(true);
        return new dev.jasper.ui.theme.GeneratedTheme("Desktop colors", true, base.author(), base.semantic(),
            base.terminal(), base.editor(), base.syntax(), base.glass(), base.derivedOverrides());
    }

}
