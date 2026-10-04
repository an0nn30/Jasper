package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import org.junit.jupiter.api.Test;

class SchemaPageTest {
    private final SettingsDraft draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "[window]\ntab_height = 44\n", true).values());
    private final List<String> navigated = new ArrayList<>();
    private final int[] changes = new int[1];
    private final PageContext context = new PageContext(draft, navigated::add, Runnable::run, (title, initial) -> Optional.empty(), () -> changes[0]++);

    private SchemaPage page() {
        return SchemaPage.page("window", "Window").parent("appearance-behavior").anchor("window-anchor")
            .section("Bars")
            .row(CoreSettings.WINDOW_STATUS_BAR)
            .row(CoreSettings.WINDOW_TAB_HEIGHT, Editors.integer("px"))
            .row(CoreSettings.TERMINAL_FONT_FAMILY, Editors.text()).note("Missing fonts fall back.")
            .row(CoreSettings.WINDOW_COLUMNS)
            .build(context);
    }

    @Test void rowsShowTheDraftAndStageEdits() {
        var page = page();
        page.component();
        var rows = page.rows();
        assertThat(rows).hasSize(4);
        var statusBar = (JCheckBox) rows.get(0).editor().component();
        assertThat(statusBar.isSelected()).isTrue();
        statusBar.doClick();
        assertThat(draft.value(CoreSettings.WINDOW_STATUS_BAR)).isFalse();
        assertThat(page.modified()).isTrue();
        page.reset();
        assertThat(page.modified()).isFalse();
        assertThat(statusBar.isSelected()).as("reset shows the saved value again").isTrue();
    }

    @Test void invalidValuesShowTheLoadersMessageAndAreNotStaged() {
        var page = page();
        page.component();
        @SuppressWarnings("unchecked") var font = (ValueEditor<String>) page.rows().get(2).editor();
        ((javax.swing.JTextField) font.component()).setText("   ");
        assertThat(page.rows().get(2).error().getText()).isEqualTo("Use a nonblank font name without NUL; using the default.");
        assertThat(page.rows().get(2).error().isVisible()).isTrue();
        assertThat(page.valid()).isFalse();
        assertThat(draft.modified(CoreSettings.TERMINAL_FONT_FAMILY)).isFalse();
        assertThat(changes[0]).isPositive();
        ((javax.swing.JTextField) font.component()).setText("Iosevka");
        assertThat(page.valid()).isTrue();
        assertThat(draft.value(CoreSettings.TERMINAL_FONT_FAMILY)).isEqualTo("Iosevka");
    }

    @Test void resetLinksAppearForNonDefaultValuesAndRemoveTheKey() {
        var page = page();
        page.component();
        JButton reset = page.rows().get(1).reset();
        assertThat(reset.isVisible()).as("44 is not the default 30").isTrue();
        assertThat(page.rows().get(0).reset().isVisible()).isFalse();
        reset.doClick();
        assertThat(draft.value(CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(30);
        assertThat(draft.edit().changes()).containsEntry(CoreSettings.WINDOW_TAB_HEIGHT, Optional.empty());
        assertThat(reset.isVisible()).isFalse();
    }

    @Test void commentsSayWhenAChangeAppliesAndIncludeNotes() {
        var page = page();
        page.component();
        assertThat(page.rows().get(3).comment().getText()).isEqualTo("Applies to new windows");
        assertThat(page.rows().get(2).comment().getText()).isEqualTo("Missing fonts fall back.");
        assertThat(page.rows().get(0).comment().isVisible()).isFalse();
    }

    @Test void searchTermsAndHighlightCoverLabelsDescriptionsAndKeywords() {
        var page = page();
        assertThat(page.searchTerms()).contains("Window", "Show status bar", "Tab height");
        assertThat(String.join(" ", page.searchTerms())).contains("footer");
        page.component();
        page.highlight("footer");
        assertThat(page.rows().get(0).box().highlighted()).isTrue();
        assertThat(page.rows().get(1).box().highlighted()).isFalse();
        page.highlight("");
        assertThat(page.rows().get(0).box().highlighted()).isFalse();
        assertThat(page.helpAnchor()).isEqualTo("window-anchor");
        assertThat(page.parent()).hasValue("appearance-behavior");
    }

    @Test void actionRowsReadTheirTextAndEnablementOnRefresh() {
        boolean[] enabled = {false};
        int[] runs = {0};
        var page = SchemaPage.page("system", "System Settings")
            .action(() -> "Rewrite old names.", new SchemaPage.Button("Migrate Config…", () -> runs[0]++, () -> enabled[0]))
            .build(context);
        page.component();
        JButton migrate = page.actionButtons().getFirst();
        assertThat(migrate.isEnabled()).isFalse();
        enabled[0] = true;
        page.refresh();
        assertThat(migrate.isEnabled()).isTrue();
        migrate.doClick();
        assertThat(runs[0]).isEqualTo(1);
    }

    @Test void groupPagesLinkToTheirChildren() {
        var child = page();
        var group = new GroupPage("appearance-behavior", "Appearance & Behavior", Optional.empty(), false, () -> List.of(child), context);
        var link = (JButton) ((javax.swing.JPanel) group.component()).getComponent(1);
        assertThat(link.getText()).isEqualTo("Window");
        link.doClick();
        assertThat(navigated).containsExactly("window");
        assertThat(group.modified()).isFalse();
        var tools = new GroupPage("tools", "Tools", Optional.empty(), true, List::of, context);
        assertThat(tools.hiddenWhenChildless()).isTrue();
    }

    @Test void aConditionEnablesARowOnlyWhileItHolds() {
        var page = SchemaPage.page("bars", "Bars")
            .row(CoreSettings.WINDOW_STATUS_BAR)
            .row(CoreSettings.WINDOW_STATUS_BAR_HEIGHT, Editors.integer("px")).enabledWhen(d -> d.value(CoreSettings.WINDOW_STATUS_BAR))
            .build(context);
        page.component();
        var height = page.rows().get(1).editor().component();
        assertThat(height.isEnabled()).isTrue();
        ((JCheckBox) page.rows().get(0).editor().component()).doClick();
        assertThat(height.isEnabled()).isFalse();
        assertThat(((java.awt.Container) height).getComponent(0).isEnabled()).as("its children too").isFalse();
        page.reset();
        assertThat(height.isEnabled()).isTrue();
    }

    @Test void aRowShownOnlyElsewhereKeepsItsSettingButBuildsNoControl() {
        var spec = SchemaPage.page("window", "Window")
            .row(CoreSettings.WINDOW_STATUS_BAR)
            .row(CoreSettings.WINDOW_NATIVE_DECORATIONS).shownWhen(false);
        assertThat(spec.settings()).containsExactly(CoreSettings.WINDOW_STATUS_BAR, CoreSettings.WINDOW_NATIVE_DECORATIONS);
        var page = spec.build(context);
        page.component();
        assertThat(page.rows()).hasSize(1);
        assertThat(page.searchTerms()).doesNotContain(CoreSettings.WINDOW_NATIVE_DECORATIONS.label());
    }

    @Test void aSectionWhoseRowsAreAllHiddenShowsNoHeader() {
        var spec = SchemaPage.page("window", "Window")
            .section("Bars")
            .row(CoreSettings.WINDOW_STATUS_BAR)
            .section("Elsewhere")
            .row(CoreSettings.WINDOW_NATIVE_DECORATIONS).shownWhen(false)
            .section("Empty")
            .section("Size")
            .row(CoreSettings.WINDOW_COLUMNS);
        var page = spec.build(context);
        assertThat(sectionTitles(page.component())).containsExactly("Bars", "Empty", "Size");
        assertThat(page.searchTerms()).contains("Bars", "Empty", "Size").doesNotContain("Elsewhere");
    }

    static List<String> sectionTitles(java.awt.Component component) {
        var titles = new ArrayList<String>();
        if (component instanceof TitledSeparator separator) titles.add(separator.title.getText());
        else if (component instanceof java.awt.Container container)
            for (var child : container.getComponents()) titles.addAll(sectionTitles(child));
        return titles;
    }

    private static void layOut(java.awt.Component component) {
        component.doLayout();
        if (component instanceof java.awt.Container container) for (var child : container.getComponents()) layOut(child);
    }

    private static List<javax.swing.JScrollPane> scrolls(java.awt.Component component) {
        var found = new ArrayList<javax.swing.JScrollPane>();
        if (component instanceof java.awt.Container container) for (var child : container.getComponents()) {
            if (child instanceof javax.swing.JScrollPane scroll) found.add(scroll); else found.addAll(scrolls(child));
        }
        return found;
    }

    @Test void aNarrowPageScrollsSidewaysRatherThanSquashingItsLists() {
        var page = SchemaPage.page("shell", "Shell")
            .row(CoreSettings.TERMINAL_SHELL_ARGS, Editors.textList())
            .row(CoreSettings.TERMINAL_ENV, Editors.table())
            .build(context);
        var viewport = new javax.swing.JScrollPane(page.component());
        for (int width : new int[]{300, 1400}) {
            viewport.setSize(width, 900);
            layOut(viewport);
            assertThat(scrolls(page.component())).as("at " + width + " px").hasSize(2)
                .allSatisfy(scroll -> assertThat(scroll.getSize()).isEqualTo(scroll.getPreferredSize()));
        }
        viewport.setSize(300, 900);
        layOut(viewport);
        assertThat(((javax.swing.Scrollable) page.component()).getScrollableTracksViewportWidth()).as("scrolls sideways").isFalse();
        viewport.setSize(1400, 900);
        layOut(viewport);
        assertThat(((javax.swing.Scrollable) page.component()).getScrollableTracksViewportWidth()).isTrue();
    }

    @Test void aLongDescriptionWrapsInsteadOfWideningThePage() {
        int plain = SchemaPage.page("a", "A").row(CoreSettings.TERMINAL_FONT_FAMILY, Editors.text()).note("Short.")
            .build(context).component().getPreferredSize().width;
        var page = SchemaPage.page("b", "B").row(CoreSettings.TERMINAL_FONT_FAMILY, Editors.text())
            .note("A long description that goes on and on, ".repeat(12)).build(context);
        assertThat(page.component().getPreferredSize().width).isEqualTo(plain);
        var viewport = new javax.swing.JScrollPane(page.component());
        viewport.setSize(500, 900);
        layOut(viewport);
        layOut(viewport);
        var comment = page.rows().get(0).comment();
        assertThat(comment.getWidth()).isLessThanOrEqualTo(500);
        assertThat(comment.getHeight()).as("several lines").isGreaterThan(3 * comment.getFontMetrics(comment.getFont()).getHeight());
    }

}
