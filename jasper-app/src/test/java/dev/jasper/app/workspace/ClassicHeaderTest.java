package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.ToolbarMode;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.ToolbarEntry;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.app.presentation.TitleFormat;
import dev.jasper.app.presentation.WorkspaceLook;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.ui.swing.FlatLafInstaller;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.border.MatteBorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Spec 10.3 and the Workstation mock: the classic toolbar row under a native title. */
class ClassicHeaderTest {
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    static WorkspaceChrome classic() {
        return WorkspaceChrome.of(new WorkspaceLook(WorkspaceLook.Menus.CLASSIC, WorkspaceLook.Header.CLASSIC, WorkspaceLook.Stripes.RAILS,
            WorkspaceLook.Tabs.EQUAL, WorkspaceLook.Status.SLASH, false, WorkspaceLook.FrameStyle.STANDARD), new TitleFormat("{context}"));
    }

    static List<String> labels(WindowToolbar bar) {
        var labels = new ArrayList<String>();
        for (Component child : bar.getComponents())
            if (child instanceof ClassicToolbarButton button) labels.add(String.valueOf(button.getClientProperty("label")));
        return labels;
    }

    record Window(WindowContent owner, JRootPane root) {
        void width(int width) { root.setSize(width, 400); LayoutTestSupport.layoutTree(root); LayoutTestSupport.layoutTree(root); }
    }

