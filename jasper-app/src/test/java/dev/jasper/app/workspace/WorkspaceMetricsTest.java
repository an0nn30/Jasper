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
    @Test void approvedFolderAndDockGeometryIsModuleOwnedAndUnscaled() {
        var metrics = DesignMetrics.DEFAULT;
        assertThat(metrics.value(DesignMetrics.Role.TAB_MIN_WIDTH)).isEqualTo(160);
        assertThat(metrics.value(DesignMetrics.Role.TAB_MAX_WIDTH)).isEqualTo(240);
        assertThat(metrics.value(DesignMetrics.Role.TAB_ACCENT_WIDTH)).isEqualTo(64);
        assertThat(metrics.value(DesignMetrics.Role.TAB_ACCENT_HEIGHT)).isEqualTo(2);
        assertThat(metrics.value(DesignMetrics.Role.TAB_OPEN_MILLIS)).isEqualTo(80);
        assertThat(metrics.value(DesignMetrics.Role.TAB_CLOSE_MILLIS)).isEqualTo(100);
        assertThat(metrics.value(DesignMetrics.Role.TAB_SWITCH_MILLIS)).isEqualTo(130);
        assertThat(AnimationSettings.defaults()).isEqualTo(new AnimationSettings(true, 80, 100, 130));
        assertThat(metrics.value(DesignMetrics.Role.WORKSPACE_SURFACE_ARC)).isEqualTo(16);
    }

    /** Each native dimension has its own name, carrying the exact pre-migration value. */
    @Test void nativeGeometryKeepsItsExactValuesUnderItsOwnNames() {
        var expected = new java.util.LinkedHashMap<DesignMetrics.Role, Integer>();
        expected.putAll(Map.of(DesignMetrics.Role.TAB_SCROLL_STEP, 16, DesignMetrics.Role.TAB_REVEAL_MARGIN, 16,
            DesignMetrics.Role.TAB_FADE_WIDTH, 16, DesignMetrics.Role.TAB_CLOSE_WIDTH, 16, DesignMetrics.Role.TAB_REORDER_THRESHOLD, 5,
            DesignMetrics.Role.STATUS_RUNNING_DOT, 5, DesignMetrics.Role.STATUS_PROGRESS_BAR_HEIGHT, 8,
            DesignMetrics.Role.STATUS_PROGRESS_MIN_HEIGHT, 24, DesignMetrics.Role.TOOL_HEADER_LEADING_PADDING, 10,
            DesignMetrics.Role.TOOL_HEADER_TRAILING_PADDING, 6));
        expected.putAll(Map.of(DesignMetrics.Role.RAIL_BUTTON_ARC, 10, DesignMetrics.Role.NATIVE_TOOLBAR_HOVER_ARC, 6,
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
        var required = Map.of("WindowStatusBar.java", List.of("STATUS_RUNNING_DOT"),
            "StatusProgressView.java", List.of("STATUS_PROGRESS_BAR_HEIGHT", "STATUS_PROGRESS_MIN_HEIGHT"),
            "WindowTabs.java", List.of("TAB_SCROLL_STEP", "TAB_REVEAL_MARGIN", "TAB_FADE_WIDTH", "TAB_CLOSE_WIDTH", "TAB_REORDER_THRESHOLD"),
            "ToolWindowHeader.java", List.of("TOOL_HEADER_LEADING_PADDING", "TOOL_HEADER_TRAILING_PADDING"));
        for (var entry : required.entrySet())
            assertThat(Files.readString(workspace.resolve(entry.getKey()))).as(entry.getKey()).contains(entry.getValue());
    }
}
