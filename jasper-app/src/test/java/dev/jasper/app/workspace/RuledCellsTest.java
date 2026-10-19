package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.ConfigSnapshot;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.contributions.ContentHost;
import dev.jasper.app.contributions.ContentRegistry;
import dev.jasper.app.contributions.ContentStatus;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.terminals.PaneSnapshot;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.EnterpriseResources;
import dev.jasper.app.testsupport.MockProfile;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.accessibility.AccessibleState;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JPanel;
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
                assertThat(bar.configButton().getText()).isEqualTo("Config warnings (line 12)");
                assertThat(bar.configButton().getIcon()).as("its painted ▲").isNotNull();
                bar.runItem().click();
                assertThat(opened).as("the run item opens the palette").hasValue(1);
                bar.setConfiguration(state(null));
                assertThat(bar.configButton().isVisible()).as("no problem, no cell").isFalse();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /**
     * IBM Plex has no "▲" and no macOS modifier symbols: the warning cell paints its triangle and keeps Plex for its text,
     * and the search field's keycap takes the platform face (plan 5b, ruling 26), so neither draws a missing glyph.
     */
    @Test void noCellOrKeycapDrawsAGlyphIbmPlexLacks() throws Exception {
        EnterpriseResources.assumePlexResolves();
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var bar = new WindowStatusBar(new RuledCells(), null);
                bar.setMetadata(local("Mac"), "130 × 69", true);
                bar.setConfiguration(state(ConfigDiagnostic.Severity.WARNING));
                var config = bar.configButton();
                assertThat(config.getFont().getFontName(java.util.Locale.ROOT)).as("the warning cell's text").startsWith("IBM Plex Sans");
                assertThat(config.getFont().canDisplayUpTo(config.getText())).as("every glyph of it").isEqualTo(-1);
                assertThat(config.getIcon().getIconWidth()).as("the painted ▲").isPositive();
                String shortcut = "\u21E7\u2318P";
                assertThat(ThemeZone.font(Typography.Role.MONO_SMALL, shortcut)).as("the keycap's face")
                    .isNotEqualTo(ThemeZone.font(Typography.Role.MONO_SMALL));
                assertThat(ThemeZone.font(Typography.Role.MONO_SMALL, shortcut).getFamily(java.util.Locale.ROOT)).isEqualTo(java.awt.Font.MONOSPACED);
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** A session or folder name IBM Plex cannot draw takes the platform face (plan 5b, ruling 26); a Latin one keeps Plex. */
    @Test void aSessionOrFolderNameIbmPlexCannotDrawTakesThePlatformFace() throws Exception {
        EnterpriseResources.assumePlexResolves();
        String name = "\u5831\u544A";
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var bar = new WindowStatusBar(new RuledCells(), null);
                bar.setMetadata(local("Mac"), "130 × 69", true);
                var session = cell(bar, "statusSession");
                assertThat(session.getText()).isEqualTo("Mac");
                assertThat(session.getFont().getFontName(java.util.Locale.ROOT)).as("a Latin session name").startsWith("IBM Plex Sans");
                bar.setMetadata(local(name), "130 × 69", true);
                assertThat(session.getText()).isEqualTo(name);
                assertThat(session.getFont()).as("the session cell").isEqualTo(ThemeZone.font(Typography.Role.STATUS_TEXT, name))
                    .isNotEqualTo(ThemeZone.font(Typography.Role.STATUS_TEXT));
                var folder = cell(bar, "statusFolder");
                assertThat(folder.getFont().canDisplayUpTo(folder.getText())).as("the folder cell draws " + folder.getText()).isEqualTo(-1);
                String path = DesktopTestSupport.HOME.resolve(name).toString();
                bar.setMetadata(new PaneSnapshot("", Optional.of(DesktopTestSupport.HOME.resolve(name)), 130, 69, true, PaneSnapshot.State.RUNNING,
                    OptionalInt.empty(), Optional.empty(), Optional.empty(), "Mac"), "130 × 69", true);
                assertThat(folder.getText()).as("the folder " + path).contains(name);
                assertThat(folder.getFont()).as("the folder cell").isEqualTo(ThemeZone.font(Typography.Role.STATUS_TEXT, folder.getText()))
                    .isNotEqualTo(ThemeZone.font(Typography.Role.STATUS_TEXT));
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    static javax.swing.JLabel cell(WindowStatusBar bar, String name) {
        return (javax.swing.JLabel) java.util.Arrays.stream(bar.getComponents()).filter(c -> name.equals(c.getName())).findFirst().orElseThrow();
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
                assertThat(bar.configButton().getText()).startsWith("Config error");
                assertThat(MockProfile.at(MockProfile.print(bar, 1440, 24), 2860, 30)).as("the error cell is on danger.ground")
                    .isEqualTo(DerivedColor.DANGER_GROUND.color(themes.current().chrome()));
                bar.configButton().doClick();
                assertThat(details).hasValue(1);
                bar.setConfiguration(state(ConfigDiagnostic.Severity.WARNING));
                assertThat(bar.configButton().getText()).startsWith("Config warnings");
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

    /**
     * The 06 Run Dialog mock: under ruled cells the open Run window's item lies on {@code surface.header.active}, its
     * accent ink held readable on it by its pair's guard.
     */
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void theOpenRunWindowsItemTakesTheMocksHeaderTintUnderRuledCells(boolean dark) throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(dark);
            try {
                var run = new RunItem(new AbstractAction("Command Palette") { @Override public void actionPerformed(ActionEvent event) { } }, () -> true);
                var bar = new WindowStatusBar(new RuledCells(), run);
                bar.setMetadata(local("Mac"), "130 × 69", true);
                var image = MockProfile.print(bar, 800, 24);
                var theme = themes.current().chrome();
                var tint = DerivedColor.SURFACE_HEADER_ACTIVE.color(theme);
                assertThat(tint).as("the shipped theme's surface.header.active").isEqualTo(MockProfile.role(dark, "surface.header.active"));
                assertThat(MockProfile.at(image, 4, 24)).as("the open Run window's item").isEqualTo(tint);
                if (!dark) assertThat(MockProfile.deltaE(MockProfile.at(image, 4, 22), MockProfile.role(dark, "surface.header.active")))
                    .as("the Run Dialog mock's item, on the tint").isLessThanOrEqualTo(2.5);
                var pair = WindowPairs.run(true, true);
                assertThat(pair.against()).as("its ink's pair lies on the tint").isEqualTo(DerivedColor.SURFACE_HEADER_ACTIVE);
                var ink = dev.jasper.ui.theme.SurfaceContrast.painted(theme, pair);
                assertThat(pair.guard().holds(ink, tint)).as("the accent ink meets its guard").isTrue();
                assertThat(WindowPairsTest.has(image, ink)).as("the accent ink is painted").isTrue();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void aContentTabsItemsAreCellsRuledBetweenTheirLabels() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var registry = new ContentRegistry();
                var window = UUID.randomUUID();
                registry.addHost(new ContentHost(window, false, entry -> { }, entry -> { }, entry -> { }, () -> { }));
                var entry = registry.open("dev.x.editor", "notes", "notes.txt", window, new JPanel());
                entry.presentation("notes.txt", "", null, false, "/work/notes.txt", "");
                entry.status(List.of(new ContentStatus("Ln 12, Col 4", null, null), new ContentStatus("Plain Text", null, null),
                    new ContentStatus("ISO-8859-1", null, null), new ContentStatus("Spaces: 4", null, null)));
                var bar = new WindowStatusBar(new RuledCells(), null);
                bar.setMetadata(local("zsh"), "130 × 69", true);
                bar.setContent(entry);
                var image = MockProfile.print(bar, 1440, 24);
                var theme = themes.current().chrome();
                var rules = MockProfile.solid(MockProfile.row(image, 30, 0, 2880), 1).stream()
                    .filter(run -> run.length() == 2 && run.color().equals(DerivedColor.BORDER_DIVIDER.color(theme))).map(MockProfile.Run::start).toList();
                var items = bar.contentItems;
                int between = 0;
                for (var child : items.getComponents()) {
                    var button = (JButton) child;
                    int from = 2 * (items.getX() + button.getX() + button.getInsets().left), to = 2 * (items.getX() + button.getX() + button.getWidth() - button.getInsets().right);
                    assertThat(rules).as(button.getText() + "'s text has no rule through it").noneMatch(rule -> rule >= from && rule < to);
                }
                for (int rule : rules) if (rule > 2 * items.getX() && rule < 2 * (items.getX() + items.getWidth())) between++;
                assertThat(between).as("a rule between each two content items").isEqualTo(3);
                assertThat(child(bar, "statusEncoding").getWidth()).as("the session's UTF-8 is not the document's").isZero();
                int pad = UIScale.scale(FlatLafInstaller.installedKit().metrics().value(DesignMetrics.Role.STATUS_CELL_PADDING));
                assertThat(child(bar, "statusSession").getX()).as("a content tab's title has no dot before it").isEqualTo(pad);
                assertThat(child(bar, "statusFolder").getToolTipText()).isEqualTo("/work/notes.txt");
                assertThat(child(bar, "statusSession").getToolTipText()).isNull();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void aNarrowBarKeepsEveryCellInsideItAndApart() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var open = new AbstractAction("Command Palette") { @Override public void actionPerformed(ActionEvent event) { } };
                var bar = new WindowStatusBar(new RuledCells(), new RunItem(open, () -> false));
                bar.setMetadata(local("a-shell-with-a-long-name"), "130 × 69", true);
                bar.setConfiguration(state(ConfigDiagnostic.Severity.WARNING));
                for (int width : new int[] {120, 200, 320}) {
                    MockProfile.print(bar, width, 24);
                    var shown = java.util.Arrays.stream(bar.getComponents()).filter(c -> c.getWidth() > 0)
                        .sorted(java.util.Comparator.comparingInt(java.awt.Component::getX)).toList();
                    for (int i = 0; i < shown.size(); i++) {
                        var cell = shown.get(i);
                        assertThat(cell.getX() + cell.getWidth()).as(width + ": " + cell.getName() + " stays inside the bar").isLessThanOrEqualTo(width);
                        if (i > 0) assertThat(cell.getX()).as(width + ": " + cell.getName() + " starts after " + shown.get(i - 1).getName())
                            .isGreaterThanOrEqualTo(shown.get(i - 1).getX() + shown.get(i - 1).getWidth());
                    }
                }
                bar.setMetadata(local("zsh"), "130 × 69", true);
                MockProfile.print(bar, 1440, 24);
                assertThat(child(bar, "statusSession").getToolTipText()).isEqualTo("Shell integration active");
                assertThat(child(bar, "statusFolder").getToolTipText()).as("the folder's full path").isEqualTo(DesktopTestSupport.HOME.toString());
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void theRunItemIsAnAccessibleToggleThatRunsOnRelease() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var open = new AtomicBoolean();
                var opened = new AtomicInteger();
                var run = new RunItem(new AbstractAction("Command Palette") { @Override public void actionPerformed(ActionEvent event) { opened.incrementAndGet(); } }, open::get);
                run.setSize(run.getPreferredSize());
                var context = run.getAccessibleContext();
                assertThat(context.getAccessibleStateSet().contains(AccessibleState.CHECKED)).isFalse();
                open.set(true);
                assertThat(context.getAccessibleStateSet().contains(AccessibleState.CHECKED)).as("checked while the palette is open").isTrue();
                assertThat(context.getAccessibleAction().getAccessibleActionCount()).isEqualTo(1);
                assertThat(context.getAccessibleAction().doAccessibleAction(0)).isTrue();
                assertThat(opened).hasValue(1);
                long when = System.currentTimeMillis();
                run.dispatchEvent(new MouseEvent(run, MouseEvent.MOUSE_PRESSED, when, InputEvent.BUTTON1_DOWN_MASK, 4, 4, 1, false, MouseEvent.BUTTON1));
                run.dispatchEvent(new MouseEvent(run, MouseEvent.MOUSE_RELEASED, when, 0, 9, 6, 1, false, MouseEvent.BUTTON1));
                assertThat(opened).as("a release over the item after the pointer moved").hasValue(2);
                run.dispatchEvent(new MouseEvent(run, MouseEvent.MOUSE_PRESSED, when, InputEvent.BUTTON1_DOWN_MASK, 4, 4, 1, false, MouseEvent.BUTTON1));
                run.dispatchEvent(new MouseEvent(run, MouseEvent.MOUSE_RELEASED, when, 0, run.getWidth() + 5, 4, 1, false, MouseEvent.BUTTON1));
                assertThat(opened).as("a release off the item cancels").hasValue(2);
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    private static javax.swing.JComponent child(WindowStatusBar bar, String name) {
        return (javax.swing.JComponent) java.util.Arrays.stream(bar.getComponents()).filter(c -> name.equals(c.getName())).findFirst().orElseThrow();
    }

    @Test void aLocalPaneIsNamedByItsShellAndARemoteOneByItsHost() {
        assertThat(WindowTitle.sessionCell(local("zsh"))).isEqualTo("zsh");
        var provided = new PaneSnapshot("", Optional.empty(), 80, 24, true, PaneSnapshot.State.RUNNING, OptionalInt.empty(),
            Optional.of("dev.jasper.remote"), Optional.empty(), "Mac");
        assertThat(WindowTitle.sessionCell(provided)).isEqualTo("Mac");
        assertThat(WindowTitle.sessionCell(null)).isEmpty();
    }
}
