package dev.jasper.app.pluginmanager;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.UiEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The plugin rows as kit list rows: groups, badges, switches, search, selection, keys and each row's own menu. */
@ExtendWith(EdtTestExtension.class)
class PluginCardListTest {
    private final List<UiEvent> events = new ArrayList<>();
    private int selections;
    private PluginCardList list;

    /** Kit views are EDT confined; the extension runs only the test methods there. */
    @BeforeEach void open() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> list = new PluginCardList(KitTestSupport.context(), events::add, () -> selections++)); }

    @AfterEach void close() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> list.close()); }

    @Test void declaredArtworkIsReceivedByTheRealRowAndRetiredOnlyAfterReplacement() {
        var icon=new dev.jasper.app.plugins.PluginRuntime.PluginIcon("art.svg",PluginIconsTest.SVG.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        list.show(List.of(TestRows.plugin("a.art", "Authored").icon(icon).row()));
        PluginKit.laidOut(list.view());
        var label=KitTestSupport.find(list.view(),"icon:a.art",JLabel.class);
        var image=new java.awt.image.BufferedImage(label.getWidth(),label.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g=image.createGraphics();try{label.paint(g);}finally{g.dispose();}
        assertThat(image.getRGB(image.getWidth()/2,image.getHeight()/2)&0xffffff).isEqualTo(0x3574f0);
    }

    @Test void sameByteHueRefreshChangesTheRealUnselectedRowBackdropOnly() {
        var bytes=PluginIconsTest.SVG.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var plain=TestRows.plugin("a.first","First").row();
        java.util.function.Function<dev.jasper.ui.model.Nodes.Hue,int[]> render=hue->{
            list.show(List.of(plain,TestRows.plugin("b.art","Z Authored").icon(new dev.jasper.app.plugins.PluginRuntime.PluginIcon("art.svg",bytes,hue)).row()));
            PluginKit.laidOut(list.view());var label=KitTestSupport.find(list.view(),"icon:b.art",JLabel.class);
            return PluginIconsTest.pixels(label.getIcon());
        };
        var blue=render.apply(dev.jasper.ui.model.Nodes.Hue.BLUE);var purple=render.apply(dev.jasper.ui.model.Nodes.Hue.PURPLE);
        assertThat(blue).isNotEqualTo(purple);assertThat(blue[blue.length/2+(int)Math.sqrt(blue.length)/2]).isEqualTo(purple[purple.length/2+(int)Math.sqrt(purple.length)/2]);
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
        var texts = PluginKit.texts(list.view());
        assertThat(texts).as("the headings and rows are on screen, in order")
            .containsSubsequence("Development", "Local", "User-installed (2 of 2 enabled)", "Alpha", "zeta tool", "Bundled (1 of 2 enabled)", "Editor", "Remote");
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
        PluginKit.laidOut(list.view());
        assertThat(KitTestSupport.find(list.view(), "badge-dot:a.five", javax.swing.JComponent.class)).as("a failure is marked as danger").isNotNull();
        assertThat(KitTestSupport.find(list.view(), "badge-dot:a.three", javax.swing.JComponent.class)).as("a pending change is not").isNull();
        assertThat(KitTestSupport.find(list.view(), "badge:a.seven", javax.swing.JComponent.class)).isNull();
    }

    @Test void theSwitchShowsTheSnapshotAndAsksTheHandlerOnce() {
        var rows = List.of(TestRows.plugin("a.on", "On").row(), TestRows.plugin("a.review", "Review").needsReview("terminal.inject").row(),
            TestRows.plugin("a.fixed", "Fixed").removing().row());
        list.show(rows);
        PluginKit.toggle(list, "a.on").doClick(0);
        assertThat(events).containsExactly(new UiEvent.Toggled("toggle:a.on", false));
        assertThat(PluginKit.toggle(list, "a.on").isSelected()).as("what the snapshot says until the next one").isTrue();
        assertThat(PluginKit.toggle(list, "a.review").isSelected()).as("not running until reviewed").isFalse();
        PluginKit.toggle(list, "a.review").doClick(0);
        assertThat(events).containsExactly(new UiEvent.Toggled("toggle:a.on", false), new UiEvent.Toggled("toggle:a.review", true));
        assertThat(PluginKit.toggle(list, "a.review").isSelected()).isFalse();
        list.show(rows);
        assertThat(PluginKit.toggle(list, "a.on").isSelected()).isTrue();
        assertThat(PluginKit.toggle(list, "a.fixed").isEnabled()).isFalse();
        assertThat(PluginKit.toggle(list, "a.fixed").getToolTipText()).isEqualTo("Will be removed at restart");
        assertThat(PluginKit.toggle(list, "a.on").getAccessibleContext().getAccessibleName()).isEqualTo("Enable On");
        list.busy(true);
        assertThat(PluginKit.toggle(list, "a.on").isEnabled()).isFalse();
        list.busy(false);
        assertThat(PluginKit.toggle(list, "a.on").isEnabled()).isTrue();
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
        assertThat(PluginKit.emptyTitle(list.view())).as("the empty state says so").isEqualTo("No plugins match");
        assertThat(list.selected()).isNull();
        list.search("");
        list.show(List.of());
        assertThat(list.emptyText()).isEqualTo("No plugins installed");
        assertThat(PluginKit.emptyTitle(list.view())).isEqualTo("No plugins installed");
    }

    @Test void theSelectionSurvivesARefreshAndTheKeysMoveToggleAndOpenTheMenu() {
        var a = TestRows.plugin("a.a", "A").row();
        var b = TestRows.plugin("a.b", "B").row();
        var c = TestRows.plugin("a.c", "C").row();
        list.show(List.of(a, b, c));
        list.select("a.b");
        list.show(List.of(a, b, c));
        assertThat(list.selected()).isEqualTo("a.b");
        PluginKit.key(list.view(), "DOWN");
        assertThat(list.selected()).isEqualTo("a.c");
        PluginKit.key(list.view(), "DOWN");
        assertThat(list.selected()).as("stays on the last card").isEqualTo("a.c");
        PluginKit.key(list.view(), "UP");
        assertThat(list.selected()).isEqualTo("a.b");
        events.clear();
        PluginKit.key(list.view(), "SPACE");
        assertThat(events).as("Space operates the row's switch, once").containsExactly(new UiEvent.Toggled("toggle:a.b", false));
        assertThat(list.selected()).isEqualTo("a.b");
        var menu = PluginKit.rowMenuOf(list.view());
        events.clear();
        PluginKit.key(list.view(), "shift F10");
        assertThat(PluginKit.labels(menu)).containsExactly("Open Settings", "Open Plugin Folder", "Open Data Folder", "-", "Remove…");
        PluginKit.key(list.view(), "CONTEXT_MENU");
        PluginKit.choose(menu, "Open Data Folder");
        assertThat(events).as("the focused row's menu names that row").containsExactly(new UiEvent.Action("data:a.b"));
        list.show(List.of(a, c));
        assertThat(list.selected()).as("the first card when the selected one is gone").isEqualTo("a.a");
    }

    @Test void aRightClickOpensThatRowsMenuWithoutSelectingItAndReportsOnlyItsAction() {
        list.show(List.of(TestRows.plugin("a.a", "A").row(), TestRows.plugin("a.b", "B").row()));
        assertThat(list.selected()).isEqualTo("a.a");
        selections = 0;
        var menu = PluginKit.rowMenu(list.view(), "name:a.b");
        assertThat(PluginKit.labels(menu)).containsExactly("Open Settings", "Open Plugin Folder", "Open Data Folder", "-", "Remove…");
        PluginKit.choose(menu, "Remove…");
        assertThat(events).containsExactly(new UiEvent.Action("remove:a.b"));
        assertThat(list.selected()).as("a context press never selects").isEqualTo("a.a");
        assertThat(selections).isZero();
    }

    /**
     * Review I3: the user's click becomes the declared selection, so a later host selection of the previously declared
     * row is applied, and the keys continue from it.
     */
    @Test void aHostSelectionAfterTheUsersClickIsApplied() {
        list.show(List.of(TestRows.plugin("a.a", "A").row(), TestRows.plugin("a.b", "B").row(), TestRows.plugin("a.c", "C").row()));
        assertThat(list.selected()).isEqualTo("a.a");
        PluginKit.click(list.view(), "name:a.b");
        assertThat(list.selected()).as("the user's click").isEqualTo("a.b");
        assertThat(((dev.jasper.ui.model.Nodes.ItemList) list.view().spec().root()).selectedIds()).as("declared too").containsExactly("a.b");
        list.select("a.a");
        assertThat(((dev.jasper.ui.model.Nodes.ItemList) list.view().spec().root()).selectedIds()).containsExactly("a.a");
        PluginKit.key(list.view(), "DOWN");
        assertThat(list.selected()).as("the kit row is the host's: Down moves on from A").isEqualTo("a.b");
    }

    @Test void pluginTextIsNeverMarkup() {
        list.show(List.of(TestRows.plugin("a.h", "<html><b>Bold").vendor("<html><i>Acme").row()));
        assertThat(list.listed()).containsExactly("<html><b>Bold  1.0.0");
        var name = KitTestSupport.require(PluginKit.laidOut(list.view()), "name:a.h", JLabel.class);
        assertThat(name.getText()).isEqualTo("<html><b>Bold");
        assertThat(name.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
    }
}
