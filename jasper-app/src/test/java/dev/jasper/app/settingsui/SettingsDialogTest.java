package dev.jasper.app.settingsui;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.until;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.config.SettingsEdit;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.awt.Rectangle;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class SettingsDialogTest {
    private static final Path FILE = Path.of("/fixture/config.toml");
    private final AuxiliaryWindows windows = new AuxiliaryWindows(UiState.inMemory(), surface -> new AuxiliarySurface.Shell(
        () -> { }, () -> { }, () -> { }, title -> { }, () -> new Rectangle(0, 0, 1000, 700), (title, initial) -> Optional.empty()));
    private final List<SettingsEdit> saved = new CopyOnWriteArrayList<>();
    private final List<URI> browsed = new ArrayList<>();
    private final List<Consumer<ConfigService.State>> listeners = new ArrayList<>();
    private final boolean[] discard = {true};
    private final int[] asked = {0};
    private CompletableFuture<ConfigService.State> saveResult;
    private ConfigService.State state = state("[window]\ntab_height = 44\n");

    private static ConfigService.State state(String text) {
        var parsed = ConfigLoader.parse(FILE, text, true);
        return new ConfigService.State(parsed.snapshot(), parsed.diagnostics(), FILE, true, parsed.legacyKeys(), parsed.deadTables(),
            parsed.values(), parsed.rejected());
    }

    private final List<String> opened = new ArrayList<>();
    private final List<PageContext> contexts = new ArrayList<>();
    private final FakePage fake = new FakePage("keymap", "Keymap", List.of("shortcuts", "copy"));

    private SettingsDialog.Hooks hooks() {
        return new SettingsDialog.Hooks(edit -> {
                saved.add(edit);
                return saveResult != null ? saveResult : CompletableFuture.completedFuture(state("[window]\ntab_height = 50\n"));
            }, () -> state, listener -> { listeners.add(listener); return new Subscription(() -> listeners.remove(listener)); },
            () -> opened.add("config"), () -> opened.add("migrate"), browsed::add,
            (parent, count) -> { asked[0] = count; return discard[0]; });
    }

    private SettingsDialog dialog() {
        return new SettingsDialog(windows, context -> {
            contexts.add(context);
            return CorePages.pages(ThemeChoices.of(() -> List.of("Jasper Dark", "IntelliJ Light")), new CorePages.SystemActions(() -> FILE,
                    () -> { }, () -> { }, () -> { }, () -> { }, () -> false), page -> fake,
                page -> new GroupPage("plugins", "Plugins", Optional.empty(), false, List::of, page)).apply(context);
        }, hooks());
    }

    @Test void aFragmentRevealsItsPartBeforeThePageIsSelectedEvenWhenItIsAlreadyShown() throws Exception {
        var revealed = new ArrayList<String>();
        var dialog = new SettingsDialog(windows, context -> CorePages.pages(ThemeChoices.of(() -> List.of("Jasper Dark")),
                new CorePages.SystemActions(() -> FILE, () -> { }, () -> { }, () -> { }, () -> { }, () -> false), ShellChoices.NONE,
                page -> fake, page -> new Revealing(new GroupPage("plugins", "Plugins", Optional.empty(), false, List::of, page), revealed), page -> List.of()).apply(context), hooks());
        edt(() -> {
            dialog.open(null, "appearance");
            dialog.select("plugins#available");
            assertThat(dialog.currentPage()).isEqualTo("plugins");
            dialog.select("plugins#installed");
            assertThat(revealed).containsExactly("reveal available", "selected", "reveal installed", "selected");
            dialog.back();
            assertThat(dialog.currentPage()).isEqualTo("appearance");
        });
    }

    /** Records what it is asked to reveal and each selection; everything else is the schema page it wraps. */
    private record Revealing(SettingsPage header, List<String> revealed) implements SettingsPage {
        @Override public String id() { return header.id(); }
        @Override public String title() { return header.title(); }
        @Override public Optional<String> parent() { return header.parent(); }
        @Override public List<String> searchTerms() { return header.searchTerms(); }
        @Override public javax.swing.JComponent component() { return header.component(); }
        @Override public boolean modified() { return header.modified(); }
        @Override public void reset() { header.reset(); }
        @Override public CompletableFuture<Void> apply() { return header.apply(); }
        @Override public void highlight(String query) { header.highlight(query); }
        @Override public void dispose() { header.dispose(); }
        @Override public void reveal(String fragment) { revealed.add("reveal " + fragment); }
        @Override public void selected() { header.selected(); revealed.add("selected"); }
    }

    @Test void themeAndWorkspaceSchemeShareOneSaveRetainFailedDraftAndCancelDiscardsThem() throws Exception {
        var dialog = new SettingsDialog(windows, context -> CorePages.pages(ThemeChoices.of(() -> List.of("Darcula", "Jasper Dark", "Jasper Light")),
            new CorePages.SystemActions(() -> FILE, () -> {}, () -> {}, () -> {}, () -> {}, () -> false),
            page -> fake, page -> new GroupPage("plugins", "Plugins", Optional.empty(), false, List::of, page)).apply(context), hooks());
        saveResult = CompletableFuture.failedFuture(new IllegalStateException("Setting changed on disk"));
        edt(() -> {
            dialog.open(null, "appearance");
            dialog.draft().stage(CoreSettings.UI_THEME_NAME, Optional.of("Jasper Light"));
            dialog.draft().stage(CoreSettings.UI_THEME_WORKSPACE, Optional.of("Darcula"));
            dialog.apply();
        });
        edt(() -> {});
        edt(() -> {
            assertThat(saved).hasSize(1);
            assertThat(saved.getFirst().changes()).containsKeys(CoreSettings.UI_THEME_NAME, CoreSettings.UI_THEME_WORKSPACE);
            assertThat(dialog.draft().modified()).isTrue();
        });
        state = state("ui.theme.name='Jasper Light'\nui.theme.workspace='Darcula'\n"); saveResult = CompletableFuture.completedFuture(state);
        edt(dialog::apply); edt(() -> {});
        edt(() -> {
            assertThat(saved).hasSize(2); assertThat(dialog.draft().modified()).isFalse();
            dialog.draft().stage(CoreSettings.UI_THEME_WORKSPACE, Optional.of("Jasper Dark")); dialog.cancel();
            assertThat(dialog.isOpen()).isFalse(); dialog.open(null,"appearance");
            assertThat(dialog.draft().value(CoreSettings.UI_THEME_WORKSPACE)).isEqualTo("Darcula"); dialog.cancel();
        });
    }

    @Test void openingShowsTheTreeHidesEmptyToolsAndSelectsTheDefaultPage() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, null);
            assertThat(dialog.isOpen()).isTrue();
            assertThat(dialog.view().tree.visibleIds()).contains("appearance-behavior", "appearance", "terminal.shell", "plugins")
                .doesNotContain("tools");
            assertThat(dialog.currentPage()).isEqualTo("appearance");
            assertThat(dialog.view().breadcrumb.getText()).isEqualTo("Appearance & Behavior › Appearance");
            assertThat(dialog.view().apply.isEnabled()).isFalse();
        });
    }

    @Test void pickingAThemeFromTheListEnablesApply() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "appearance");
            // The dialog's window replays the current theme when it opens, which updates every component's UI.
            javax.swing.SwingUtilities.updateComponentTreeUI(dialog.view());
            @SuppressWarnings("unchecked") var box = (javax.swing.JComboBox<String>) find(dialog.view(), javax.swing.JComboBox.class);
            box.setSelectedIndex(1);
            assertThat(dialog.view().apply.isEnabled()).isTrue();
        });
    }

    static java.awt.Component find(java.awt.Component c, Class<?> type) {
        if (type.isInstance(c)) return c;
        if (c instanceof java.awt.Container k) for (var x : k.getComponents()) { var f = find(x, type); if (f != null) return f; }
        return null;
    }

    @Test void backAndForwardWalkThePagesVisited() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "window");
            dialog.select("terminal.font");
            dialog.select("terminal.cursor");
            dialog.back();
            assertThat(dialog.currentPage()).isEqualTo("terminal.font");
            dialog.back();
            assertThat(dialog.currentPage()).isEqualTo("window");
            assertThat(dialog.view().back.isEnabled()).isFalse();
            dialog.forward();
            assertThat(dialog.currentPage()).isEqualTo("terminal.font");
            dialog.select("desk-buddy");
            assertThat(dialog.view().forward.isEnabled()).isFalse();
        });
    }

    @Test void searchFiltersTheTreeCountsMatchesAndRestoresOnClear() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "desk-buddy");
            dialog.search("ligatures");
            assertThat(dialog.view().tree.visibleIds()).containsExactly("terminal", "terminal.font");
            assertThat(dialog.view().tree.count("terminal.font")).isPositive();
            assertThat(dialog.currentPage()).isEqualTo("terminal.font");
            dialog.search("shortcuts");
            assertThat(dialog.view().tree.visibleIds()).containsExactly("keymap");
            assertThat(fake.highlights).contains("shortcuts");
            dialog.search("");
            assertThat(dialog.view().tree.visibleIds()).contains("appearance", "terminal.shell");
            assertThat(dialog.currentPage()).isEqualTo("keymap");
        });
    }

    @Test void applySavesTheDraftThenOtherPagesAndOkCloses() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "window");
            dialog.draft().stage(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(50));
            fake.modified = true;
            dialog.refreshNow();
            assertThat(dialog.view().apply.isEnabled()).isTrue();
            assertThat(dialog.view().tree.isModified("window")).isTrue();
            dialog.apply();
        });
        until(() -> fake.applied == 1);
        edt(() -> {
            assertThat(saved).singleElement().satisfies(edit -> assertThat(edit.changes()).containsKey(CoreSettings.WINDOW_TAB_HEIGHT));
            assertThat(dialog.draft().modified()).isFalse();
            fake.modified = false;
            dialog.refreshNow();
            assertThat(dialog.view().apply.isEnabled()).isFalse();
            dialog.draft().stage(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(60));
            dialog.ok();
        });
        until(() -> !dialog.isOpen());
    }

    @Test void aFailedSaveKeepsTheEditsAndSaysWhy() throws Exception {
        var dialog = dialog();
        saveResult = CompletableFuture.failedFuture(new IllegalArgumentException("window.tab_height changed on disk. Review the new value and try again."));
        edt(() -> {
            dialog.open(null, "window");
            dialog.draft().stage(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(50));
            dialog.ok();
        });
        until(() -> !dialog.view().message.getText().isEmpty());
        edt(() -> {
            assertThat(dialog.view().message.getText()).contains("changed on disk");
            assertThat(dialog.isOpen()).isTrue();
            assertThat(dialog.draft().modified()).isTrue();
        });
    }

    @Test void cancellingWithChangesAsksFirst() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "window");
            dialog.draft().stage(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(50));
            discard[0] = false;
            dialog.cancel();
            assertThat(asked[0]).isEqualTo(1);
            assertThat(dialog.isOpen()).isTrue();
            discard[0] = true;
            dialog.cancel();
            assertThat(dialog.isOpen()).isFalse();
        });
    }

    @Test void bannersFollowTheStateAndARejectedFileBlocksApply() throws Exception {
        state = state("[font]\nsize = 18\n");
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "window");
            assertThat(dialog.view().banner.isVisible()).isTrue();
            assertThat(dialog.view().bannerText.getText()).isEqualTo("config.toml uses old setting names.");
            dialog.view().bannerButton.doClick();
            assertThat(opened).containsExactly("migrate");
            var broken = state("[window\n");
            listeners.forEach(listener -> listener.accept(new ConfigService.State(broken.snapshot(), broken.diagnostics(), FILE, true,
                List.of(), List.of(), state.values(), true)));
            assertThat(dialog.view().bannerText.getText()).isEqualTo("config.toml has errors, so changes can't be saved until they're fixed.");
            dialog.draft().stage(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(50));
            assertThat(dialog.view().apply.isEnabled()).isFalse();
            assertThat(dialog.view().ok.isEnabled()).isFalse();
        });
    }

    @Test void changesOnDiskReachRowsWithoutPendingEdits() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "window");
            dialog.draft().stage(CoreSettings.WINDOW_STATUS_BAR, Optional.of(false));
            listeners.forEach(listener -> listener.accept(state("[window]\ntab_height = 60\n")));
            assertThat(dialog.draft().value(CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(60);
            assertThat(dialog.draft().value(CoreSettings.WINDOW_STATUS_BAR)).isFalse();
        });
    }

    @Test void helpOpensThePagesSectionOfTheGuide() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "terminal.font");
            dialog.view().help.doClick();
        });
        assertThat(browsed).containsExactly(URI.create(SettingsDialog.HELP_URL + "terminal-colors"));
    }

    @Test void openingAgainSelectsTheRequestedPage() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "window");
            dialog.open(null, "keymap");
            assertThat(dialog.currentPage()).isEqualTo("keymap");
        });
    }

    @Test void showingAPageTellsItAndPagesCanOpenDialogsOverSettings() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "window");
            assertThat(fake.selections).isZero();
            dialog.select("keymap");
            assertThat(fake.selections).isEqualTo(1);
            dialog.select("window");
            dialog.back();
            assertThat(fake.selections).as("Back shows it again").isEqualTo(2);
            var surface = contexts.getLast().surface().get();
            assertThat(surface).isNotNull();
            assertThat(surface.title()).isEqualTo("Settings");
            dialog.cancel();
            assertThat(contexts.getLast().surface().get()).as("gone once the dialog closes").isNull();
        });
    }

    /** A page standing in for Keymap: settable modification, counted applies and recorded highlights. */
    static final class FakePage implements SettingsPage {
        final String id;
        final String title;
        final List<String> terms;
        final List<String> highlights = new ArrayList<>();
        boolean modified;
        int applied;
        int selections;
        FakePage(String id, String title, List<String> terms) { this.id = id; this.title = title; this.terms = terms; }
        @Override public String id() { return id; }
        @Override public String title() { return title; }
        @Override public Optional<String> parent() { return Optional.empty(); }
        @Override public List<String> searchTerms() { return terms; }
        @Override public javax.swing.JComponent component() { return new javax.swing.JPanel(); }
        @Override public boolean modified() { return modified; }
        @Override public void reset() { modified = false; }
        @Override public CompletableFuture<Void> apply() { applied++; return CompletableFuture.completedFuture(null); }
        @Override public void highlight(String query) { highlights.add(query); }
        @Override public void dispose() { }
        @Override public void selected() { selections++; }
    }
}
