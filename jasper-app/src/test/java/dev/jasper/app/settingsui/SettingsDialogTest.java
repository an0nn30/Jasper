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
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
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
            (parent, count) -> { asked[0] = count; return discard[0]; }, KitTestSupport::context);
    }

    static javax.swing.JButton button(SettingsDialog dialog, String id) { return dialog.view().control(id, javax.swing.JButton.class); }
    static String label(SettingsDialog dialog, String id) { return dialog.view().control(id, javax.swing.JLabel.class).getText(); }
    /** Presses a kit number field's arrow-key step, as the user does to change a value without typing. */
    static void step(javax.swing.JComponent input, int key) {
        var name = input.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).get(javax.swing.KeyStroke.getKeyStroke(key, 0));
        input.getActionMap().get(name).actionPerformed(new java.awt.event.ActionEvent(input, 0, ""));
    }
    static java.awt.Component page(SettingsDialog dialog) { return dialog.view().body.getViewport().getView(); }

    private SettingsDialog dialog() {
        return new SettingsDialog(windows, context -> {
            contexts.add(context);
            return CorePages.pages(ThemeChoices.of(() -> List.of("Jasper Dark", "IntelliJ Light")), new CorePages.SystemActions(() -> FILE,
                    () -> { }, () -> { }, () -> { }, () -> { }, () -> false), page -> fake,
                page -> new GroupPage("plugins", "Plugins", Optional.empty(), false, List::of, page)).apply(context);
        }, hooks());
    }

    @Test void returningToAPageRestoresScrollAndRetainsTheEditedDocument() throws Exception {
        var dialog=dialog();final Object[] document={null};final java.awt.Point[] position={null};
        try{
            edt(()->{
                dialog.open(null,"appearance");dialog.view().setSize(980,400);KitTestSupport.layout(dialog.view());
                var input=KitTestSupport.require(page(dialog),"ui.font.family.input",javax.swing.JComboBox.class);
                var text=(javax.swing.text.JTextComponent)input.getEditor().getEditorComponent();document[0]=text.getDocument();text.setText("Retained Font");text.select(2,7);
                dialog.view().body.getViewport().setViewPosition(new java.awt.Point(0,100));position[0]=dialog.view().body.getViewport().getViewPosition();
                dialog.select("window");KitTestSupport.layout(dialog.view());dialog.back();KitTestSupport.layout(dialog.view());
            });
            edt(()->{
                var input=KitTestSupport.require(page(dialog),"ui.font.family.input",javax.swing.JComboBox.class);var text=(javax.swing.text.JTextComponent)input.getEditor().getEditorComponent();
                assertThat(text.getDocument()).isSameAs(document[0]);assertThat(text.getText()).isEqualTo("Retained Font");
                assertThat(dialog.view().body.getViewport().getViewPosition()).isEqualTo(position[0]);
            });
        }finally{edt(()->{dialog.cancel();windows.close();});}
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
            assertThat(dialog.view().breadcrumb()).isEqualTo("Appearance & Behavior › Appearance");
            assertThat(button(dialog, "apply").isEnabled()).isFalse();
            assertThat(((Nodes.Stack) dialog.view().navigation.spec().root()).children().getFirst()).as("the page tree is the kit's").isInstanceOf(Nodes.Tree.class);
            assertThat(dialog.view().search.spec().root()).isEqualTo(new Nodes.Field("search", dev.jasper.ui.model.ControlState.READY,
                Nodes.FieldKind.SEARCH, "", "Search settings", "Search settings", ""));
        });
    }

    @Test void pickingAThemeFromTheListEnablesApply() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "appearance");
            // The dialog's window replays the current theme when it opens, which updates every component's UI.
            javax.swing.SwingUtilities.updateComponentTreeUI(dialog.view());
            var box = KitTestSupport.require(dialog.view(), "ui.theme.name.input", javax.swing.JComboBox.class);
            box.setSelectedIndex(1);
            assertThat(dialog.draft().value(CoreSettings.UI_THEME_NAME)).isEqualTo("IntelliJ Light");
            assertThat(button(dialog, "apply").isEnabled()).isTrue();
        });
    }

    @Test void steppingANumberEnablesApply() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "window");
            javax.swing.SwingUtilities.updateComponentTreeUI(dialog.view());
            var field = KitTestSupport.require(dialog.view(), "window.tab_height.input", javax.swing.JTextField.class);
            assertThat(field.getText()).isEqualTo("44");
            step(field, java.awt.event.KeyEvent.VK_UP);
            assertThat(dialog.draft().value(CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(45);
            assertThat(button(dialog, "apply").isEnabled()).as("Apply after a step").isTrue();
        });
    }

    @Test void aNumberOutsideItsRangeIsRefusedInlineAndBlocksOk() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "window");
            var field = KitTestSupport.require(dialog.view(), "window.tab_height.input", javax.swing.JTextField.class);
            field.setText("99");
            assertThat(KitTestSupport.require(dialog.view(), "window.tab_height.error", javax.swing.JLabel.class).getText()).isNotBlank();
            assertThat(dialog.draft().value(CoreSettings.WINDOW_TAB_HEIGHT)).as("not staged").isEqualTo(44);
            assertThat(button(dialog, "ok").isEnabled()).isFalse();
            field.setText("50");
            assertThat(dialog.draft().value(CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(50);
            assertThat(button(dialog, "ok").isEnabled()).isTrue();
        });
    }

    @Test void turningBlurOnAndChangingOpacityEnableApply() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "appearance");
            javax.swing.SwingUtilities.updateComponentTreeUI(dialog.view());
            var blur = KitTestSupport.require(dialog.view(), "ui.workspace.blur", javax.swing.JButton.class);
            blur.doClick(0);
            assertThat(button(dialog, "apply").isEnabled()).as("Apply after Blur: On").isTrue();
            var useTheme = KitTestSupport.require(dialog.view(), "ui.workspace.opacity:default", javax.swing.JButton.class);
            assertThat(useTheme.getAccessibleContext().getAccessibleName()).isEqualTo("Use theme's opacity");
            useTheme.doClick();
            assertThat(dialog.draft().modified(CoreSettings.UI_WORKSPACE_OPACITY)).isTrue();
            assertThat(button(dialog, "apply").isEnabled()).as("Apply after turning off Use theme's value").isTrue();
        });
    }

    @SuppressWarnings("unchecked")
    static <T> void collect(java.awt.Component c, Class<?> type, java.util.List<T> out) {
        if (type.isInstance(c)) out.add((T) c);
        if (c instanceof java.awt.Container k) for (var x : k.getComponents()) collect(x, type, out);
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
            assertThat(button(dialog, "back").isEnabled()).isFalse();
            dialog.forward();
            assertThat(dialog.currentPage()).isEqualTo("terminal.font");
            dialog.select("desk-buddy");
            assertThat(button(dialog, "forward").isEnabled()).isFalse();
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
            dialog.view().navigation.setSize(260, 600);
            KitTestSupport.layout(dialog.view().navigation);
            assertThat(KitTestSupport.require(dialog.view().navigation, "@tree/pages/terminal.font/count", javax.swing.JLabel.class).getText())
                .as("the count shows at the row's end").isEqualTo(Integer.toString(dialog.view().tree.count("terminal.font")));
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
            assertThat(button(dialog, "apply").isEnabled()).isTrue();
            assertThat(dialog.view().tree.isModified("window")).isTrue();
            assertThat(item((Nodes.Tree) ((Nodes.Stack) dialog.view().navigation.spec().root()).children().getFirst(), "window").emphasized()).as("unsaved pages stand out").isTrue();
            assertThat(item((Nodes.Tree) ((Nodes.Stack) dialog.view().navigation.spec().root()).children().getFirst(), "terminal").emphasized()).isFalse();
            dialog.apply();
        });
        until(() -> fake.applied == 1);
        edt(() -> {
            assertThat(saved).singleElement().satisfies(edit -> assertThat(edit.changes()).containsKey(CoreSettings.WINDOW_TAB_HEIGHT));
            assertThat(dialog.draft().modified()).isFalse();
            fake.modified = false;
            dialog.refreshNow();
            assertThat(button(dialog, "apply").isEnabled()).isFalse();
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
        until(() -> !dialog.view().message().isEmpty());
        edt(() -> {
            assertThat(label(dialog, "message")).contains("changed on disk");
            assertThat(dialog.view().control("message-status", java.awt.Component.class).isVisible()).isTrue();
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
            assertThat(label(dialog, "banner-text")).isEqualTo("config.toml uses old setting names.");
            assertThat(button(dialog, "banner-action").getText()).isEqualTo("Migrate Config…");
            button(dialog, "banner-action").doClick();
            assertThat(opened).containsExactly("migrate");
            var broken = state("[window\n");
            listeners.forEach(listener -> listener.accept(new ConfigService.State(broken.snapshot(), broken.diagnostics(), FILE, true,
                List.of(), List.of(), state.values(), true)));
            assertThat(label(dialog, "banner-text")).isEqualTo("config.toml has errors, so changes can't be saved until they're fixed.");
            button(dialog, "banner-action").doClick();
            assertThat(opened).containsExactly("migrate", "config");
            dialog.draft().stage(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(50));
            assertThat(button(dialog, "apply").isEnabled()).isFalse();
            assertThat(button(dialog, "ok").isEnabled()).isFalse();
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
            button(dialog, "help").doClick();
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

    static dev.jasper.ui.model.TreeItem item(Nodes.Tree tree, String id) {
        var pending = new java.util.ArrayDeque<>(tree.roots());
        while (!pending.isEmpty()) {
            var next = pending.removeFirst();
            if (next.id().equals(id)) return next;
            pending.addAll(next.children());
        }
        throw new AssertionError("No tree item " + id);
    }

    /**
     * Before Plan 2 the page tree was a JTree, which binds no Enter key, and OK was the root pane's default button:
     * Enter on the tree pressed OK. The kit tree has no activate action, so its Enter passes on to the same OK.
     */
    @Test void enterOnThePageTreePressesOkAsTheOldDefaultButtonDid() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "window");
            var view = dialog.view();
            var root = new javax.swing.JRootPane();
            root.getContentPane().add(view);
            view.addNotify();
            view.navigation.setSize(260, 600);
            KitTestSupport.layout(view.navigation);
            var canvas = (javax.swing.JComponent) KitTestSupport.require(view.navigation, "pages", javax.swing.JScrollPane.class).getViewport().getView();
            javax.swing.SwingUtilities.processKeyBindings(new java.awt.event.KeyEvent(canvas, java.awt.event.KeyEvent.KEY_PRESSED, 0, 0, java.awt.event.KeyEvent.VK_DOWN, java.awt.event.KeyEvent.CHAR_UNDEFINED));
            assertThat(dialog.isOpen()).isTrue();
            var enter = new java.awt.event.KeyEvent(canvas, java.awt.event.KeyEvent.KEY_PRESSED, 0, 0, java.awt.event.KeyEvent.VK_ENTER, '\n');
            assertThat(javax.swing.SwingUtilities.processKeyBindings(enter)).as("neither the tree nor the dialog's panels take Enter").isFalse();
            // Swing then offers the key to the window's bindings, where OK sits as the default button did.
            var name = root.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0));
            assertThat(name).isEqualTo("okSettings");
            root.getActionMap().get(name).actionPerformed(new java.awt.event.ActionEvent(root, 0, ""));
        });
        until(() -> !dialog.isOpen());
    }

    /**
     * A disabled default button takes no Enter: the root pane's press action is disabled, so the key is not consumed.
     * Enter's window binding follows OK the same way while the page is invalid or a save is in flight (spec §6).
     */
    @Test void enterDoesNothingWhileOkIsDisabledAsADisabledDefaultButton() throws Exception {
        saveResult = new CompletableFuture<>();
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "window");
            var view = dialog.view();
            var root = new javax.swing.JRootPane();
            root.getContentPane().add(view);
            view.addNotify();
            var stroke = javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0);
            var okSettings = root.getActionMap().get(root.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).get(stroke));
            java.util.function.BooleanSupplier enter = () -> javax.swing.SwingUtilities.notifyAction(okSettings, stroke,
                new java.awt.event.KeyEvent(root, java.awt.event.KeyEvent.KEY_PRESSED, 0, 0, java.awt.event.KeyEvent.VK_ENTER, '\n'), root, 0);
            assertThat(okSettings.isEnabled()).as("OK is enabled, so Enter is too").isTrue();

            var field = KitTestSupport.require(view, "window.tab_height.input", javax.swing.JTextField.class);
            field.setText("99");
            assertThat(button(dialog, "ok").isEnabled()).isFalse();
            assertThat(okSettings.isEnabled()).as("invalid: Enter follows the disabled OK").isFalse();
            assertThat(enter.getAsBoolean()).as("and the key is not consumed").isFalse();
            assertThat(dialog.isOpen()).isTrue();

            field.setText("50");
            assertThat(okSettings.isEnabled()).as("valid again").isTrue();
            assertThat(enter.getAsBoolean()).as("Enter presses OK, which starts the save").isTrue();
            assertThat(saved).hasSize(1);
            assertThat(button(dialog, "ok").isEnabled()).as("busy while the save is in flight").isFalse();
            assertThat(okSettings.isEnabled()).as("busy: Enter follows the disabled OK").isFalse();
            assertThat(enter.getAsBoolean()).isFalse();
            assertThat(saved).as("a second Enter starts no second save").hasSize(1);
            assertThat(dialog.isOpen()).isTrue();
        });
    }

    @Test void theTreeNavigatesAndRemembersClosedGroupsThroughKitEvents() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "window");
            var tree = dialog.view().navigation;
            tree.setSize(260, 600);
            KitTestSupport.layout(tree);
            assertThat(((Nodes.Tree) ((Nodes.Stack)tree.spec().root()).children().getFirst()).selectedIds()).containsExactly("window");
            // A row click reaches the dialog as a kit selection event naming the page.
            var row = KitTestSupport.require(tree, "@tree/pages/terminal.cursor/title", javax.swing.JLabel.class);
            for (var listener : row.getMouseListeners())
                listener.mouseClicked(new java.awt.event.MouseEvent(row, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false, java.awt.event.MouseEvent.BUTTON1));
            assertThat(dialog.currentPage()).isEqualTo("terminal.cursor");
            assertThat(dialog.view().breadcrumb()).isEqualTo("Terminal › Cursor");
            assertThat(((Nodes.Tree) ((Nodes.Stack)tree.spec().root()).children().getFirst()).selectedIds()).containsExactly("terminal.cursor");
            dialog.select("window");
            assertThat(((Nodes.Tree) ((Nodes.Stack)tree.spec().root()).children().getFirst()).expandedIds()).contains("terminal");
            var collapse = KitTestSupport.require(tree, "@tree/pages/terminal/expand", javax.swing.JButton.class);
            collapse.doClick();
            assertThat(((Nodes.Tree) ((Nodes.Stack)tree.spec().root()).children().getFirst()).expandedIds()).as("closed by the user").doesNotContain("terminal");
            dialog.search("ligatures");
            assertThat(((Nodes.Tree) ((Nodes.Stack)tree.spec().root()).children().getFirst()).expandedIds()).as("a search opens everything").contains("terminal");
            dialog.search("");
            dialog.select("window");
            assertThat(((Nodes.Tree) ((Nodes.Stack)tree.spec().root()).children().getFirst()).expandedIds()).as("and clearing it restores the user's choice").doesNotContain("terminal");
        });
    }

    @Test void searchHighlightAndQuerySurviveAThemeSwitch() throws Exception {
        var bindings = new dev.jasper.app.appearance.AppearanceBindings[1];
        edt(() -> bindings[0] = KitTestSupport.bindings(true));
        var dialog = new SettingsDialog(windows, context -> CorePages.pages(ThemeChoices.of(() -> List.of("Jasper Dark")),
            new CorePages.SystemActions(() -> FILE, () -> { }, () -> { }, () -> { }, () -> { }, () -> false), page -> fake,
            page -> new GroupPage("plugins", "Plugins", Optional.empty(), false, List::of, page)).apply(context),
            new SettingsDialog.Hooks(edit -> CompletableFuture.completedFuture(state), () -> state, listener -> new Subscription(() -> { }),
                () -> { }, () -> { }, uri -> { }, (parent, count) -> true,
                () -> bindings[0].context(dev.jasper.ui.theme.ThemeRoute.CHROME)));
        edt(() -> {
            try {
                dialog.open(null, "appearance");
                var query = KitTestSupport.require(dialog.view().search, "search.input", javax.swing.JTextField.class);
                query.setText("ligatures");
                assertThat(dialog.currentPage()).isEqualTo("terminal.font");
                var row = KitTestSupport.require(page(dialog), "row:terminal.font.ligatures", javax.swing.JComponent.class);
                assertThat(row.getAccessibleContext().getAccessibleDescription()).isEqualTo("Matches search");
                var light = dev.jasper.app.appearance.ThemeTestSupport.pair(false);
                bindings[0].binding().publish(light);
                assertThat(query.getText()).as("the query survives").isEqualTo("ligatures");
                assertThat(KitTestSupport.require(dialog.view().search, "search.input", javax.swing.JTextField.class)).isSameAs(query);
                assertThat(dialog.view().tree.visibleIds()).containsExactly("terminal", "terminal.font");
                assertThat(KitTestSupport.require(page(dialog), "row:terminal.font.ligatures", javax.swing.JComponent.class))
                    .as("the same row, still highlighted").isSameAs(row);
                assertThat(row.getAccessibleContext().getAccessibleDescription()).isEqualTo("Matches search");
                row.setSize(400, 48); row.doLayout();
                var image = new java.awt.image.BufferedImage(400, 48, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics(); try { row.paint(g); } finally { g.dispose(); }
                assertThat(image.getRGB(3, 24)).as("painted in the new theme's highlight").isEqualTo(light.chrome().semantic().color(dev.jasper.ui.theme.SemanticColor.SELECTION_BG).getRGB());
                dialog.cancel();
            } finally { bindings[0].close(); }
        });
    }

    @Test void cancelLeavesConfigBytesUntouchedWhileApplyWritesThem(@org.junit.jupiter.api.io.TempDir Path home) throws Exception {
        Path file = home.resolve("config.toml");
        String original = "# my settings\n[window]\ntab_height = 44 # tall tabs\n";
        java.nio.file.Files.writeString(file, original);
        try (var service = new ConfigService(file, false)) {
            var current = new ConfigService.State[]{service.initialState()};
            var dialog = new SettingsDialog(windows, context -> CorePages.pages(ThemeChoices.of(() -> List.of("Jasper Dark")),
                new CorePages.SystemActions(() -> file, () -> { }, () -> { }, () -> { }, () -> { }, () -> false), page -> fake,
                page -> new GroupPage("plugins", "Plugins", Optional.empty(), false, List::of, page)).apply(context),
                new SettingsDialog.Hooks(edit -> service.save(edit).thenApply(next -> current[0] = next), () -> current[0],
                    listener -> new Subscription(() -> { }), () -> { }, () -> { }, uri -> { }, (parent, count) -> true, KitTestSupport::context));
            edt(() -> {
                dialog.open(null, "window");
                KitTestSupport.require(dialog.view(), "window.status_bar", javax.swing.JButton.class).doClick();
                KitTestSupport.require(dialog.view(), "window.tab_height.input", javax.swing.JTextField.class).setText("50");
                assertThat(dialog.draft().modified()).isTrue();
                button(dialog, "cancel").doClick();
                assertThat(dialog.isOpen()).isFalse();
            });
            assertThat(java.nio.file.Files.readString(file)).as("Cancel writes nothing").isEqualTo(original);
            edt(() -> {
                dialog.open(null, "window");
                assertThat(dialog.draft().modified()).as("a new opening starts from the file").isFalse();
                assertThat(KitTestSupport.require(dialog.view(), "window.tab_height.input", javax.swing.JTextField.class).getText()).isEqualTo("44");
                KitTestSupport.require(dialog.view(), "window.tab_height.input", javax.swing.JTextField.class).setText("50");
                button(dialog, "ok").doClick();
            });
            until(() -> !dialog.isOpen());
            assertThat(java.nio.file.Files.readString(file)).as("OK writes the edit, keeping comments").contains("# my settings", "tab_height = 50");
        }
    }

    /**
     * The kit discard confirmation over Settings. The fake native shell answers the modal sheet while it is shown, as
     * the user does, through the sheet's own buttons or its Enter/Escape bindings.
     */
    @Test void discardingAsksWithAKitSheetOverSettingsAndKeepsEditingUnlessDiscarded() throws Exception {
        var answer = new String[1];
        var sheets = new ArrayList<AuxiliarySurface>();
        var texts = new ArrayList<String>();
        var fronted = new int[1];
        var confirming = new AuxiliaryWindows(UiState.inMemory(), surface -> new AuxiliarySurface.Shell(() -> {
                if (!surface.title().equals("Discard Changes")) return;
                sheets.add(surface);
                texts.addAll(KitTestSupport.texts(surface.holder()));
                var form = KitTestSupport.require(surface.holder(), "discard-sheet", javax.swing.JComponent.class);
                switch (answer[0]) {
                    case "discard", "keep" -> KitTestSupport.require(surface.holder(), answer[0], javax.swing.JButton.class).doClick();
                    case "enter" -> form.getActionMap().get("primary").actionPerformed(new java.awt.event.ActionEvent(form, 0, ""));
                    case "escape" -> form.getActionMap().get("cancel").actionPerformed(new java.awt.event.ActionEvent(form, 0, ""));
                    default -> surface.close();
                }
            }, () -> { if (surface.title().equals("Settings")) fronted[0]++; }, () -> { }, title -> { }, () -> new Rectangle(0, 0, 1000, 700),
            (title, initial) -> Optional.empty()));
        var holder = new SettingsDialog[1];
        holder[0] = new SettingsDialog(confirming, context -> CorePages.pages(ThemeChoices.of(() -> List.of("Jasper Dark")),
            new CorePages.SystemActions(() -> FILE, () -> { }, () -> { }, () -> { }, () -> { }, () -> false), page -> fake,
            page -> new GroupPage("plugins", "Plugins", Optional.empty(), false, List::of, page)).apply(context),
            new SettingsDialog.Hooks(edit -> CompletableFuture.completedFuture(state), () -> state, listener -> new Subscription(() -> { }),
                () -> { }, () -> { }, uri -> { }, (parent, count) -> holder[0].askDiscard(parent, count), KitTestSupport::context));
        var dialog = holder[0];
        edt(() -> {
            dialog.open(null, "window");
            dialog.draft().stage(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(50));
            for (String keep : List.of("keep", "escape", "closed")) {
                answer[0] = keep;
                int before = fronted[0];
                dialog.cancel();
                assertThat(dialog.isOpen()).as(keep + " keeps editing").isTrue();
                assertThat(dialog.draft().modified()).isTrue();
                assertThat(sheets.getLast().closed()).as("the sheet closes").isTrue();
                assertThat(sheets.getLast().ownerSurface()).as("owned by Settings").hasValueSatisfying(owner -> assertThat(owner.title()).isEqualTo("Settings"));
                assertThat(fronted[0]).as("focus returns to Settings").isEqualTo(before + 1);
            }
            assertThat(texts).contains("Discard 1 unsaved change?");
            dialog.draft().stage(CoreSettings.WINDOW_STATUS_BAR, Optional.of(false));
            answer[0] = "enter";
            dialog.cancel();
            assertThat(dialog.isOpen()).as("Enter discards, as the default answer did").isFalse();
            assertThat(texts).contains("Discard 2 unsaved changes?");
            dialog.open(null, "window");
            dialog.draft().stage(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(50));
            answer[0] = "discard";
            dialog.cancel();
            assertThat(dialog.isOpen()).isFalse();
            assertThat(sheets).allSatisfy(sheet -> assertThat(sheet.closed()).isTrue());
            confirming.close();
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
