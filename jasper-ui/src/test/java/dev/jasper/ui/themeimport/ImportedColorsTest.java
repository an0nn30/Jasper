package dev.jasper.ui.themeimport;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ImportedColorsTest {
    @Test void geometryOverridesAreReportedAndDiscarded() {
        var result = ImportedColors.filter(Map.of("Button.arc", 50, "Button.background", "#123456"));
        assertThat(result.colors()).containsEntry("Button.background", "#123456").doesNotContainKey("Button.arc");
        assertThat(result.warnings()).anyMatch(s -> s.contains("Button.arc"));
    }
    @Test void palettesAndReferencesRetainAlphaButDelegateSpecificationsNeverPass() {
        var data = new LinkedHashMap<String, Object>();
        data.put("ColorPalette.accent", "#12345680");
        data.put("Button.background", "$ColorPalette.accent");
        data.put("Button.foreground", "com.formdev.flatlaf.ui.FlatButtonUI");
        data.put("Button.border", "1,2,3,4,#123456");
        data.put("ButtonUI", "#123456");
        data.put("Label.font", "#123456");
        var result = ImportedColors.filter(data);
        assertThat(result.colors()).containsOnlyKeys("ColorPalette.accent", "Button.background")
            .containsEntry("ColorPalette.accent", "#12345680").containsEntry("Button.background", "$ColorPalette.accent");
        assertThat(result.warnings()).hasSize(4);
    }
}
