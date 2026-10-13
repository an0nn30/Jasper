package dev.jasper.app.workspace;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.app.config.AnimationSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class WorkspaceMetricsTest {
    /** Main's Alacritty-style tabs (merged 2026-10-05): equal widths, measured 475/425 ms motion, no switch motion. */
    @Test void approvedTabAndDockGeometryIsModuleOwnedAndUnscaled() {
        var metrics = DesignMetrics.DEFAULT;
        assertThat(metrics.value(DesignMetrics.Role.TAB_OPEN_MILLIS)).isEqualTo(475);
        assertThat(metrics.value(DesignMetrics.Role.TAB_CLOSE_MILLIS)).isEqualTo(425);
        assertThat(metrics.value(DesignMetrics.Role.TAB_SWITCH_MILLIS)).isEqualTo(0);
        assertThat(AnimationSettings.defaults()).isEqualTo(new AnimationSettings(true, 475, 425, 0));
        assertThat(metrics.value(DesignMetrics.Role.WORKSPACE_SURFACE_ARC)).isEqualTo(16);
    }

    /** Each native dimension has its own name, carrying the exact pre-migration value. */
    @Test void nativeGeometryKeepsItsExactValuesUnderItsOwnNames() {
        var expected = new java.util.LinkedHashMap<DesignMetrics.Role, Integer>();
        expected.putAll(Map.of(DesignMetrics.Role.TAB_REVEAL_INSET, 100, DesignMetrics.Role.TAB_CLOSE_FIT_WIDTH, 80,
            DesignMetrics.Role.TAB_CLOSE_GUTTER, 28, DesignMetrics.Role.TAB_COMPACT_GUTTER, 4, DesignMetrics.Role.TAB_CLOSE_WIDTH, 16,
            DesignMetrics.Role.TAB_CLOSE_TRAILING, 6, DesignMetrics.Role.TAB_DIVIDER, 1, DesignMetrics.Role.TAB_REORDER_THRESHOLD, 5));
        expected.putAll(Map.of(DesignMetrics.Role.STATUS_RUNNING_DOT, 5, DesignMetrics.Role.STATUS_PROGRESS_BAR_HEIGHT, 8,
            DesignMetrics.Role.STATUS_PROGRESS_MIN_HEIGHT, 24, DesignMetrics.Role.TOOL_HEADER_LEADING_PADDING, 10,
            DesignMetrics.Role.TOOL_HEADER_TRAILING_PADDING, 6, DesignMetrics.Role.TOOL_HEADER_DETAIL_GAP, 6,
            DesignMetrics.Role.TOOL_HEADER_ACTIVE_DOT, 6));
        expected.putAll(Map.of(DesignMetrics.Role.RAIL_BUTTON_ARC, 8, DesignMetrics.Role.NATIVE_TOOLBAR_HOVER_ARC, 6,
            DesignMetrics.Role.NATIVE_TOOLBAR_DISABLED_PERCENT, 38, DesignMetrics.Role.RAIL_BADGE_DOT, 6,
            DesignMetrics.Role.FIND_GAP, 6, DesignMetrics.Role.FIND_SIDE_PADDING, 6, DesignMetrics.Role.FIND_CONTROL_GAP, 4,
            DesignMetrics.Role.FIND_CLOSE_SPACER, 16, DesignMetrics.Role.TITLE_SEARCH_PADDING, 8, DesignMetrics.Role.TITLE_SEARCH_GAP, 6));
        expected.putAll(Map.of(DesignMetrics.Role.NATIVE_MENU_ARROW_GAP, 3, DesignMetrics.Role.NOTICE_VERTICAL_PADDING, 8,
            DesignMetrics.Role.NOTICE_HORIZONTAL_PADDING, 12, DesignMetrics.Role.NOTICE_BUTTON_GAP, 8,
            DesignMetrics.Role.BREADCRUMB_COLLAPSED_HEIGHT, 16, DesignMetrics.Role.DOCK_DRAG_THRESHOLD, 5));
        expected.forEach((role, value) -> assertThat(DesignMetrics.DEFAULT.value(role)).as(role.name()).isEqualTo(value));
    }

    /**
     * Native adapters must not borrow a value-coincident kit/icon/dock role: changing that role would silently move
     * folder-tab, rail, status, header or find-bar geometry. Kit roles stay with kit controls.
     */
    @Test void nativeAdaptersDoNotBorrowGenericKitRoles() throws Exception {
        Path root = Path.of(System.getProperty("jasper.repoRoot"));
        var generic = List.of("ICON_RAIL", "ICON_GAP", "BUTTON_GAP", "COLUMN_GAP", "PANEL_ACTION", "FIELD_HORIZONTAL_PADDING",
            "PORT_PREFIX_GAP", "STATUS_DOT", "CONTROL_RADIUS", "DOCK_DRAG_THRESHOLD", "MENU_PADDING", "MENU_ITEM_PADDING");
        var workspace = root.resolve("jasper-app/src/main/java/dev/jasper/app/workspace");
        var swing = root.resolve("jasper-ui/src/main/java/dev/jasper/ui/swing");
        var files = List.of(workspace.resolve("WindowTabs.java"), workspace.resolve("WindowStatusBar.java"),
            workspace.resolve("StatusProgressView.java"), workspace.resolve("ToolWindowHeader.java"), workspace.resolve("FindBar.java"),
            workspace.resolve("TerminalPane.java"), workspace.resolve("BadgedIcon.java"), workspace.resolve("PathBreadcrumbs.java"),
            workspace.resolve("ToolStripe.java"), workspace.resolve("WindowChrome.java"), workspace.resolve("WindowToolbar.java"),
            workspace.resolve("WindowHeader.java"), swing.resolve("NativeChromePainting.java"), swing.resolve("NativeControlStyles.java"));
        for (Path file : files) {
            String text = Files.readString(file);
            for (String role : generic)
                assertThat(text).as(root.relativize(file) + " borrows " + role).doesNotContain("DesignMetrics.Role." + role + ")");
        }
        // Plan 3 Task 10: the running dot, tab strip and toolbar painting live in the kit's native chrome painters.
        assertThat(Files.readString(swing.resolve("NativeChromePainting.java"))).contains("STATUS_RUNNING_DOT", "TAB_DIVIDER",
            "SURFACE_TABSTRIP", "SURFACE_TERMINAL", "TOOLBAR_ACTION_ICON", "NATIVE_HOVER_OVERLAY_PERCENT", "POPOVER_SHADOW_BLUR");
        var required = Map.of("WindowStatusBar.java", List.of("NativeChromePainting.statusDot"),
            "StatusProgressView.java", List.of("STATUS_PROGRESS_BAR_HEIGHT", "STATUS_PROGRESS_MIN_HEIGHT"),
            "WindowTabs.java", List.of("TAB_REVEAL_INSET", "TAB_CLOSE_FIT_WIDTH", "TAB_CLOSE_GUTTER", "TAB_COMPACT_GUTTER",
                "TAB_CLOSE_WIDTH", "TAB_CLOSE_TRAILING", "NativeChromePainting.tabStrip", "TAB_REORDER_THRESHOLD"),
            "ToolWindowHeader.java", List.of("TOOL_HEADER_LEADING_PADDING", "TOOL_HEADER_TRAILING_PADDING",
                "TOOL_HEADER_DETAIL_GAP", "TOOL_HEADER_ACTIVE_DOT"));
        for (var entry : required.entrySet())
            assertThat(Files.readString(workspace.resolve(entry.getKey()))).as(entry.getKey()).contains(entry.getValue());
    }
}
