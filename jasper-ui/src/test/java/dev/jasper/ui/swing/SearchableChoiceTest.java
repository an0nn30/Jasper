package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/**
 * The kit's searchable choice: the type-to-filter behaviour the Settings theme dropdowns had before the migration,
 * driven through a mounted descriptor. The host below is controlled, as a domain owner is: it applies each pick.
 */
class SearchableChoiceTest {
    static final List<String> NAMES = List.of("Same as UI theme", "Catppuccin Mocha", "Darcula", "Jasper Dark", "Jasper Light", "Nord");

    static String id(String label) { return label.toLowerCase(Locale.ROOT).replace(' ', '-'); }

    static Choice theme(List<String> labels, String selected) {
        return new Choice("theme", ControlState.READY, ChoiceKind.SEARCHABLE,
            labels.stream().map(label -> new Option(id(label), label, Optional.empty())).toList(),
            selected.isEmpty() ? "" : id(selected), "Theme", "", "", Optional.of(new ChoiceSearch(1, "No matching themes", Optional.empty())));
    }

    /** A mounted searchable choice whose host applies every pick, as the Settings draft does. */
    static final class Fixture implements AutoCloseable {
        final KitContext context = KitFixtures.context();
        final List<UiEvent> events = new ArrayList<>();
        List<String> labels = new ArrayList<>(NAMES);
        String selected = "Same as UI theme";
        final KitView view;
        Fixture() {
            Consumer<UiEvent> host = event -> {
                events.add(event);
                if (event instanceof UiEvent.Selected picked)
                    selected = labels.stream().filter(label -> picked.ids().contains(id(label))).findFirst().orElseThrow();
            };
            view = new KitView(context, stack(theme(labels, selected)), event -> { host.accept(event); refresh(); });
        }
        void refresh() { view.update(stack(theme(labels, selected))); }
        void select(String label) { selected = label; refresh(); }
        SearchChoices.Combo combo() { return find(view, "theme.input", SearchChoices.Combo.class); }
        SearchChoices.Popup popup() { return combo().searchPopup(); }
        String shownSelection() { return ((Option) combo().getSelectedItem()).label(); }
        void type(String text) {
            for (char c : text.toCharArray()) popup().getKeyListener().keyTyped(new KeyEvent(combo(), KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, c));
        }
        boolean press(int code) {
            var event = new KeyEvent(combo(), KeyEvent.KEY_PRESSED, 0, 0, code, KeyEvent.CHAR_UNDEFINED);
            popup().getKeyListener().keyPressed(event);
            return event.isConsumed();
        }
        @Override public void close() { view.close(); release(context); }
    }

    @Test void typingShowsTheSearchBarAndNarrowsTheListToNamesHoldingEveryWord() {
        Edt.run(() -> { try (var f = new Fixture()) {
            f.popup().show();
            assertThat(f.popup().searchBar().isVisible()).isFalse();
            assertThat(f.popup().shown()).hasSize(6);
            f.type("dark");
            assertThat(f.popup().searchBar().isVisible()).isTrue();
            assertThat(f.popup().query()).isEqualTo("dark");
            assertThat(f.popup().shown()).as("the pinned entry stays first").containsExactly("Same as UI theme", "Jasper Dark");
            f.press(KeyEvent.VK_ESCAPE);
            f.type("MOCHA cat");
            assertThat(f.popup().shown()).containsExactly("Same as UI theme", "Catppuccin Mocha");
        } });
    }

    @Test void theHighlightStartsOnTheChoiceAndMovesToTheFirstMatch() {
        Edt.run(() -> { try (var f = new Fixture()) {
            f.select("Nord");
            f.popup().show();
            assertThat(f.popup().highlighted()).isEqualTo("Nord");
            f.type("jasper");
            assertThat(f.popup().highlighted()).as("the choice no longer matches").isEqualTo("Jasper Dark");
            assertThat(f.press(KeyEvent.VK_DOWN)).isTrue();
            assertThat(f.popup().highlighted()).isEqualTo("Jasper Light");
            f.press(KeyEvent.VK_DOWN);
            assertThat(f.popup().highlighted()).as("the end stops").isEqualTo("Jasper Light");
            f.press(KeyEvent.VK_UP);
            f.press(KeyEvent.VK_UP);
            assertThat(f.popup().highlighted()).as("the pinned entry is reachable").isEqualTo("Same as UI theme");
        } });
    }

