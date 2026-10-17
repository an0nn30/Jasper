package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.terminals.PaneSnapshot;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockProfile;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractAction;
import org.junit.jupiter.api.Test;

/** Spec 10.6 and the Workstation and Run Dialog mocks: ruled status cells and the run item. */
class RuledCellsTest {
    static PaneSnapshot local(String shell) {
        return new PaneSnapshot("", Optional.of(DesktopTestSupport.HOME), 130, 69, true, PaneSnapshot.State.RUNNING, OptionalInt.empty(),
            Optional.empty(), Optional.empty(), shell);
    }

    static ConfigService.State state(ConfigDiagnostic.Severity severity) {
        Path file = Path.of("config.toml");
        var diagnostics = severity == null ? List.<ConfigDiagnostic>of() : List.of(new ConfigDiagnostic(severity, file, 12, 1, "x", "bad"));
        return new ConfigService.State(ConfigSnapshot.defaults(), diagnostics, file, true);
    }

    @Test void theCellsAreTheMocks() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var opened = new AtomicInteger();
                var open = new AbstractAction("Command Palette") { @Override public void actionPerformed(ActionEvent event) { opened.incrementAndGet(); } };
                var bar = new WindowStatusBar(new RuledCells(), new RunItem(open, () -> false));
                bar.setMetadata(local("Mac"), "130 × 69", true);
                var model = new Contributions();
                var vault = model.addStatus("dev.x.vault", false, 10); vault.setText("Vault: Locked");
                var ssh = model.addStatus("dev.x.ssh", false, 20); ssh.setText("1 SSH session");
                bar.setContributed(model.status(), id -> null);
                bar.setConfiguration(state(ConfigDiagnostic.Severity.WARNING));
                var image = MockProfile.print(bar, 1440, 24);
                var palette = themes.current().chrome().semantic();
                var theme = themes.current().chrome();
                assertThat(MockProfile.at(image, 400, 1)).as("the top rule, 1 px (2 rows at 2x)").isEqualTo(palette.color(SemanticColor.BORDER_WINDOW));
                assertThat(MockProfile.at(image, 400, 24)).as("surface.app").isEqualTo(palette.color(SemanticColor.SURFACE_APP));
                var rules = MockProfile.solid(MockProfile.row(image, 30, 0, 2880), 1).stream()
                    .filter(run -> run.length() == 2 && run.color().equals(DerivedColor.BORDER_DIVIDER.color(theme))).map(MockProfile.Run::start).toList();
                assertThat(rules).as("a rule after the run, session and folder cells and before each right cell").hasSize(3 + 5);
                assertThat(MockProfile.at(image, 2860, 30)).as("the warning cell reaches the edge").isEqualTo(DerivedColor.WARNING_GROUND.color(theme));
                assertThat(bar.configButton().getText()).isEqualTo("▲ Config warnings (line 12)");
                bar.runItem().click();
                assertThat(opened).as("the run item opens the palette").hasValue(1);
                bar.setConfiguration(state(null));
                assertThat(bar.configButton().isVisible()).as("no problem, no cell").isFalse();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void theConfigurationCellShowsOnlyWhileThereIsAProblem() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var bar = new WindowStatusBar(new RuledCells(), null);
                var details = new AtomicInteger();
                bar.onConfigurationDetails = details::incrementAndGet;
                bar.setConfiguration(state(ConfigDiagnostic.Severity.ERROR));
                assertThat(bar.configButton().isVisible()).isTrue();
                assertThat(bar.configButton().getText()).startsWith("▲ Config error");
                bar.configButton().doClick();
                assertThat(details).hasValue(1);
                bar.setConfiguration(state(ConfigDiagnostic.Severity.WARNING));
                assertThat(bar.configButton().getText()).startsWith("▲ Config warnings");
                bar.setConfiguration(state(null));
                assertThat(bar.configButton().isVisible()).isFalse();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void theRunItemShowsWhileThePaletteIsOpen() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var open = new AtomicBoolean();
                var run = new RunItem(new AbstractAction("Command Palette") { @Override public void actionPerformed(ActionEvent event) { open.set(true); } }, open::get);
                var bar = new WindowStatusBar(new SlashSegments(), run);
                bar.setMetadata(local("zsh"), "80 × 24", true);
                var closed = MockProfile.print(bar, 800, 24);
                open.set(true);
                var shown = MockProfile.print(bar, 800, 24);
                int x = 2 * (run.getX() + 2), y = 24;
                assertThat(MockProfile.at(shown, x, y)).as("the active toggle")
                    .isEqualTo(themes.current().chrome().semantic().color(SemanticColor.SURFACE_CONTROL_SELECTED));
                assertThat(MockProfile.at(closed, x, y)).isNotEqualTo(MockProfile.at(shown, x, y));
                assertThat(run.getX()).as("it leads the bar").isZero();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void aLocalPaneIsNamedByItsShellAndARemoteOneByItsHost() {
        assertThat(WindowTitle.sessionCell(local("zsh"))).isEqualTo("zsh");
        var provided = new PaneSnapshot("", Optional.empty(), 80, 24, true, PaneSnapshot.State.RUNNING, OptionalInt.empty(),
            Optional.of("dev.jasper.remote"), Optional.empty(), "Mac");
        assertThat(WindowTitle.sessionCell(provided)).isEqualTo("Mac");
        assertThat(WindowTitle.sessionCell(null)).isEmpty();
    }
}
