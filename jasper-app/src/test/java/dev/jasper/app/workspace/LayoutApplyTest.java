package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.persistence.ToolWindows;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.persistence.WorkspaceFile;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Full windows plan 4: applying a layout replaces a window's tabs, panes and tool windows, after every tab's guard
 * agrees; its bounds stay.
 */
class LayoutApplyTest {
    private TestWindows windows;

    @AfterEach void cleanup() throws Exception {
        if (windows != null) edt(() -> windows.manager.closeAll());
        closeOwners();
    }

    /** A layout of one tab per item, the first showing, with {@code tools}. */
    private static WorkspaceFile.WindowRecord layout(Map<TabId, WorkspaceFile.TabRecord> items, List<TabId> order, ToolWindows tools) {
        var tabs = order.stream().map(item -> new ParentTab(ParentTabId.random(), Layout.of(item))).toList();
        return new WorkspaceFile.WindowRecord(Optional.empty(), 0, new ParentTabs(tabs, tabs.getFirst().id()), items, tools);
    }

    private static WorkspaceFile.TabRecord.Terminal shell(String title) {
        return new WorkspaceFile.TabRecord.Terminal(title, HOME.toString(), "");
    }

    /** A window of a terminal tab A and a plugin's tab B, placed. */
    private JasperWindow windowOfTwo() {
        JasperWindow window = windows.manager.ensureWindow("cold", null).orElseThrow();
        windows.model.tabs().open("dev.example.editor", "b", "B", window.id(), new JPanel());
        window.placed(new JasperWindow.Placement(Optional.of(new UiState.Bounds(40, 60, 1200, 800)), 0));
        return window;
    }

    @Test void applyingALayoutReplacesTheWindowsTabsAndToolWindows() throws Exception {
        edt(() -> windows = new TestWindows());
        TabId c = TabId.random();
        TabId d = TabId.random();
        var layout = layout(Map.of(c, shell("C"), d, shell("D")), List.of(c, d), new ToolWindows(false, false, Map.of()));
        edt(() -> {
            JasperWindow window = windowOfTwo();
            List<Tab> before = window.content().tabs();
            JasperWindow.Placement placement = window.placement();
            assertThat(windows.manager.applyLayout(window, layout)).isEqualTo(WindowManager.LayoutApplied.APPLIED);
            assertThat(window.closed()).as("the window stays").isFalse();
            assertThat(windows.manager.windows()).containsExactly(window);
            assertThat(window.content().tabs()).extracting(Tab::title).containsExactly("C", "D");
            assertThat(window.content().tabs()).extracting(Tab::id).as("fresh ids").doesNotContain(c, d);
            assertThat(before).allSatisfy(tab -> assertThat(tab.closed()).isTrue());
            assertThat(window.content().toolWindows().railVisible()).isFalse();
            assertThat(window.content().railVisible()).isFalse();
            assertThat(window.placement()).as("its bounds stay").isEqualTo(placement);
            // A layout is a template: applied again, here or elsewhere, it never clashes on ids.
            JasperWindow other = windows.manager.openWindow(null).orElseThrow();
            assertThat(windows.manager.applyLayout(other, layout)).isEqualTo(WindowManager.LayoutApplied.APPLIED);
            assertThat(windows.manager.applyLayout(window, layout)).isEqualTo(WindowManager.LayoutApplied.APPLIED);
            assertThat(other.content().tabs()).extracting(Tab::title).containsExactly("C", "D");
            assertThat(window.content().tabs()).extracting(Tab::title).containsExactly("C", "D");
        });
        windows.launch();
    }

    @Test void applyingALayoutAGuardRefusesChangesNothing() throws Exception {
        edt(() -> windows = new TestWindows());
        TabId c = TabId.random();
        var layout = layout(Map.of(c, shell("C")), List.of(c), new ToolWindows(false, true, Map.of()));
        edt(() -> {
            JasperWindow window = windowOfTwo();
            windows.model.tabs().guard("dev.example.editor", (tabs, batch) -> CompletableFuture.completedFuture(false));
            List<Tab> before = window.content().tabs();
            ToolWindows tools = window.content().toolWindows().snapshot();
            assertThat(windows.manager.applyLayout(window, layout)).isEqualTo(WindowManager.LayoutApplied.REFUSED);
            assertThat(window.content().tabs()).isEqualTo(before);
            assertThat(before).noneMatch(Tab::closed);
            assertThat(window.content().toolWindows().snapshot()).isEqualTo(tools);
        });
        windows.launch();
    }

    @Test void aLayoutTabWhosePluginIsMissingComesBackAsAPlaceholder() throws Exception {
        edt(() -> windows = new TestWindows());
        TabId c = TabId.random();
        TabId note = TabId.random();
        var layout = layout(Map.of(c, shell("C"), note, new WorkspaceFile.TabRecord.Content("todo.md", "dev.example.notes", "note",
            Map.of("name", "todo"))), List.of(c, note), ToolWindows.BUILT_IN);
        edt(() -> {
            JasperWindow window = windowOfTwo();
            assertThat(windows.manager.applyLayout(window, layout)).isEqualTo(WindowManager.LayoutApplied.APPLIED);
            assertThat(window.content().tabs()).extracting(Tab::title).containsExactly("C", "todo.md");
            assertThat(window.content().tabs().getLast().kind()).isEqualTo(Tab.Kind.PLACEHOLDER);
        });
        windows.launch();
    }

    /** Ruling 3: a layout that fails after the guards agreed is reported as failed; the window keeps an empty workspace. */
    @Test void aLayoutThatFailsHalfwayIsReportedAsFailed() throws Exception {
        edt(() -> windows = new TestWindows());
        TabId c = TabId.random();
        var layout = layout(Map.of(c, shell("C")), List.of(c), new ToolWindows(false, true, Map.of()));
        edt(() -> {
            JasperWindow window = windowOfTwo();
            // Closing the plugin's tab fails after its guard agreed.
            window.content().tabs().getLast().onClosed(() -> { throw new IllegalStateException("closing failed"); });
            assertThat(windows.manager.applyLayout(window, layout)).isEqualTo(WindowManager.LayoutApplied.FAILED);
            assertThat(window.closed()).as("the window stays").isFalse();
            assertThat(window.content().toolWindows().railVisible()).as("it takes the layout's tool windows").isFalse();
        });
        windows.launch();
    }
}
