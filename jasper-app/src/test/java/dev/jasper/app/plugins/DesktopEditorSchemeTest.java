package dev.jasper.app.plugins;

import dev.jasper.app.platform.DesktopEnvironment;
import dev.jasper.app.appearance.*;
import dev.jasper.app.config.*;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.theme.JasperTheme;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.*;

/** The actual bundled Editor retains a separately saved content scheme through application desktop transactions. */
class DesktopEditorSchemeTest {
    @TempDir Path home;
    @Test void runtimeDesktopChangesRetainTheIndependentEditorAndFixedTerminalSchemes() throws Exception {
        Path settings = home.resolve("home/dev.jasper.editor/dev.jasper.editor.toml");
        Files.createDirectories(settings.getParent());
        Files.writeString(settings, "color_scheme = 'jasper-meadow/light'\n");
        var selection = ThemeSelection.of(ThemeLibrary.bundledOnly(), "desktop-colors", "desktop-colors",
            TerminalSchemeChoice.parse("slate-cobalt/high-contrast").orElseThrow(), (key, problem) -> { throw new AssertionError(problem); });
        var themes = onEdtValue(() -> ThemeController.start(true, selection, Kit.DEFAULT,
            kit -> theme -> true, failure -> { throw failure; }).controller());
        var appearance = onEdtValue(() -> new WorkspaceAppearanceFixture(themes.current()));
        var subscription = onEdtValue(() -> themes.subscribe((value, changed) -> appearance.setPair(value)));
        var fixtures = dev.jasper.app.platform.DesktopRenderFixtures.read();
        try (var editor = new StagedEditorHost(home, true, appearance)) {
            onEdt(() -> {
                StagedEditorHost.call(editor.editorWindow(), "newUntitled");
                var area = editor.documentText();
                try {
                    var expected = JasperTheme.builtIn("jasper-meadow").orElseThrow().generated(false).editor();
                    assertThat(background(editor)).isEqualTo(expected.background().getRGB() & 0xffffff);
                    area.setText("int preserved = 1;\n"); area.select(4, 13);
                    var terminal = themes.current().palette();
                    for (var fixture : fixtures) {
                        boolean dark = fixture.dark();
                        var result = DesktopPalette.resolve(dark, DesktopPalette.SourceMode.AUTO,
                            DesktopEnvironment.from(Map.of("XDG_CURRENT_DESKTOP", "KDE")), fixture.input());
                        themes.configure(dark, selection, UiFontConfig.defaults(), result.theme());
                        assertThat(background(editor)).isEqualTo(expected.background().getRGB() & 0xffffff);
                        assertThat(area.getForeground()).isEqualTo(expected.foreground());
                        assertThat(area.getText()).isEqualTo("int preserved = 1;\n");
                        assertThat(area.getSelectedText()).isEqualTo("preserved");
                        assertThat(themes.current().palette()).isEqualTo(terminal);
                    }
                } finally {
                    StagedEditorHost.call(area,"setMarkOccurrences",new Class<?>[]{boolean.class},false);
                }
            });
            assertThat(editor.problems).isEmpty();
        } finally { onEdt(() -> { subscription.close(); themes.close(); }); }
    }
    private static int background(StagedEditorHost editor) {
        var surface = (javax.swing.JComponent) StagedEditorHost.call(editor.selectedTab(), "surface");
        var scroll = (javax.swing.JScrollPane) StagedEditorHost.call(editor.selectedTab(), "scroll");
        scroll.setHorizontalScrollBarPolicy(javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scroll.setVerticalScrollBarPolicy(javax.swing.JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        EditorEvidence.measure(editor.documentText());
        surface.setSize(600, 300); EditorEvidence.layout(surface);
        var image = new java.awt.image.BufferedImage(600, 300, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { surface.paint(graphics); } finally { graphics.dispose(); }
        var corner = javax.swing.SwingUtilities.convertPoint(scroll, scroll.getWidth() - 2, scroll.getHeight() - 2, surface);
        return image.getRGB(corner.x, corner.y) & 0xffffff;
    }

}
