package dev.jasper.app.pluginmanager;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EdtTestExtension.class)
class PluginCardListTest {
    private final List<String> events = new ArrayList<>();
    private final List<String> menus = new ArrayList<>();
    private int selections;
    private final PluginCardList list = new PluginCardList(row -> events.add("toggle:" + row.id()), () -> selections++,
        (component, point) -> menus.add("menu"));

    private void key(int code, int modifiers) {
        Object name = list.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(code, modifiers));
        list.getActionMap().get(name).actionPerformed(new ActionEvent(list, ActionEvent.ACTION_PERFORMED, ""));
    }

    @Test void cardsAreGroupedSortedAndCounted() {
        list.show(List.of(
            TestRows.plugin("dev.jasper.remote", "Remote").origin("Bundled").row(),
            TestRows.plugin("dev.jasper.editor", "Editor").origin("Bundled").disabled().row(),
            TestRows.plugin("dev.example.zeta", "zeta tool").row(),
            TestRows.plugin("dev.example.alpha", "Alpha").row(),
            TestRows.plugin("dev.example.local", "Local").origin("Development").row()));
        assertThat(list.groupTitles()).containsExactly("Development", "User-installed (2 of 2 enabled)", "Bundled (1 of 2 enabled)");
        assertThat(list.listed()).containsExactly("Local  1.0.0", "Alpha  1.0.0", "zeta tool  1.0.0", "Editor  1.0.0", "Remote  1.0.0");
        assertThat(list.selected()).as("the first visible card").isEqualTo("dev.example.local");
        assertThat(selections).isPositive();
    }

    @Test void eachCardShowsAtMostOneBadgeInTheSpecsOrder() {
        list.show(List.of(
            TestRows.plugin("a.one", "One").removing().row(),
            TestRows.plugin("a.two", "Two").installing().row(),
            TestRows.plugin("a.three", "Three").pending("Enabled at restart").row(),
            TestRows.plugin("a.four", "Four").needsReview("terminal.inject").row(),
            TestRows.plugin("a.five", "Five").state("FAILED", "boom").row(),
            TestRows.plugin("a.six", "Six").state("SKIPPED", "requires a.base").row(),
            TestRows.plugin("a.seven", "Seven").row(),
            TestRows.plugin("a.eight", "Eight").needsReview().pending("Enabled at restart").row()));
        assertThat(list.listed()).containsExactly(
            "Eight  1.0.0 — Restart to apply", "Five  1.0.0 — Failed", "Four  1.0.0 — Needs review",
            "One  1.0.0 — Remove at restart", "Seven  1.0.0", "Six  1.0.0 — Skipped",
            "Three  1.0.0 — Restart to apply", "Two  1.0.0 — Install at restart");
    }

    @Test void theCheckboxShowsTheSnapshotAndAsksTheHandler() {
        var rows = List.of(TestRows.plugin("a.on", "On").row(), TestRows.plugin("a.review", "Review").needsReview("terminal.inject").row(),
            TestRows.plugin("a.fixed", "Fixed").removing().row());
        list.show(rows);
        list.card("a.on").enabled.doClick();
        assertThat(events).containsExactly("toggle:a.on");
        assertThat(list.card("a.on").enabled.isSelected()).as("what the snapshot says until the next one").isTrue();
        list.card("a.review").enabled.doClick();
        assertThat(events).containsExactly("toggle:a.on", "toggle:a.review");
        list.show(rows);
        assertThat(list.card("a.on").enabled.isSelected()).isTrue();
        assertThat(list.card("a.fixed").enabled.isEnabled()).isFalse();
        assertThat(list.card("a.fixed").enabled.getToolTipText()).isEqualTo("Will be removed at restart");
        list.busy(true);
        assertThat(list.card("a.on").enabled.isEnabled()).isFalse();
        list.busy(false);
        assertThat(list.card("a.on").enabled.isEnabled()).isTrue();
    }

    @Test void searchFiltersCardsHidesEmptyGroupsAndMovesTheSelection() {
        list.show(List.of(TestRows.plugin("dev.jasper.remote", "Remote").origin("Bundled").description("SSH and SFTP").row(),
            TestRows.plugin("dev.example.tool", "Tool").vendor("Acme").row()));
        list.select("dev.example.tool");
        list.search("ssh");
        assertThat(list.listed()).containsExactly("Remote  1.0.0");
        assertThat(list.groupTitles()).as("counts cover the whole group").containsExactly("Bundled (1 of 1 enabled)");
        assertThat(list.selected()).isEqualTo("dev.jasper.remote");
        list.search("ACME");
        assertThat(list.listed()).containsExactly("Tool  1.0.0");
        list.search("example.tool");
        assertThat(list.listed()).as("the id matches too").containsExactly("Tool  1.0.0");
        list.search("nothing like it");
        assertThat(list.listed()).isEmpty();
        assertThat(list.emptyText()).isEqualTo("No plugins match");
        assertThat(list.selected()).isNull();
        list.search("");
        list.show(List.of());
        assertThat(list.emptyText()).isEqualTo("No plugins installed");
    }

    @Test void theSelectionSurvivesARefreshAndTheKeysMoveToggleAndOpenTheMenu() {
        var a = TestRows.plugin("a.a", "A").row();
        var b = TestRows.plugin("a.b", "B").row();
        var c = TestRows.plugin("a.c", "C").row();
        list.show(List.of(a, b, c));
        list.select("a.b");
        list.show(List.of(a, b, c));
        assertThat(list.selected()).isEqualTo("a.b");
        key(KeyEvent.VK_DOWN, 0);
        assertThat(list.selected()).isEqualTo("a.c");
        key(KeyEvent.VK_DOWN, 0);
        assertThat(list.selected()).as("stays on the last card").isEqualTo("a.c");
        key(KeyEvent.VK_UP, 0);
        assertThat(list.selected()).isEqualTo("a.b");
        key(KeyEvent.VK_SPACE, 0);
        assertThat(events).containsExactly("toggle:a.b");
        key(KeyEvent.VK_F10, InputEvent.SHIFT_DOWN_MASK);
        key(KeyEvent.VK_CONTEXT_MENU, 0);
        assertThat(menus).hasSize(2);
        list.show(List.of(a, c));
        assertThat(list.selected()).as("the first card when the selected one is gone").isEqualTo("a.a");
    }

    @Test void pluginTextIsNeverMarkup() {
        list.show(List.of(TestRows.plugin("a.h", "<html><b>Bold").vendor("<html><i>Acme").row()));
        assertThat(list.listed()).containsExactly("<html><b>Bold  1.0.0");
        assertThat(list.card("a.h").name.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
    }
}
