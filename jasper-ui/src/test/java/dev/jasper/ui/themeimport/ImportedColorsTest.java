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
    @Test void booleanColorValuesAreDiscardedAndPlatformDiagnosticsStayOncePerKey() {
        var result = ImportedColors.filter(Map.of(
            "Tree.forceFocusedSelectionForeground", "true",
            "Probe.foreground", " false ",
            "Probe.background", Map.of("os.mac", "true", "os.windows", "false", "os.default", "#12345680"),
            "Probe.borderColor", Map.of("os.mac", "true", "os.default", "false"),
            "Label.foreground", "Gray2",
            "Button.background", "if(true, #123456, #abcdef)",
            "Button.foreground", "if(false, Gray2, #abcdef)"));
        assertThat(result.colors()).doesNotContainKeys("Tree.forceFocusedSelectionForeground", "Probe.foreground", "Probe.borderColor")
            .containsEntry("Probe.background", Map.of("os.default", "#12345680"))
            .containsEntry("Label.foreground", "Gray2")
            .containsEntry("Button.background", "if(true, #123456, #abcdef)")
            .containsEntry("Button.foreground", "if(false, Gray2, #abcdef)");
        assertThat(result.warnings()).hasSize(4);
        for (String key : java.util.List.of("Tree.forceFocusedSelectionForeground", "Probe.foreground", "Probe.background", "Probe.borderColor"))
            assertThat(result.warnings().stream().filter(warning -> warning.contains(key)).toList()).hasSize(1);
    }

    @Test void booleanNamedColorsResolveBeforeLiteralFiltering() {
        var resolved = new ThemeLoader(Map.of("named.json", """
            {"name":"Named","colors":{"true":"#12345680","false":"#abcdef"},
             "ui":{"Button.background":"true","Button.foreground":{"os.mac":"false","os.default":"true"}}}
            """)::get, Map.of()).load("named.json");
        assertThat(resolved.ui()).containsEntry("Button.background", "#12345680")
            .containsEntry("Button.foreground", Map.of("os.mac", "#abcdef", "os.default", "#12345680"));
        assertThat(resolved.warnings()).isEmpty();
    }

}
