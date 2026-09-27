package dev.jasper.editor.ui;

import dev.jasper.editor.syntax.Languages;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_PYTHON;

class LanguagePickerTest {
    @Test void filtersByNameAndChoosesTheSelectedStyle() {
        Languages.register();
        List<String> chosen = new ArrayList<>();
        var picker = new LanguagePicker(SYNTAX_STYLE_PYTHON, chosen::add);
        assertThat(picker.selectedName()).as("the current language starts selected").isEqualTo("Python");
        picker.filter("tom");
        assertThat(picker.visibleNames()).containsExactly("TOML");
        picker.chooseSelected();
        assertThat(chosen).containsExactly(Languages.TOML);
        picker.filter("zzz");
        assertThat(picker.visibleNames()).isEmpty();
        picker.chooseSelected();
        assertThat(chosen).hasSize(1);
    }
}
