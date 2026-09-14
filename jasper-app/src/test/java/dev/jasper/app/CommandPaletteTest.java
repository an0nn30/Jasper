package dev.jasper.app;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractAction;
import javax.swing.Action;
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
    @Test void queryNotifiesWhenClearedAndKeepsNativeEditingActions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var changes = new ArrayList<String>();
            var dismissed = new AtomicInteger();
            var executed = new ArrayList<Command>();
            var palette = new CommandPalette(false, changes::add, executed::add, dismissed::incrementAndGet);

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
            assertThat(palette.queryField().getToolTipText()).isEqualTo("Type a command");
        });
    }

    @Test void limitsAreVisibleAndMissingNumbersCannotExecute() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var executed = new ArrayList<String>();
            var palette = new CommandPalette(true, query -> {}, command -> executed.add(command.id()), () -> {});
            var commands = new ArrayList<Command>();
            for (int i = 0; i < 5; i++) {
                var action = new AbstractAction("Command " + i) {
                    @Override public void actionPerformed(java.awt.event.ActionEvent event) {}
                };
                commands.add(new Command("test." + i, action, List.of()));
            }
            palette.setResults(commands, false, null);
            palette.executeNumber(5);
            palette.executeNumber(6);
            assertThat(executed).containsExactly("test.4");
            assertThat(palette.resultList().getModel().getSize()).isEqualTo(5);
            assertThat(palette.resultList().getAccessibleContext().getAccessibleDescription()).isEqualTo("5 commands");
            assertThatIllegalArgumentException().isThrownBy(() -> palette.setResults(
                List.of(commands.get(0), commands.get(1), commands.get(2), commands.get(3)), true, null));
            palette.setResults(List.of(), false, null);
            palette.executeSelected();
            assertThat(executed).containsExactly("test.4");
        });
    }

    @Test void selectionSurvivesRefreshByIdentityAndQueryAcceptsDigits() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = new CommandPalette(false, query -> {}, command -> {}, () -> {});
            var action = new AbstractAction("Select Tab 2") {
                @Override public void actionPerformed(java.awt.event.ActionEvent event) {}
            };
            var command = new Command("select_tab_2", action, List.of("tab"));
            palette.setResults(List.of(command), false, command.id());
            palette.queryField().setText("tab 2");
            assertThat(palette.queryField().getText()).isEqualTo("tab 2");
            assertThat(palette.resultList().getSelectedValue()).isSameAs(command);
        });
    }

    @Test void navigationClampsAndRefreshFallsBackWhenIdentityDisappears() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var first = command("test.first", "First");
            var second = command("test.second", "Second");
            var third = command("test.third", "Third");
            var palette = new CommandPalette(false, query -> {}, command -> {}, () -> {});

            palette.setResults(List.of(first, second, third), false, null);
            assertThat(palette.resultList().getSelectedValue()).isSameAs(first);
            palette.selectRelative(50);
            assertThat(palette.resultList().getSelectedValue()).isSameAs(third);
            palette.selectRelative(-50);
            assertThat(palette.resultList().getSelectedValue()).isSameAs(first);
            palette.setResults(List.of(third, second, first), false, second.id());
            assertThat(palette.resultList().getSelectedValue()).isSameAs(second);
            palette.setResults(List.of(third, first), false, second.id());
            assertThat(palette.resultList().getSelectedValue()).isSameAs(third);
            palette.setResults(List.of(), false, null);
            palette.selectRelative(1);
            assertThat(palette.resultList().isSelectionEmpty()).isTrue();
        });
    }

    @Test void preservedSelectionStaysVisibleAcrossReorderAndResultGrowth() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var first = command("test.first", "First");
            var second = command("test.second", "Second");
            var third = command("test.third", "Third");
            var fourth = command("test.fourth", "Fourth");
            var selected = command("test.selected", "Selected");
            var palette = new CommandPalette(false, query -> {}, command -> {}, () -> {});
            palette.setResults(List.of(first, second, third, fourth, selected), false, null);
            palette.setSize(318, 140);
            layoutTree(palette);

            palette.selectRelative(4);
            assertThat(palette.resultList().getVisibleRect().contains(
                palette.resultList().getCellBounds(4, 4))).isTrue();

            palette.setResults(List.of(selected, first, second, third, fourth), false, selected.id());
            layoutTree(palette);
            assertThat(palette.resultList().getSelectedIndex()).isZero();
            assertThat(palette.resultList().getVisibleRect().contains(
                palette.resultList().getCellBounds(0, 0))).isTrue();

            palette.setResults(List.of(first, selected), false, selected.id());
            layoutTree(palette);
            palette.setResults(List.of(first, second, third, fourth, selected), false, selected.id());
            layoutTree(palette);
            assertThat(palette.resultList().getSelectedIndex()).isEqualTo(4);
            assertThat(palette.resultList().getVisibleRect().contains(
                palette.resultList().getCellBounds(4, 4))).isTrue();
        });
    }

    @Test void realRowBoundsExecuteClicksButBlankListSpaceDoesNothing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var executed = new ArrayList<String>();
            var first = command("test.first", "First");
            var second = command("test.second", "Second");
            var palette = new CommandPalette(false, query -> {}, command -> executed.add(command.id()), () -> {});
            palette.setResults(List.of(first, second), false, null);
            palette.setSize(palette.getPreferredSize());
            layoutTree(palette);

            Rectangle firstBounds = palette.resultList().getCellBounds(0, 0);
            Rectangle secondBounds = palette.resultList().getCellBounds(1, 1);
            assertThat(firstBounds.height).isEqualTo(40);
            assertThat(secondBounds).isEqualTo(new Rectangle(0, 40,
                palette.resultList().getWidth(), 40));

            // BasicListUI asks the native toolkit for the platform menu mask on mouse press.
            // Remove only that delegate listener so this component-level hit test stays headless.
            Arrays.stream(palette.resultList().getMouseListeners())
                .filter(listener -> listener.getClass().getName().startsWith("javax.swing.plaf."))
                .forEach(palette.resultList()::removeMouseListener);
            palette.resultList().dispatchEvent(mousePress(palette.resultList(), secondBounds.x + 4, secondBounds.y + 4));
            palette.resultList().setSize(palette.resultList().getWidth(), 120);
            palette.resultList().dispatchEvent(mousePress(palette.resultList(), 4, 100));
            assertThat(executed).containsExactly("test.second");
        });
    }

    @Test void inputMethodEventsExposeOnlyActiveComposition() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = new CommandPalette(false, query -> {}, command -> {}, () -> {});
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
            new ThemeController().selectLaf(UiLookAndFeel.METAL);
            var first = command("test.first", "First");
            var second = command("test.second", "Second");
            var palette = new CommandPalette(true, query -> {}, command -> {}, () -> {});
            palette.queryField().setText("fir");
            palette.setResults(List.of(first, second), false, second.id());
            palette.setSize(palette.getPreferredSize());
            layoutTree(palette);

            assertThat(palette.getPreferredSize().width).isEqualTo(560);
            assertThat(palette.queryField().getParent().getHeight()).isEqualTo(56);
            assertThat(palette.resultList().getFixedCellHeight()).isEqualTo(40);
            assertThat(palette.resultList().getFont().getFamily())
                .isEqualTo(UIManager.getFont("Label.font").getFamily());
            assertThat(palette.isOpaque()).isTrue();

            var image = new BufferedImage(palette.getWidth(), palette.getHeight(), BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            palette.paint(graphics);
            graphics.dispose();
            assertThat(new Color(image.getRGB(0, 0), true).getAlpha()).isEqualTo(255);
            assertThat(new Color(image.getRGB(palette.getWidth() / 2, 3), true))
                .isEqualTo(UIManager.getColor("Panel.background"));

            for (UiLookAndFeel theme : List.of(UiLookAndFeel.METAL, UiLookAndFeel.NIMBUS)) {
                new ThemeController().selectLaf(theme);
                SwingUtilities.updateComponentTreeUI(palette);
                palette.refreshTheme();
                assertThat(palette.getBackground()).isEqualTo(UIManager.getColor("Panel.background"));
                assertThat(palette.resultList().getSelectionBackground()).isNotNull()
                    .isNotEqualTo(palette.resultList().getBackground());
                assertThat(palette.resultList().getSelectionForeground()).isNotNull();
                assertThat(palette.queryField().getText()).isEqualTo("fir");
                assertThat(palette.resultList().getSelectedValue()).isSameAs(second);
            }
            new ThemeController().selectLaf(UiLookAndFeel.METAL);
        });
    }

    @Test void standardLookAndFeelSelectionIsVisibleOnFirstRendererPaint() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (UiLookAndFeel theme : List.of(UiLookAndFeel.METAL, UiLookAndFeel.NIMBUS)) {
                new ThemeController().selectLaf(theme);
                var palette = new CommandPalette(false, query -> {}, selected -> {}, () -> {});
                var command = command("test.selection", "Selected command");
                Component rendered = palette.resultList().getCellRenderer().getListCellRendererComponent(
                    palette.resultList(), command, 0, true, false);
                rendered.setSize(320, 40);
                var image = new BufferedImage(320, 40, BufferedImage.TYPE_INT_ARGB);
                var graphics = image.createGraphics();
                rendered.paint(graphics);
                graphics.dispose();
                assertThat(new Color(image.getRGB(2, 2))).as(theme + " selected row")
                    .isEqualTo(new Color(palette.resultList().getSelectionBackground().getRGB()));
            }
            new ThemeController().selectLaf(UiLookAndFeel.METAL);
        });
    }

    @Test void openingLabelCanShowRecentOrSuggestedResults() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var palette = new CommandPalette(false, query -> {}, command -> {}, () -> {});
            palette.setOpeningLabel("Suggested");
            palette.setResults(List.of(command("test.first", "First")), true, null);
            assertThat(labels(palette)).anySatisfy(label -> {
                assertThat(label.getText()).isEqualTo("Suggested");
                assertThat(label.isVisible()).isTrue();
            });
            palette.setOpeningLabel("Recent");
            assertThat(labels(palette)).anyMatch(label -> label.getText().equals("Recent"));
        });
    }

    @Test void rendererKeepsLiteralLongTitlesBehindTheQuickBadge() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var action = new AbstractAction("<html>Extremely long command title that cannot share a narrow row with its shortcut") {
                @Override public void actionPerformed(java.awt.event.ActionEvent event) {}
            };
            action.putValue(Action.ACCELERATOR_KEY,
                KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
            var command = new Command("test.long", action, List.of());
            var palette = new CommandPalette(false, query -> {}, selected -> {}, () -> {});
            palette.setResults(List.of(command), false, null);
            palette.resultList().setSize(320, 40);

            Component rendered = palette.resultList().getCellRenderer().getListCellRendererComponent(
                palette.resultList(), command, 0, true, false);
            rendered.setSize(320, 40);
            layoutTree(rendered);
            var rowLabels = labels(rendered);
            JLabel title = rowLabels.stream().filter(label -> label.getText().startsWith("<html>"))
                .findFirst().orElseThrow();
            JLabel shortcut = rowLabels.stream().filter(label -> label.getText().equals("Ctrl+Shift+K"))
                .findFirst().orElseThrow();
            JLabel badge = rowLabels.stream().filter(label -> label.getText().equals("Ctrl+1"))
                .findFirst().orElseThrow();

            assertThat(title.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
            assertThat(shortcut.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
            assertThat(shortcut.isVisible()).isFalse();
            assertThat(badge.isVisible()).isTrue();
            assertThat(title.getX() + title.getWidth()).isLessThanOrEqualTo(badge.getX());
            assertThat(badge.getX() + badge.getWidth()).isLessThanOrEqualTo(rendered.getWidth());

            action.putValue(Action.NAME, "Open");
            action.putValue(Action.ACCELERATOR_KEY,
                KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
            var macPalette = new CommandPalette(true, query -> {}, selected -> {}, () -> {});
            Component macRendered = macPalette.resultList().getCellRenderer().getListCellRendererComponent(
                macPalette.resultList(), command, 0, false, false);
            macRendered.setSize(560, 40);
            layoutTree(macRendered);
            assertThat(labels(macRendered)).anyMatch(label -> label.getText().equals("\u21e7\u2318K"));
        });
    }

    @Test void actualListPaintingLaysOutRendererTextAndBadge() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var command = command("test.rendered", "Rendered command");
            var palette = new CommandPalette(false, query -> {}, selected -> {}, () -> {});
            palette.setResults(List.of(command), false, null);
            palette.setSize(palette.getPreferredSize());
            layoutTree(palette);

            var image = new BufferedImage(palette.getWidth(), palette.getHeight(), BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            palette.printAll(graphics);
            graphics.dispose();

            Component rendered = palette.resultList().getCellRenderer().getListCellRendererComponent(
                palette.resultList(), command, 0, true, false);
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
            var palette = new CommandPalette(false, query -> {}, selected -> {}, () -> {});
            palette.setResults(List.of(command("test.first", "First")), false, null);
            palette.setSize(palette.getPreferredSize());
            layoutTree(palette);

            JButton escape = buttons(palette).getFirst();
            Container inputRow = escape.getParent().getParent();
            assertThat(escape.getHeight()).isLessThan(32);
            int actualTop = escape.getY() + escape.getParent().getY();
            int centeredTop = (inputRow.getHeight() - escape.getHeight()) / 2;
            assertThat(Math.abs(actualTop - centeredTop)).isLessThanOrEqualTo(1);
        });
    }

    private static Command command(String id, String title) {
        return new Command(id, new AbstractAction(title) {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {}
        }, List.of());
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
}