    @Test void enterPicksTheHighlightedNameAndClearsTheSearch() {
        Edt.run(() -> { try (var f = new Fixture()) {
            f.popup().show();
            f.type("nord");
            assertThat(f.press(KeyEvent.VK_ENTER)).as("Enter does not reach the dialog's OK").isTrue();
            assertThat(f.events).containsExactly(new UiEvent.Selected("theme", Set.of("nord")));
            assertThat(f.shownSelection()).isEqualTo("Nord");
            assertThat(f.popup().query()).isEmpty();
            assertThat(f.popup().searchBar().isVisible()).isFalse();
        } });
    }

    @Test void backspaceEditsTheSearchAndEscapeEndsIt() {
        Edt.run(() -> { try (var f = new Fixture()) {
            f.popup().show();
            f.type("nordx");
            assertThat(f.popup().shown()).as("a line says so").containsExactly("Same as UI theme", "No matching themes");
            assertThat(f.popup().highlighted()).as("nothing matched, so nothing is highlighted").isNull();
            f.press(KeyEvent.VK_DOWN);
            f.press(KeyEvent.VK_DOWN);
            assertThat(f.popup().highlighted()).as("the line is not an entry").isEqualTo("Same as UI theme");
            assertThat(f.press(KeyEvent.VK_BACK_SPACE)).isTrue();
            assertThat(f.popup().shown()).containsExactly("Same as UI theme", "Nord");
            assertThat(f.press(KeyEvent.VK_ESCAPE)).as("Escape does not close Settings").isTrue();
            assertThat(f.popup().query()).isEmpty();
            assertThat(f.events).as("nothing was picked").isEmpty();
            assertThat(f.shownSelection()).isEqualTo("Same as UI theme");
        } });
    }

    @Test void typingNeverPicksByFirstLetterAndShortcutsAreLeftAlone() {
        Edt.run(() -> { try (var f = new Fixture()) {
            assertThat(f.combo().selectWithKeyChar('n')).isFalse();
            assertThat(f.shownSelection()).isEqualTo("Same as UI theme");
            var shortcut = new KeyEvent(f.combo(), KeyEvent.KEY_TYPED, 0, InputEvent.META_DOWN_MASK, KeyEvent.VK_UNDEFINED, 'w');
            f.popup().getKeyListener().keyTyped(shortcut);
            assertThat(shortcut.isConsumed()).isFalse();
            assertThat(f.popup().query()).isEmpty();
            assertThat(f.press(KeyEvent.VK_DOWN)).as("closed and not searching: the combo's own keys").isFalse();
            assertThat(f.events).isEmpty();
        } });
    }

    @Test void enterAfterASearchWithNoMatchesPicksNothing() {
        Edt.run(() -> { try (var f = new Fixture()) {
            f.select("Nord");
            f.popup().show();
            f.type("xyz");
            assertThat(f.press(KeyEvent.VK_ENTER)).isTrue();
            assertThat(f.events).isEmpty();
            assertThat(f.shownSelection()).isEqualTo("Nord");
        } });
    }

    @Test void homeEndAndTheKeypadArrowsMoveTheHighlightWithoutPicking() {
        Edt.run(() -> { try (var f = new Fixture()) {
            f.popup().show();
            f.type("jasper");
            assertThat(f.press(KeyEvent.VK_END)).isTrue();
            assertThat(f.popup().highlighted()).isEqualTo("Jasper Light");
            assertThat(f.press(KeyEvent.VK_HOME)).isTrue();
            assertThat(f.popup().highlighted()).isEqualTo("Same as UI theme");
            assertThat(f.press(KeyEvent.VK_KP_DOWN)).isTrue();
            assertThat(f.popup().highlighted()).isEqualTo("Jasper Dark");
            assertThat(f.press(KeyEvent.VK_KP_UP)).isTrue();
            assertThat(f.popup().highlighted()).isEqualTo("Same as UI theme");
            assertThat(f.events).as("only Enter picks").isEmpty();
            assertThat(f.shownSelection()).isEqualTo("Same as UI theme");
        } });
    }

    @Test void aNewLookAndFeelKeepsTheSearchAndNeverPicksByFirstLetter() {
        Edt.run(() -> { try (var f = new Fixture()) {
            var popup = f.popup();
            SwingUtilities.updateComponentTreeUI(f.view);
            var fresh = f.popup();
            assertThat(fresh).isNotSameAs(popup);
            assertThat(f.combo().getUI()).isInstanceOf(Choices.ChoiceCombo.KitUi.class);
            assertThat(f.combo().selectWithKeyChar('n')).isFalse();
            assertThat(f.shownSelection()).isEqualTo("Same as UI theme");
            fresh.show();
            fresh.getKeyListener().keyTyped(new KeyEvent(f.combo(), KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'n'));
            assertThat(fresh.shown()).containsExactly("Same as UI theme", "Catppuccin Mocha", "Nord");
        } });
    }

