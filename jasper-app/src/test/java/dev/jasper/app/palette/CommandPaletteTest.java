package dev.jasper.app.palette;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.Command;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.event.InputMethodEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.DefaultEditorKit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CommandPaletteTest {
    @Test void liveTypographyResizesExistingRowsAndSecondarySections() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            var themes = new dev.jasper.app.appearance.ThemeController();
            var palette = new CommandPalette(true, text -> {}, (row, verb) -> {}, () -> {}, () -> {});
            try {
                for (float size : new float[]{18, 32, 12}) {
                    themes.configure(dev.jasper.app.config.Appearance.DARK, new dev.jasper.app.config.UiFontConfig("system", size));
                    javax.swing.SwingUtilities.updateComponentTreeUI(palette); palette.refreshTheme();
                    assertThat(palette.resultList().getFixedCellHeight()).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(40));
                    assertThat(palette.footer().getPreferredSize().height).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(24));
                    assertThat(palette.sectionLabel().getPreferredSize().height).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(24));
                }
            } finally { themes.configure(dev.jasper.app.config.Appearance.DARK, dev.jasper.app.config.UiFontConfig.defaults()); }
        });
    }

    @Test void queryNotifiesWhenClearedAndKeepsNativeEditingActions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var changes = new ArrayList<String>();
            var dismissed = new AtomicInteger();
            var executed = new ArrayList<PaletteRow>();
            var palette = new CommandPalette(false, changes::add, (row, verb) -> executed.add(row),
                dismissed::incrementAndGet, () -> {});

            palette.queryField().setText("split 2");
            palette.queryField().setText("");
            buttons(palette).getFirst().doClick();

            assertThat(changes).containsExactly("split 2", "");
            assertThat(dismissed).hasValue(1);
            assertThat(executed).isEmpty();
            assertThat(palette.queryField().getActionMap().get(DefaultEditorKit.copyAction)).isNotNull();
            assertThat(palette.queryField().getActionMap().get(DefaultEditorKit.pasteAction)).isNotNull();
            assertThat(hasInputBinding(palette.queryField(), DefaultEditorKit.copyAction)).isTrue();
            assertThat(hasInputBinding(palette.queryField(), DefaultEditorKit.pasteAction)).isTrue();
            assertThat(palette.queryField().getAccessibleContext().getAccessibleName()).isEqualTo("Search commands");
            assertThat(palette.resultList().getAccessibleContext().getAccessibleName()).isEqualTo("Commands");
            assertThat(palette.queryField().getClientProperty("JTextField.placeholderText"))
                .isEqualTo("Type a command…");
        });
    }

    @Test void limitsAreVisibleAndMissingNumbersCannotExecute() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var executed = new ArrayList<String>();
            var palette = new CommandPalette(true, query -> {}, (row, verb) -> executed.add(row.id()), () -> {}, () -> {});
            var commands = new ArrayList<PaletteRow>();
            for (int i = 0; i < 5; i++) {
                commands.add(PaletteRow.of("test." + i, "Command " + i));
            }
            palette.setResults(commands, null, null);
            palette.executeNumber(5);
            palette.executeNumber(6);
            assertThat(executed).containsExactly("test.4");
            assertThat(palette.resultList().getModel().getSize()).isEqualTo(5);
            assertThat(palette.resultList().getAccessibleContext().getAccessibleDescription()).isEqualTo("5 results");
            var tooMany = new ArrayList<PaletteRow>();
            for (int i = 0; i < PaletteResults.MAX_ROWS + 1; i++) tooMany.add(PaletteRow.of("many." + i, "Many " + i));
            assertThatIllegalArgumentException().isThrownBy(() -> palette.setResults(tooMany, null, null));
            palette.setResults(List.of(), null, null);
            palette.executeSelected();
            assertThat(executed).containsExactly("test.4");
        });
    }

    @Test void selectionSurvivesRefreshByIdentityAndQueryAcceptsDigits() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = new CommandPalette(false, query -> {}, (row, verb) -> {}, () -> {}, () -> {});
            var row = PaletteRow.of("select_tab_2", "Select Tab 2");
            palette.setResults(List.of(row), null, row.id());
            palette.queryField().setText("tab 2");
            assertThat(palette.queryField().getText()).isEqualTo("tab 2");
            assertThat(palette.resultList().getSelectedValue()).isSameAs(row);
        });
    }

    @Test void navigationClampsAndRefreshFallsBackWhenIdentityDisappears() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var first = PaletteRow.of("test.first", "First");
            var second = PaletteRow.of("test.second", "Second");
            var third = PaletteRow.of("test.third", "Third");
            var palette = new CommandPalette(false, query -> {}, (row, verb) -> {}, () -> {}, () -> {});

            palette.setResults(List.of(first, second, third), null, null);
            assertThat(palette.resultList().getSelectedValue()).isSameAs(first);
            palette.selectRelative(50);
            assertThat(palette.resultList().getSelectedValue()).isSameAs(third);
            palette.selectRelative(-50);
            assertThat(palette.resultList().getSelectedValue()).isSameAs(first);
            palette.setResults(List.of(third, second, first), null, second.id());
            assertThat(palette.resultList().getSelectedValue()).isSameAs(second);
            palette.setResults(List.of(third, first), null, second.id());
            assertThat(palette.resultList().getSelectedValue()).isSameAs(third);
            palette.setResults(List.of(), null, null);
            palette.selectRelative(1);
            assertThat(palette.resultList().isSelectionEmpty()).isTrue();
        });
    }

    @Test void preservedSelectionStaysVisibleAcrossReorderAndResultGrowth() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var first = PaletteRow.of("test.first", "First");
            var second = PaletteRow.of("test.second", "Second");
            var third = PaletteRow.of("test.third", "Third");
            var fourth = PaletteRow.of("test.fourth", "Fourth");
            var selected = PaletteRow.of("test.selected", "Selected");
            var palette = new CommandPalette(false, query -> {}, (row, verb) -> {}, () -> {}, () -> {});
            palette.setResults(List.of(first, second, third, fourth, selected), null, null);
            palette.setSize(UIScale.scale(318), UIScale.scale(140));
            layoutTree(palette);

            palette.selectRelative(4);
            assertThat(palette.resultList().getVisibleRect().contains(
                palette.resultList().getCellBounds(4, 4))).isTrue();

            palette.setResults(List.of(selected, first, second, third, fourth), null, selected.id());
            layoutTree(palette);
            assertThat(palette.resultList().getSelectedIndex()).isZero();
            assertThat(palette.resultList().getVisibleRect().contains(
                palette.resultList().getCellBounds(0, 0))).isTrue();

            palette.setResults(List.of(first, selected), null, selected.id());
            layoutTree(palette);
            palette.setResults(List.of(first, second, third, fourth, selected), null, selected.id());
            layoutTree(palette);
            assertThat(palette.resultList().getSelectedIndex()).isEqualTo(4);
            assertThat(palette.resultList().getVisibleRect().contains(
                palette.resultList().getCellBounds(4, 4))).isTrue();
        });
    }

    @Test void realRowBoundsExecuteClicksButBlankListSpaceDoesNothing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var executed = new ArrayList<String>();
            var first = PaletteRow.of("test.first", "First");
            var second = PaletteRow.of("test.second", "Second");
            var palette = new CommandPalette(false, query -> {}, (row, verb) -> executed.add(row.id()), () -> {}, () -> {});
            palette.setResults(List.of(first, second), null, null);
            palette.setSize(palette.getPreferredSize());
            layoutTree(palette);

            Rectangle firstBounds = palette.resultList().getCellBounds(0, 0);
            Rectangle secondBounds = palette.resultList().getCellBounds(1, 1);
            assertThat(firstBounds.height).isEqualTo(UIScale.scale(40));
            assertThat(secondBounds).isEqualTo(new Rectangle(0, UIScale.scale(40),
                palette.resultList().getWidth(), UIScale.scale(40)));

            // BasicListUI asks the native toolkit for the platform menu mask on mouse press.
            // Remove only that delegate listener so this component-level hit test stays headless.
            Arrays.stream(palette.resultList().getMouseListeners())
                .filter(listener -> listener.getClass().getName().startsWith("javax.swing.plaf."))
                .forEach(palette.resultList()::removeMouseListener);
            palette.resultList().dispatchEvent(mousePress(palette.resultList(), secondBounds.x + 4, secondBounds.y + 4));
            palette.resultList().setSize(palette.resultList().getWidth(), UIScale.scale(120));
            palette.resultList().dispatchEvent(mousePress(palette.resultList(), 4, UIScale.scale(100)));
            assertThat(executed).containsExactly("test.second");
        });
    }

    @Test void inputMethodEventsExposeOnlyActiveComposition() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = new CommandPalette(false, query -> {}, (row, verb) -> {}, () -> {}, () -> {});
            var composing = new AttributedString("ab").getIterator();
            palette.queryField().dispatchEvent(new InputMethodEvent(palette.queryField(),
                InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, composing, 1, null, null));
            assertThat(palette.composing()).isTrue();

            var committed = new AttributedString("ab").getIterator();
            palette.queryField().dispatchEvent(new InputMethodEvent(palette.queryField(),
                InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, committed, 2, null, null));
            assertThat(palette.composing()).isFalse();
        });
    }

    @Test void geometryPaintingAndThemeRefreshKeepInputAndSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ThemeTestSupport.install(Theme.DARK);
            var first = PaletteRow.of("test.first", "First");
            var second = PaletteRow.of("test.second", "Second");
            var palette = new CommandPalette(true, query -> {}, (row, verb) -> {}, () -> {}, () -> {});
            palette.queryField().setText("fir");
            palette.setResults(List.of(first, second), null, second.id());
            palette.setSize(palette.getPreferredSize());
            layoutTree(palette);

            assertThat(palette.getPreferredSize().width).isEqualTo(UIScale.scale(560));
            assertThat(palette.queryField().getParent().getHeight()).isEqualTo(UIScale.scale(56));
            assertThat(palette.resultList().getFixedCellHeight()).isEqualTo(UIScale.scale(40));
            assertThat(palette.resultList().getFont().getFamily())
                .isEqualTo(UIManager.getFont("Label.font").getFamily());
            assertThat(palette.isOpaque()).isFalse();

            var image = new BufferedImage(palette.getWidth(), palette.getHeight(), BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            palette.paint(graphics);
            graphics.dispose();
            assertThat(new Color(image.getRGB(0, 0), true).getAlpha()).isZero();
            assertThat(new Color(image.getRGB(palette.getWidth() / 2, UIScale.scale(10)), true))
                .isEqualTo(UIManager.getColor("Jasper.paletteBackground"));

            for (Theme theme : java.util.List.of(Theme.DARK, Theme.LIGHT)) {
                ThemeTestSupport.install(theme);
                for (String key : List.of("Jasper.paletteBackground", "Jasper.paletteForeground",
                    "Jasper.paletteMutedForeground", "Jasper.paletteBorder", "Jasper.paletteAccent",
                    "Jasper.paletteSelectionBackground", "Jasper.paletteSelectionForeground")) {
                    assertThat(UIManager.getColor(key)).as(theme + " " + key).isNotNull();
                }
                palette.refreshTheme();
                assertThat(palette.getBackground()).isEqualTo(UIManager.getColor("Jasper.paletteBackground"));
                assertThat(palette.queryField().getText()).isEqualTo("fir");
                assertThat(palette.resultList().getSelectedValue()).isSameAs(second);
            }
            ThemeTestSupport.install(Theme.DARK);
        });
    }

    @Test void actualListPaintingLaysOutRendererTextAndBadge() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var row = PaletteRow.of("test.rendered", "Rendered command");
            var palette = new CommandPalette(false, query -> {}, (r, verb) -> {}, () -> {}, () -> {});
            palette.setResults(List.of(row), null, null);
            palette.setSize(palette.getPreferredSize());
            layoutTree(palette);

            var image = new BufferedImage(palette.getWidth(), palette.getHeight(), BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            palette.printAll(graphics);
            graphics.dispose();

            Component rendered = palette.resultList().getCellRenderer().getListCellRendererComponent(
                palette.resultList(), row, 0, true, false);
            JLabel title = labels(rendered).stream().filter(label -> label.getText().equals("Rendered command"))
                .findFirst().orElseThrow();
            JLabel badge = labels(rendered).stream().filter(label -> label.getText().equals("Ctrl+1"))
                .findFirst().orElseThrow();
            assertThat(title.getWidth()).isGreaterThan(0);
            assertThat(badge.getWidth()).isGreaterThan(0);
        });
    }

    @Test void escapeHintRemainsCompactAndVerticallyCentered() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = new CommandPalette(false, query -> {}, (row, verb) -> {}, () -> {}, () -> {});
            palette.setResults(List.of(PaletteRow.of("test.first", "First")), null, null);
            palette.setSize(palette.getPreferredSize());
            layoutTree(palette);

            JButton escape = buttons(palette).getFirst();
            Container inputRow = escape.getParent().getParent();
            assertThat(escape.getHeight()).isLessThan(UIScale.scale(32));
            int actualTop = escape.getY() + escape.getParent().getY();
            int centeredTop = (inputRow.getHeight() - escape.getHeight()) / 2;
            assertThat(Math.abs(actualTop - centeredTop)).isLessThanOrEqualTo(1);
        });
    }

    @Test void scopeChipPlaceholderFooterAndSectionLabelFollowSetScope() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = new CommandPalette(true, query -> {}, (row, verb) -> {}, () -> {}, () -> {});
            assertThat(palette.chip().getText()).isEqualTo("Commands");
            assertThat(palette.footer().isVisible()).isFalse();
            var verbs = List.of(new PaletteVerb("paste", "Paste"), new PaletteVerb("paste_run", "Paste and run"));
            palette.setScope("History", null, "Search shell history", verbs, 12, true);
            assertThat(palette.chip().getText()).isEqualTo("History");
            assertThat(palette.chip().getAccessibleContext().getAccessibleName()).isEqualTo("Scope: History");
            assertThat(palette.queryField().getClientProperty("JTextField.placeholderText")).isEqualTo("Search shell history");
            assertThat(palette.queryField().getAccessibleContext().getAccessibleName()).isEqualTo("Search history");
            assertThat(palette.resultList().getAccessibleContext().getAccessibleName()).isEqualTo("History");
            assertThat(palette.footer().isVisible()).isTrue();
            assertThat(palette.footer().getText()).isEqualTo("⏎ Paste  ⌘⏎ Paste and run");
            assertThat(CommandPalette.footerText(verbs, false)).isEqualTo("Enter Paste  Ctrl+Enter Paste and run");
            palette.setResults(List.of(PaletteRow.of("a", "ls")), "Most recent", null);
            assertThat(palette.sectionLabel().isVisible()).isTrue();
            assertThat(palette.sectionLabel().getText()).isEqualTo("Most recent");
            assertThat(palette.resultList().getAccessibleContext().getAccessibleDescription())
                .isEqualTo("1 results; ⏎ Paste  ⌘⏎ Paste and run");
            palette.setResults(List.of(), null, null);
            assertThat(palette.sectionLabel().isVisible()).isFalse();
            int expected = UIScale.scale(56 + 40 + 24);
            assertThat(palette.getPreferredSize().height).isEqualTo(expected);
        });
    }

    @Test void preferredRowsBoundTheCardHeightAndTheListScrollsBeyondThem() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = new CommandPalette(false, query -> {}, (row, verb) -> {}, () -> {}, () -> {});
            palette.setScope("History", null, "x", List.of(new PaletteVerb("paste", "Paste")), 12, true);
            var rows = new ArrayList<PaletteRow>();
            for (int i = 0; i < 30; i++) rows.add(PaletteRow.of("r" + i, "row " + i));
            palette.setResults(rows, null, null);
            assertThat(palette.getPreferredSize().height).isEqualTo(UIScale.scale(56 + 12 * 40));
            assertThat(palette.resultList().getVisibleRowCount()).isEqualTo(12);
            palette.setSize(palette.getPreferredSize());
            layoutTree(palette);
            palette.selectRelative(29);
            assertThat(palette.resultList().getSelectedIndex()).isEqualTo(29);
            assertThat(palette.resultList().getVisibleRect().intersects(palette.resultList().getCellBounds(29, 29))).isTrue();
            palette.setScope("Commands", null, "x", List.of(new PaletteVerb("run", "Run")), 5, false);
            palette.setResults(rows.subList(0, 5), null, null);
            assertThat(palette.getPreferredSize().height).isEqualTo(UIScale.scale(56 + 5 * 40));
        });
    }

    @Test void verbsRouteThroughExecuteWithTheirIndexAndBadgesStopAtFive() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var executed = new ArrayList<String>();
            var palette = new CommandPalette(true, query -> {}, (row, verb) -> executed.add(row.id() + ":" + verb), () -> {}, () -> {});
            var rows = new ArrayList<PaletteRow>();
            for (int i = 0; i < 7; i++) rows.add(new PaletteRow("r" + i, "row " + i, "detail " + i, "zsh", null, true, null));
            palette.setResults(rows, null, null);
            palette.executeSelected();
            palette.executeSelected(1);
            palette.selectRelative(6);
            palette.executeNumber(2);
            palette.executeNumber(7);
            assertThat(executed).containsExactly("r0:0", "r0:1", "r1:0");
            palette.setSize(palette.getPreferredSize());
            palette.doLayout();
            // The renderer is a single shared component reused for every cell (the usual Swing
            // ListCellRenderer pattern), so its labels must be captured immediately after each
            // getListCellRendererComponent call, before the next call reconfigures it.
            var renderer = palette.resultList().getCellRenderer();
            var sixth = (java.awt.Container) renderer.getListCellRendererComponent(palette.resultList(), rows.get(5), 5, false, false);
            sixth.setSize(UIScale.scale(560), UIScale.scale(40));
            sixth.doLayout();
            var sixthLabels = visibleLabels(sixth);
            var first = (java.awt.Container) renderer.getListCellRendererComponent(palette.resultList(), rows.get(0), 0, true, false);
            first.setSize(UIScale.scale(560), UIScale.scale(40));
            first.doLayout();
            assertThat(visibleLabels(first)).contains("row 0", "detail 0", "zsh", "⌘1");
            assertThat(sixthLabels).contains("row 5", "detail 5", "zsh").doesNotContain("⌘6");
        });
    }

    @Test void threeVerbsShowInTheFooterAndAStepReplacesTheListWithFields() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            new ThemeController(dev.jasper.app.config.Appearance.LIGHT);
            try {
            var palette = new CommandPalette(true, query -> {}, (row, verb) -> {}, () -> {}, () -> {});
            var verbs = List.of(new PaletteVerb("paste", "Paste"), new PaletteVerb("paste_run", "Paste and run"),
                new PaletteVerb("save", "Save as snippet…"));
            palette.setScope("History", null, "x", verbs, 5, true);
            assertThat(palette.footer().getText())
                .isEqualTo("⏎ Paste  ⌘⏎ Paste and run  ⇧⏎ Save as snippet…");
            assertThat(CommandPalette.footerText(verbs, false))
                .isEqualTo("Enter Paste  Ctrl+Enter Paste and run  Shift+Enter Save as snippet…");
            palette.setResults(List.of(PaletteRow.of("a", "A"), PaletteRow.of("b", "B")), null, null);
            palette.showStep("Rebase", List.of(new PaletteStep.Field("branch", "branch", "main"),
                new PaletteStep.Field("remote", "remote", "")));
            assertThat(palette.stepShowing()).isTrue();
            assertThat(palette.stepFields()).hasSize(2);
            assertThat(palette.stepFields().getFirst().getText()).isEqualTo("main");
            assertThat(palette.stepFields().getFirst().getAccessibleContext().getAccessibleName()).isEqualTo("branch");
            assertThat(palette.stepFocusIndex()).isZero();
            assertThat(palette.sectionLabel().getText()).isEqualTo("Rebase");
            assertThat(palette.sectionLabel().isVisible()).isTrue();
            assertThat(palette.getPreferredSize().height).isEqualTo(UIScale.scale(56 + 24 + 2 * 40 + 24));
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
            assertThat(palette.getPreferredSize().height).isEqualTo(UIScale.scale(56 + 24 + 2 * 40 + 24 + 24));
            palette.setStepError(null);
            assertThat(palette.stepError().isVisible()).isFalse();
            palette.hideStep();
            assertThat(palette.stepShowing()).isFalse();
            assertThat(palette.stepFields()).isEmpty();
            palette.selectRow("b");
            assertThat(palette.resultList().getSelectedValue().id()).isEqualTo("b");
            palette.selectRow("missing");
            assertThat(palette.resultList().getSelectedValue().id()).isEqualTo("b");
            } finally { new ThemeController(); }
        });
    }

    private static List<String> visibleLabels(java.awt.Container container) {
        var texts = new ArrayList<String>();
        for (Component child : container.getComponents())
            if (child instanceof JLabel label && label.isVisible() && label.getWidth() > 0 && !label.getText().isEmpty())
                texts.add(label.getText());
        return texts;
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
        if (component instanceof Container container) Arrays.stream(container.getComponents())
            .forEach(child -> found.addAll(labels(child)));
        return found;
    }

    private static List<JButton> buttons(Component component) {
        var found = new ArrayList<JButton>();
        if (component instanceof JButton button) found.add(button);
        if (component instanceof Container container) Arrays.stream(container.getComponents())
            .forEach(child -> found.addAll(buttons(child)));
        return found;
    }

    private static boolean hasInputBinding(javax.swing.JTextField field, String action) {
        KeyStroke[] keys = field.getInputMap().allKeys();
        return keys != null && Arrays.stream(keys).anyMatch(key -> action.equals(field.getInputMap().get(key)));
    }
    @Test void renderPaletteResultsAndFormsAtBothScales() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                for (var choice : java.util.List.of(dev.jasper.app.config.Appearance.DARK, dev.jasper.app.config.Appearance.LIGHT)) {
                    new ThemeController(choice);
                    var card = new CommandPalette(false, query -> {}, (row, verb) -> {}, () -> {}, () -> {});
                    card.setResults(List.of(PaletteRow.of("new_tab", "New terminal tab"), PaletteRow.of("settings", "Settings")), null, null);
                    var host = new javax.swing.JPanel(new java.awt.GridBagLayout()); host.add(card);
                    for (int scale : new int[]{1, 2}) saveRender(host, choice+"-palette-"+scale, scale);
                    card.showStep("Connect", List.of(new PaletteStep.Field("host", "Host", "example.org"), new PaletteStep.Field("user", "Username", "dustin")));
                    for (int scale : new int[]{1, 2}) saveRender(host, choice+"-palette-form-"+scale, scale);
                }
            } finally { new ThemeController(); }
        });
    }
private static void layoutTree(java.awt.Container container) {
    container.doLayout();
    for (var child : container.getComponents())
        if (child instanceof java.awt.Container nested) layoutTree(nested);
}
private static void saveRender(javax.swing.JComponent component, String name, int scale) {
    component.setSize(960, 640);
    layoutTree(component);
    var image = new java.awt.image.BufferedImage(960 * scale, 640 * scale,
        java.awt.image.BufferedImage.TYPE_INT_ARGB);
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
