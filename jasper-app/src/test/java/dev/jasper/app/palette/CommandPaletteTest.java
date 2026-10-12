package dev.jasper.app.palette;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.UiFontConfig;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.ThemeRoute;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultEditorKit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * The palette card on kit views (Plan 2 Task 6b-2): the query field, chip, RESULTS list, hint bar and step form are kit
 * controls on the chrome context; the shell keeps the palette's arrangement and exact geometry under named metrics.
 */
class CommandPaletteTest {
    private static final PaletteScope COMMANDS = scope("jasper.commands", "Commands", false, List.of(new PaletteVerb("run", "Run")));
    private static final PaletteScope HISTORY = scope("test.history", "History", true, List.of(new PaletteVerb("paste", "Paste"),
        new PaletteVerb("paste_run", "Paste and run"), new PaletteVerb("save", "Save as snippet…")));

    static PaletteScope scope(String id, String label, boolean monospace, List<PaletteVerb> verbs) {
        return new PaletteScope() {
            @Override public String id() { return id; }
            @Override public String label() { return label; }
            @Override public String placeholder() { return "Search " + label; }
            @Override public List<PaletteVerb> verbs() { return verbs; }
            @Override public boolean monospaceRows() { return monospace; }
            @Override public PaletteResults search(String query, PaletteContext context) { return PaletteResults.none(); }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) { }
            @Override public Subscription onChanged(Runnable listener) { return new Subscription(() -> { }); }
        };
    }

    private static CommandPalette palette(List<String> events) { return palette(KitTestSupport.context(), true, events); }

    private static CommandPalette palette(KitContext kit, boolean macOs, List<String> events) {
        return new CommandPalette(kit, macOs, query -> { },
            (entry, verb) -> events.add(PaletteTestSupport.describe(entry) + ":" + verb),
            text -> java.util.Optional.empty(), scope -> events.add("chip:" + scope.id()));
    }

    private static PaletteEntry item(PaletteScope scope, String id) { return new PaletteEntry.Item(scope, PaletteRow.of(id, "Row " + id)); }

    private static Component row(CommandPalette palette, int entry) {
        var row = PaletteTestSupport.entryComponent(palette, entry);
        if (row == null) throw new AssertionError("Entry " + entry + " is not mounted");
        return row;
    }

    @Test void semanticCustomArtworkReachesRowsAndClosesWithTheReceivingPalette() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var source=KitTestSupport.context();
            var palette=palette(source,true,new ArrayList<>());
            var icon=new dev.jasper.app.platform.PresentationIcon("owner:custom","M 2 2 L 14 14");
            palette.setEntries(List.of(new PaletteEntry.Item(COMMANDS,new PaletteRow("custom","Custom",null,null,icon,true,null))),null,null);
            var list=(dev.jasper.ui.model.Nodes.ItemList)palette.resultsView().spec().root();
            assertThat(((dev.jasper.ui.model.Nodes.Icon)list.items().getFirst().leading().orElseThrow()).name()).isEqualTo("owner:custom");
            assertThat(palette.kit().icons().names()).contains("owner:custom");
            assertThat(source.icons().names()).doesNotContain("owner:custom");
            palette.setEntries(List.of(),null,null);
            assertThat(palette.kit().icons().names()).doesNotContain("owner:custom");
            palette.close();assertThatIllegalStateException().isThrownBy(() -> palette.kit().icons().names());
        });
    }

    @Test void sameRowReplacesCustomArtworkBeforeReleasingThePreviousGlyph() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (String nextName : List.of("file", "owner:next")) {
                var palette=palette(new ArrayList<>());
                try {
                    var old=new dev.jasper.app.platform.PresentationIcon("owner:old","M 2 2 L 14 14");
                    palette.setEntries(List.of(new PaletteEntry.Item(COMMANDS,new PaletteRow("row","Row",null,null,old,true,null))),null,null);
                    var next=new dev.jasper.app.platform.PresentationIcon(nextName,nextName.contains(":") ? "M 2 14 L 14 2" : null);
                    palette.setEntries(List.of(new PaletteEntry.Item(COMMANDS,new PaletteRow("row","Row",null,null,next,true,null))),null,null);
                    var list=(dev.jasper.ui.model.Nodes.ItemList)palette.resultsView().spec().root();
                    assertThat(((dev.jasper.ui.model.Nodes.Icon)list.items().getFirst().leading().orElseThrow()).name()).isEqualTo(nextName);
                    assertThat(palette.kit().icons().names()).doesNotContain("owner:old").contains(nextName);
                } finally { palette.close(); }
            }
        });
    }

    @Test void sameRowRepublishedNameUsesItsNewImmutablePath() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try(var palette=palette(new ArrayList<>())) {
                var old=new dev.jasper.app.platform.PresentationIcon("owner:mark","M 2 2 L 14 14");
                palette.setEntries(List.of(new PaletteEntry.Item(COMMANDS,new PaletteRow("row","Row",null,null,old,true,null))),null,null);
                var before=KitTestSupport.require(palette.resultsView(),"entry.0.icon",javax.swing.JLabel.class).getIcon();
                var next=new dev.jasper.app.platform.PresentationIcon("owner:mark","M 2 14 L 14 2");
                palette.setEntries(List.of(new PaletteEntry.Item(COMMANDS,new PaletteRow("row","Row",null,null,next,true,null))),null,null);
                var after=KitTestSupport.require(palette.resultsView(),"entry.0.icon",javax.swing.JLabel.class).getIcon();
                try(var expected=new dev.jasper.ui.icons.IconRegistry()) {
                    expected.register("owner:mark","M 2 14 L 14 2");
                    assertThat(dev.jasper.app.testsupport.IconPixels.of(after)).isNotEqualTo(dev.jasper.app.testsupport.IconPixels.of(before))
                        .containsExactly(dev.jasper.app.testsupport.IconPixels.of(expected.icon("owner:mark",palette.kit().theme().semantic(),dev.jasper.ui.model.Nodes.Hue.NONE,after.getIconWidth())));
                }
            }
        });
    }

    @Test void queryNotifiesWhenClearedAndKeepsNativeEditingActions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var changes = new ArrayList<String>();
            var palette = new CommandPalette(KitTestSupport.context(), false, changes::add, (entry, verb) -> { }, text -> java.util.Optional.empty(), scope -> { });
            var query = PaletteTestSupport.query(palette);
            query.setText("split 2");
            query.setText("");
            assertThat(changes).containsExactly("split 2", "");
            assertThat(query.getActionMap().get(DefaultEditorKit.copyAction)).isNotNull();
            assertThat(query.getActionMap().get(DefaultEditorKit.pasteAction)).isNotNull();
            assertThat(hasInputBinding(query, DefaultEditorKit.copyAction)).isTrue();
            assertThat(hasInputBinding(query, DefaultEditorKit.pasteAction)).isTrue();
            assertThat(palette.chipText()).isNull();
            assertThat(PaletteTestSupport.chip(palette).isShowing() || palette.chipView().isVisible()).as("no chip").isFalse();
            assertThat(query.getAccessibleContext().getAccessibleName()).isEqualTo("Search all");
            assertThat(PaletteTestSupport.resultsView(palette).getAccessibleContext().getAccessibleName()).isEqualTo("All");
            assertThat(PaletteTestSupport.resultsView(palette).getAccessibleContext().getAccessibleDescription()).isEqualTo("0 results");
            assertThat(palette.placeholder()).isEqualTo("Search everything");
            assertThat(((Nodes.Field) palette.queryView().spec().root()).hint()).as("the kit field paints the placeholder").isEqualTo("Search everything");
            assertThat(KitTestSupport.find(palette.queryView(), CommandPalette.QUERY + ".label", JLabel.class).isVisible())
                .as("the search field shows no label in the 44 px row").isFalse();
            palette.close();
        });
    }

    @Test void theChipNamesTheScopeAndATypedPrefixBecomesAChip() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var chips = new ArrayList<String>();
            var palette = new CommandPalette(KitTestSupport.context(), true, query -> { }, (entry, verb) -> { },
                text -> ScopePrefix.parse(text, List.of(COMMANDS, HISTORY)), scope -> chips.add(scope.id()));
            var query = PaletteTestSupport.query(palette);
            palette.setChip("History", null);
            assertThat(palette.chipText()).isEqualTo("History");
            assertThat(palette.chipView().isVisible()).isTrue();
            assertThat(((Nodes.Chip) palette.chipView().spec().root()).label()).isEqualTo("History");
            assertThat(palette.chipView().getAccessibleContext().getAccessibleName()).isEqualTo("Scope: History");
            assertThat(query.getAccessibleContext().getAccessibleName()).isEqualTo("Search history");
            assertThat(PaletteTestSupport.resultsView(palette).getAccessibleContext().getAccessibleName()).isEqualTo("History");
            palette.setChip(null, null);
            assertThat(palette.chipText()).isNull();
            assertThat(palette.chipView().isVisible()).isFalse();
            assertThat(query.getAccessibleContext().getAccessibleName()).isEqualTo("Search all");
            query.setText("hist > ls");
            assertThat(chips).containsExactly("test.history");
            assertThat(palette.queryText()).as("the prefix is consumed").isEqualTo("ls");
            PaletteTestSupport.settle();
            assertThat(query.getText()).as("and leaves the field once it settles").isEqualTo("ls");
            query.setText("");
            query.replaceSelection(">");
            assertThat(chips).containsExactly("test.history", "jasper.commands");
            assertThat(palette.queryText()).isEmpty();
            PaletteTestSupport.settle();
            assertThat(query.getText()).isEmpty();
            query.setText("cat foo > bar");
            assertThat(query.getText()).as("not a scope name").isEqualTo("cat foo > bar");
            assertThat(palette.queryText()).isEqualTo("cat foo > bar");
            assertThat(chips).hasSize(2);
            query.setText("hist x> ls");
            try { query.getDocument().remove(5, 1); } catch (javax.swing.text.BadLocationException failure) { throw new AssertionError(failure); }
            assertThat(palette.queryText()).as("deleting text never makes a chip, as the prefix filter ignored removals").isEqualTo("hist > ls");
            assertThat(chips).hasSize(2);
            palette.setPlaceholder("Search shell history");
            assertThat(palette.placeholder()).isEqualTo("Search shell history");
            assertThat(((Nodes.Field) palette.queryView().spec().root()).hint()).isEqualTo("Search shell history");
            palette.close();
        });
    }

    /**
     * A key typed before the field shows the normalized query still carries the consumed prefix; the palette strips it,
     * as the old synchronous prefix filter did, so the chip's query is only what was typed after it.
     */
    @Test void aKeyQueuedBeforeTheChipSettlesStillLeavesThePrefixOut() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var chips = new ArrayList<String>();
            CommandPalette[] holder = new CommandPalette[1];
            // As the controller: a prefix makes a chip only while none is set.
            holder[0] = new CommandPalette(KitTestSupport.context(), true, query -> { }, (entry, verb) -> { },
                text -> holder[0].chipText() != null ? java.util.Optional.empty() : ScopePrefix.parse(text, List.of(COMMANDS, HISTORY)),
                scope -> { chips.add(scope.id()); holder[0].setChip(scope.label(), null); });
            var palette = holder[0];
            var query = PaletteTestSupport.query(palette);
            query.setText("hist > ");
            assertThat(chips).containsExactly("test.history");
            assertThat(palette.queryText()).isEmpty();
            try { query.getDocument().insertString(query.getDocument().getLength(), "l", null); }
            catch (javax.swing.text.BadLocationException failure) { throw new AssertionError(failure); }
            assertThat(palette.queryText()).as("the queued key's text without the consumed prefix").isEqualTo("l");
            PaletteTestSupport.settle();
            assertThat(palette.chipText()).isEqualTo("History");
            assertThat(chips).as("one chip").containsExactly("test.history");
            assertThat(palette.queryText()).isEqualTo("l");
            assertThat(query.getText()).isEqualTo("l");
            assertThat(query.getCaretPosition()).as("the caret after the typed text").isEqualTo(1);
            query.replaceSelection("s");
            assertThat(palette.queryText()).isEqualTo("ls");
            query.setText("hist > x");
            assertThat(palette.queryText()).as("once settled, text that looks like the prefix is ordinary text").isEqualTo("hist > x");
            palette.close();
        });
    }

    @Test void allGroupsRowsUnderHeadersAndSelectionSkipsThem() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = palette(new ArrayList<>());
            var entries = List.<PaletteEntry>of(new PaletteEntry.Header("Commands"), item(COMMANDS, "new_tab"),
                new PaletteEntry.Header("History"), item(HISTORY, "ls"), new PaletteEntry.More(HISTORY));
            palette.setEntries(entries, null, "Nothing found");
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).as("the first row, never a header").isEqualTo("jasper.commands/new_tab");
            var list = PaletteTestSupport.resultsSpec(palette);
            assertThat(list.kind()).isEqualTo(Nodes.ListKind.RESULTS);
            assertThat(list.items()).extracting(dev.jasper.ui.model.ListItem::heading).containsExactly(true, false, true, false, false);
            assertThat(list.selectedIds()).containsExactly("entry.1");
            palette.selectRelative(1);
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).isEqualTo("test.history/ls");
            assertThat(PaletteTestSupport.resultsSpec(palette).selectedIds()).as("the kit list follows").containsExactly("entry.3");
            palette.selectRelative(1);
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).isEqualTo("more:test.history");
            palette.selectRelative(50);
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).isEqualTo("more:test.history");
            palette.selectRelative(-50);
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).isEqualTo("jasper.commands/new_tab");
            assertThat(PaletteTestSupport.resultsView(palette).getAccessibleContext().getAccessibleDescription()).isEqualTo("2 results");
            palette.setEntries(entries, PaletteEntry.key(HISTORY, "ls"), "Nothing found");
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).as("kept by scope and row").isEqualTo("test.history/ls");
            palette.setEntries(entries.subList(0, 2), PaletteEntry.key(HISTORY, "ls"), "Nothing found");
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).as("falls back to the first row").isEqualTo("jasper.commands/new_tab");
            palette.setEntries(List.of(), null, "Nothing found");
            palette.selectRelative(1);
            assertThat(palette.selectedEntry()).isNull();
            assertThat(((Nodes.Text) palette.emptyView().spec().root()).text()).isEqualTo("Nothing found");
            var tooMany = new ArrayList<PaletteEntry>();
            for (int i = 0; i < PaletteResults.MAX_ROWS + 1; i++) tooMany.add(item(COMMANDS, "many." + i));
            assertThatIllegalArgumentException().isThrownBy(() -> palette.setEntries(tooMany, null, null));
            palette.close();
        });
    }

    @Test void selectRowFindsARowByIdAndTheQueryAcceptsDigits() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = palette(new ArrayList<>());
            palette.setEntries(List.of(item(COMMANDS, "select_tab_1"), item(COMMANDS, "select_tab_2")), null, null);
            PaletteTestSupport.query(palette).setText("tab 2");
            palette.selectRow("select_tab_2");
            assertThat(PaletteTestSupport.query(palette).getText()).isEqualTo("tab 2");
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).isEqualTo("jasper.commands/select_tab_2");
            palette.selectRow("missing");
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).isEqualTo("jasper.commands/select_tab_2");
            palette.close();
        });
    }

    @Test void preservedSelectionStaysVisibleAcrossReorderAndGrowth() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = palette(new ArrayList<>());
            List<PaletteEntry> rows = new ArrayList<>();
            for (String id : List.of("first", "second", "third", "fourth", "selected")) rows.add(item(COMMANDS, id));
            palette.setEntries(rows, null, null);
            palette.setSize(UIScale.scale(318), UIScale.scale(150));
            layoutTree(palette);
            palette.selectRelative(4);
            layoutTree(palette);
            var canvas = PaletteTestSupport.resultsCanvas(palette);
            assertThat(canvas.getVisibleRect().contains(PaletteTestSupport.rowBounds(palette, 4))).isTrue();
            var reordered = new ArrayList<>(rows); reordered.addFirst(reordered.removeLast());
            palette.setEntries(reordered, PaletteEntry.key(COMMANDS, "selected"), null);
            layoutTree(palette);
            assertThat(palette.selectedIndex()).isZero();
            assertThat(canvas.getVisibleRect().contains(PaletteTestSupport.rowBounds(palette, 0))).isTrue();
            palette.setEntries(rows, PaletteEntry.key(COMMANDS, "selected"), null);
            layoutTree(palette);
            assertThat(palette.selectedIndex()).isEqualTo(4);
            assertThat(canvas.getVisibleRect().contains(PaletteTestSupport.rowBounds(palette, 4))).isTrue();
            palette.close();
        });
    }

    @Test void rowsExecuteOnAPressWhileHeadersAndBlankSpaceDoNothing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var events = new ArrayList<String>();
            var palette = palette(events);
            palette.setEntries(List.of(new PaletteEntry.Header("Commands"), item(COMMANDS, "first"), item(COMMANDS, "second")), null, null);
            palette.setSize(palette.getPreferredSize());
            layoutTree(palette);
            Component header = row(palette, 0), second = row(palette, 2);
            assertThat(header.getHeight()).isEqualTo(UIScale.scale(22));
            assertThat(second.getHeight()).isEqualTo(UIScale.scale(24));
            header.dispatchEvent(mousePress(header, 4, 4));
            second.dispatchEvent(mousePress(second, 4, 4));
            var canvas = PaletteTestSupport.resultsCanvas(palette);
            canvas.setSize(canvas.getWidth(), UIScale.scale(200));
            canvas.dispatchEvent(mousePress(canvas, 4, UIScale.scale(190)));
            assertThat(events).containsExactly("jasper.commands/second:0");
            assertThat(canvas.isFocusable()).as("the list never takes the query's focus").isFalse();
            palette.close();
        });
    }

    @Test void inputMethodEventsExposeOnlyActiveComposition() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = palette(new ArrayList<>());
            var query = PaletteTestSupport.query(palette);
            query.dispatchEvent(new InputMethodEvent(query, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, new AttributedString("ab").getIterator(), 1, null, null));
            assertThat(palette.composing()).isTrue();
            query.dispatchEvent(new InputMethodEvent(query, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, new AttributedString("ab").getIterator(), 2, null, null));
            assertThat(palette.composing()).isFalse();
            palette.close();
        });
    }

    @Test void geometryFollowsSearchEverywhere() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = palette(new ArrayList<>());
            palette.setEntries(List.of(new PaletteEntry.Header("Commands"), item(COMMANDS, "a"), item(COMMANDS, "b")), null, null);
            palette.setSize(palette.getPreferredSize());
            layoutTree(palette);
            Insets insets = palette.getInsets();
            assertThat(palette.getPreferredSize().width).isEqualTo(UIScale.scale(680));
            assertThat(palette.getPreferredSize().height).isEqualTo(UIScale.scale(44 + 22 + 2 * 24 + 26) + insets.top + insets.bottom);
            assertThat(PaletteTestSupport.fieldRow(palette).getHeight()).isEqualTo(UIScale.scale(44));
            assertThat(palette.hintBar().getHeight()).isEqualTo(UIScale.scale(26));
            assertThat(palette.itemHeight()).isEqualTo(UIScale.scale(24));
            assertThat(palette.isOpaque()).as("rounded corners expose the underlying workspace").isFalse();
            palette.setEntries(List.of(), null, "Nothing found");
            assertThat(palette.getPreferredSize().height).as("the empty line").isEqualTo(UIScale.scale(44 + 24 + 26) + insets.top + insets.bottom);
            palette.close();
        });
    }

    @Test void theListShowsTheVisibleRowsAndScrollsToTheRest() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = palette(new ArrayList<>());
            var rows = new ArrayList<PaletteEntry>();
            for (int i = 0; i < 30; i++) rows.add(item(HISTORY, "r" + i));
            palette.setEntries(rows, null, null);
            Insets insets = palette.getInsets();
            assertThat(palette.visibleRows()).as("five by default").isEqualTo(5);
            assertThat(PaletteTestSupport.resultsSpec(palette).visibleRows()).hasValue(5);
            assertThat(palette.getPreferredSize().height).isEqualTo(UIScale.scale(44 + 5 * 24 + 26) + insets.top + insets.bottom);
            palette.setEntries(List.of(new PaletteEntry.Header("History"), item(HISTORY, "a"), item(HISTORY, "b"),
                new PaletteEntry.Header("Commands"), item(COMMANDS, "c"), item(COMMANDS, "d"), item(COMMANDS, "e"),
                item(COMMANDS, "f")), null, null);
            assertThat(palette.getPreferredSize().height).as("headers above the fifth row add their own height")
                .isEqualTo(UIScale.scale(44 + 2 * 22 + 5 * 24 + 26) + insets.top + insets.bottom);
            assertThat(((javax.swing.Scrollable) PaletteTestSupport.resultsCanvas(palette)).getPreferredScrollableViewportSize().height)
                .as("the kit list agrees").isEqualTo(UIScale.scale(2 * 22) + 5 * UIScale.scale(24));
            palette.setVisibleRows(8);
            palette.setEntries(rows, null, null);
            assertThat(palette.getPreferredSize().height).isEqualTo(UIScale.scale(44 + 8 * 24 + 26) + insets.top + insets.bottom);
            assertThat(PaletteTestSupport.resultsSpec(palette).visibleRows()).hasValue(8);
            palette.setSize(palette.getPreferredSize());
            layoutTree(palette);
            palette.selectRelative(29);
            layoutTree(palette);
            assertThat(palette.selectedIndex()).isEqualTo(29);
            assertThat(PaletteTestSupport.resultsCanvas(palette).getVisibleRect().intersects(PaletteTestSupport.rowBounds(palette, 29))).isTrue();
            palette.close();
        });
    }

    @Test void everySurfaceTakesItsColourFromTheTheme() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (boolean dark : new boolean[]{false, true}) {
                var bindings = KitTestSupport.bindings(dark);
                try {
                    var kit = bindings.context(ThemeRoute.CHROME);
                    var semantic = kit.theme().semantic();
                    String name = dark ? "dark" : "light";
                    var palette = palette(kit, true, new ArrayList<>());
                    var historyRow = new PaletteRow("c", "Row c", "a detail", null, null, true, null);
                    palette.setEntries(List.of(new PaletteEntry.Header("Commands"), item(COMMANDS, "a"), item(COMMANDS, "b"),
                        new PaletteEntry.Header("History"), new PaletteEntry.Item(HISTORY, historyRow)), null, null);
                    palette.setSize(palette.getPreferredSize());
                    layoutTree(palette);
                    var image = paint(palette);
                    assertPixel(image, new Point(palette.getWidth() / 2, 0), semantic.color(SemanticColor.BORDER_MENU), name + " outline");
                    for (Point corner : List.of(new Point(0, 0), new Point(palette.getWidth() - 1, 0),
                            new Point(0, palette.getHeight() - 1), new Point(palette.getWidth() - 1, palette.getHeight() - 1))) {
                        assertThat(image.getRGB(corner.x, corner.y) >>> 24).as("transparent rounded corner").isZero();
                        assertThat(palette.contains(corner)).isFalse();
                    }
                    assertThat(palette.contains(palette.getWidth() / 2, 1)).isTrue();
                    var fieldRow = PaletteTestSupport.fieldRow(palette);
                    assertPixel(image, at(fieldRow, fieldRow.getWidth() - UIScale.scale(3), fieldRow.getHeight() / 2, palette), semantic.color(SemanticColor.SURFACE_CARD), name + " field row");
                    // One surface throughout: the query field is the card's colour inside its input outline.
                    assertThat(((Nodes.Field) palette.queryView().spec().root()).presentation()).isEqualTo(Nodes.FieldPresentation.TRANSPARENT);
                    var input = PaletteTestSupport.query(palette);
                    var surface = (JComponent) input.getParent();
                    assertPixel(image, at(surface, surface.getWidth() - UIScale.scale(4), surface.getHeight() / 2, palette),
                        semantic.color(SemanticColor.SURFACE_CARD), name + " the field is the card's colour");
                    assertPixel(image, at(surface, surface.getWidth() / 2, 0, palette), semantic.color(SemanticColor.BORDER_INPUT), name + " field outline");
                    Component selected = row(palette, 1), plain = row(palette, 2), rule = row(palette, 3);
                    assertPixel(image, at(selected, selected.getWidth() - 12, 3, palette), semantic.color(SemanticColor.SELECTION_BG), name + " selection");
                    assertPixel(image, at(plain, plain.getWidth() - 3, 3, palette), semantic.color(SemanticColor.SURFACE_CARD), name + " row");
                    assertPixel(image, at(rule, rule.getWidth() - 3, 0, palette), semantic.color(SemanticColor.BORDER_SUBTLE), name + " heading rule");
                    var hintBar = palette.hintBar();
                    assertPixel(image, at(hintBar, hintBar.getWidth() - 3, hintBar.getHeight() / 2, palette), semantic.color(SemanticColor.SURFACE_CARD), name + " hint bar");
                    assertPixel(image, at(hintBar, hintBar.getWidth() / 2, 0, palette), semantic.color(SemanticColor.BORDER_SUBTLE), name + " hint rule");
                    // Select History's row (three verbs, a detail) to populate the hint bar with link-coloured
                    // secondary verbs and its detail text, without disturbing the pixel assertions taken above.
                    palette.selectRow("c");
                    assertThat(palette.hintText()).as(name).isEqualTo("a detail");
                    assertThat(palette.hintActionTexts()).as(name).isNotEmpty();
                    assertThat(label(hintBar, "a detail").getForeground()).as(name).isEqualTo(semantic.color(SemanticColor.TEXT_SECONDARY));
                    for (int verb = 1; verb <= palette.hintActionTexts().size(); verb++)
                        assertThat(KitTestSupport.require(palette.hintView(), CommandPalette.VERB + verb, AbstractButton.class).getForeground())
                            .as(name + " verb " + verb).isEqualTo(semantic.color(SemanticColor.TEXT_LINK));
                    layoutTree(palette);
                    assertThat(label(row(palette, 4), "a detail").getForeground()).as(name).isEqualTo(dev.jasper.ui.theme.SurfaceContrast.on(semantic, SemanticColor.TEXT_ONACCENT_SECONDARY, SemanticColor.SELECTION_BG, dev.jasper.ui.theme.SurfaceContrast.TEXT));
                    assertThat(label(row(palette, 3), "History").getForeground()).as(name).isEqualTo(semantic.color(SemanticColor.TEXT_SECONDARY));
                    palette.close();
                } finally { bindings.close(); }
            }
        });
    }

    @Test void selectedAndHoveredRowsPaintTheKitListColours() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var bindings = KitTestSupport.bindings(false);
            try {
                var kit = bindings.context(ThemeRoute.CHROME);
                var semantic = kit.theme().semantic();
                var palette = palette(kit, true, new ArrayList<>());
                palette.setEntries(List.of(item(COMMANDS, "a"), item(COMMANDS, "b")), null, null);
                palette.setSize(palette.getPreferredSize());
                layoutTree(palette);
                Component first = row(palette, 0), second = row(palette, 1);
                second.dispatchEvent(new MouseEvent(second, MouseEvent.MOUSE_ENTERED, 1L, 0, 4, 4, 0, false));
                var image = paint(palette);
                Color card = semantic.color(SemanticColor.SURFACE_CARD);
                assertPixel(image, at(first, first.getWidth() - 12, 3, palette), semantic.color(SemanticColor.SELECTION_BG), "selected");
                assertPixel(image, at(second, second.getWidth() - 12, 3, palette),
                    over(card, ColorMath.opacity(semantic.color(SemanticColor.HOVER_OVERLAY), .05)), "hovered");
                assertPixel(image, at(first, first.getWidth() - 5, 0, palette), card, "selected rounded corner");
                assertPixel(image, at(second, second.getWidth() - 5, 0, palette), card, "hover rounded corner");
                second.dispatchEvent(new MouseEvent(second, MouseEvent.MOUSE_EXITED, 1L, 0, 4, 4, 0, false));
                assertPixel(paint(palette), at(second, second.getWidth() - 12, 3, palette), card, "after the mouse left");
                palette.close();
            } finally { bindings.close(); }
        });
    }

    @Test void rowsLayOutOnOneLineAndTheTagGoesFirstWhenSpaceRunsOut() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = palette(new ArrayList<>());
            var row = new PaletteRow("r", "Rendered command", "a detail", "⌘T", null, true, null);
            palette.setEntries(List.of(new PaletteEntry.Item(COMMANDS, row), new PaletteEntry.More(HISTORY), new PaletteEntry.Header("History")), null, null);
            palette.setSize(palette.getPreferredSize().width, UIScale.scale(400));
            layoutTree(palette);
            var line = (Container) row(palette, 0);
            line.setSize(UIScale.scale(680), UIScale.scale(24));
            line.doLayout();
            JLabel title = label(line, "Rendered command"), detail = label(line, "a detail"), tag = label(line, "⌘T");
            java.util.function.Function<JComponent, Rectangle> in = c -> SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), line);
            assertThat(in.apply(detail).x).as("the detail follows the title").isGreaterThanOrEqualTo(in.apply(title).x + title.getWidth());
            assertThat(Math.abs(in.apply(detail).getCenterY() - in.apply(title).getCenterY())).as("on the same line").isLessThanOrEqualTo(1);
            assertThat(in.apply(tag).x + tag.getWidth()).as("the tag is right-aligned").isEqualTo(UIScale.scale(680) - UIScale.scale(8));
            int side = UIScale.scale(8), gap = UIScale.scale(8), iconSize = UIScale.scale(16);
            int start = side + iconSize + gap;
            assertThat(in.apply(title).x).as("after the icon column").isEqualTo(start);
            line.setSize(title.getPreferredSize().width + UIScale.scale(40), UIScale.scale(24));
            line.doLayout();
            assertThat(tag.getParent().getWidth()).as("the tag goes first").isZero();
            assertThat(title.getParent().getWidth()).isPositive();
            // Wide enough for title + tag alone, but not for title + detail + tag: the tag must still be
            // dropped first, not just cut for the detail's sake, or the detail would never get its turn.
            int fitsTitleAndTagOnly = start + title.getPreferredSize().width + gap + tag.getParent().getPreferredSize().width;
            line.setSize(fitsTitleAndTagOnly + side, UIScale.scale(24));
            line.doLayout();
            assertThat(tag.getParent().getWidth()).as("the tag is dropped when the detail would not otherwise fit too").isZero();
            assertThat(detail.getParent().getWidth()).as("the detail takes its place, cut if needed").isPositive();
            assertThat(labels(row(palette, 1))).extracting(JLabel::getText).contains("More in History…");
            assertThat(row(palette, 2).getHeight()).isEqualTo(UIScale.scale(22));
            palette.close();
        });
    }

    @Test void renderedRowsCarryNoNumberBadge() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = palette(new ArrayList<>());
            var entries = new ArrayList<PaletteEntry>();
            for (int i = 0; i < 6; i++) entries.add(item(COMMANDS, "row" + i));
            palette.setEntries(entries, null, null);
            palette.setSize(palette.getPreferredSize().width, UIScale.scale(400));
            layoutTree(palette);
            var badges = List.of("⌘1", "⌘2", "⌘3", "⌘4", "⌘5", "Ctrl+1", "Ctrl+2", "Ctrl+3", "Ctrl+4", "Ctrl+5");
            for (int i = 0; i < entries.size(); i++)
                for (JLabel label : labels(row(palette, i)))
                    for (String badge : badges) assertThat(label.getText()).as("row " + i).doesNotContain(badge);
            palette.close();
        });
    }

    @Test void theHintBarShowsTheDetailOrTagAndTheOtherVerbsWithTheirKeys() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var events = new ArrayList<String>();
            var palette = palette(events);
            palette.setEntries(List.of(new PaletteEntry.Item(HISTORY, new PaletteRow("ls", "ls -la", "in ~/src", null, null, true, null)),
                new PaletteEntry.Item(COMMANDS, new PaletteRow("new_tab", "New Tab", null, "⌘T", null, true, null)),
                new PaletteEntry.More(HISTORY)), null, null);
            assertThat(palette.hintText()).isEqualTo("in ~/src");
            assertThat(palette.hintActionTexts()).containsExactly("Paste and run ⌘⏎", "Save as snippet… ⇧⏎");
            assertThat(PaletteTestSupport.hintBarTexts(palette)).containsExactly("in ~/src", "Paste and run ⌘⏎", "Save as snippet… ⇧⏎");
            PaletteTestSupport.clickHintAction(palette, 1);
            assertThat(events).containsExactly("test.history/ls:2");
            palette.selectRelative(1);
            assertThat(palette.hintText()).as("a command shows its shortcut").isEqualTo("⌘T");
            assertThat(palette.hintActionTexts()).isEmpty();
            palette.selectRelative(1);
            assertThat(palette.hintText()).isEqualTo("Show every match in History");
            var elsewhere = palette(KitTestSupport.context(), false, new ArrayList<>());
            elsewhere.setEntries(List.of(item(HISTORY, "ls")), null, null);
            assertThat(elsewhere.hintActionTexts()).containsExactly("Paste and run Ctrl+Enter", "Save as snippet… Shift+Enter");
            palette.close(); elsewhere.close();
        });
    }

    @Test void theAccessibleDescriptionCarriesTheCountAndTheSelectedScopesVerbsWithTheirKeys() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = palette(new ArrayList<>());
            var results = PaletteTestSupport.resultsView(palette).getAccessibleContext();
            palette.setEntries(List.of(new PaletteEntry.Item(HISTORY, new PaletteRow("ls", "ls -la", "in ~/src", null, null, true, null)),
                new PaletteEntry.Item(COMMANDS, new PaletteRow("new_tab", "New Tab", null, "⌘T", null, true, null)),
                new PaletteEntry.More(HISTORY)), null, null);
            assertThat(results.getAccessibleDescription())
                .as("a three-verb scope's row carries every verb with its key")
                .isEqualTo("2 results; ⏎ Paste  ⌘⏎ Paste and run  ⇧⏎ Save as snippet…");
            palette.selectRelative(1);
            assertThat(results.getAccessibleDescription()).as("a single-verb scope adds nothing beyond the count").isEqualTo("2 results");
            palette.selectRelative(1);
            assertThat(results.getAccessibleDescription())
                .as("a selected \"More in\" row describes what Enter does there")
                .isEqualTo("2 results; ⏎ Show every match in History");
            palette.selectRelative(-2);
            assertThat(results.getAccessibleDescription())
                .as("switching back to a three-verb scope restores its verbs")
                .isEqualTo("2 results; ⏎ Paste  ⌘⏎ Paste and run  ⇧⏎ Save as snippet…");
            var elsewhere = palette(KitTestSupport.context(), false, new ArrayList<>());
            elsewhere.setEntries(List.of(item(HISTORY, "ls")), null, null);
            assertThat(PaletteTestSupport.resultsView(elsewhere).getAccessibleContext().getAccessibleDescription())
                .as("the other platform's key symbols")
                .isEqualTo("1 results; Enter Paste  Ctrl+Enter Paste and run  Shift+Enter Save as snippet…");
            elsewhere.setEntries(List.of(), null, "Nothing found");
            assertThat(PaletteTestSupport.resultsView(elsewhere).getAccessibleContext().getAccessibleDescription())
                .as("nothing selected keeps only the count").isEqualTo("0 results");
            palette.close(); elsewhere.close();
        });
    }

    @Test void aStepReplacesTheListAndHidesTheHintBar() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = palette(new ArrayList<>());
            palette.setEntries(List.of(item(HISTORY, "a"), item(HISTORY, "b")), null, null);
            palette.showStep("Rebase", List.of(new PaletteStep.Field("branch", "branch", "main"),
                new PaletteStep.Field("remote", "remote", "")));
            var fields = PaletteTestSupport.stepFields(palette);
            assertThat(palette.stepShowing()).isTrue();
            assertThat(fields).hasSize(2);
            assertThat(fields.getFirst().getText()).isEqualTo("main");
            assertThat(fields.getFirst().getSelectedText()).as("selected for replacement").isEqualTo("main");
            assertThat(fields.getFirst().getAccessibleContext().getAccessibleName()).isEqualTo("branch");
            assertThat(palette.stepFocusIndex()).isZero();
            assertThat(palette.stepTitleText()).isEqualTo("Rebase");
            assertThat(KitTestSupport.require(palette.stepPanel(), CommandPalette.STEP + ".title", JLabel.class).getText()).isEqualTo("Rebase");
            assertThat(palette.hintBar().isVisible()).isFalse();
            Insets insets = palette.getInsets();
            assertThat(palette.getPreferredSize().height).isEqualTo(UIScale.scale(44 + 22 + 2 * 40) + insets.top + insets.bottom);
            // The laid-out form keeps the former geometry: a 22 px title row, 40 px field rows padded 4/12, a 140 px
            // label column and a 10 px gap before the field.
            palette.setSize(palette.getPreferredSize());
            layoutTree(palette);
            var panel = palette.stepPanel();
            var titleRow = palette.stepTitleRow();
            assertThat(titleRow.getHeight()).isEqualTo(UIScale.scale(22));
            assertThat(SwingUtilities.convertPoint(KitTestSupport.require(panel, CommandPalette.STEP + ".title", JLabel.class), 0, 0, titleRow).x)
                .as("the title is inset like the rows").isEqualTo(UIScale.scale(12));
            for (int i = 0; i < 2; i++) {
                var row = palette.stepRow(i);
                assertThat(row.getHeight()).as("row %d", i).isEqualTo(UIScale.scale(40));
                assertThat(SwingUtilities.convertPoint(row, 0, 0, panel).y).isEqualTo(UIScale.scale(22) + i * UIScale.scale(40));
                var label = palette.stepLabelView(i);
                assertThat(label.getX()).isEqualTo(UIScale.scale(12));
                assertThat(label.getWidth()).as("the label column").isEqualTo(UIScale.scale(140));
                assertThat(KitTestSupport.require(label, CommandPalette.STEP + ".label." + i, JLabel.class).getText()).isEqualTo(i == 0 ? "branch" : "remote");
                var field = palette.stepFieldView(i);
                assertThat(field.getX()).as("a 10 px gap").isEqualTo(UIScale.scale(12 + 140 + 10));
                assertThat(field.getY()).isEqualTo(UIScale.scale(4));
                assertThat(field.getHeight()).isEqualTo(UIScale.scale(40 - 2 * 4));
                assertThat(row.getWidth() - field.getX() - field.getWidth()).isEqualTo(UIScale.scale(12));
                assertThat(KitTestSupport.find(field, CommandPalette.STEP + ".field." + i + ".label", JLabel.class).isVisible())
                    .as("the row's label column labels the field").isFalse();
            }
            palette.focusStepField(1);
            assertThat(palette.stepFocusIndex()).isEqualTo(1);
            palette.focusStepField(1);
            assertThat(palette.stepFocusIndex()).isZero();
            palette.focusStepField(-1);
            assertThat(palette.stepFocusIndex()).isEqualTo(1);
            fields.get(1).setText("origin");
            assertThat(palette.stepValues()).hasSize(2).containsEntry("branch", "main").containsEntry("remote", "origin");
            palette.setStepError("Nope");
            assertThat(palette.stepErrorText()).isEqualTo("Nope");
            assertThat(KitTestSupport.require(palette.stepPanel(), CommandPalette.STEP + ".error", JComponent.class).isVisible()).isTrue();
            assertThat(KitTestSupport.require(palette.stepPanel(), CommandPalette.STEP + ".error.text", JLabel.class).getText()).isEqualTo("Nope");
            assertThat(fields.get(1).getText()).as("an error keeps what was typed").isEqualTo("origin");
            assertThat(palette.getPreferredSize().height).isEqualTo(UIScale.scale(44 + 22 + 2 * 40 + 22) + insets.top + insets.bottom);
            palette.setSize(palette.getPreferredSize());
            layoutTree(palette);
            assertThat(palette.stepErrorRow().getHeight()).as("the error row").isEqualTo(UIScale.scale(22));
            assertThat(SwingUtilities.convertPoint(palette.stepErrorRow(), 0, 0, palette.stepPanel()).y).isEqualTo(UIScale.scale(22 + 2 * 40));
            palette.setStepError(null);
            assertThat(palette.stepErrorText()).isNull();
            assertThat(KitTestSupport.require(palette.stepPanel(), CommandPalette.STEP + ".error", JComponent.class).isVisible()).isFalse();
            palette.hideStep();
            assertThat(palette.stepShowing()).isFalse();
            assertThat(PaletteTestSupport.stepFields(palette)).isEmpty();
            assertThat(palette.hintBar().isVisible()).isTrue();
            palette.close();
        });
    }

    @Test void headingsFooterAndQueryGlyphUseTheirReferenceRoles() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            try(var palette=palette(new ArrayList<>())) {
                palette.setEntries(List.of(new PaletteEntry.Header("Recently used"),
                    new PaletteEntry.Item(HISTORY,new PaletteRow("ls","ls -la","in ~/src",null,null,true,null))),null,null);
                palette.setSize(palette.getPreferredSize());layoutTree(palette);
                var hint=dev.jasper.ui.swing.NativeControlStyles.font(palette.kit(),dev.jasper.ui.metrics.Typography.Role.PALETTE_HINT);
                assertThat(label(PaletteTestSupport.entryComponent(palette,0),"Recently used").getFont())
                    .isEqualTo(dev.jasper.ui.swing.NativeControlStyles.font(palette.kit(),dev.jasper.ui.metrics.Typography.Role.RESULTS_HEADING));
                assertThat(label(palette.hintView(),"in ~/src").getFont()).as("footer detail").isEqualTo(hint);
                for(int verb=1;verb<3;verb++)
                    assertThat(KitTestSupport.require(palette.hintView(),CommandPalette.VERB+verb,AbstractButton.class).getFont()).as("footer verb").isEqualTo(hint);
                // The transparent query shows the rail-size search glyph its reference depicts.
                assertThat(icons(PaletteTestSupport.fieldRow(palette))).anySatisfy(icon -> assertThat(icon.getIconWidth())
                    .isEqualTo(com.formdev.flatlaf.util.UIScale.scale(dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.ICON_RAIL))));
            }
        });
    }

    private static List<javax.swing.Icon> icons(java.awt.Container root) {
        var found=new ArrayList<javax.swing.Icon>();
        for(var child:root.getComponents()) {
            if(child instanceof JLabel label && label.getIcon()!=null)found.add(label.getIcon());
            if(child instanceof java.awt.Container nested)found.addAll(icons(nested));
        }
        return found;
    }

    @Test void detailsUseOrdinaryBodyEvenWhenTheScopeTitleIsMonospace() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            try(var palette=palette(new ArrayList<>())) {
                for(var scope:List.of(COMMANDS,HISTORY)) {
                    palette.setEntries(List.of(new PaletteEntry.Item(scope,new PaletteRow("one","Title","Detail",null,null,true,null))),null,null);
                    palette.setSize(palette.getPreferredSize());layoutTree(palette);
                    var detail=label(row(palette,0),"Detail");
                    var expected=dev.jasper.ui.swing.NativeControlStyles.font(palette.kit(),dev.jasper.ui.metrics.Typography.Role.BODY);
                    assertThat(detail.getFont()).isEqualTo(expected);
                    if(scope.monospaceRows())assertThat(label(row(palette,0),"Title").getFont().getFamily()).isEqualTo(java.awt.Font.MONOSPACED);
                }
            }
        });
    }

    @Test void selectedResultDetailUsesReadableReceivingSelectionText() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for(boolean dark:new boolean[]{false,true})try(var bindings=KitTestSupport.bindings(dark);var palette=palette(bindings.context(ThemeRoute.CHROME),true,new ArrayList<>())) {
                palette.setEntries(List.of(new PaletteEntry.Item(HISTORY,new PaletteRow("one","Selected title","selected detail",null,null,true,null))),null,null);
                palette.setSize(palette.getPreferredSize());layoutTree(palette);
                var detail=label(row(palette,0),"selected detail");
                assertThat(detail.getForeground()).isEqualTo(dev.jasper.ui.theme.SurfaceContrast.on(palette.kit().theme().semantic(),SemanticColor.TEXT_ONACCENT_SECONDARY,SemanticColor.SELECTION_BG,dev.jasper.ui.theme.SurfaceContrast.TEXT));
            }
        });
    }

    @Test void narrowStepRetainsAUsableFieldBelowItsLiteralLabel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var palette = palette(new ArrayList<>())) {
                palette.showStep("Destination", List.of(new PaletteStep.Field("host", "A long literal destination label", "example.test")));
                palette.setSize(230, 240); layoutTree(palette);
                var input = palette.stepInput(0);
                var label = palette.stepLabelView(0);
                assertThat(input.getWidth()).as("narrow field keeps at least its entered text width")
                    .isGreaterThanOrEqualTo(input.getFontMetrics(input.getFont()).stringWidth(input.getText()));
                assertThat(SwingUtilities.convertPoint(input, 0, 0, palette).y)
                    .isGreaterThanOrEqualTo(SwingUtilities.convertPoint(label, 0, label.getHeight(), palette).y);
                assertThat(input.getAccessibleContext().getAccessibleName()).isEqualTo("A long literal destination label");
            }
        });
    }

    @Test void enlargedQueryAndResultInkFitsItsActualMountedAllocation() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var themes = new ThemeController();
            var palette = palette(themes.appearance().context(ThemeRoute.CHROME), true, new ArrayList<>());
            try {
                palette.setEntries(List.of(new PaletteEntry.Header("Recently used"), item(COMMANDS, "AgjyÉ")), null, null);
                var query = PaletteTestSupport.query(palette);
                query.setText("AgjyÉ"); query.select(1, 3);
                var document = query.getDocument();
                themes.configure(Theme.DARK, new UiFontConfig("system", 32f));
                palette.refreshTheme();
                palette.setSize(palette.getPreferredSize()); layoutTree(palette);
                var frc = new java.awt.font.FontRenderContext(null, true, true);
                for (var target : List.of(query, label(row(palette, 0), "Recently used"), label(row(palette, 1), "Row AgjyÉ"))) {
                    var ink = new java.awt.font.TextLayout("AgjyÉ", target.getFont(), frc).getBounds();
                    var insets = target.getInsets();
                    assertThat(target.getHeight() - insets.top - insets.bottom)
                        .as("actual ink fits %s at %s", target.getClass().getSimpleName(), target.getFont())
                        .isGreaterThanOrEqualTo((int) Math.ceil(ink.getHeight()));
                }
                assertThat(query.getDocument()).isSameAs(document);
                assertThat(query.getSelectionStart()).isEqualTo(1);
                assertThat(query.getSelectionEnd()).isEqualTo(3);
                assertThat(palette.selectedKey()).isEqualTo(COMMANDS.id() + "/AgjyÉ");
            } finally { themes.configure(Theme.DARK, UiFontConfig.defaults()); palette.close(); themes.close(); }
        });
    }

    @Test void liveTypographyKeepsNominalMinimumsAndGrowsForRealContent() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var themes = new ThemeController();
            var palette = palette(themes.appearance().context(ThemeRoute.CHROME), true, new ArrayList<>());
            palette.setEntries(List.of(new PaletteEntry.Header("Commands"), item(COMMANDS, "a")), null, null);
            try {
                for (float size : new float[]{18, 32, 12}) {
                    themes.configure(Theme.DARK, new UiFontConfig("system", size));
                    palette.refreshTheme();
                    palette.setSize(palette.getPreferredSize());
                    layoutTree(palette);
                    assertThat(row(palette, 0).getHeight()).as("heading at %s pt", size).isEqualTo(dev.jasper.ui.swing.ResultMetrics.height(palette.kit(), true));
                    assertThat(row(palette, 1).getHeight()).as("row at %s pt", size).isEqualTo(dev.jasper.ui.swing.ResultMetrics.height(palette.kit(), false));
                    assertThat(palette.hintBar().getHeight()).isGreaterThanOrEqualTo(UIScale.scale(26));
                    assertThat(PaletteTestSupport.fieldRow(palette).getHeight()).isGreaterThanOrEqualTo(UIScale.scale(44));
                }
            } finally { themes.configure(Theme.DARK, UiFontConfig.defaults()); palette.close(); }
        });
    }

    /** Spec section 6: a theme change keeps the query, its caret, the selected row and where the list was scrolled. */
    @Test void aThemeSwitchKeepsTheQueryCaretSelectionAndScroll() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var bindings = KitTestSupport.bindings(true);
            try {
                var kit = bindings.context(ThemeRoute.CHROME);
                var palette = palette(kit, true, new ArrayList<>());
                var rows = new ArrayList<PaletteEntry>();
                rows.add(new PaletteEntry.Header("History"));
                for (int i = 0; i < 30; i++) rows.add(item(HISTORY, "r" + i));
                palette.setEntries(rows, null, null);
                palette.setSize(palette.getPreferredSize());
                layoutTree(palette);
                var query = PaletteTestSupport.query(palette);
                query.setText("first row");
                query.setCaretPosition(3);
                palette.selectRow("r20");
                layoutTree(palette);
                var viewport = PaletteTestSupport.resultsScroll(palette).getViewport();
                int scrolled = viewport.getViewPosition().y;
                assertThat(scrolled).as("the selection scrolled the list").isPositive();
                var dark = paint(palette);
                var light = ThemeTestSupport.pair(false);
                bindings.binding().publish(light);
                layoutTree(palette);
                assertThat(query.getText()).isEqualTo("first row");
                assertThat(palette.queryText()).isEqualTo("first row");
                assertThat(query.getCaretPosition()).isEqualTo(3);
                assertThat(PaletteTestSupport.query(palette)).as("the same input").isSameAs(query);
                assertThat(PaletteTestSupport.selectedRow(palette).id()).isEqualTo("r20");
                assertThat(PaletteTestSupport.resultsSpec(palette).selectedIds()).containsExactly("entry.21");
                assertThat(viewport.getViewPosition().y).as("the scroll position").isEqualTo(scrolled);
                var semantic = kit.theme().semantic();
                assertThat(semantic.color(SemanticColor.SURFACE_CARD)).isEqualTo(light.chrome().semantic().color(SemanticColor.SURFACE_CARD));
                var image = paint(palette);
                var hint = palette.hintBar();
                assertPixel(image, at(hint, hint.getWidth() - 3, hint.getHeight() / 2, palette), semantic.color(SemanticColor.SURFACE_CARD), "light card");
                assertThat(image.getRGB(palette.getWidth() / 2, palette.getHeight() / 2)).as("the card repainted")
                    .isNotEqualTo(dark.getRGB(palette.getWidth() / 2, palette.getHeight() / 2));
                assertPixel(image, new Point(palette.getWidth() / 2, 0), semantic.color(SemanticColor.BORDER_MENU), "light outline");
                palette.close();
            } finally { bindings.close(); }
        });
    }

    @Test void renderPaletteResultsAndFormsAtBothScales() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (var choice : List.of(Theme.DARK, dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT)) {
                var themes = new ThemeController(choice);
                var card = palette(themes.appearance().context(ThemeRoute.CHROME), true, new ArrayList<>());
                try {
                    card.setEntries(List.of(new PaletteEntry.Header("Commands"), item(COMMANDS, "new_tab"), item(COMMANDS, "settings")), null, null);
                    var host = new javax.swing.JPanel(new java.awt.GridBagLayout()); host.add(card);
                    for (int scale : new int[]{1, 2}) saveRender(host, choice.name() + "-palette-" + scale, scale);
                    card.showStep("Connect", List.of(new PaletteStep.Field("host", "Host", "example.org"), new PaletteStep.Field("user", "Username", "dustin")));
                    for (int scale : new int[]{1, 2}) saveRender(host, choice.name() + "-palette-form-" + scale, scale);
                } finally { card.close(); }
            }
            new ThemeController();
        });
    }

    private static Color over(Color base, Color overlay) {
        var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { g.setColor(base); g.fillRect(0, 0, 1, 1); g.setColor(overlay); g.fillRect(0, 0, 1, 1); } finally { g.dispose(); }
        return new Color(image.getRGB(0, 0), true);
    }

    private static Point at(Component component, int x, int y, Component root) {
        return SwingUtilities.convertPoint(component, x, y, root);
    }

    private static void assertPixel(BufferedImage image, Point point, Color expected, String context) {
        assertThat(new Color(image.getRGB(point.x, point.y), true)).as("%s at %s", context, point).isEqualTo(expected);
    }

    private static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        component.paint(graphics);
        graphics.dispose();
        return image;
    }

    private static JLabel label(Component container, String text) {
        return labels(container).stream().filter(label -> text.equals(label.getText())).findFirst().orElseThrow();
    }

    private static MouseEvent mousePress(Component source, int x, int y) {
        return new MouseEvent(source, MouseEvent.MOUSE_PRESSED, 1L, InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1);
    }

    private static void layoutTree(Component component) {
        if (!(component instanceof Container container)) return;
        container.doLayout();
        for (Component child : container.getComponents()) layoutTree(child);
    }

    private static List<JLabel> labels(Component component) {
        var found = new ArrayList<JLabel>();
        if (component instanceof JLabel label) found.add(label);
        if (component instanceof Container container) Arrays.stream(container.getComponents()).forEach(child -> found.addAll(labels(child)));
        return found;
    }

    private static boolean hasInputBinding(javax.swing.JTextField field, String action) {
        KeyStroke[] keys = field.getInputMap().allKeys();
        return keys != null && Arrays.stream(keys).anyMatch(key -> action.equals(field.getInputMap().get(key)));
    }

    private static void saveRender(JComponent component, String name, int scale) {
        component.setSize(960, 640);
        layoutTree(component);
        var image = new BufferedImage(960 * scale, 640 * scale, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { graphics.scale(scale, scale); component.printAll(graphics); }
        finally { graphics.dispose(); }
        try {
            var folder = java.nio.file.Path.of("build/render-preview");
            java.nio.file.Files.createDirectories(folder);
            javax.imageio.ImageIO.write(image, "png", folder.resolve(name + ".png").toFile());
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