    @Test void spaceOpensTheListAndAltGrCharactersAreSearchText() {
        Edt.run(() -> { try (var f = new Fixture()) {
            var space = new KeyEvent(f.combo(), KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, ' ');
            f.popup().getKeyListener().keyTyped(space);
            assertThat(space.isConsumed()).isTrue();
            assertThat(f.popup().query()).isEmpty();
            var altGr = new KeyEvent(f.combo(), KeyEvent.KEY_TYPED, 0, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK, KeyEvent.VK_UNDEFINED, '@');
            f.popup().getKeyListener().keyTyped(altGr);
            assertThat(f.popup().query()).isEqualTo("@");
        } });
    }

    @Test void rowsArePaddedLikeTheKitsOwnDropdownSoTheHighlightCoversTheText() {
        Edt.run(() -> {
            for (var theme : List.of(KitFixtures.dark(), KitFixtures.light())) {
                try (var f = new Fixture()) {
                    f.context.binding().publish(new dev.jasper.ui.theme.ThemePair(theme, theme));
                    var dropdown = new Choice("plain", ControlState.READY, ChoiceKind.DROPDOWN,
                        List.of(new Option("nord", "Nord", Optional.empty())), "nord", "Plain", "");
                    f.view.update(stack(theme(f.labels, f.selected), dropdown));
                    var plain = find(f.view, "plain.input", JComboBox.class);
                    var plainList = new JList<Object>();
                    @SuppressWarnings("unchecked") var plainRenderer = (ListCellRenderer<Object>) plain.getRenderer();
                    var stockRow = (JComponent) plainRenderer.getListCellRendererComponent(plainList, plain.getItemAt(0), 0, true, false);
                    var list = f.popup().getList();
                    f.popup().show();
                    var row = (JComponent) list.getCellRenderer().getListCellRendererComponent(list, "Nord", 0, true, false);
                    assertThat(row.getInsets().left).as(theme.name()).isEqualTo(stockRow.getInsets().left);
                    assertThat(row.getInsets().right).as(theme.name()).isEqualTo(stockRow.getInsets().right);
                    assertThat(list.getFont()).as(theme.name()).isEqualTo(f.combo().getFont());
                    assertThat(row.getBackground()).as("the highlight is the kit's").isEqualTo(theme.semantic().color(dev.jasper.ui.theme.SemanticColor.ACCENT));
                }
            }
        });
    }

    @Test void aListChangeWhileSearchingRefilters() {
        Edt.run(() -> { try (var f = new Fixture()) {
            f.popup().show();
            f.type("gruv");
            assertThat(f.popup().shown()).containsExactly("Same as UI theme", "No matching themes");
            f.labels.add("Gruvbox Dark");
            f.refresh();
            assertThat(f.popup().shown()).containsExactly("Same as UI theme", "Gruvbox Dark");
            assertThat(f.popup().highlighted()).isEqualTo("Gruvbox Dark");
        } });
    }

    // Free entry: the same control with an editable value and the options as suggestions (font families).

    static Choice font(String entry) {
        return new Choice("font", ControlState.READY, ChoiceKind.SEARCHABLE,
            List.of(new Option("menlo", "Menlo", Optional.empty()), new Option("jetbrains-mono", "JetBrains Mono", Optional.empty()),
                new Option("sf-mono", "SF Mono", Optional.empty())),
            "", "Font", "", "", Optional.of(new ChoiceSearch(0, "No installed font matches", Optional.of(entry))));
    }

