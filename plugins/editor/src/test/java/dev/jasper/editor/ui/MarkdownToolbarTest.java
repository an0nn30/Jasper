package dev.jasper.editor.ui;

import dev.jasper.editor.markdown.MarkdownMode;
import dev.jasper.sdk.ui.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MarkdownToolbarTest {
    private static Presentation presentation(boolean classicBase, Presentation.Icons icons) {
        return new Presentation(classicBase, icons, Presentation.ToolWindows.RAILS, Presentation.TitleBar.NATIVE,
            Presentation.Menus.WINDOW, Presentation.ToolbarSearch.FIELD, Optional.empty(), Optional.empty(), 13, 40);
    }
    @Test void modeTogglesAreExclusiveNamedAndChooseArtworkByIconsAlone() {
        for (boolean base : List.of(false, true)) for (var icons : Presentation.Icons.values()) for (var mode : MarkdownMode.values()) {
            var row = MarkdownToolbar.node(mode, true, true, true, presentation(base, icons));
            assertThat(row.role()).isEqualTo(Nodes.LayoutRole.INLINE);
            var fill = (Nodes.Text) row.children().getFirst();
            assertThat(fill.text()).isEmpty();
            assertThat(fill.role()).isEqualTo(Nodes.TextRole.CAPTION);
            var buttons = row.children().stream().filter(Nodes.Button.class::isInstance).map(Nodes.Button.class::cast).toList();
            assertThat(buttons).extracting(Nodes.Button::id).containsExactlyElementsOf(mode == MarkdownMode.SPLIT
                ? List.of("markdown.source", "markdown.split", "markdown.preview", "markdown.scroll-sync")
                : List.of("markdown.source", "markdown.split", "markdown.preview"));
            assertThat(buttons.subList(0, 3)).filteredOn(b -> b.selected().orElseThrow()).singleElement()
                .extracting(Nodes.Button::id).isEqualTo("markdown." + mode.key());
            for (int i = 0; i < buttons.size(); i++) {
                var button = buttons.get(i);
                assertThat(button.kind()).isEqualTo(Nodes.ButtonKind.GHOST);
                assertThat(button.label()).isEmpty();
                assertThat(button.action()).isEqualTo(button.id());
                assertThat(button.icon()).isEqualTo(button.id().replace("markdown.", "editor.") + (icons == Presentation.Icons.CLASSIC ? ".classic" : ""));
                assertThat(button.tooltip()).isEqualTo(List.of("Source", "Source and preview", "Preview", "Synchronize scrolling").get(i));
                assertThat(button.state().enabled()).isTrue();
            }
        }
    }
    @Test void ineligibleIsHiddenAndLoadingDisablesAllControlsWhileLinkedStateIsPerTab() {
        var look = presentation(false, Presentation.Icons.NEW_UI);
        assertThat(MarkdownToolbar.node(MarkdownMode.SOURCE, true, true, false, look).state().visible()).isFalse();
        for (boolean linked : List.of(false, true)) {
            var row = MarkdownToolbar.node(MarkdownMode.SPLIT, linked, false, true, look);
            for (var child : row.children()) if (child instanceof Nodes.Button b) assertThat(b.state().enabled()).isFalse();
            assertThat(((Nodes.Button) row.children().getLast()).selected()).contains(linked);
        }
    }
}
