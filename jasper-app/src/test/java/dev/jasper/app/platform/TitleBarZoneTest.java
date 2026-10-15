package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.TitleBarTheme;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The window's header row paints with its theme's title bar: a dark header on Slate Cobalt Light here. */
@ExtendWith(EdtTestExtension.class)
class TitleBarZoneTest {
    @Test void theHeaderAndWhatItHoldsPaintWithTheTitleBarsThemeAndTheRestWithTheChromes() throws Exception {
        String json;
        try (var in = JasperTheme.class.getResourceAsStream("/dev/jasper/ui/theme/slate-cobalt.json")) { json = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
        var light = JasperTheme.parse(json.replace("\"accent\": \"#2563C9\"",
            "\"surface.titlebar\": \"#27282E\", \"text.titlebar\": \"#DFE1E5\", \"accent\": \"#2563C9\""), "header").generated(false);
        var original = UIManager.getLookAndFeel();
        try {
            FlatLafInstaller.install(light, dev.jasper.ui.kit.Kit.DEFAULT);
            var header = new JPanel();
            ThemeZone.titleBar(header);
            var label = new JLabel();
            header.add(label);
            var elsewhere = new JLabel();
            assertThat(ThemeZone.theme(label)).isSameAs(TitleBarTheme.of(light));
            assertThat(ThemeZone.color(label, SemanticColor.SURFACE_APP)).isEqualTo(new Color(0x27282E));
            assertThat(ThemeZone.color(label, SemanticColor.TEXT_PRIMARY)).isEqualTo(new Color(0xDFE1E5));
            assertThat(ThemeZone.dark(label, false)).as("dark artwork on the dark header").isTrue();
            assertThat(ThemeZone.workspace(label)).as("the title bar is not the workspace").isFalse();
            assertThat(ThemeZone.color(elsewhere, SemanticColor.SURFACE_APP)).isEqualTo(light.semantic().color(SemanticColor.SURFACE_APP));
            assertThat(ThemeZone.dark(elsewhere, false)).isFalse();
        } finally { UIManager.setLookAndFeel(original); }
    }
}
