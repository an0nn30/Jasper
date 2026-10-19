package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.platform.ClassicMenuRow;
import dev.jasper.app.presentation.TitleFormat;
import dev.jasper.app.presentation.WorkspaceLook;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.ui.theme.DerivedColor;
import java.awt.Component;
import java.awt.Insets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRootPane;
import javax.swing.border.CompoundBorder;
import javax.swing.border.MatteBorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Spec 10.2: the standard and classic menu bars over the same contributions, and the classic menu row of the mocks. */
class WindowChromeMenusTest {
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    static WorkspaceChrome classic() {
        return WorkspaceChrome.of(new WorkspaceLook(WorkspaceLook.Menus.CLASSIC, WorkspaceLook.Header.UNIFIED, WorkspaceLook.Stripes.RAILS,
            WorkspaceLook.Tabs.EQUAL, WorkspaceLook.Status.SLASH, false, WorkspaceLook.FrameStyle.STANDARD), new TitleFormat("{context}"));
    }

    static List<String> titles(WindowContent owner) {
        return owner.chrome().menuBar().menus().stream().map(JMenu::getText).toList();
    }

    static JMenu menu(WindowContent owner, String title) {
        return owner.chrome().menuBar().menus().stream().filter(menu -> menu.getText().equals(title)).findFirst().orElseThrow();
    }

    static List<String> items(JMenu menu) {
        var labels = new ArrayList<String>();
        for (Component child : menu.getMenuComponents())
            labels.add(child instanceof JMenu sub ? "submenu:" + sub.getText() : child instanceof JMenuItem item ? item.getText() : "---");
        return labels;
    }