    static Window window(ThemeController themes, boolean view) {
        var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, classic());
        owner.setToolbarHeight(34);
        var model = new Contributions();
        owner.connectContributions(model);
        if (view) {
            var panel = new JPanel(); panel.setPreferredSize(new Dimension(300, 22)); panel.setName("sessionView");
            model.addToolbar(new ToolbarEntry.View(id -> Optional.of(new ToolbarEntry.Mounted(panel, () -> { }))));
            owner.connectTerminals(new TerminalRegistry(), () -> { });
        }
        var root = new JRootPane();
        WindowContent.mount(root, owner, new WindowLook(classic(), false, true), title -> { });
        return new Window(owner, root);
    }

    @Test void theClassicRowIsTheMocks() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var window = window(themes, false);
                window.width(1440);
                var header = window.owner().chrome().header();
                var bar = window.owner().chrome().windowToolbar();
                assertThat(header.getHeight()).as("34 and the rule").isEqualTo(35);
                var rule = (MatteBorder) header.getBorder();
                assertThat(rule.getMatteColor()).isEqualTo(themes.current().chrome().semantic().color(dev.jasper.ui.theme.SemanticColor.BORDER_WINDOW));
                assertThat(ThemeZone.theme(header.field())).as("not a title-bar zone").isSameAs(FlatLafInstaller.installedTheme());
                assertThat(bar.leading()).isTrue();
                assertThat(labels(bar)).containsExactly("New Session", "Split", "Maximize");
                var first = (ClassicToolbarButton) bar.getComponent(0);
                assertThat(first.getX()).as("toolbar padding 8").isEqualTo(8);
                assertThat(first.getHeight()).isEqualTo(24);
                assertThat(first.getY()).isEqualTo(5);
                var classic = (ClassicHeader) window.owner().chrome().composition();
                var settings = (ClassicToolbarButton) classic.trailing().getComponent(0);
                int settingsRight = classic.trailing().getX() + settings.getX() + settings.getWidth();
                assertThat(settingsRight).as("8 from the content edge").isEqualTo(1440 - 8);
                var field = header.field();
                assertThat(field.getSize()).isEqualTo(new Dimension(300, 22));
                assertThat(field.getX() + field.getWidth()).as("6 before Settings…").isEqualTo(classic.trailing().getX() + settings.getX() - 6);
                assertThat(field.getY()).as("centred in the 34 px row").isEqualTo(6);
                assertThat(window.owner().chrome().menuBar().getParent()).as("the menus stay a row of their own").isSameAs(window.root().getLayeredPane());
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void aNarrowWindowGivesWayInOrder() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var window = window(themes, true);
                var header = window.owner().chrome().header();
                var bar = window.owner().chrome().windowToolbar();
                var classic = (ClassicHeader) window.owner().chrome().composition();
                int labelsGone = 0, maximize = 0, view = 0, shrunk = 0, collapsed = 0;
                for (int width = 1440; width >= 200; width -= 10) {
                    window.width(width);
                    var first = (ClassicToolbarButton) bar.getComponent(0);
                    var maximizeButton = bar.getComponent(3);
                    Component sessionView = WindowTabsTest.named(bar, "sessionView");
                    if (labelsGone == 0 && first.getText() == null) labelsGone = width;
                    if (maximize == 0 && !maximizeButton.isVisible()) maximize = width;
                    if (view == 0 && sessionView != null && !sessionView.isVisible()) view = width;
                    if (shrunk == 0 && header.field().getWidth() < 300) shrunk = width;
                    if (collapsed == 0 && header.field().getWidth() == 0) collapsed = width;
                    assertThat(classic.trailing().getComponent(0).isVisible()).as("Settings… stays at " + width).isTrue();
                }
                assertThat(List.of(labelsGone, maximize, view, shrunk, collapsed)).as("each gave way").doesNotContain(0);
                // As in the trailing layout (plan 6b Task 2's review): an icon button frees less than the overflow button
                // takes, so the view hides first and the buttons that gave way for it come back; then they overflow.
                assertThat(labelsGone).as("labels first").isGreaterThanOrEqualTo(view);
                assertThat(view).as("then the view hides").isGreaterThanOrEqualTo(maximize);
                assertThat(maximize).as("then Maximize into the menu").isGreaterThanOrEqualTo(shrunk);
                assertThat(shrunk).as("then the field shrinks, then collapses").isGreaterThan(collapsed);
                window.width(1440);
                assertThat(header.field().getWidth()).isEqualTo(300);
                assertThat(WindowTabsTest.named(bar, "sessionView").isVisible()).isTrue();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void theToolbarSettingChoosesLabelsIconsOrNoRow() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(true);
            var window = window(themes, false);
            window.owner().setToolbarMode(ToolbarMode.ICONS);
            window.width(1440);
            var first = (ClassicToolbarButton) window.owner().chrome().windowToolbar().getComponent(0);
            assertThat(first.getText()).as("icons only").isNull();
            assertThat(first.getPreferredSize().width).as("square").isEqualTo(first.getPreferredSize().height);
            window.owner().setToolbarMode(ToolbarMode.HIDDEN);
            assertThat(window.owner().chrome().header().isVisible()).as("hidden: no row").isFalse();
            window.owner().setToolbarMode(ToolbarMode.ICONS_AND_LABELS);
            window.width(1440);
            assertThat(window.owner().chrome().header().isVisible()).isTrue();
            assertThat(first.getText()).isEqualTo("New Session");
        });
    }

    @Test void theDefaultHeaderIsTodays() throws Exception {
        edt(() -> {
            var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD);
            assertThat(owner.chrome().composition()).isInstanceOf(UnifiedHeader.class);
            assertThat(owner.chrome().windowToolbar().leading()).isFalse();
            assertThat(owner.chrome().header().getBorder()).isNull();
        });
    }

    /** Spec 10.3: icons_and_labels gives label-first text buttons, contributed ones included; a dropdown has its caret. */
    @Test void contributedButtonsAndDropdownsAreClassicButtons() throws Exception {
        edt(() -> {
            var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), ThemeTestSupport.controller(true), classic());
            var model = new Contributions();
            model.addAction("dev.x.ping", "Ping", null, List.of(), Optional.empty(), invocation -> { });
            model.addAction("dev.x.mac", "Mac", null, List.of(), Optional.empty(), invocation -> { });
            model.addToolbar(new ToolbarEntry.Button("dev.x.ping"));
            model.addToolbar(new ToolbarEntry.Dropdown(null, "Sessions", List.of("dev.x.mac")));
            owner.connectContributions(model);
            var contributed = new ArrayList<ClassicToolbarButton>();
            for (Component child : owner.chrome().windowToolbar().getComponents())
                if (child instanceof ClassicToolbarButton button && !List.of("New Session", "Split", "Maximize").contains(button.getClientProperty("label")))
                    contributed.add(button);
            assertThat(contributed).extracting(button -> button.getClientProperty("label")).containsExactly("Ping", "Sessions");
            assertThat(contributed.get(0).getText()).as("label-first").isEqualTo("Ping");
            assertThat(contributed.get(0).caret()).isFalse();
            assertThat(contributed.get(1).caret()).as("a dropdown shows its caret").isTrue();
            assertThat(owner.chrome().windowToolbar().getComponents()).as("no IntelliJ reference button in the classic row")
                .noneMatch(child -> child instanceof javax.swing.JButton && !(child instanceof ClassicToolbarButton) && child != owner.chrome().windowToolbar().overflowButton());
        });
    }

    /** Spec 9.1's fallback row and 10.1: under a native title the unified header is a row of window chrome. */
    @Test void aNativeTitleMakesTheHeaderChrome() throws Exception {
        edt(() -> {
            var fallback = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD);
            WindowContent.mount(new JRootPane(), fallback, new WindowLook(WorkspaceChrome.STANDARD, false, true), title -> { });
            assertThat(fallback.chrome().header().getClientProperty("Jasper.zone")).as("not a title-bar zone").isNull();
            assertThat(fallback.chrome().headerIsTitleBar()).isFalse();
            var today = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD);
            WindowContent.mount(new JRootPane(), today, new WindowLook(WorkspaceChrome.STANDARD, false, false), title -> { });
            assertThat(today.chrome().header().getClientProperty("Jasper.zone")).as("the default look keeps today's zone").isEqualTo("titlebar");
            assertThat(today.chrome().headerIsTitleBar()).isTrue();
        });
    }
}
