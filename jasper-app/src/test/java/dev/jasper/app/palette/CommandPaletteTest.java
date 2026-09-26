package dev.jasper.app.palette;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.UiFontConfig;
import dev.jasper.app.lifecycle.Subscription;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputMethodEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.text.DefaultEditorKit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CommandPaletteTest {
    private static final PaletteScope COMMANDS = scope("jasper.commands", "Commands", false, List.of(new PaletteVerb("run", "Run")));
    private static final PaletteScope HISTORY = scope("test.history", "History", true, List.of(new PaletteVerb("paste", "Paste"),
        new PaletteVerb("paste_run", "Paste and run"), new PaletteVerb("save", "Save as snippet…")));

    // A theme's SearchEverywhere.*/List.*/Component.borderColor keys back every colour this component
    // paints. The default cross-platform look and feel (active until something installs a FlatLaf theme)
    // defines plain Swing keys such as List.foreground, but not FlatLaf-only ones such as
    // Component.borderColor, so color()'s two-key fallback can still return null. Production always
    // installs a theme before any window exists; here, install one once so tests that build a bare
    // CommandPalette do not depend on another test class running first.
    @BeforeAll static void installDefaultTheme() throws Exception {
        SwingUtilities.invokeAndWait(() -> ThemeTestSupport.install(Theme.DARK));
    }

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

    private static CommandPalette palette(List<String> events) {
        return new CommandPalette(true, query -> { },
            (entry, verb) -> events.add(PaletteTestSupport.describe(entry) + ":" + verb), id -> events.add("tab:" + id));
    }

    private static PaletteEntry item(PaletteScope scope, String id) { return new PaletteEntry.Item(scope, PaletteRow.of(id, "Row " + id)); }

    @Test void queryNotifiesWhenClearedAndKeepsNativeEditingActions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var changes = new ArrayList<String>();
            var palette = new CommandPalette(false, changes::add, (entry, verb) -> { }, id -> { });
            palette.queryField().setText("split 2");
            palette.queryField().setText("");
            assertThat(changes).containsExactly("split 2", "");
            assertThat(palette.queryField().getActionMap().get(DefaultEditorKit.copyAction)).isNotNull();
            assertThat(palette.queryField().getActionMap().get(DefaultEditorKit.pasteAction)).isNotNull();
            assertThat(hasInputBinding(palette.queryField(), DefaultEditorKit.copyAction)).isTrue();
            assertThat(hasInputBinding(palette.queryField(), DefaultEditorKit.pasteAction)).isTrue();
            assertThat(palette.tabIds()).containsExactly(PaletteScope.ALL_ID);
            assertThat(palette.queryField().getAccessibleContext().getAccessibleName()).isEqualTo("Search all");
            assertThat(palette.entryList().getAccessibleContext().getAccessibleName()).isEqualTo("All");
            assertThat(palette.queryField().getClientProperty("JTextField.placeholderText")).isEqualTo("Search everywhere");
        });
    }

    @Test void tabsShowInOrderMarkTheSelectedOneAndReportClicks() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var events = new ArrayList<String>();
            var palette = palette(events);
            palette.setTabs(List.of(new CommandPalette.Tab(PaletteScope.ALL_ID, "All", null, "Search everywhere (⌘K)"),
                new CommandPalette.Tab("jasper.commands", "Commands", null, null),
                new CommandPalette.Tab("test.history", "History", null, "Search shell history")), "test.history");
            assertThat(palette.tabIds()).containsExactly(PaletteScope.ALL_ID, "jasper.commands", "test.history");
            assertThat(palette.selectedTabId()).isEqualTo("test.history");
            assertThat(palette.queryField().getAccessibleContext().getAccessibleName()).isEqualTo("Search history");
            assertThat(palette.entryList().getAccessibleContext().getAccessibleName()).isEqualTo("History");
            var labels = labels(palette.tabStrip());
            assertThat(labels).extracting(JLabel::getText).containsExactly("All", "Commands", "History");
            assertThat(labels.getFirst().getToolTipText()).isEqualTo("Search everywhere (⌘K)");
            labels.get(1).dispatchEvent(mousePress(labels.get(1), 3, 3));
            assertThat(events).containsExactly("tab:jasper.commands");
            palette.clickTab("test.history");
            palette.clickTab("missing");
            assertThat(events).containsExactly("tab:jasper.commands", "tab:test.history");
            palette.setPlaceholder("Search shell history");
            assertThat(palette.queryField().getClientProperty("JTextField.placeholderText")).isEqualTo("Search shell history");
        });
    }

    @Test void allGroupsRowsUnderHeadersAndSelectionSkipsThem() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = palette(new ArrayList<>());
            var entries = List.<PaletteEntry>of(new PaletteEntry.Header("Commands"), item(COMMANDS, "new_tab"),
                new PaletteEntry.Header("History"), item(HISTORY, "ls"), new PaletteEntry.More(HISTORY));
            palette.setEntries(entries, null, "Nothing found");
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).as("the first row, never a header").isEqualTo("jasper.commands/new_tab");
            palette.selectRelative(1);
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).isEqualTo("test.history/ls");
            palette.selectRelative(1);
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).isEqualTo("more:test.history");
            palette.selectRelative(50);
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).isEqualTo("more:test.history");
            palette.selectRelative(-50);
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).isEqualTo("jasper.commands/new_tab");
            assertThat(palette.entryList().getAccessibleContext().getAccessibleDescription()).isEqualTo("2 results");
            palette.setEntries(entries, PaletteEntry.key(HISTORY, "ls"), "Nothing found");
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).as("kept by scope and row").isEqualTo("test.history/ls");
            palette.setEntries(entries.subList(0, 2), PaletteEntry.key(HISTORY, "ls"), "Nothing found");
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).as("falls back to the first row").isEqualTo("jasper.commands/new_tab");
            palette.setEntries(List.of(), null, "Nothing found");
            palette.selectRelative(1);
            assertThat(palette.selectedEntry()).isNull();
            var tooMany = new ArrayList<PaletteEntry>();
            for (int i = 0; i < PaletteResults.MAX_ROWS + 1; i++) tooMany.add(item(COMMANDS, "many." + i));
            assertThatIllegalArgumentException().isThrownBy(() -> palette.setEntries(tooMany, null, null));
        });
    }

    @Test void selectRowFindsARowByIdAndTheQueryAcceptsDigits() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = palette(new ArrayList<>());
            palette.setEntries(List.of(item(COMMANDS, "select_tab_1"), item(COMMANDS, "select_tab_2")), null, null);
            palette.queryField().setText("tab 2");
            palette.selectRow("select_tab_2");
            assertThat(palette.queryField().getText()).isEqualTo("tab 2");
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).isEqualTo("jasper.commands/select_tab_2");
            palette.selectRow("missing");
            assertThat(PaletteTestSupport.describe(palette.selectedEntry())).isEqualTo("jasper.commands/select_tab_2");
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
            var list = palette.entryList();
            assertThat(list.getVisibleRect().contains(list.getCellBounds(4, 4))).isTrue();
            var reordered = new ArrayList<>(rows); reordered.addFirst(reordered.removeLast());
            palette.setEntries(reordered, PaletteEntry.key(COMMANDS, "selected"), null);
            layoutTree(palette);
            assertThat(list.getSelectedIndex()).isZero();
            assertThat(list.getVisibleRect().contains(list.getCellBounds(0, 0))).isTrue();
            palette.setEntries(rows, PaletteEntry.key(COMMANDS, "selected"), null);
            layoutTree(palette);
            assertThat(list.getSelectedIndex()).isEqualTo(4);
            assertThat(list.getVisibleRect().contains(list.getCellBounds(4, 4))).isTrue();
        });
    }

    @Test void rowsExecuteOnClickWhileHeadersAndBlankSpaceDoNothing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var events = new ArrayList<String>();
            var palette = palette(events);
            palette.setEntries(List.of(new PaletteEntry.Header("Commands"), item(COMMANDS, "first"), item(COMMANDS, "second")), null, null);
            palette.setSize(palette.getPreferredSize());
            layoutTree(palette);
            var list = palette.entryList();
            Rectangle header = list.getCellBounds(0, 0), second = list.getCellBounds(2, 2);
            assertThat(header.height).isEqualTo(UIScale.scale(22));
            assertThat(second.height).isEqualTo(UIScale.scale(24));
            // BasicListUI asks the native toolkit for the platform menu mask on mouse press.
            // Remove only that delegate listener so this component-level hit test stays headless.
            Arrays.stream(list.getMouseListeners())
                .filter(listener -> listener.getClass().getName().startsWith("javax.swing.plaf."))
                .forEach(list::removeMouseListener);
            list.dispatchEvent(mousePress(list, header.x + 4, header.y + 4));
            list.dispatchEvent(mousePress(list, second.x + 4, second.y + 4));
            list.setSize(list.getWidth(), UIScale.scale(200));
            list.dispatchEvent(mousePress(list, 4, UIScale.scale(190)));
            assertThat(events).containsExactly("jasper.commands/second:0");
        });
    }

    @Test void inputMethodEventsExposeOnlyActiveComposition() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = palette(new ArrayList<>());
            palette.queryField().dispatchEvent(new InputMethodEvent(palette.queryField(),
                InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, new AttributedString("ab").getIterator(), 1, null, null));
            assertThat(palette.composing()).isTrue();
            palette.queryField().dispatchEvent(new InputMethodEvent(palette.queryField(),
                InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, new AttributedString("ab").getIterator(), 2, null, null));
            assertThat(palette.composing()).isFalse();
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
            assertThat(palette.getPreferredSize().height).isEqualTo(UIScale.scale(30 + 40 + 22 + 2 * 24 + 26) + insets.top + insets.bottom);
            assertThat(palette.tabStrip().getHeight()).isEqualTo(UIScale.scale(30));
            assertThat(palette.queryField().getParent().getHeight()).isEqualTo(UIScale.scale(40));
            assertThat(palette.hintBar().getHeight()).isEqualTo(UIScale.scale(26));
            assertThat(palette.itemHeight()).isEqualTo(UIScale.scale(24));
            assertThat(palette.isOpaque()).isTrue();
            palette.setEntries(List.of(), null, "Nothing found");
            assertThat(palette.getPreferredSize().height).as("the empty line").isEqualTo(UIScale.scale(30 + 40 + 24 + 26) + insets.top + insets.bottom);
        });
    }

    @Test void theListScrollsPastFifteenLines() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = palette(new ArrayList<>());
            var rows = new ArrayList<PaletteEntry>();
            for (int i = 0; i < 30; i++) rows.add(item(HISTORY, "r" + i));
            palette.setEntries(rows, null, null);
            Insets insets = palette.getInsets();
            assertThat(palette.getPreferredSize().height).isEqualTo(UIScale.scale(30 + 40 + 15 * 24 + 26) + insets.top + insets.bottom);
            palette.setSize(palette.getPreferredSize());
            layoutTree(palette);
            palette.selectRelative(29);
            var list = palette.entryList();
            assertThat(list.getSelectedIndex()).isEqualTo(29);
            assertThat(list.getVisibleRect().intersects(list.getCellBounds(29, 29))).isTrue();
        });
    }

    @Test void everySurfaceTakesItsColourFromTheTheme() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var original = UIManager.getLookAndFeel();
            try {
                for (Theme theme : List.of(Theme.LIGHT, Theme.DARK)) {
                    ThemeTestSupport.install(theme);
                    var palette = palette(new ArrayList<>());
                    palette.setTabs(List.of(new CommandPalette.Tab(PaletteScope.ALL_ID, "All", null, null),
                        new CommandPalette.Tab("jasper.commands", "Commands", null, null)), PaletteScope.ALL_ID);
                    var historyRow = new PaletteRow("c", "Row c", "a detail", null, null, true, null);
                    palette.setEntries(List.of(new PaletteEntry.Header("Commands"), item(COMMANDS, "a"), item(COMMANDS, "b"),
                        new PaletteEntry.Header("History"), new PaletteEntry.Item(HISTORY, historyRow)), null, null);
                    palette.setSize(palette.getPreferredSize());
                    layoutTree(palette);
                    var image = paint(palette);
                    var list = palette.entryList();
                    var allTab = labels(palette.tabStrip()).getFirst();
                    String name = theme.name();
                    assertPixel(image, new Point(0, 0), "Popup.borderColor", name);
                    assertPixel(image, at(allTab, 3, 3, palette), "SearchEverywhere.Tab.selectedBackground", name);
                    assertPixel(image, at(palette.tabStrip(), palette.tabStrip().getWidth() - 3, 3, palette), "SearchEverywhere.Header.background", name);
                    assertPixel(image, at(palette.queryField().getParent(), palette.queryField().getParent().getWidth() - 3, 2, palette),
                        "SearchEverywhere.SearchField.background", name);
                    Rectangle selected = list.getCellBounds(1, 1), plain = list.getCellBounds(2, 2), rule = list.getCellBounds(3, 3);
                    assertPixel(image, at(list, selected.width - 3, selected.y + 3, palette), "List.selectionBackground", name);
                    assertPixel(image, at(list, plain.width - 3, plain.y + 3, palette), "List.background", name);
                    assertPixel(image, at(list, rule.width - 3, rule.y, palette), "SearchEverywhere.List.separatorColor", name);
                    assertPixel(image, at(palette.hintBar(), palette.hintBar().getWidth() - 3, palette.hintBar().getHeight() / 2, palette),
                        "SearchEverywhere.Advertiser.background", name);
                    assertThat(labels(palette.tabStrip()).get(1).getForeground()).as(name).isEqualTo(UIManager.getColor("Label.foreground"));
                    assertThat(allTab.getForeground()).as(name).isEqualTo(UIManager.getColor("SearchEverywhere.Tab.selectedForeground"));

                    // List.foreground and Label.foreground happen to coincide in both bundled themes, so
                    // the assertion above alone can't tell which key the unselected tab actually reads.
                    // Give Label.foreground a distinctive value List.foreground doesn't share, and prove
                    // the tab follows it.
                    Object originalLabelForeground = UIManager.get("Label.foreground");
                    try {
                        var distinctiveForeground = new ColorUIResource(0x123456);
                        UIManager.put("Label.foreground", distinctiveForeground);
                        palette.refreshTheme();
                        assertThat(labels(palette.tabStrip()).get(1).getForeground()).as(name).isEqualTo(distinctiveForeground);
                    } finally {
                        UIManager.put("Label.foreground", originalLabelForeground);
                        palette.refreshTheme();
                    }

                    // Select History's row (three verbs, a detail) to populate the hint bar with link-coloured
                    // secondary verbs and its detail text, without disturbing the pixel assertions taken above.
                    list.setSelectedIndex(4);
                    assertThat(palette.hintText()).as(name).isEqualTo("a detail");
                    assertThat(palette.hintActionTexts()).as(name).isNotEmpty();
                    assertThat(label(palette.hintBar(), "a detail").getForeground()).as(name)
                        .isEqualTo(UIManager.getColor("SearchEverywhere.Advertiser.foreground"));
                    for (String action : palette.hintActionTexts())
                        assertThat(label(palette.hintBar(), action).getForeground()).as(name + " " + action)
                            .isEqualTo(UIManager.getColor("Component.linkColor"));
                    // Re-render specific rows directly (the cell renderer is one shared instance whose
                    // children only reflect whichever row it painted last) to check colours the isSelected
                    // parameter, not the list's actual selection, controls.
                    var detailRendered = (Container) list.getCellRenderer()
                        .getListCellRendererComponent(list, list.getModel().getElementAt(4), 4, false, false);
                    assertThat(label(detailRendered, "a detail").getForeground()).as(name)
                        .isEqualTo(UIManager.getColor("SearchEverywhere.SearchField.infoForeground"));
                    var headerRendered = (Container) list.getCellRenderer()
                        .getListCellRendererComponent(list, list.getModel().getElementAt(3), 3, false, false);
                    assertThat(label(headerRendered, "History").getForeground()).as(name)
                        .isEqualTo(UIManager.getColor("SearchEverywhere.List.separatorForeground"));
                }
            } finally {
                try { UIManager.setLookAndFeel(original); } catch (Exception failure) { throw new IllegalStateException(failure); }
            }
        });
    }

    @Test void selectedAndHoveredRowsPaintTheListsColours() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var original = UIManager.getLookAndFeel();
            try {
                ThemeTestSupport.install(Theme.LIGHT);
                var palette = palette(new ArrayList<>());
                palette.setEntries(List.of(item(COMMANDS, "a"), item(COMMANDS, "b")), null, null);
                palette.setSize(palette.getPreferredSize());
                layoutTree(palette);
                var list = palette.entryList();
                Rectangle first = list.getCellBounds(0, 0), second = list.getCellBounds(1, 1);
                list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_MOVED, 1L, 0, 4, second.y + 4, 0, false));
                var image = paint(palette);
                assertPixel(image, at(list, first.width - 3, first.y + 3, palette), "List.selectionBackground", "selected");
                assertPixel(image, at(list, second.width - 3, second.y + 3, palette), "List.hoverBackground", "hovered");
                list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_EXITED, 1L, 0, 4, second.y + 4, 0, false));
                assertPixel(paint(palette), at(list, second.width - 3, second.y + 3, palette), "List.background", "after the mouse left");
            } finally {
                try { UIManager.setLookAndFeel(original); } catch (Exception failure) { throw new IllegalStateException(failure); }
            }
        });
    }

    @Test void rowsLayOutOnOneLineAndTheTagGoesFirstWhenSpaceRunsOut() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = palette(new ArrayList<>());
            var row = new PaletteRow("r", "Rendered command", "a detail", "⌘T", null, true, null);
            palette.setEntries(List.of(new PaletteEntry.Item(COMMANDS, row), new PaletteEntry.More(HISTORY), new PaletteEntry.Header("History")), null, null);
            var list = palette.entryList();
            var renderer = list.getCellRenderer();
            var line = (Container) renderer.getListCellRendererComponent(list, list.getModel().getElementAt(0), 0, false, false);
            line.setSize(UIScale.scale(680), UIScale.scale(24));
            line.doLayout();
            JLabel title = label(line, "Rendered command"), detail = label(line, "a detail"), tag = label(line, "⌘T");
            assertThat(detail.getX()).as("the detail follows the title").isGreaterThanOrEqualTo(title.getX() + title.getWidth());
            assertThat(detail.getY()).isEqualTo(title.getY());
            assertThat(tag.getX() + tag.getWidth()).as("the tag is right-aligned").isEqualTo(UIScale.scale(680) - UIScale.scale(8));
            line.setSize(title.getPreferredSize().width + UIScale.scale(40), UIScale.scale(24));
            line.doLayout();
            assertThat(tag.getWidth()).as("the tag goes first").isZero();
            assertThat(title.getWidth()).isPositive();
            // Wide enough for title + tag alone, but not for title + detail + tag: the tag must still be
            // dropped first, not just cut for the detail's sake, or the detail would never get its turn.
            int side = UIScale.scale(8), gap = UIScale.scale(8), iconSize = UIScale.scale(16);
            int start = side + iconSize + gap;
            int fitsTitleAndTagOnly = start + title.getPreferredSize().width + gap + tag.getPreferredSize().width;
            line.setSize(fitsTitleAndTagOnly + side, UIScale.scale(24));
            line.doLayout();
            assertThat(tag.getWidth()).as("the tag is dropped when the detail would not otherwise fit too").isZero();
            assertThat(detail.getWidth()).as("the detail takes its place, cut if needed").isPositive();
            var more = (Container) renderer.getListCellRendererComponent(list, list.getModel().getElementAt(1), 1, false, false);
            assertThat(labels(more)).extracting(JLabel::getText).contains("More in History…");
            var header = renderer.getListCellRendererComponent(list, list.getModel().getElementAt(2), 2, false, false);
            assertThat(header.getPreferredSize().height).isEqualTo(UIScale.scale(22));
        });
    }

    @Test void renderedRowsCarryNoNumberBadge() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = palette(new ArrayList<>());
            var entries = new ArrayList<PaletteEntry>();
            for (int i = 0; i < 6; i++) entries.add(item(COMMANDS, "row" + i));
            palette.setEntries(entries, null, null);
            var list = palette.entryList();
            var renderer = list.getCellRenderer();
            var badges = List.of("⌘1", "⌘2", "⌘3", "⌘4", "⌘5", "Ctrl+1", "Ctrl+2", "Ctrl+3", "Ctrl+4", "Ctrl+5");
            for (int i = 0; i < entries.size(); i++) {
                var rendered = (Container) renderer.getListCellRendererComponent(list, list.getModel().getElementAt(i), i, false, false);
                for (JLabel label : labels(rendered))
                    for (String badge : badges) assertThat(label.getText()).as("row " + i).doesNotContain(badge);
            }
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
            palette.clickHintAction(1);
            assertThat(events).containsExactly("test.history/ls:2");
            palette.selectRelative(1);
            assertThat(palette.hintText()).as("a command shows its shortcut").isEqualTo("⌘T");
            assertThat(palette.hintActionTexts()).isEmpty();
            palette.selectRelative(1);
            assertThat(palette.hintText()).isEqualTo("Show every match in History");
            var elsewhere = new CommandPalette(false, query -> { }, (entry, verb) -> { }, id -> { });
            elsewhere.setEntries(List.of(item(HISTORY, "ls")), null, null);
            assertThat(elsewhere.hintActionTexts()).containsExactly("Paste and run Ctrl+Enter", "Save as snippet… Shift+Enter");
        });
    }

    @Test void theAccessibleDescriptionCarriesTheCountAndTheSelectedScopesVerbsWithTheirKeys() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = palette(new ArrayList<>());
            palette.setEntries(List.of(new PaletteEntry.Item(HISTORY, new PaletteRow("ls", "ls -la", "in ~/src", null, null, true, null)),
                new PaletteEntry.Item(COMMANDS, new PaletteRow("new_tab", "New Tab", null, "⌘T", null, true, null)),
                new PaletteEntry.More(HISTORY)), null, null);
            assertThat(palette.entryList().getAccessibleContext().getAccessibleDescription())
                .as("a three-verb scope's row carries every verb with its key")
                .isEqualTo("2 results; ⏎ Paste  ⌘⏎ Paste and run  ⇧⏎ Save as snippet…");
            palette.selectRelative(1);
            assertThat(palette.entryList().getAccessibleContext().getAccessibleDescription())
                .as("a single-verb scope adds nothing beyond the count").isEqualTo("2 results");
            palette.selectRelative(1);
            assertThat(palette.entryList().getAccessibleContext().getAccessibleDescription())
                .as("a selected \"More in\" row describes what Enter does there")
                .isEqualTo("2 results; ⏎ Show every match in History");
            palette.selectRelative(-2);
            assertThat(palette.entryList().getAccessibleContext().getAccessibleDescription())
                .as("switching back to a three-verb scope restores its verbs")
                .isEqualTo("2 results; ⏎ Paste  ⌘⏎ Paste and run  ⇧⏎ Save as snippet…");
            var elsewhere = new CommandPalette(false, query -> { }, (entry, verb) -> { }, id -> { });
            elsewhere.setEntries(List.of(item(HISTORY, "ls")), null, null);
            assertThat(elsewhere.entryList().getAccessibleContext().getAccessibleDescription())
                .as("the other platform's key symbols")
                .isEqualTo("1 results; Enter Paste  Ctrl+Enter Paste and run  Shift+Enter Save as snippet…");
            elsewhere.setEntries(List.of(), null, "Nothing found");
            assertThat(elsewhere.entryList().getAccessibleContext().getAccessibleDescription())
                .as("nothing selected keeps only the count").isEqualTo("0 results");
        });
    }

    @Test void aStepReplacesTheListAndHidesTheHintBar() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            new ThemeController(Appearance.LIGHT);
            try {
                var palette = palette(new ArrayList<>());
                palette.setEntries(List.of(item(HISTORY, "a"), item(HISTORY, "b")), null, null);
                palette.showStep("Rebase", List.of(new PaletteStep.Field("branch", "branch", "main"),
                    new PaletteStep.Field("remote", "remote", "")));
                assertThat(palette.stepShowing()).isTrue();
                assertThat(palette.stepFields()).hasSize(2);
                assertThat(palette.stepFields().getFirst().getText()).isEqualTo("main");
                assertThat(palette.stepFields().getFirst().getAccessibleContext().getAccessibleName()).isEqualTo("branch");
                assertThat(palette.stepFocusIndex()).isZero();
                assertThat(palette.stepTitle().getText()).isEqualTo("Rebase");
                assertThat(palette.hintBar().isVisible()).isFalse();
                Insets insets = palette.getInsets();
                assertThat(palette.getPreferredSize().height).isEqualTo(UIScale.scale(30 + 40 + 22 + 2 * 36) + insets.top + insets.bottom);
                palette.focusStepField(1);
                assertThat(palette.stepFocusIndex()).isEqualTo(1);
                palette.focusStepField(1);
                assertThat(palette.stepFocusIndex()).isZero();
                palette.focusStepField(-1);
                assertThat(palette.stepFocusIndex()).isEqualTo(1);
                palette.stepFields().get(1).setText("origin");
                assertThat(palette.stepValues()).hasSize(2).containsEntry("branch", "main").containsEntry("remote", "origin");
                palette.setStepError("Nope");
                assertThat(palette.stepError().isVisible()).isTrue();
                assertThat(palette.getPreferredSize().height).isEqualTo(UIScale.scale(30 + 40 + 22 + 2 * 36 + 22) + insets.top + insets.bottom);
                palette.setStepError(null);
                assertThat(palette.stepError().isVisible()).isFalse();
                palette.hideStep();
                assertThat(palette.stepShowing()).isFalse();
                assertThat(palette.stepFields()).isEmpty();
                assertThat(palette.hintBar().isVisible()).isTrue();
            } finally { new ThemeController(); }
        });
    }

    @Test void liveTypographyKeepsRowAndHeaderHeights() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var themes = new ThemeController();
            var palette = palette(new ArrayList<>());
            palette.setEntries(List.of(new PaletteEntry.Header("Commands"), item(COMMANDS, "a")), null, null);
            try {
                for (float size : new float[]{18, 32, 12}) {
                    themes.configure(Appearance.DARK, new UiFontConfig("system", size));
                    SwingUtilities.updateComponentTreeUI(palette);
                    palette.refreshTheme();
                    palette.setSize(palette.getPreferredSize());
                    layoutTree(palette);
                    assertThat(palette.entryList().getCellBounds(0, 0).height).isEqualTo(UIScale.scale(22));
                    assertThat(palette.entryList().getCellBounds(1, 1).height).isEqualTo(UIScale.scale(24));
                    assertThat(palette.hintBar().getHeight()).isEqualTo(UIScale.scale(26));
                }
            } finally { themes.configure(Appearance.DARK, UiFontConfig.defaults()); }
        });
    }

    @Test void refreshThemeKeepsTheQueryAndTheSelectedRow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var original = UIManager.getLookAndFeel();
            try {
                var palette = palette(new ArrayList<>());
                palette.setEntries(List.of(item(COMMANDS, "a"), item(COMMANDS, "b"), item(COMMANDS, "c")), null, null);
                palette.queryField().setText("fir");
                palette.selectRow("b");
                assertThat(PaletteTestSupport.describe(palette.selectedEntry())).isEqualTo("jasper.commands/b");
                for (Theme theme : List.of(Theme.LIGHT, Theme.DARK)) {
                    ThemeTestSupport.install(theme);
                    palette.refreshTheme();
                    assertThat(palette.queryField().getText()).as(theme.name()).isEqualTo("fir");
                    assertThat(PaletteTestSupport.describe(palette.selectedEntry())).as(theme.name()).isEqualTo("jasper.commands/b");
                }
            } finally {
                try { UIManager.setLookAndFeel(original); } catch (Exception failure) { throw new IllegalStateException(failure); }
            }
        });
    }

    @Test void renderPaletteResultsAndFormsAtBothScales() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                for (var choice : List.of(Appearance.DARK, Appearance.LIGHT)) {
                    new ThemeController(choice);
                    var card = palette(new ArrayList<>());
                    card.setTabs(List.of(new CommandPalette.Tab(PaletteScope.ALL_ID, "All", null, null),
                        new CommandPalette.Tab("jasper.commands", "Commands", null, null)), PaletteScope.ALL_ID);
                    card.setEntries(List.of(new PaletteEntry.Header("Commands"), item(COMMANDS, "new_tab"), item(COMMANDS, "settings")), null, null);
                    var host = new javax.swing.JPanel(new java.awt.GridBagLayout()); host.add(card);
                    for (int scale : new int[]{1, 2}) saveRender(host, choice + "-palette-" + scale, scale);
                    card.showStep("Connect", List.of(new PaletteStep.Field("host", "Host", "example.org"), new PaletteStep.Field("user", "Username", "dustin")));
                    for (int scale : new int[]{1, 2}) saveRender(host, choice + "-palette-form-" + scale, scale);
                }
            } finally { new ThemeController(); }
        });
    }

    private static Point at(Component component, int x, int y, Component root) {
        return SwingUtilities.convertPoint(component, x, y, root);
    }

    private static void assertPixel(BufferedImage image, Point point, String key, String context) {
        assertThat(new Color(image.getRGB(point.x, point.y), true)).as("%s %s at %s", context, key, point).isEqualTo(UIManager.getColor(key));
    }

    private static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        component.paint(graphics);
        graphics.dispose();
        return image;
    }

    private static JLabel label(Container container, String text) {
        return labels(container).stream().filter(label -> text.equals(label.getText())).findFirst().orElseThrow();
    }

    private static MouseEvent mousePress(Component source, int x, int y) {
        return new MouseEvent(source, MouseEvent.MOUSE_PRESSED, 1L, 0, x, y, 1, false, MouseEvent.BUTTON1);
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
