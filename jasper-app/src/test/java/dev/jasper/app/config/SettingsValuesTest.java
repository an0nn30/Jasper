package dev.jasper.app.config;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SettingsValuesTest {
@Test void paletteBoundsIncludeBothEndpoints() {
    assertThat(new PaletteSettings(1).maxResults()).isEqualTo(1);
    assertThat(new PaletteSettings(20).maxResults()).isEqualTo(20);
    assertThatThrownBy(() -> new PaletteSettings(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PaletteSettings(21)).isInstanceOf(IllegalArgumentException.class);
}
}
