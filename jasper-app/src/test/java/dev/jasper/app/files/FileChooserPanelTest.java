package dev.jasper.app.files;

import static dev.jasper.app.files.ChooserKit.*;
import static dev.jasper.app.files.ChooserRequest.Mode.*;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.ThemeRoute;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class FileChooserPanelTest {
    static int m(dev.jasper.ui.metrics.DesignMetrics.Role role) { return dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(role); }

    @TempDir Path dir;
    private final UiState state = UiState.inMemory();
    private final ChooserPrefs prefs = new ChooserPrefs(state);
    private final List<List<Location>> finished = new ArrayList<>();
    private final List<CompletableFuture<Browser>> connects = new ArrayList<>();
    private final List<Runnable> sourceListeners = new ArrayList<>();
    private boolean replace;
    private boolean hostConnected;
    private Optional<String> folderName = Optional.empty();

    @BeforeEach void tree() throws IOException {
        Files.createDirectory(dir.resolve("docs"));
        Files.writeString(dir.resolve("notes.txt"), "hello\n");
        Files.writeString(dir.resolve("todo.md"), "- one\n");
    }

    private Location here(String name) { return Location.local(dir.resolve(name)); }

    @Test void enlargedNarrowToolbarNeverAllocatesANegativePathOrOverlapsNavigation() {
        var themes = ThemeTestSupport.controller(true);
        themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, dev.jasper.app.config.UiFontConfig.defaults());
        var panel = panel(themes.appearance().context(ThemeRoute.CHROME),SAVE,Optional.of("example.txt"),List.of());
        try {
            var input=panel.nameInput().orElseThrow();var document=input.getDocument();input.select(1,4);
            panel.setSize(920,560);for(int i=0;i<5;i++)KitTestSupport.layout(panel);
            themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, new dev.jasper.app.config.UiFontConfig("system",32));
            panel.setSize(600,320); for(int i=0;i<5;i++)KitTestSupport.layout(panel);
            var path = KitTestSupport.require(panel,"path",javax.swing.JComponent.class);
            var back = KitTestSupport.require(panel,"back",javax.swing.JComponent.class);
            var pathBounds = javax.swing.SwingUtilities.convertRectangle(path.getParent(),path.getBounds(),panel);
            var backBounds = javax.swing.SwingUtilities.convertRectangle(back.getParent(),back.getBounds(),panel);
            assertThat(path.getWidth()).as("path remains usable").isPositive();
            assertThat(pathBounds.intersects(backBounds)).isFalse();
            var listing=KitTestSupport.require(panel,"files",javax.swing.JScrollPane.class);
            var table=(JComponent)listing.getViewport().getView();
            int row=((javax.swing.Scrollable)table).getScrollableUnitIncrement(new java.awt.Rectangle(),javax.swing.SwingConstants.VERTICAL,1);
            assertThat(listing.getViewport().getHeight()).as("two browse rows survive short height").isGreaterThanOrEqualTo(2*row);
            reveal(listing);table.scrollRectToVisible(new java.awt.Rectangle(0,0,1,row));
            assertThat(table.getVisibleRect().height).as("the first row is reachable by scrolling").isGreaterThanOrEqualTo(row);
            for(String id:List.of("primary","cancel")) {
                var action=KitTestSupport.require(panel,id,JComponent.class);action.scrollRectToVisible(new java.awt.Rectangle(0,0,action.getWidth(),action.getHeight()));
                assertThat(action.getVisibleRect().height).isEqualTo(action.getHeight());
            }
            themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, dev.jasper.app.config.UiFontConfig.defaults());
            panel.setSize(920,560);for(int i=0;i<5;i++)KitTestSupport.layout(panel);
            assertThat(panel.nameInput().orElseThrow()).isSameAs(input);assertThat(input.getDocument()).isSameAs(document);
            assertThat(input.getSelectedText()).isEqualTo("xam");
            assertThat(KitTestSupport.require(panel,"native.surface.scroll",javax.swing.JScrollPane.class).getVerticalScrollBar().isVisible()).isFalse();
        } finally {panel.close();themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, dev.jasper.app.config.UiFontConfig.defaults());themes.close();}
    }

    @Test void freshEnlargedSplitAndPreviewKeepBrowseContentReachable() {
        var themes=ThemeTestSupport.controller(true);
        themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, new dev.jasper.app.config.UiFontConfig("system",32));
        var request=new ChooserRequest("Choose",OPEN,true,Location.local(dir),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty());
        var model=new ChooserModel(request,List.of(new LocalSource(new LocalBrowser(dir))),Runnable::run,Runnable::run,prefs.showHidden());
        var panel=new FileChooserPanel(themes.appearance().context(ThemeRoute.CHROME),model,prefs,dir,finished::add,file->false,Optional::empty);
        model.start();
        try {
            panel.setSize(600,320);for(int i=0;i<6;i++)KitTestSupport.layout(panel);
            var listing=KitTestSupport.require(panel,"files",javax.swing.JScrollPane.class);
            assertThat(listing.getViewport().getWidth()).as("fresh enlarged receiving lane").isGreaterThanOrEqualTo(140);
            if(!previewToggle(panel).isSelected())previewToggle(panel).doClick(0);
            selectRow(panel,rows(panel).indexOf("notes.txt"));
            for(int i=0;i<6;i++)KitTestSupport.layout(panel);
            assertThat(listing.getViewport().getWidth()).as("visible preview cannot consume listing").isGreaterThanOrEqualTo(140);
            var nameCell=KitTestSupport.require(panel,"@table/files/"+dir.resolve("notes.txt")+"/name",JLabel.class);
            int inkWidth=nameCell.getWidth()-nameCell.getIcon().getIconWidth()-nameCell.getIconTextGap();
            assertThat(inkWidth).as("filename has meaningful ink beyond its icon and ellipsis").isGreaterThanOrEqualTo(nameCell.getFontMetrics(nameCell.getFont()).stringWidth("no…"));
            var table=(JComponent)listing.getViewport().getView();
            reveal(listing);table.scrollRectToVisible(new java.awt.Rectangle(0,0,1,40));
            assertThat(table.getVisibleRect().height).isGreaterThanOrEqualTo(40);
            assertThat(panel.previewText()).isEqualTo("hello\n");
            panel.setSize(2400,900);for(int i=0;i<6;i++)KitTestSupport.layout(panel);
            panel.split().setDividerLocation(410);for(int i=0;i<6;i++)KitTestSupport.layout(panel);
            themes.configure(false, dev.jasper.app.config.TerminalSchemeChoice.MATCH, new dev.jasper.app.config.UiFontConfig("system",32));
            for(int i=0;i<6;i++)KitTestSupport.layout(panel);
            assertThat(panel.split().getDividerLocation()).as("explicit drag survives refresh").isEqualTo(410);
        } finally {panel.close();themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, dev.jasper.app.config.UiFontConfig.defaults());themes.close();}
    }

    /**
     * A short path's box still fills the lane up to the search field, and each navigation button's focus ring has room in
     * its view, which clipped it to brackets before; the buttons and the box keep their places.
     */
    @Test void aShortPathFillsItsLaneAndTheNavigationRingsHaveRoom() {
        var root = Location.local(dir.getRoot());
        var request = new ChooserRequest("Choose", OPEN, false, root, Optional.of(root), Optional.empty(), Optional.empty(), Optional.empty());
        var model = new ChooserModel(request, List.of(new LocalSource(new LocalBrowser(dir))), Runnable::run, Runnable::run, prefs.showHidden());
        var panel = new FileChooserPanel(KitTestSupport.context(), model, prefs, dir, finished::add, file -> false, Optional::empty);
        model.start();
        try {
            panel.setSize(920, 520);
            for (int i = 0; i < 5; i++) KitTestSupport.layout(panel);
            int ring = m(dev.jasper.ui.metrics.DesignMetrics.Role.FOCUS_RING), gap = m(dev.jasper.ui.metrics.DesignMetrics.Role.BUTTON_GAP);
            var back = KitTestSupport.require(panel, "back", JComponent.class);
            var refresh = KitTestSupport.require(panel, "refresh", JComponent.class);
            var path = KitTestSupport.require(panel, "path", JComponent.class);
            var search = KitTestSupport.require(panel, "search.input", JTextField.class);
            var backBounds = javax.swing.SwingUtilities.convertRectangle(back.getParent(), back.getBounds(), panel);
            var refreshBounds = javax.swing.SwingUtilities.convertRectangle(refresh.getParent(), refresh.getBounds(), panel);
            var pathBounds = javax.swing.SwingUtilities.convertRectangle(path.getParent(), path.getBounds(), panel);
            var tools = javax.swing.SwingUtilities.getAncestorOfClass(dev.jasper.ui.swing.KitView.class, search);
            var toolsBounds = javax.swing.SwingUtilities.convertRectangle(tools.getParent(), tools.getBounds(), panel);
            assertThat(backBounds.getLocation()).isEqualTo(new java.awt.Point(m(dev.jasper.ui.metrics.DesignMetrics.Role.CHOOSER_TOOLBAR_SIDE_PADDING),
                m(dev.jasper.ui.metrics.DesignMetrics.Role.CHOOSER_TOOLBAR_VERTICAL_PADDING)));
            assertThat(pathBounds.x).as("the box keeps its gap after Refresh").isEqualTo(refreshBounds.x + refreshBounds.width + gap);
            assertThat(pathBounds.y + (pathBounds.height - backBounds.height) / 2).isEqualTo(backBounds.y);
            var bodyFont = KitTestSupport.context().typography().font(dev.jasper.ui.metrics.Typography.Role.BODY);
            int line = (int) Math.ceil(bodyFont.getLineMetrics("Ag", new java.awt.font.FontRenderContext(null, true, true)).getHeight());
            assertThat(pathBounds.height).isEqualTo(Math.max(m(dev.jasper.ui.metrics.DesignMetrics.Role.FIELD_HEIGHT),
                line + m(dev.jasper.ui.metrics.DesignMetrics.Role.FIELD_VERTICAL_PADDING)));
            assertThat(pathBounds.x + pathBounds.width).as("the box reaches the trailing tools").isEqualTo(toolsBounds.x - gap);
            for (String id : List.of("back", "forward", "up", "refresh")) {
                var button = KitTestSupport.require(panel, id, JComponent.class);
                var view = javax.swing.SwingUtilities.getAncestorOfClass(dev.jasper.ui.swing.KitView.class, button);
                var halo = javax.swing.SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), view);
                halo.grow(ring, ring);
                assertThat(new java.awt.Rectangle(0, 0, view.getWidth(), view.getHeight()).contains(halo)).as("%s's ring fits its view", id).isTrue();
            }
        } finally { panel.close(); }
    }

    /**
     * app-unmocked.md: the column titles sit as the Places heading does, on the dialog surface above their pane (no strip
     * of their own over the pane's rounded top) and on the first glyph's inset, a multiple-selection listing included:
     * its rows show no checkboxes, so the titles leave no room for one.
     */
    @Test void theColumnTitlesSitOnTheDialogSurfaceAboveTheFirstGlyph() {
        var request = new ChooserRequest("Choose", OPEN, true, Location.local(dir), Optional.of(Location.local(dir)), Optional.empty(),
            Optional.empty(), Optional.empty());
        var model = new ChooserModel(request, List.of(new LocalSource(new LocalBrowser(dir))), Runnable::run, Runnable::run, prefs.showHidden());
        var panel = new FileChooserPanel(KitTestSupport.context(), model, prefs, dir, finished::add, file -> false, Optional::empty);
        model.start();
        try {
            panel.setSize(820, 400);
            for (int i = 0; i < 5; i++) KitTestSupport.layout(panel);
            var files = KitTestSupport.require(panel, "files", javax.swing.JScrollPane.class);
            var header = files.getColumnHeader();
            assertThat(header.isOpaque()).isFalse();
            var image = new java.awt.image.BufferedImage(panel.getWidth(), panel.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
            var g = image.createGraphics();
            try { panel.paint(g); } finally { g.dispose(); }
            var edge = javax.swing.SwingUtilities.convertPoint(header, 2, header.getHeight() / 2, panel);
            assertThat(new java.awt.Color(image.getRGB(edge.x, edge.y))).as("the dialog surface shows behind the titles")
                .isEqualTo(KitTestSupport.context().theme().semantic().color(SemanticColor.SURFACE_DIALOG));
            var title = KitTestSupport.require(panel, "@table/files/header/name", JLabel.class);
            var cell = KitTestSupport.require(panel, "@table/files/" + dir.resolve("docs") + "/name", JLabel.class);
            assertThat(javax.swing.SwingUtilities.convertPoint(title, title.getInsets().left, 0, panel).x).as("Name over the first glyph")
                .isEqualTo(javax.swing.SwingUtilities.convertPoint(cell, cell.getInsets().left, 0, panel).x);
        } finally { panel.close(); }
    }

    /** Scrolls every scrolling ancestor of {@code target} until its top is in view, as a user scrolling there would. */
    private static void reveal(java.awt.Component target) {
        for (var parent = target.getParent(); parent != null; parent = parent.getParent())
            if (parent instanceof javax.swing.JViewport viewport && viewport.getView() != null) {
                var at = javax.swing.SwingUtilities.convertPoint(target, 0, 0, viewport.getView());
                int limit = Math.max(0, viewport.getView().getHeight() - viewport.getExtentSize().height);
                viewport.setViewPosition(new java.awt.Point(viewport.getViewPosition().x, Math.min(limit, Math.max(0, at.y))));
            }
    }

    private ChooserSource hosts() {
        return new ChooserSource() {
            @Override public String id() { return "dev.x.hosts"; }
            @Override public String heading() { return "Remote"; }
            @Override public List<Root> roots() { return List.of(new Root("box", "Box", "me@box:22", hostConnected)); }
            @Override public Runnable onChanged(Runnable listener) { sourceListeners.add(listener); return () -> sourceListeners.remove(listener); }
            @Override public CompletableFuture<Browser> connect(String root, Optional<UUID> ownerWindow) {
                var attempt = new CompletableFuture<Browser>();
                connects.add(attempt);
                return attempt;
            }
        };
    }

    private FileChooserPanel panel(KitContext kit, ChooserRequest.Mode mode, Optional<String> name, List<ChooserSource> extra) {
        var request = new ChooserRequest("Choose", mode, false, Location.local(dir), Optional.of(Location.local(dir)), name,
            Optional.empty(), Optional.empty());
        var sources = new ArrayList<ChooserSource>();
        sources.add(new LocalSource(new LocalBrowser(dir)));
        sources.addAll(extra);
        var model = new ChooserModel(request, sources, Runnable::run, Runnable::run, prefs.showHidden());
        var panel = new FileChooserPanel(kit, model, prefs, dir, finished::add, file -> replace, () -> folderName);
        model.start();
        layout(panel);
        return panel;
    }

    private FileChooserPanel panel(ChooserRequest.Mode mode, Optional<String> name, List<ChooserSource> extra) {
        return panel(KitTestSupport.context(), mode, name, extra);
    }

    private FileChooserPanel panel(ChooserRequest.Mode mode, Optional<String> name) { return panel(mode, name, List.of()); }

    private static Places.Place place(FileChooserPanel panel, String label) {
        return panel.navigationItems().stream()
            .filter(item -> item instanceof Places.Place place && place.label().equals(label)).map(Places.Place.class::cast)
            .findFirst().orElseThrow();
    }

    private static void enterIn(JTextField field) {
        field.getActionMap().get(field.getInputMap().get(KeyStroke.getKeyStroke("ENTER"))).actionPerformed(null);
    }

    @Test void enterOnAFolderEntersItAndEnterOnAFileOpensIt() {
        var panel = panel(OPEN, Optional.empty());
        assertThat(rows(panel)).containsExactly("docs", "notes.txt", "todo.md");
        selectRow(panel, 0);
        enter(panel);
        assertThat(rows(panel)).isEmpty();
        assertThat(panel.centerMessage()).contains("This folder is empty.");
        backspace(panel);
        selectRow(panel, 1);
        assertThat(panel.nameText()).isEqualTo("notes.txt");
        enter(panel);
        assertThat(finished).containsExactly(List.of(here("notes.txt")));
        assertThat(prefs.recent()).containsExactly(Location.local(dir));
    }

    @Test void openHasNoNameFieldAndOpensTheSelection() {
        var panel = panel(OPEN, Optional.empty());
        assertThat(panel.nameInput()).as("Open picks rows; nothing is typed").isEmpty();
        panel.approve();
        assertThat(panel.statusText()).isEqualTo("Select a file.");
        selectRow(panel, 2);
        panel.approve();
        assertThat(finished).containsExactly(List.of(here("todo.md")));
    }

    @Test void saveAndDirectoryKeepTheNameField() {
        var save = panel(SAVE, Optional.empty());
        var directory = panel(DIRECTORY, Optional.empty());
        assertThat(javax.swing.SwingUtilities.isDescendingFrom(save.nameInput().orElseThrow(), save)).isTrue();
        assertThat(javax.swing.SwingUtilities.isDescendingFrom(directory.nameInput().orElseThrow(), directory)).isTrue();
    }

    @Test void theSaveNameFieldAcceptsPathsAndReportsProblems() {
        var panel = panel(SAVE, Optional.empty());
        var name = (JTextField) panel.nameInput().orElseThrow();
        name.setText("docs");
        panel.approve();
        assertThat(panel.pathText()).endsWith("docs");
        name.setText("../nowhere/x.txt");
        panel.approve();
        assertThat(panel.statusText()).isEqualTo("The folder for \"../nowhere/x.txt\" doesn't exist.");
        replace = true;
        name.setText("../todo.md");
        enterIn(name);
        assertThat(finished).as("Enter in the name field approves").containsExactly(List.of(here("todo.md")));
    }

    @Test void saveSelectsTheNamePartAndAsksBeforeReplacing() {
        var panel = panel(SAVE, Optional.of("notes.txt"));
        assertThat(panel.nameInput().orElseThrow().getSelectedText()).isEqualTo("notes");
        assertThat(kit(panel, "primary", JButton.class).getText()).isEqualTo("Save");
        replace = false;
        panel.approve();
        assertThat(finished).isEmpty();
        replace = true;
        kit(panel, "primary", JButton.class).doClick(0);
        assertThat(finished).containsExactly(List.of(here("notes.txt")));
    }

    @Test void enterAnywhereInTheDialogApprovesLikeTheDefaultButton() {
        var panel = panel(SAVE, Optional.of("notes.txt"));
        var root = new javax.swing.JRootPane();
        panel.installDefaultAction(root);
        replace = true;
        var name = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke("ENTER"));
        root.getActionMap().get(name).actionPerformed(null);
        assertThat(finished).containsExactly(List.of(here("notes.txt")));
    }

    /**
     * The default action is the primary button's: while the primary is disabled, Enter is not consumed and does
     * nothing, exactly as a disabled default button (spec §6). The primary is disabled once the chooser has closed.
     */
    @Test void enterFollowsThePrimaryButtonAndDoesNothingWhileItIsDisabled() {
        var panel = panel(SAVE, Optional.of("notes.txt"));
        var root = new javax.swing.JRootPane();
        panel.installDefaultAction(root);
        var stroke = KeyStroke.getKeyStroke("ENTER");
        var approve = root.getActionMap().get(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(stroke));
        java.util.function.BooleanSupplier enter = () -> javax.swing.SwingUtilities.notifyAction(approve, stroke,
            new java.awt.event.KeyEvent(root, java.awt.event.KeyEvent.KEY_PRESSED, 0, 0, java.awt.event.KeyEvent.VK_ENTER, '\n'), root, 0);
        var primary = kit(panel, "primary", JButton.class);
        assertThat(primary.isEnabled()).isTrue();
        assertThat(approve.isEnabled()).as("the primary is enabled, so Enter is too").isTrue();
        panel.close();
        assertThat(approve.isEnabled()).as("closed: Enter follows the disabled primary").isFalse();
        replace = true;
        assertThat(enter.getAsBoolean()).as("and the key is not consumed").isFalse();
        assertThat(finished).isEmpty();
    }

    @Test void newFolderCreatesAndSelectsIt() {
        folderName = Optional.of("reports");
        var panel = panel(SAVE, Optional.empty());
        kit(panel, "newFolder", JButton.class).doClick(0);
        assertThat(dir.resolve("reports")).isDirectory();
        assertThat(table(panel).selectedIds()).containsExactly(table(panel).rows().get(rows(panel).indexOf("reports")).id());
    }

    @Test void cancelFinishesOnceWithNothing() {
        var panel = panel(OPEN, Optional.empty());
        kit(panel, "cancel", JButton.class).doClick(0);
        panel.cancel();
        assertThat(finished).containsExactly(List.of());
    }

    @Test void theNavigationPaneListsPlacesAndPinnedFolders() {
        var panel = panel(OPEN, Optional.empty());
        var items = panel.navigationItems();
        assertThat(((Places.Place) items.getFirst()).label()).isEqualTo("Terminal folder");
        assertThat(panel.selectedPlace()).as("the folder shown is highlighted").contains((Places.Place) items.getFirst());
        var places = (Nodes.Tree) view(panel, FileChooserPanel.PLACES).spec().root();
        assertThat(places.selectedIds()).containsExactly(panel.placeId((Places.Place) items.getFirst()));
        panel.togglePin(here("docs"));
        assertThat(prefs.pinned()).containsExactly(here("docs"));
        choosePlace(panel, place(panel, "docs"));
        assertThat(panel.pathText()).endsWith("docs");
    }

    @Test void pinMenusPinTheirFolderWithoutNavigating() {
        var panel = panel(OPEN, Optional.empty());
        assertThat(table(panel).rows().get(rows(panel).indexOf("notes.txt")).contextActions()).as("files have no pin menu").contains(List.of());
        String shown = panel.pathText();
        var docsCell = kit(panel, "@table/files/" + table(panel).rows().get(rows(panel).indexOf("docs")).id() + "/name", JLabel.class);
        chooseFromMenu(docsCell, "Pin");
        assertThat(prefs.pinned()).containsExactly(here("docs"));
        assertThat(panel.pathText()).as("pinning a row does not open it").isEqualTo(shown);
        assertThat(rows(panel)).contains("notes.txt");
        layout(panel);
        var docsPlace = place(panel, "docs");
        var placeTitle = kit(panel, "@tree/places/" + panel.placeId(docsPlace) + "/title", JLabel.class);
        assertThat(placeTitle.getToolTipText()).isEqualTo(dir.resolve("docs").toString());
        chooseFromMenu(placeTitle, "Unpin");
        assertThat(prefs.pinned()).as("unpinned from the places menu").isEmpty();
        assertThat(panel.pathText()).as("the place's menu does not navigate to it").isEqualTo(shown);
        assertThat(rows(panel)).containsExactly("docs", "notes.txt", "todo.md");
        layout(panel);
        var crumbButtons = java.util.Arrays.stream(crumbs(panel).getComponents()).filter(c -> c instanceof JButton).map(JButton.class::cast).toList();
        chooseFromMenu(crumbButtons.getLast(), "Pin");
        assertThat(prefs.pinned()).as("a crumb's menu pins the folder it names").containsExactly(Location.local(dir));
        assertThat(panel.pathText()).isEqualTo(shown);
    }

    @Test void aRightClickOnAPlaceLeavesTheHighlightAndTheFolderAlone() {
        var panel = panel(OPEN, Optional.empty());
        panel.togglePin(here("docs"));
        layout(panel);
        String shown = panel.pathText();
        var before = ((Nodes.Tree) view(panel, FileChooserPanel.PLACES).spec().root()).selectedIds();
        var title = kit(panel, "@tree/places/" + panel.placeId(place(panel, "docs")) + "/title", JLabel.class);
        var press = new java.awt.event.MouseEvent(title, java.awt.event.MouseEvent.MOUSE_PRESSED, 0, java.awt.event.InputEvent.BUTTON3_DOWN_MASK, 2, 2, 1, true, java.awt.event.MouseEvent.BUTTON3);
        for (var listener : title.getMouseListeners()) listener.mousePressed(press);
        assertThat(panel.pathText()).as("a right click does not open the place").isEqualTo(shown);
        assertThat(panel.selectedPlace()).as("nor moves the highlight").contains((Places.Place) panel.navigationItems().getFirst());
        assertThat(((Nodes.Tree) view(panel, FileChooserPanel.PLACES).spec().root()).selectedIds()).isEqualTo(before);
        var canvas = (JComponent) kit(panel, FileChooserPanel.PLACES, javax.swing.JScrollPane.class).getViewport().getView();
        canvas.getActionMap().get(canvas.getInputMap().get(KeyStroke.getKeyStroke("ENTER"))).actionPerformed(null);
        assertThat(panel.pathText()).as("Enter after the right click stays on the place shown").isEqualTo(shown);
    }

    /** Before Plan 2 the places were a JList, which binds no Enter key, so Enter there pressed the default button. */
    @Test void enterOnThePlacesPassesToTheDialogsDefaultActionAsTheOldListDid() {
        var panel = panel(OPEN, Optional.empty());
        var root = new javax.swing.JRootPane();
        root.getContentPane().add(panel);
        panel.installDefaultAction(root);
        panel.togglePin(here("docs"));
        layout(panel);
        String shown = panel.pathText();
        var canvas = (JComponent) kit(panel, FileChooserPanel.PLACES, javax.swing.JScrollPane.class).getViewport().getView();
        var enter = new java.awt.event.KeyEvent(canvas, java.awt.event.KeyEvent.KEY_PRESSED, 0, 0, java.awt.event.KeyEvent.VK_ENTER, '\n');
        assertThat(javax.swing.SwingUtilities.processKeyBindings(enter)).as("the places do not take Enter").isFalse();
        assertThat(panel.pathText()).isEqualTo(shown);
        var name = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke("ENTER"));
        root.getActionMap().get(name).actionPerformed(null);
        assertThat(panel.statusText()).as("the default action approves, as the default button did").isEqualTo("Select a file.");
        var files = (JComponent) scroll(panel).getViewport().getView();
        assertThat(javax.swing.SwingUtilities.processKeyBindings(new java.awt.event.KeyEvent(files, java.awt.event.KeyEvent.KEY_PRESSED, 0, 0,
            java.awt.event.KeyEvent.VK_ENTER, '\n'))).as("the file listing keeps its own Enter, as the JTable did").isTrue();
    }

    @Test void arrowKeysInThePlacesOpenEachPlaceAsBeforeAndALeftClickOpensOne() {
        var panel = panel(OPEN, Optional.empty());
        panel.togglePin(here("docs"));
        layout(panel);
        var canvas = (JComponent) kit(panel, FileChooserPanel.PLACES, javax.swing.JScrollPane.class).getViewport().getView();
        // Terminal folder, home (both the folder shown), the Pinned heading, then docs.
        for (int i = 0; i < 4; i++) canvas.getActionMap().get(canvas.getInputMap().get(KeyStroke.getKeyStroke("DOWN"))).actionPerformed(null);
        assertThat(panel.pathText()).as("moving the highlight with the keys opens the place, as before").endsWith("docs");
        assertThat(panel.selectedPlace()).contains(place(panel, "docs"));
        choosePlace(panel, (Places.Place) panel.navigationItems().getFirst());
        assertThat(panel.pathText()).as("a left click opens a place").endsWith(dir.getFileName().toString());
        choosePlace(panel, place(panel, "docs"));
        assertThat(panel.pathText()).as("a left click opens a place").endsWith("docs");
    }

    @Test void theHiddenFilesToggleIsRememberedAndTypingSelects() throws IOException {
        Files.writeString(dir.resolve(".env"), "");
        var panel = panel(OPEN, Optional.empty());
        assertThat(rows(panel)).contains(".env");
        panel.toggleHidden();
        assertThat(rows(panel)).doesNotContain(".env");
        assertThat(prefs.showHidden()).isFalse();
        type(panel, 't');
        assertThat(panel.nameText()).isEqualTo("todo.md");
    }

    @Test void theHiddenItemsButtonTogglesThemAndIsPressedWhileTheyShow() throws IOException {
        Files.writeString(dir.resolve(".env"), "");
        var panel = panel(OPEN, Optional.empty());
        var button = kit(panel, FileChooserPanel.HIDDEN, JButton.class);
        assertThat(button.getToolTipText()).isEqualTo("Show hidden items");
        assertThat(button.isSelected()).as("pressed: hidden items show").isTrue();
        button.doClick(0);
        assertThat(rows(panel)).doesNotContain(".env");
        assertThat(prefs.showHidden()).as("the same saved choice as Cmd+Shift+.").isFalse();
        assertThat(kit(panel, FileChooserPanel.HIDDEN, JButton.class).isSelected()).isFalse();
        kit(panel, FileChooserPanel.HIDDEN, JButton.class).doClick(0);
        assertThat(rows(panel)).contains(".env");
        assertThat(prefs.showHidden()).isTrue();
    }

    @Test void thePreviewPaneShowsTheStartOfTheSelectedFile() {
        var panel = panel(OPEN, Optional.empty());
        previewToggle(panel).doClick(0);
        assertThat(prefs.preview()).isTrue();
        selectRow(panel, 1);
        assertThat(panel.previewText()).isEqualTo("hello\n");
    }

    /**
     * Plan 2 Task 6c (review I2): the preview title is a one-line strong title, as the former JLabel was; a long name
     * without spaces is cut, never broken character by character over several lines in the narrow preview column.
     */
    @Test void thePreviewTitleStaysOneLineForALongName() throws IOException {
        String name = "a" + "verylongfilenamewithoutanyspaces".repeat(4) + ".txt";
        Files.writeString(dir.resolve(name), "x\n");
        var panel = panel(OPEN, Optional.empty());
        previewToggle(panel).doClick(0);
        selectRow(panel, rows(panel).indexOf(name));
        panel.setSize(820, 520);
        for (int pass = 0; pass < 3; pass++) dev.jasper.app.testsupport.KitTestSupport.layout(panel);
        var title = dev.jasper.app.testsupport.KitTestSupport.require(panel, "preview.title", javax.swing.JLabel.class);
        var details = dev.jasper.app.testsupport.KitTestSupport.require(panel, "preview.details", javax.swing.JLabel.class);
        assertThat(title.getText()).isEqualTo(name);
        int line = title.getFontMetrics(title.getFont()).getHeight();
        assertThat(title.getHeight()).as("one line").isEqualTo(line);
        assertThat(title.getWidth()).as("inside the preview column").isLessThan(title.getFontMetrics(title.getFont()).stringWidth(name));
        assertThat(details.getY()).as("the details follow one title line").isLessThanOrEqualTo(title.getY() + line + 2 * line);
    }

    @Test void thePathBarAcceptsATypedFolder() {
        var panel = panel(OPEN, Optional.empty());
        editPath(panel, dir.resolve("docs").toString());
        assertThat(panel.pathText()).endsWith("docs");
        editPath(panel, dir.resolve("nowhere").toString());
        assertThat(panel.statusText()).startsWith("There is no folder at ");
    }

    @Test void theFreeAreaOpensTheTypedPathAndEscapeClosesItWithoutCancelling() {
        var panel = panel(OPEN, Optional.empty());
        var area = crumbs(panel);
        for (var listener : area.getMouseListeners())
            listener.mouseClicked(new java.awt.event.MouseEvent(area, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 2, 2, 1, false, java.awt.event.MouseEvent.BUTTON1));
        var field = kit(panel, "@crumbs/path/field", JTextField.class);
        assertThat(field.getText()).as("the field holds the folder shown").isEqualTo(dir.toAbsolutePath().normalize().toString());
        field.getActionMap().get(field.getInputMap().get(KeyStroke.getKeyStroke("ESCAPE"))).actionPerformed(null);
        assertThat(((Nodes.Breadcrumbs) view(panel, FileChooserPanel.PATH).spec().root()).editing()).isFalse();
        assertThat(finished).as("Escape in the path field does not cancel the dialog").isEmpty();
    }

    @Test void aSourceRootConnectsWhenOpenedAndOffersRetryWhenItFails() throws IOException {
        Path remote = Files.createDirectory(dir.resolve("remote-home"));
        Files.writeString(remote.resolve("r.txt"), "r");
        var panel = panel(OPEN, Optional.empty(), List.of(hosts()));
        assertThat(panel.navigationItems()).contains("Remote");
        assertThat(place(panel, "Box").connected()).isFalse();
        hostConnected = true;
        sourceListeners.forEach(Runnable::run);
        assertThat(place(panel, "Box").connected()).as("the pane follows the source").isTrue();
        layout(panel);
        assertThat(KitTestSupport.find(panel, "@tree/places/" + panel.placeId(place(panel, "Box")) + "/status", JComponent.class))
            .as("a connected root shows its status dot").isNotNull();
        choosePlace(panel, place(panel, "Box"));
        assertThat(panel.centerMessage()).contains("Connecting to Box…");
        assertThat(panel.centerAction()).contains("Cancel connection");
        connects.getLast().completeExceptionally(new IOException("offline"));
        assertThat(panel.centerMessage()).contains("Couldn't connect to Box: offline");
        assertThat(panel.centerAction()).contains("Retry");
        layout(panel);
        kit(panel, "center", JButton.class).doClick(0);
        connects.getLast().complete(new LocalBrowser(remote));
        assertThat(rows(panel)).containsExactly("r.txt");
        assertThat(panel.pathText()).startsWith("Box");
        selectRow(panel, 0);
        panel.approve();
        assertThat(finished).containsExactly(List.of(new Location("dev.x.hosts", "box", remote.resolve("r.txt").toString())));
        panel.close();
        assertThat(sourceListeners).isEmpty();
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test void fileNamesAreShownAsPlainText() throws IOException {
        Files.writeString(dir.resolve("<html><b>x"), "");
        var panel = panel(OPEN, Optional.empty());
        layout(panel);
        var id = table(panel).rows().get(rows(panel).indexOf("<html><b>x")).id();
        var cell = KitTestSupport.require(panel, "@table/files/" + id + "/name", JLabel.class);
        assertThat(cell.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
        assertThat(cell.getText()).isEqualTo("<html><b>x");
    }

    @Test void directoryModeSaysWhichFolderChooseWillPick() {
        var panel = panel(DIRECTORY, Optional.empty());
        assertThat(kit(panel, "primary", JButton.class).getText()).isEqualTo("Choose");
        assertThat(rows(panel)).containsExactly("docs");
        assertThat(panel.statusText()).endsWith("Choose: " + dir.getFileName());
        selectRow(panel, 0);
        assertThat(panel.statusText()).endsWith("Choose: docs");
        panel.approve();
        assertThat(finished).containsExactly(List.of(here("docs")));
    }

    @Test void saveModeMutesFilesAndTheHeaderSorts() {
        var panel = panel(SAVE, Optional.empty());
        var rows = table(panel).rows();
        assertThat(rows.get(rows(panel).indexOf("notes.txt")).muted()).as("files are dimmed in Save, as before").isTrue();
        assertThat(rows.get(rows(panel).indexOf("docs")).muted()).isFalse();
        assertThat(table(panel).columns().getFirst().sorted()).contains(true);
        var header = kit(panel, "@table/files/header/name", JLabel.class);
        for (var listener : header.getMouseListeners())
            listener.mouseClicked(new java.awt.event.MouseEvent(header, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 2, 2, 1, false, java.awt.event.MouseEvent.BUTTON1));
        assertThat(table(panel).columns().getFirst().sorted()).as("clicking the sorted column reverses it").contains(false);
        assertThat(rows(panel)).containsExactly("docs", "todo.md", "notes.txt");
        assertThat(table(panel).scroll()).as("the listing keeps its overlay scroll bar").isEqualTo(Nodes.ScrollPolicy.OVERLAY);
    }

    @Test void selectionScrollAndPreviewSurviveAThemeSwitch() throws IOException {
        for (int i = 0; i < 80; i++) Files.writeString(dir.resolve(String.format("file-%02d.txt", i)), "line " + i + "\n");
        var bindings = KitTestSupport.bindings(true);
        try {
            var panel = panel(bindings.context(ThemeRoute.CHROME), OPEN, Optional.empty(), List.of());
            previewToggle(panel).doClick(0);
            panel.setSize(900, 400);
            layout(panel);
            int row = rows(panel).indexOf("file-60.txt");
            var viewport = scroll(panel).getViewport();
            int rowHeight = viewport.getViewSize().height / rows(panel).size();
            viewport.setViewPosition(new java.awt.Point(0, row * rowHeight - viewport.getExtentSize().height / 2));
            layout(panel);
            selectRow(panel, row);
            int scrolled = viewport.getViewPosition().y;
            assertThat(scrolled).isPositive();
            assertThat(panel.previewText()).isEqualTo("line 60\n");
            var search = KitTestSupport.require(panel, FileChooserPanel.SEARCH + ".input", javax.swing.text.JTextComponent.class);
            var light = ThemeTestSupport.pair(false);
            bindings.binding().publish(light);
            layout(panel);
            assertThat(table(panel).selectedIds()).as("selection survives").containsExactly(table(panel).rows().get(row).id());
            assertThat(panel.nameText()).isEqualTo("file-60.txt");
            assertThat(viewport.getViewPosition().y).as("scroll position survives").isEqualTo(scrolled);
            assertThat(panel.previewText()).as("preview survives").isEqualTo("line 60\n");
            assertThat(previewToggle(panel).isSelected()).isTrue();
            assertThat(search.getText()).isEmpty();
            assertThat(panel.getBackground()).as("the dialog surface follows the theme").isEqualTo(light.chrome().semantic().color(SemanticColor.SURFACE_DIALOG));
            // The selected row's name is white on the selection (spec 2).
            assertThat(KitTestSupport.require(panel, "@table/files/" + table(panel).rows().get(row).id() + "/name", JLabel.class).getForeground())
                .isEqualTo(light.chrome().semantic().color(SemanticColor.TEXT_ONACCENT));
            panel.close();
        } finally { bindings.close(); }
    }

    @Test void thePathBarIsDrawnAsATextFieldLikeTheSearchBox() {
        var panel = panel(OPEN, Optional.empty());
        JComponent box = pathBox(panel);
        assertThat(box.isOpaque()).as("it paints its own rounded field").isFalse();
        int height = box.getPreferredSize().height;
        assertThat(height).as("a kit field's height").isGreaterThanOrEqualTo(com.formdev.flatlaf.util.UIScale.scale(m(dev.jasper.ui.metrics.DesignMetrics.Role.FIELD_HEIGHT)));
        box.setSize(300, height);
        KitTestSupport.layout(box);
        var image = new java.awt.image.BufferedImage(300, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        box.paint(graphics);
        graphics.dispose();
        assertThat(image.getRGB(299, height / 2) >>> 24).as("an outline at the right edge").isNotZero();
        java.awt.Color fill = KitTestSupport.context().theme().semantic().color(SemanticColor.SURFACE_INPUT);
        assertThat(new java.awt.Color(image.getRGB(296, height / 2), true)).as("the field's background inside, as the search box's").isEqualTo(fill);
    }

    @Test void placesAndFilesKeepRoundedCornersWithOpaqueContents() {
        var panel = panel(OPEN, Optional.empty());
        try {
            for (boolean preview : new boolean[] {false, true}) {
                if (previewToggle(panel).isSelected() != preview) previewToggle(panel).doClick(0);
                for (var pane : panel.roundedPanes().subList(0, 2)) {
                    pane.setSize(320, 240);
                    dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(pane);
                    int top = pane.surfaceTop();
                    for (int scale : new int[] {1, 2}) {
                        var image = new java.awt.image.BufferedImage(320 * scale, 240 * scale,
                            java.awt.image.BufferedImage.TYPE_INT_RGB);
                        var graphics = image.createGraphics();
                        try {
                            graphics.scale(scale, scale);
                            pane.paint(graphics);
                        } finally { graphics.dispose(); }
                        int background = panel.getBackground().getRGB();
                        for (int x : new int[] {0, image.getWidth() - 1}) {
                            for (int y : new int[] {top * scale, image.getHeight() - 1}) {
                                assertThat(image.getRGB(x, y)).as("corner with preview=%s at %sx", preview, scale)
                                    .isEqualTo(background);
                            }
                        }
                        assertThat(pane.contains(0, top)).as("outside the rounded surface").isFalse();
                        assertThat(pane.contains(8, top)).as("workspace's 16px arc reaches the top at 8px").isTrue();
                        int[] before = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
                        graphics = image.createGraphics();
                        try {
                            graphics.scale(scale, scale);
                            graphics.setClip(0, top, 12, 12);
                            pane.paint(graphics);
                        } finally { graphics.dispose(); }
                        assertThat(image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()))
                            .as("partial repaint preserves the rounded edge").containsExactly(before);
                    }
                }
            }
        } finally { panel.close(); }
    }

    @Test void placesHeadingAlignsWithFileColumnsAndTheResizeGutterHasNoGrip() {
        var panel = panel(OPEN, Optional.empty());
        try {
            panel.setSize(900, 560);
            dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(panel);
            assertThat(panel.navigationItems().getFirst()).isInstanceOf(Places.Place.class);
            assertThat(KitTestSupport.require(panel, "places.heading", JLabel.class).getText()).isEqualTo("Places");
            var placesViewport = KitTestSupport.require(panel, FileChooserPanel.PLACES, javax.swing.JScrollPane.class).getViewport();
            var filesViewport = scroll(panel).getViewport();
            assertThat(javax.swing.SwingUtilities.convertPoint(placesViewport, 0, 0, panel).y)
                .as("places start level with the file rows").isEqualTo(javax.swing.SwingUtilities.convertPoint(filesViewport, 0, 0, panel).y);
            var split = panel.split();
            var gutter = split.getInsets();
            javax.swing.SwingUtilities.updateComponentTreeUI(panel);
            dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(panel);
            assertThat(split.getInsets()).as("theme refresh preserves the exterior gutter").isEqualTo(gutter);
            var divider = ((javax.swing.plaf.basic.BasicSplitPaneUI) split.getUI()).getDivider();
            var image = new java.awt.image.BufferedImage(divider.getWidth(), divider.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            try { divider.paint(graphics); } finally { graphics.dispose(); }
            assertThat(image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()))
                .as("only chrome in the draggable gutter").containsOnly(split.getBackground().getRGB());
        } finally { panel.close(); }
    }

    @Test void crumbsSitCloseToTheirSeparators() {
        var panel = panel(OPEN, Optional.empty());
        var separators = new ArrayList<String>();
        for (var child : crumbs(panel).getComponents()) {
            if (child instanceof JButton crumb) {
                assertThat(crumb.getClientProperty(com.formdev.flatlaf.FlatClientProperties.MINIMUM_WIDTH)).as("no minimum width").isEqualTo(0);
                assertThat(crumb.getMargin().left + crumb.getMargin().right).as("tight side margins").isLessThanOrEqualTo(8);
            } else if (child instanceof JLabel separator) {
                separators.add(separator.getText());
                java.awt.Insets insets = separator.getInsets();
                assertThat(insets.left + insets.right).isLessThanOrEqualTo(4);
            }
        }
        assertThat(separators).isNotEmpty().allMatch("›"::equals);
    }
}
