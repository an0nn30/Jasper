package dev.jasper.app.config;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SettingsValuesTest {
@Test void historyDefensivelyCopiesAndPreservesAnExplicitEmptyList() {
    var input = new java.util.ArrayList<>(java.util.List.of("ls"));
    var value = new HistorySettings(true, input);
    input.clear();
    assertThat(value.trivialCommands()).containsExactly("ls");
    assertThat(new HistorySettings(true, java.util.List.of()).trivialCommands()).isEmpty();
}
@Test void paletteBoundsIncludeBothEndpoints() {
    assertThat(new PaletteSettings(1).maxResults()).isEqualTo(1);
    assertThat(new PaletteSettings(20).maxResults()).isEqualTo(20);
    assertThatThrownBy(() -> new PaletteSettings(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PaletteSettings(21)).isInstanceOf(IllegalArgumentException.class);
}
}
