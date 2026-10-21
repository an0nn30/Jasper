package dev.jasper.app.plugins;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.sdk.testing.FakePresentations;
import java.nio.file.Path;
import javax.swing.AbstractButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static dev.jasper.app.plugins.StagedEditorHost.call;
import static dev.jasper.app.plugins.WorkspaceContentRouteTest.named;
import static org.assertj.core.api.Assertions.*;

/** Actual staged plugin controls through the host kit, including narrow layouts and both icon sets. */
class MarkdownToolbarRenderTest {
    @TempDir Path home;
    @Test void classicAndModernModeControlsRemainReachableAtNarrowWidths() throws Exception {
        for(boolean dark:new boolean[]{false,true})for(boolean classic:new boolean[]{false,true}) {
            var theme=onEdtValue(() -> ThemeTestSupport.controller(true));
            onEdt(() -> theme.configure(dark,dev.jasper.app.config.TerminalSchemeChoice.MATCH));
            try(var editor=new StagedEditorHost(home.resolve(""+dark+classic),dark)) {
                onEdt(() -> {
                    editor.host.presentation=classic?FakePresentations.CLASSIC:FakePresentations.STANDARD;
                    call(editor.editorWindow(),"newUntitled");
                    editor.documentText().setText("# Markdown\n\nA document toolbar.\n");
                    call(call(editor.selectedTab(),"session"),"setStyle",new Class<?>[]{String.class},"text/markdown");
                    var view=editor.contributions.tabs().hosted().getFirst().component();
                    named(view,"markdown.split",AbstractButton.class).doClick();
                    for(int width:new int[]{260,760}) {
                        String variant=(dark?"dark":"light")+(classic?"-classic":"-modern");
                        EditorEvidence.capture(variant,"markdown-width-"+width,view,width,280);
                        for(String id:new String[]{"source","split","preview","scroll-sync"}) {
                            var button=named(view,"markdown."+id,AbstractButton.class);
                            assertThat(button.isVisible()).isTrue();assertThat(button.getWidth()).isPositive();
                            var bounds=javax.swing.SwingUtilities.convertRectangle(button.getParent(),button.getBounds(),view);
                            assertThat(bounds.x).isGreaterThanOrEqualTo(0);assertThat(bounds.x+bounds.width).isLessThanOrEqualTo(width);
                            assertThat(button.getAccessibleContext().getAccessibleName()).isNotBlank();
                        }
                    }
                    assertThat(named(view,"markdown.split",AbstractButton.class).isSelected()).isTrue();
                });
                assertThat(editor.problems).isEmpty();
            } finally {onEdt(() -> {theme.close();ThemeTestSupport.controller(true);});}
        }
    }
}