    @Test void anEmptyFreeEntryPaintsItsPlaceholderWithoutMakingItTheValue() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var group = new Choice("group", ControlState.READY, ChoiceKind.SEARCHABLE, List.of(new Option("work", "Work", Optional.empty())),
                "", "Group", "", "", Optional.of(new ChoiceSearch(0, "No other groups", Optional.of(""), "Other")));
            try (var view = new KitView(context, stack(group), events::add)) {
                var editor = find(view, "group.input", SearchChoices.Combo.class).editorField();
                editor.setSize(160, 22);
                java.util.function.IntSupplier ink = () -> {
                    var image = new java.awt.image.BufferedImage(160, 22, java.awt.image.BufferedImage.TYPE_INT_ARGB); var g = image.createGraphics();
                    try { editor.paint(g); } finally { g.dispose(); }
                    var placeholder = ThemePainting.color(context, dev.jasper.ui.theme.SemanticColor.TEXT_PLACEHOLDER);
                    int count = 0;
                    for (int x = 0; x < 160; x++) for (int y = 0; y < 22; y++) {
                        int rgb = image.getRGB(x, y);
                        if ((rgb >>> 24) > 200 && Math.abs(((rgb >> 16) & 255) - placeholder.getRed()) < 40 && Math.abs((rgb & 255) - placeholder.getBlue()) < 40) count++;
                    }
                    return count;
                };
                assertThat(editor.getText()).isEmpty();
                assertThat(ink.getAsInt()).as("placeholder ink while empty").isPositive();
                editor.setText("Lab");
                assertThat(ink.getAsInt()).as("typed text replaces the placeholder").isZero();
                assertThat(events).last().isEqualTo(new UiEvent.Edited("group", "Lab"));
            } finally { release(context); }
            assertThatThrownBy(() -> new ChoiceSearch(0, "No matches", Optional.empty(), "Other")).isInstanceOf(IllegalArgumentException.class);
            assertThat(new ChoiceSearch(0, "No matches", Optional.of("x")).placeholder()).isEmpty();
        });
    }

    @Test void freeEntryEditsTheTextAndOffersMatchingSuggestions() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stack(font("Menlo")), events::add)) {
                var combo = find(view, "font.input", SearchChoices.Combo.class);
                var editor = combo.editorField();
                assertThat(combo.isEditable()).isTrue();
                assertThat(editor.getText()).isEqualTo("Menlo");
                editor.setText("mono");
                assertThat(events).last().isEqualTo(new UiEvent.Edited("font", "mono"));
                assertThat(combo.searchPopup().shown()).as("suggestions hold every typed word").containsExactly("JetBrains Mono", "SF Mono");
                editor.setText("Iosevka Term");
                assertThat(events).last().isEqualTo(new UiEvent.Edited("font", "Iosevka Term"));
                assertThat(combo.searchPopup().shown()).containsExactly("No installed font matches");
                assertThat(combo.searchPopup().highlighted()).isNull();
                var enter = new KeyEvent(editor, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ENTER, KeyEvent.CHAR_UNDEFINED);
                combo.searchPopup().getKeyListener().keyPressed(enter);
                assertThat(editor.getText()).as("an uninstalled family stays as typed").isEqualTo("Iosevka Term");
            } finally { release(context); }
        });
    }

    @Test void pickingASuggestionEntersItsNameAndHostUpdatesKeepTheTypedText() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            KitView[] holder = {null};
            try (var view = new KitView(context, stack(font("Menlo")), event -> {
                events.add(event);
                if (event instanceof UiEvent.Edited edited) holder[0].update(stack(font(edited.text())));
            })) {
                holder[0] = view;
                var combo = find(view, "font.input", SearchChoices.Combo.class);
                var editor = combo.editorField();
                editor.setText("jet");
                editor.setCaretPosition(2);
                view.update(stack(font("jet")));
                assertThat(editor.getText()).isEqualTo("jet");
                assertThat(editor.getCaretPosition()).as("an unchanged value keeps the caret").isEqualTo(2);
                var down = new KeyEvent(editor, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED);
                combo.searchPopup().getKeyListener().keyPressed(down);
                assertThat(combo.searchPopup().highlighted()).isEqualTo("JetBrains Mono");
                var enter = new KeyEvent(editor, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ENTER, KeyEvent.CHAR_UNDEFINED);
                combo.searchPopup().getKeyListener().keyPressed(enter);
                assertThat(enter.isConsumed()).isTrue();
                assertThat(editor.getText()).isEqualTo("JetBrains Mono");
                assertThat(events).last().isEqualTo(new UiEvent.Edited("font", "JetBrains Mono"));
                assertThat(events).noneMatch(UiEvent.Selected.class::isInstance);
                view.update(stack(font("SF Mono")));
                assertThat(editor.getText()).as("a domain value replaces the text").isEqualTo("SF Mono");
            } finally { release(context); }
        });
    }

    /**
     * Opening and closing rebuild the list in one step: rebuilt row by row with the highlight on the first row, the
     * highlight followed every added row and each move measured every row again, so a long font list lagged.
     */
    @Test void openingALongListMeasuresEachRowAFewTimesNotOncePerAddedRow() {
        Edt.run(() -> { try (var f = new Fixture()) {
            f.labels = new ArrayList<>(java.util.stream.IntStream.range(0, 400).mapToObj(i -> "Family " + i).toList());
            f.select("Family 0");
            var list = f.popup().getList();
            var renderer = list.getCellRenderer();
            int[] measured = {0};
            list.setCellRenderer((source, value, row, selected, focused) -> {
                measured[0]++;
                return renderer.getListCellRendererComponent(source, value, row, selected, focused);
            });
            for (int round = 0; round < 3; round++) { f.popup().show(); f.popup().hide(); }
            assertThat(f.popup().shown()).hasSize(400);
            assertThat(measured[0]).as("cell measurements for three opens and closes of 400 rows").isLessThan(400 * 3 * 2 * 2);
        } });
    }
}