    /** A plugin's Session, Transfer and Tools entries and its own menu, then the app's Help; or Help first when asked. */
    static Contributions model(boolean helpFirst) {
        var model = new Contributions();
        for (String id : List.of("dev.x.connect", "dev.x.upload", "dev.x.tool", "dev.x.own", "dev.x.split", "dev.x.about"))
            model.addAction(id, id.substring(6), null, List.of(), Optional.empty(), invocation -> { });
        if (helpFirst) model.addMenuSection(MenuTarget.topLevel(MenuTarget.HELP, "Help")).set(List.of(new MenuEntry.Item("dev.x.about")));
        model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.SESSION)).set(List.of(new MenuEntry.Item("dev.x.connect")));
        model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.TRANSFER)).set(List.of(new MenuEntry.Item("dev.x.upload")));
        model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.TOOLS)).set(List.of(new MenuEntry.Item("dev.x.tool")));
        model.addMenuSection(MenuTarget.topLevel("dev.x.menu", "Own")).set(List.of(new MenuEntry.Item("dev.x.own")));
        if (!helpFirst) model.addMenuSection(MenuTarget.topLevel(MenuTarget.HELP, "Help")).set(List.of(new MenuEntry.Item("dev.x.about")));
        return model;
    }

    @Test void theStandardBarKeepsTodaysOrder() throws Exception {
        edt(() -> {
            var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD);
            owner.connectContributions(model(false));
            assertThat(titles(owner)).containsExactly("File", "Edit", "View", "Tab", "Session", "Transfer", "Tools", "Own", "Help");
            var first = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD);
            first.connectContributions(model(true));
            assertThat(titles(first)).as("registration order, as today").containsExactly("File", "Edit", "View", "Tab", "Session", "Transfer", "Tools", "Help", "Own");
            assertThat(owner.chrome().menuBar().getClientProperty("FlatLaf.style")).as("the standard bar is not restyled").isNull();
        });
    }

    @Test void theClassicBarKeepsHelpLastWhateverTheRegistrationOrder() throws Exception {
        edt(() -> {
            for (boolean helpFirst : new boolean[] {false, true}) {
                var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), ThemeTestSupport.controller(true), classic());
                owner.connectContributions(model(helpFirst));
                assertThat(titles(owner)).as(helpFirst ? "help first" : "help last")
                    .containsExactly("File", "Edit", "View", "Session", "Transfer", "Own", "Tools", "Window", "Help");
            }
        });
    }

    @Test void theClassicWindowMenuHoldsTheTabMenuThenTheToolWindows() throws Exception {
        edt(() -> {
            var model = model(false);
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel());
            model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.TAB)).set(List.of(new MenuEntry.Item("dev.x.split")));
            var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), ThemeTestSupport.controller(true), classic());
            owner.connectContributions(model);
            var window = items(menu(owner, "Window"));
            assertThat(window.getFirst()).isEqualTo("Split Right");
            assertThat(window).as("the Tab menu's group, prompt and tab items with the plugin's Tab section, then the toggles")
                .containsSubsequence("Split Right", "Next Prompt", "---", "Next Tab", "Previous Tab", "---", "split", "---", "Toggle Hosts");
            assertThat(window.getLast()).as("the tool-window toggles close the menu").isEqualTo("Toggle Hosts");
            assertThat(items(menu(owner, "View"))).as("the toggles moved to Window").doesNotContain("submenu:Tool Windows");
            var standard = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD);
            standard.connectContributions(model);
            assertThat(items(menu(standard, "View"))).as("the default look keeps them in View").contains("submenu:Tool Windows");
        });
    }

    /** Mock measurements: a 24 px row on surface.app over a 1 px border.divider rule, the first title 15 from the edge. */
    @Test void theClassicMenuRowIsTheMocksRow() throws Exception {
        dev.jasper.app.testsupport.EnterpriseResources.assumePlexResolves();
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, classic());
                owner.connectContributions(model(false));
                var root = new JRootPane();
                root.setJMenuBar(owner.menuBar());
                root.setContentPane(owner);
                root.setSize(1440, 600);
                LayoutTestSupport.layoutTree(root);
                var bar = owner.chrome().menuBar();
                assertThat(bar.getHeight()).as("24 and the rule").isEqualTo(25);
                assertThat(bar.getInsets()).isEqualTo(new Insets(0, 5, 1, 5));
                var rule = (MatteBorder) ((CompoundBorder) bar.getBorder()).getOutsideBorder();
                assertThat(rule.getMatteColor()).isEqualTo(DerivedColor.BORDER_DIVIDER.color(themes.current().chrome()));
                var file = bar.menus().getFirst();
                assertThat(file.getHeight()).isEqualTo(24);
                assertThat(file.getX() + file.getInsets().left).as("the first title starts 14 in, its ink at 15").isEqualTo(14);
                assertThat(file.getInsets().left).isEqualTo(9);
                assertThat(file.getFont()).as("the kit's body face").isEqualTo(dev.jasper.app.platform.ThemeZone.font(dev.jasper.ui.metrics.Typography.Role.BODY));
                assertThat(file.getFont().getSize()).as("the Enterprise kit's 12 px body").isEqualTo(12);
                assertThat(file.getFont().getFontName(java.util.Locale.ROOT)).as("plan 6a Rulings 20: the kit's own face").isEqualTo("IBM Plex Sans");
                // Rulings 6: a hovered closed title on surface.control.hover, an open one on the accent in text.onAccent, square.
                var palette = themes.current().chrome().semantic();
                @SuppressWarnings("unchecked") var style = (java.util.Map<String, Object>) bar.getClientProperty("FlatLaf.style");
                assertThat(style)
                    .containsEntry("hoverBackground", palette.color(dev.jasper.ui.theme.SemanticColor.SURFACE_CONTROL_HOVER))
                    .containsEntry("selectionBackground", palette.color(dev.jasper.ui.theme.SemanticColor.ACCENT))
                    .containsEntry("selectionForeground", palette.color(dev.jasper.ui.theme.SemanticColor.TEXT_ONACCENT))
                    .containsEntry("selectionArc", 0)
                    .containsEntry("selectionInsets", new Insets(0, 0, 0, 0));
                // An open title paints the row's accent, whatever its own menu style says.
                file.setSelected(true);
                var image = new java.awt.image.BufferedImage(bar.getWidth(), bar.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var graphics = image.createGraphics();
                try { bar.paint(graphics); } finally { graphics.dispose(); file.setSelected(false); }
                assertThat(new java.awt.Color(image.getRGB(file.getX() + 2, file.getY() + 2), true))
                    .as("the open title's background").isEqualTo(palette.color(dev.jasper.ui.theme.SemanticColor.ACCENT));
                assertThat(file.getForeground()).as("the titles' ink").isEqualTo(palette.color(dev.jasper.ui.theme.SemanticColor.TEXT_PRIMARY));
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /**
     * The classic row keeps the window bar's own folding layout: its titles stand at their {@link ClassicMenuRow#titleWidth},
     * and a narrow window folds the trailing titles into "…", then every title into ☰ (spec 10.2).
     */
    @Test void aNarrowClassicWindowStillFoldsItsMenus() throws Exception {
        dev.jasper.app.testsupport.EnterpriseResources.assumePlexResolves();
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, classic());
                owner.connectContributions(model(false));
                var root = new JRootPane();
                root.setJMenuBar(owner.menuBar());
                root.setContentPane(owner);
                var bar = owner.chrome().menuBar();
                assertThat(ClassicMenuRow.styled(bar)).isTrue();
                assertThat(bar.getLayout().getClass().getEnclosingClass()).as("the bar's own folding layout").isEqualTo(CollapsingMenuBar.class);
                root.setSize(1440, 600);
                LayoutTestSupport.layoutTree(root);
                assertThat(bar.fold()).isEqualTo(CollapsingMenuBar.Fold.NONE);
                double run = bar.getInsets().left;
                for (JMenu menu : bar.menus()) {
                    assertThat(menu.getX()).as(menu.getText() + " starts at the rounded running total").isEqualTo((int) Math.round(run));
                    run += ClassicMenuRow.titleWidth(menu);
                }
                root.setSize(300, 600);
                LayoutTestSupport.layoutTree(root);
                assertThat(bar.fold()).isEqualTo(CollapsingMenuBar.Fold.MORE);
                var shown = bar.menus().stream().filter(JMenu::isVisible).toList();
                assertThat(shown).as("File always stays; the trailing titles fold").isNotEmpty().hasSizeLessThan(bar.menus().size());
                assertThat(shown.getFirst().getText()).isEqualTo("File");
                var more = bar.overflowMenu();
                assertThat(more.isVisible()).isTrue();
                assertThat(more.getX() + more.getWidth()).as("the folded row fits the window").isLessThanOrEqualTo(300 - bar.getInsets().right);
                root.setSize(60, 600);
                LayoutTestSupport.layoutTree(root);
                assertThat(bar.fold()).isEqualTo(CollapsingMenuBar.Fold.ALL);
                assertThat(bar.hamburgerMenu().isVisible()).isTrue();
                assertThat(bar.menus()).noneMatch(JMenu::isVisible);
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }
}
