package dev.jasper.editor.ui;

import dev.jasper.editor.syntax.Languages;
import dev.jasper.editor.testsupport.Kits.RecordingKit;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static dev.jasper.editor.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_PYTHON;

/**
 * The Language picker on kit descriptors (Plan 2 Task 9a). Baseline f16ae32f: Enter in the filter or the list chose the
 * selection, a double click chose, Up/Down in the filter moved the selection, and there was no default button.
 */
class LanguagePickerTest {
    @Test void filtersByNameAndChoosesTheSelectedStyle() throws Exception {
        Languages.register();
        edt(() -> {
            List<String> chosen = new ArrayList<>();
            var picker = new LanguagePicker(new FakeKit("dev.jasper.editor"), SYNTAX_STYLE_PYTHON, chosen::add, () -> { });
            assertThat(picker.selectedName()).as("the current language starts selected").isEqualTo("Python");
            assertThat(list(picker).selectedIds()).containsExactly(LanguagePicker.row(SYNTAX_STYLE_PYTHON));
            picker.filter("tom");
            assertThat(picker.visibleNames()).containsExactly("TOML");
            picker.chooseSelected();
            assertThat(chosen).containsExactly(Languages.TOML);
            picker.filter("zzz");
            assertThat(picker.visibleNames()).isEmpty();
            picker.chooseSelected();
            assertThat(chosen).hasSize(1);
        });
    }

    @Test void typingFiltersTheListAndSelectsItsFirstMatchThroughTheView() throws Exception {
        Languages.register();
        edt(() -> {
            List<String> chosen = new ArrayList<>();
            var picker = new LanguagePicker(new FakeKit("dev.jasper.editor"), SYNTAX_STYLE_PYTHON, chosen::add, () -> { });
            type(picker.view(), LanguagePicker.FILTER, "TO");
            assertThat(field(picker).value()).as("the typed text stays in the field").isEqualTo("TO");
            assertThat(rows(picker)).allMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("to"));
            assertThat(list(picker).selectedIds()).as("the first match is selected").containsExactly(list(picker).items().getFirst().id());
            press(picker.view(), field(picker).trailingAction());
            assertThat(chosen).as("Enter in the filter chooses the selection").hasSize(1);
            assertThat(Languages.name(chosen.getFirst())).isEqualTo(rows(picker).getFirst());
        });
    }

    @Test void theFilterMovesTheListAndEnterOrADoubleClickOnARowChoosesIt() throws Exception {
        Languages.register();
        edt(() -> {
            List<String> chosen = new ArrayList<>();
            var picker = new LanguagePicker(new FakeKit("dev.jasper.editor"), SYNTAX_STYLE_PYTHON, chosen::add, () -> { });
            assertThat(field(picker).kind()).isEqualTo(Nodes.FieldKind.SEARCH);
            assertThat(field(picker).listId()).as("Up/Down in the filter move the list while the field keeps focus").isEqualTo(LanguagePicker.LIST);
            assertThat(field(picker).trailingAction()).isEqualTo(LanguagePicker.CHOOSE);
            assertThat(list(picker).activateAction()).as("a double click (and Enter on a row) chooses").isEqualTo(LanguagePicker.CHOOSE);
            assertThat(list(picker).enterAction()).isIn("", LanguagePicker.CHOOSE);
            assertThat(list(picker).visibleRows()).hasValue(14);
            assertThat(primaries(picker.view())).as("no default button, as at baseline").isEmpty();
            assertThat(form(picker.view()).actions()).isEmpty();
            String toml = LanguagePicker.row(Languages.TOML);
            emit(picker.view(), new UiEvent.Selected(LanguagePicker.LIST, Set.of(toml)));
            assertThat(picker.selectedName()).isEqualTo("TOML");
            assertThat(list(picker).selectedIds()).containsExactly(toml);
            press(picker.view(), list(picker).activateAction());
            assertThat(chosen).containsExactly(Languages.TOML);
        });
    }

    @Test void escapeClosesWithoutChoosing() throws Exception {
        Languages.register();
        edt(() -> {
            List<String> chosen = new ArrayList<>();
            int[] cancelled = {0};
            var picker = new LanguagePicker(new FakeKit("dev.jasper.editor"), SYNTAX_STYLE_PYTHON, chosen::add, () -> cancelled[0]++);
            escape(picker.view());
            assertThat(cancelled[0]).isEqualTo(1);
            assertThat(chosen).isEmpty();
        });
    }

    @Test void rowsShowLiteralNamesAndAClosedPickerNeverTouchesItsView() throws Exception {
        Languages.register();
        edt(() -> {
            List<String> chosen = new ArrayList<>();
            var kit = new RecordingKit("dev.jasper.editor");
            var picker = new LanguagePicker(kit, SYNTAX_STYLE_PYTHON, chosen::add, () -> { });
            assertThat(rows(picker)).hasSize(Languages.all().size()).contains("Python", "TOML");
            assertThat(rows(picker).getFirst()).as("plain text first, as before").isEqualTo(Languages.name(Languages.all().getFirst().style()));
            var view = picker.view();
            picker.close();
            picker.filter("py");
            picker.chooseSelected();
            assertThat(kit.lateCalls).isEmpty();
            assertThat(chosen).isEmpty();
            assertThat(closed(view)).isTrue();
        });
    }

    private static Nodes.Field field(LanguagePicker picker) { return node(picker.view(), LanguagePicker.FILTER, Nodes.Field.class); }
    private static Nodes.ItemList list(LanguagePicker picker) { return node(picker.view(), LanguagePicker.LIST, Nodes.ItemList.class); }
    private static List<String> rows(LanguagePicker picker) {
        return list(picker).items().stream().map(ListItem::title).map(title -> ((Nodes.Text) title.getFirst()).text()).toList();
    }
}
