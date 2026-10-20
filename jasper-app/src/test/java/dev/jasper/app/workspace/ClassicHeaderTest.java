package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;
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
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.app.testsupport.MockProfile;
import dev.jasper.ui.icons.ChromeArtwork;
import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
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

    static WorkspaceChrome classic() { return classic(WorkspaceLook.Icons.NEW_UI); }

    static WorkspaceChrome classic(WorkspaceLook.Icons icons) {
        return WorkspaceChrome.of(new WorkspaceLook(WorkspaceLook.Menus.CLASSIC, WorkspaceLook.Header.CLASSIC, WorkspaceLook.Stripes.RAILS,
            WorkspaceLook.Tabs.EQUAL, WorkspaceLook.Status.SLASH, false, WorkspaceLook.FrameStyle.STANDARD,
            icons), new TitleFormat("{context}"));
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

    static Window window(ThemeController themes, boolean view) { return window(themes, view, WorkspaceLook.Icons.NEW_UI); }

    static Window window(ThemeController themes, boolean view, WorkspaceLook.Icons icons) {
        var chrome = classic(icons);
        var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, chrome);
        // The user's 2026-10-19 direction: IntelliJ's 28 px toolbar, where the mocks draw 34.
        owner.setToolbarHeight(28);
        var model = new Contributions();
        owner.connectContributions(model);
        if (view) {
            var panel = new JPanel(); panel.setPreferredSize(new Dimension(300, 22)); panel.setName("sessionView");
            model.addToolbar(new ToolbarEntry.View(id -> Optional.of(new ToolbarEntry.Mounted(panel, () -> { }))));
            owner.connectTerminals(new TerminalRegistry(), () -> { });
        }
        var root = new JRootPane();
        WindowContent.mount(root, owner, new WindowLook(chrome, false, true), title -> { });
        return new Window(owner, root);
    }

    static ClassicToolbarButton labelled(WindowToolbar bar, String label) {
        for (Component child : bar.getComponents())
            if (child instanceof ClassicToolbarButton button && label.equals(button.getClientProperty("label"))) return button;
        throw new AssertionError(label);
    }

    @Test void theClassicRowIsTheMocks() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var window = window(themes, false);
                window.width(1440);
                var header = window.owner().chrome().header();
                var bar = window.owner().chrome().windowToolbar();
                // The user's 2026-10-19 direction: IntelliJ's 28 px toolbar, where the mocks draw 34.
                assertThat(header.getHeight()).as("28 and the rule").isEqualTo(29);
                var rule = (MatteBorder) header.getBorder();
                assertThat(rule.getMatteColor()).isEqualTo(themes.current().chrome().semantic().color(dev.jasper.ui.theme.SemanticColor.BORDER_WINDOW));
                assertThat(ThemeZone.theme(header.field())).as("not a title-bar zone").isSameAs(FlatLafInstaller.installedTheme());
                assertThat(bar.leading()).isTrue();
                assertThat(labels(bar)).containsExactly("New Session", "Split", "Maximize");
                var first = (ClassicToolbarButton) bar.getComponent(0);
                assertThat(first.getX()).as("toolbar padding 8").isEqualTo(8);
                assertThat(first.getHeight()).isEqualTo(24);
                // The user's 2026-10-19 direction: IntelliJ's 28 px toolbar, where the mocks draw 34.
                assertThat(first.getY()).isEqualTo(2);
                var classic = (ClassicHeader) window.owner().chrome().composition();
                var settings = (ClassicToolbarButton) classic.trailing().getComponent(0);
                int settingsRight = classic.trailing().getX() + settings.getX() + settings.getWidth();
                // Workspace plan 7b Rulings 20: the window's ⋮, a 22 px icon button 8 after Settings…, ends the row.
                var windowMenu = (ClassicToolbarButton) classic.trailing().getComponent(1);
                assertThat(classic.trailing().getX() + windowMenu.getX() + windowMenu.getWidth()).as("8 from the content edge")
                    .isEqualTo(1440 - 8);
                assertThat(settingsRight).as("Settings… 8 before the ⋮").isEqualTo(1440 - 8 - 22 - 8);
                var field = header.field();
                assertThat(field.getSize()).isEqualTo(new Dimension(300, 22));
                assertThat(field.getX() + field.getWidth()).as("6 before Settings…").isEqualTo(classic.trailing().getX() + settings.getX() - 6);
                // The user's 2026-10-19 direction: IntelliJ's 28 px toolbar, where the mocks draw 34.
                assertThat(field.getY()).as("centred in the 28 px row").isEqualTo(3);
                // The field reads "Search everywhere": its tip and name say so, not the pane's context.
                field.setContext("~/projects", "dustin@host:~/projects", null);
                String shortcut = field.shortcutText();
                assertThat(field.getToolTipText()).isEqualTo(shortcut.isBlank() ? "Search everywhere" : "Search everywhere (" + shortcut + ")");
                assertThat(field.getAccessibleContext().getAccessibleName()).isEqualTo("Search everywhere");
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
            var bar = window.owner().chrome().windowToolbar();
            var first = (ClassicToolbarButton) bar.getComponent(0);
            assertThat(first.getText()).as("icons only").isNull();
            assertThat(first.getPreferredSize()).as("IntelliJ's 22 px square").isEqualTo(new Dimension(scale(22), scale(22)));
            assertThat(first.getY()).as("centred in the row").isEqualTo((bar.getHeight() - scale(22)) / 2);
            // A disabled icon is drawn faded, as a disabled label is.
            ClassicToolbarButton maximize = null;
            for (Component child : window.owner().chrome().windowToolbar().getComponents())
                if (child instanceof ClassicToolbarButton button && "Maximize".equals(button.getClientProperty("label"))) maximize = button;
            assertThat(maximize).isNotNull();
            maximize.setSize(maximize.getPreferredSize());
            maximize.setEnabled(true);
            int enabled = strongestAlpha(maximize);
            maximize.setEnabled(false);
            assertThat(strongestAlpha(maximize)).as("the disabled icon at 0.42").isLessThanOrEqualTo((int) Math.ceil(enabled * .42) + 1).isPositive();
            window.owner().setToolbarMode(ToolbarMode.HIDDEN);
            assertThat(window.owner().chrome().header().isVisible()).as("hidden: no row").isFalse();
            window.owner().setToolbarMode(ToolbarMode.ICONS_AND_LABELS);
            window.width(1440);
            assertThat(window.owner().chrome().header().isVisible()).isTrue();
            assertThat(first.getText()).isEqualTo("New Session");
        });
    }

    /** The strongest alpha {@code component} paints on a transparent image. */
    static int strongestAlpha(javax.swing.JComponent component) {
        return alphaIn(component, new Rectangle(0, 0, component.getWidth(), component.getHeight()));
    }

    /** The strongest alpha {@code component}, painted on a transparent image, has in {@code area}. */
    static int alphaIn(javax.swing.JComponent component, Rectangle area) {
        var image = painted(component);
        var inside = area.intersection(new Rectangle(0, 0, image.getWidth(), image.getHeight()));
        int strongest = 0;
        for (int y = inside.y; y < inside.y + inside.height; y++)
            for (int x = inside.x; x < inside.x + inside.width; x++) strongest = Math.max(strongest, image.getRGB(x, y) >>> 24);
        return strongest;
    }

    /** {@code component} painted on a transparent image of its size. */
    static BufferedImage painted(javax.swing.JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { component.paint(g); } finally { g.dispose(); }
        return image;
    }

    static int scale(int value) { return UIScale.scale(value); }

    /** Rulings 7: icons and labels draws the icon, then the label; icons alone a 22 px square; Split marks its menu in its corner. */
    @Test void iconButtonsAreTwentyTwoSquaresAndIconsAndLabelsDrawTheIconThenTheLabel() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(true);
            var window = window(themes, false);
            var bar = window.owner().chrome().windowToolbar();
            window.owner().setToolbarMode(ToolbarMode.ICONS_AND_LABELS);
            window.width(1440);
            var session = labelled(bar, "New Session");
            int text = session.getFontMetrics(session.getFont()).stringWidth("New Session");
            assertThat(session.getPreferredSize().width).isEqualTo(2 * scale(8) + scale(16) + scale(6) + text);
            session.setSize(session.getPreferredSize());
            assertThat(alphaIn(session, new Rectangle(scale(8), 0, scale(16), session.getHeight()))).as("the icon before the label").isPositive();
            window.owner().setToolbarMode(ToolbarMode.ICONS);
            window.width(1440);
            var split = labelled(bar, "Split");
            assertThat(split.getPreferredSize()).isEqualTo(new Dimension(scale(22), scale(22)));
            assertThat(split.getToolTipText()).isEqualTo("Split right or down");
            split.setSize(split.getPreferredSize());
            var corner = new Rectangle(scale(22) - scale(7), scale(22) - scale(5), scale(5), scale(3));
            assertThat(alphaIn(split, corner)).as("Split's corner ▼").isPositive();
            var maximize = labelled(bar, "Maximize");
            maximize.setSize(maximize.getPreferredSize());
            assertThat(alphaIn(maximize, corner)).as("no ▼ without a menu").isZero();
        });
    }

    /** Review Focus 5: the squares, Split's corner ▼ while it is placed, and the order things give way in. */
    @Test void asTheRowNarrowsIconButtonsKeepTheirSquaresAndSplitItsCornerArrow() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(true);
            var window = window(themes, true);
            window.owner().setToolbarMode(ToolbarMode.ICONS);
            var bar = window.owner().chrome().windowToolbar();
            var header = window.owner().chrome().header();
            var split = labelled(bar, "Split");
            var corner = new Rectangle(scale(22) - scale(7), scale(22) - scale(5), scale(5), scale(3));
            int splitGone = 0, maximize = 0, view = 0, shrunk = 0, collapsed = 0;
            for (int width = 1440; width >= 200; width -= 10) {
                window.width(width);
                for (Component child : bar.getComponents())
                    if (child instanceof ClassicToolbarButton button && button.isVisible() && button.getWidth() > 0)
                        assertThat(button.getSize()).as(width + " " + button.getClientProperty("label")).isEqualTo(new Dimension(scale(22), scale(22)));
                boolean placed = split.isVisible() && split.getWidth() > 0;
                if (placed) assertThat(alphaIn(split, corner)).as("Split's corner ▼ at " + width).isPositive();
                Component sessionView = WindowTabsTest.named(bar, "sessionView");
                if (splitGone == 0 && !placed) splitGone = width;
                if (maximize == 0 && !bar.getComponent(3).isVisible()) maximize = width;
                if (view == 0 && sessionView != null && !sessionView.isVisible()) view = width;
                if (shrunk == 0 && header.field().getWidth() < 300) shrunk = width;
                if (collapsed == 0 && header.field().getWidth() == 0) collapsed = width;
            }
            assertThat(List.of(splitGone, maximize, view, shrunk, collapsed)).as("each gave way").doesNotContain(0);
            // Today's order, as aNarrowWindowGivesWayInOrder's: the view hides, Maximize then Split go into the overflow
            // menu, then the field shrinks and collapses.
            assertThat(view).as("the view hides first").isGreaterThanOrEqualTo(maximize);
            assertThat(maximize).as("Maximize no later than Split").isGreaterThanOrEqualTo(splitGone);
            assertThat(splitGone).as("then the field shrinks").isGreaterThanOrEqualTo(shrunk);
            assertThat(shrunk).as("then collapses").isGreaterThan(collapsed);
        });
    }

    /** Rulings 3 and 7: under the classic set the buttons paint IntelliJ's classic artwork. */
    @Test void underTheClassicSetTheButtonsPaintIntelliJsClassicIcons() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(true);
            var window = window(themes, false, WorkspaceLook.Icons.CLASSIC);
            window.owner().setToolbarMode(ToolbarMode.ICONS);
            window.width(1440);
            assertPaintsClassicAdd(labelled(window.owner().chrome().windowToolbar(), "New Session"));
        });
    }

    /** {@code session}, at its preferred size, paints exactly IntelliJ's classic add icon at its centre. */
    static void assertPaintsClassicAdd(ClassicToolbarButton session) {
        session.setSize(session.getPreferredSize());
        int size = scale(16), x = (session.getWidth() - size) / 2, y = (session.getHeight() - size) / 2;
        var actual = painted(session).getSubimage(x, y, size, size);
        var expected = new BufferedImage(session.getWidth(), session.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = expected.createGraphics();
        try {
            FlatUIUtils.setRenderingHints(g);
            new IconRegistry().chromeIcon("add", size, ChromeArtwork.CLASSIC).icon(ThemeZone.theme(session), SemanticColor.TEXT_PRIMARY)
                .paintIcon(session, g, x, y);
        } finally { g.dispose(); }
        var centre = expected.getSubimage(x, y, size, size);
        int differing = 0, inked = 0;
        for (int row = 0; row < size; row++)
            for (int column = 0; column < size; column++) {
                if (actual.getRGB(column, row) != centre.getRGB(column, row)) differing++;
                if (centre.getRGB(column, row) >>> 24 != 0) inked++;
            }
        assertThat(inked).as("the classic add icon has ink").isPositive();
        assertThat(differing).as("New Session paints the classic add icon").isZero();
    }

    /**
     * Final review: Enterprise's own default toolbar (icons, the classic set, the Enterprise kit and theme, with a toolbar view),
     * in Light and Dark; the renders are written under build/ for inspection.
     */
    @Test void enterprisesDefaultToolbarIsClassicIconSquares() throws Exception {
        for (boolean dark : new boolean[] {false, true})
            edt(() -> {
                var themes = EnterpriseLook.controller(dark);
                try {
                    var window = window(themes, true, WorkspaceLook.Icons.CLASSIC);
                    window.owner().setToolbarMode(ToolbarMode.ICONS);
                    window.width(1440);
                    var bar = window.owner().chrome().windowToolbar();
                    assertThat(labels(bar)).containsExactly("New Session", "Split", "Maximize");
                    for (Component child : bar.getComponents())
                        if (child instanceof ClassicToolbarButton button) {
                            assertThat(button.getText()).as("icons only").isNull();
                            assertThat(button.getSize()).as(String.valueOf(button.getClientProperty("label"))).isEqualTo(new Dimension(scale(22), scale(22)));
                        }
                    assertThat(WindowTabsTest.named(bar, "sessionView").isVisible()).isTrue();
                    assertPaintsClassicAdd(labelled(bar, "New Session"));
                    var header = window.owner().chrome().header();
                    MockProfile.write(MockCrops.render(header, header.getWidth(), header.getHeight(), 2), "enterprise-default-toolbar-" + (dark ? "dark" : "light"));
                } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
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
