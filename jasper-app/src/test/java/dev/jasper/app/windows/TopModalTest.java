package dev.jasper.app.windows;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class TopModalTest {
    /** Each window's showing modal dialogs, oldest first. */
    private static Function<String, List<String>> tree(Map<String, List<String>> children) {
        return window -> children.getOrDefault(window, List.of());
    }

    @Test void withoutAModalDialogThePickerBelongsToItsOwner() {
        assertThat(TopModal.over("terminal", tree(Map.of()))).isEqualTo("terminal");
    }

    @Test void aModalDialogOverTheOwnerOwnsThePicker() {
        assertThat(TopModal.over("terminal", tree(Map.of("terminal", List.of("New File Server"))))).isEqualTo("New File Server");
    }

    @Test void theNewestDialogAtTheDeepestLevelWins() {
        var children = tree(Map.of(
            "terminal", List.of("older", "Settings"),
            "Settings", List.of("Keymap edit")));
        assertThat(TopModal.over("terminal", children)).isEqualTo("Keymap edit");
    }
}
