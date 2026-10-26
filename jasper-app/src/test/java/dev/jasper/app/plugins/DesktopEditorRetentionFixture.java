package dev.jasper.app.plugins;

import dev.jasper.app.platform.DesktopEnvironment;
import java.util.Map;
import dev.jasper.app.appearance.*;
import dev.jasper.app.config.*;
import dev.jasper.app.platform.DesktopRenderFixtures;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.JasperTheme;
import java.awt.Point;
import java.nio.file.*;
import javax.swing.*;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.assertThat;

/** Test-only owner bridge: exercise the staged Editor's real documents, undo history and host subscriptions. */
public final class DesktopEditorRetentionFixture {
    private DesktopEditorRetentionFixture() { }
    public static void verify(Path home, Kit kit) throws Exception {
        Path settings = home.resolve("home/dev.jasper.editor/dev.jasper.editor.toml");
        Files.createDirectories(settings.getParent()); Files.writeString(settings,"color_scheme = 'jasper-meadow/light'\n");
        var selection = ThemeSelection.of(ThemeLibrary.bundledOnly(),"desktop-colors","desktop-colors",
            TerminalSchemeChoice.parse("slate-cobalt/high-contrast").orElseThrow(),(key,problem)->{throw new AssertionError(problem);});
        var themes = onEdtValue(() -> ThemeController.start(true,selection,kit,
            selected -> theme -> { FlatLafInstaller.install(theme,selected); return true; }, failure->{throw failure;}).controller());
        var appearance = onEdtValue(() -> new WorkspaceAppearanceFixture(themes.current()));
        var subscription = onEdtValue(() -> themes.subscribe((value,changed)->appearance.setPair(value)));
        try(var editor = new StagedEditorHost(home,true,appearance)) {
            var fixtures = DesktopRenderFixtures.read();
            onEdt(() -> {
                StagedEditorHost.call(editor.editorWindow(),"newUntitled");
                var area = editor.documentText();
                String original = "int retained = 42;\n".repeat(80);
                area.setText(original); StagedEditorHost.call(area,"discardAllEdits");
                area.append("// undo this edit\n"); area.select(4,12);
                var document = area.getDocument(); var caret = area.getCaret(); var tab = editor.selectedTab();
                var scroll = (JScrollPane)StagedEditorHost.call(tab,"scroll");
                scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
                scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
                var viewport = scroll.getViewport();
                var surface = (JComponent)StagedEditorHost.call(tab,"surface");
                surface.setSize(600,300); EditorEvidence.measure(area); EditorEvidence.layout(surface);
                viewport.setViewPosition(new Point(0,100));
                var position = viewport.getViewPosition();
                assertThat(position.y).isPositive();
                var colors = JasperTheme.builtIn("jasper-meadow").orElseThrow().generated(false).editor();
                for(var fixture : fixtures) {
                    var next = DesktopPalette.resolve(fixture.dark(),DesktopPalette.SourceMode.AUTO,DesktopEnvironment.from(Map.of("XDG_CURRENT_DESKTOP", "GNOME")),fixture.input()).theme();
                    for(int repeat=0;repeat<2;repeat++) {
                        themes.configure(fixture.dark(),selection,UiFontConfig.defaults(),next);
                        assertThat(editor.selectedTab()).isSameAs(tab);
                        assertThat(editor.documentText()).isSameAs(area);
                        assertThat(area.getDocument()).isSameAs(document);
                        assertThat(area.getCaret()).isSameAs(caret);
                        assertThat(area.getSelectedText()).isEqualTo("retained");
                        assertThat(area.getCaretPosition()).isEqualTo(12);
                        assertThat(scroll.getViewport()).isSameAs(viewport);
                        assertThat(viewport.getViewPosition()).isEqualTo(position);
                        assertThat(area.getText()).isEqualTo(original+"// undo this edit\n");
                        assertThat(area.getForeground()).isEqualTo(colors.foreground());
                        assertThat(StagedEditorHost.call(area,"canUndo")).isEqualTo(true);
                        var image = new java.awt.image.BufferedImage(600,300,java.awt.image.BufferedImage.TYPE_INT_RGB);
                        var g=image.createGraphics(); try {surface.paint(g);} finally {g.dispose();}
                        var corner=SwingUtilities.convertPoint(scroll,scroll.getWidth()-2,scroll.getHeight()-2,surface);
                        assertThat(image.getRGB(corner.x,corner.y)).isEqualTo(colors.background().getRGB());
                        assertThat(java.util.Arrays.stream(image.getRGB(0,0,600,300,null,0,600)).filter(pixel->pixel==colors.foreground().getRGB()).count())
                            .as("visible retained Editor text").isGreaterThan(10);
                    }
                }
                StagedEditorHost.call(area,"undoLastAction");
                assertThat(area.getText()).isEqualTo(original);
            });
            assertThat(editor.problems).isEmpty();
        } finally {onEdt(()->{subscription.close();themes.close();});}
    }
}
