package dev.jasper.app.plugins;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.contributions.ToolbarEntry;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.platform.PresentationIcon;
import dev.jasper.app.presentation.ChromeSpec;
import dev.jasper.app.presentation.Projections;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.workspace.DesktopTestSupport;
import dev.jasper.app.workspace.WorkspaceChrome;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Editor's New Editor Window toolbar button (user request 2026-10-19): the staged Editor in the application's real
 * plugin host places it with the {@code window.add} glyph, and both looks' headers draw it as a contributed button
 * with the action's title for its label and tip, the standard look's unified header and the Enterprise look's classic row.
 */
class EditorToolbarButtonTest {
    private static final String NEW_WINDOW = "dev.jasper.editor.new_window";
    @TempDir Path root;

    @Test @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void newEditorWindowIsAToolbarButtonInTheStandardAndTheEnterpriseLook() throws Exception {
        try (var editor = new StagedEditorHost(root, true)) {
            onEdt(() -> {
                assertThat(editor.contributions.toolbar()).containsExactly(new ToolbarEntry.Button(NEW_WINDOW));
                assertThat(editor.contributions.action(NEW_WINDOW).orElseThrow().icon()).isInstanceOfSatisfying(PresentationIcon.class,
                    named -> assertThat(named.name()).isEqualTo("window.add"));
                var enterprise = Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", new Projections.Host(Projections.Platform.LINUX, false));
                for (boolean classic : new boolean[] {false, true}) {
                    var themes = classic ? EnterpriseLook.controller(false) : ThemeTestSupport.controller(true);
                    try {
                        var look = classic ? WorkspaceChrome.of(enterprise.workspace(), enterprise.title()) : WorkspaceChrome.STANDARD;
                        var window = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, look);
                        window.connectContributions(editor.contributions, UiState.inMemory(), false);
                        var buttons = buttonsTipped(window, "New Editor Window");
                        assertThat(buttons).as((classic ? "Enterprise" : "standard") + " look: one New Editor Window button").hasSize(1);
                        var button = buttons.getFirst();
                        assertThat(button.getText()).as("the label").isEqualTo("New Editor Window");
                        assertThat(button.getIcon()).as("its glyph").isInstanceOfSatisfying(PresentationIcon.class,
                            named -> assertThat(named.name()).isEqualTo("window.add"));
                        assertThat(button.getParent()).as("placed in the toolbar").isInstanceOf(javax.swing.JToolBar.class);
                    } finally {
                        DesktopTestSupport.closeOwnersNow();
                        themes.close();
                        if (classic) EnterpriseLook.restoreDefault();
                    }
                }
            });
        }
    }

    private static List<JButton> buttonsTipped(Container root, String tip) {
        var found = new ArrayList<JButton>();
        var pending = new ArrayDeque<Component>(List.of(root));
        while (!pending.isEmpty()) {
            Component next = pending.poll();
            if (next instanceof JButton button && tip.equals(button.getToolTipText())) found.add(button);
            if (next instanceof Container container) pending.addAll(List.of(container.getComponents()));
        }
        return found;
    }
}
