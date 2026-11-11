package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.MenuSection;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.app.plugins.MenuEntriesTestBridge;
import java.awt.Component;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browser plan 7 Task 1b (F9, F18): a plugin's menu entries in a window's menu bar. A change of labels and enabled states
 * alone updates the built items in place, an open menu's included; any other change rebuilds the contributed menus, but
 * not while one of the window's menus is open, whether Swing's selection path or, as with macOS's screen menu bar, only a
 * selected top-level menu says so.
 */
class WindowChromeMenuEntriesTest {
    @AfterEach void closeWindows() throws Exception {
        edt(() -> MenuSelectionManager.defaultManager().clearSelectedPath());
        DesktopTestSupport.closeOwners();
    }

    private static WindowContent window(Contributions model) {
        WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
        owner.connectContributions(model);
        return owner;
    }

    private static JMenu menu(WindowContent owner, String title) {
        return owner.chrome().menuBar().menus().stream().filter(menu -> menu.getText().equals(title)).findFirst().orElse(null);
    }

    private static List<String> items(JMenu menu) {
        List<String> lines = new ArrayList<>();
        for (Component child : menu.getMenuComponents())
            lines.add(child instanceof JMenu sub ? "submenu:" + sub.getText()
                : child instanceof JMenuItem item ? item.getText() + (item.isEnabled() ? "" : " (disabled)") : "---");
        return lines;
    }

    /** Clear All, a rule and one download's submenu titled {@code title}, holding Stop. */
    private static List<MenuEntry> downloads(String title, boolean stop, Runnable chosen) {
        return List.of(new MenuEntry.Command("dev.x/1/clear", "Clear All", true, () -> { }), new MenuEntry.Separator(),
            new MenuEntry.Submenu("dev.x/1/a", title, true, List.of(new MenuEntry.Command("dev.x/1/a.stop", "Stop", stop, chosen))));
    }

    /** As {@link #downloads}, with a second download's submenu after the first. */
    private static List<MenuEntry> twoDownloads(String title) {
        var entries = new ArrayList<>(downloads(title, true, () -> { }));
        entries.add(new MenuEntry.Submenu("dev.x/1/b", "b.zip \u2014 1.2 MB", true,
            List.of(new MenuEntry.Command("dev.x/1/b.stop", "Stop", true, () -> { }))));
        return entries;
    }

    @Test void aLabelChangeUpdatesTheShownItemInPlace() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window(model);
            List<String> heard = new ArrayList<>();
            MenuSection section = model.addMenuSection(MenuTarget.topLevel("dev.x.downloads", "Downloads"));
            section.set(downloads("a.zip \u2014 40%", true, () -> heard.add("first")));
            JMenu downloads = menu(owner, "Downloads");
            assertThat(items(downloads)).containsExactly("Clear All", "---", "submenu:a.zip \u2014 40%");
            JMenu file = (JMenu) downloads.getMenuComponent(2);
            JMenuItem stop = (JMenuItem) file.getMenuComponent(0);

            section.set(downloads("a.zip \u2014 41%", false, () -> heard.add("second")));
            assertThat(menu(owner, "Downloads")).as("the same menu").isSameAs(downloads);
            assertThat(downloads.getMenuComponent(2)).as("the same submenu").isSameAs(file);
            assertThat(file.getText()).isEqualTo("a.zip \u2014 41%");
            assertThat(file.getMenuComponent(0)).as("the same item").isSameAs(stop);
            assertThat(stop.isEnabled()).isFalse();

            section.set(downloads("a.zip \u2014 42%", true, () -> heard.add("third")));
            assertThat(stop.isEnabled()).isTrue();
            stop.doClick();
            assertThat(heard).as("the newest snapshot's choice").containsExactly("third");

            section.set(List.of(new MenuEntry.Command("dev.x/1/clear", "Clear All", true, () -> { }), new MenuEntry.Separator(),
                new MenuEntry.Submenu("dev.x/1/a", "a.zip \u2014 Stopped", false,
                    List.of(new MenuEntry.Command("dev.x/1/a.stop", "Stop", true, () -> heard.add("fourth"))))));
            assertThat(downloads.getMenuComponent(2)).as("a submenu's enabled state, in place too").isSameAs(file);
            assertThat(file.isEnabled()).isFalse();
            assertThat(file.getText()).isEqualTo("a.zip \u2014 Stopped");

            model.addAction("dev.x.unplaced", "Unplaced", null, List.of(), Optional.empty(), invocation -> { }).setTitle("Renamed");
            assertThat(menu(owner, "Downloads")).as("an action that no menu places rebuilds nothing").isSameAs(downloads);
        });
    }

    @Test void aStructureChangeWaitsWhileAMenuIsOpen() throws Exception {
        var model = new Contributions();
        var owner = new AtomicReference<WindowContent>();
        var section = new AtomicReference<MenuSection>();
        var shown = new AtomicReference<JMenu>();
        edt(() -> {
            owner.set(window(model));
            section.set(model.addMenuSection(MenuTarget.topLevel("dev.x.downloads", "Downloads")));
            section.get().set(downloads("a.zip \u2014 40%", true, () -> { }));
            shown.set(menu(owner.get(), "Downloads"));
            // Swing's selection path alone: it starts at this window's menu bar, and no menu of it is selected.
            MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[]{owner.get().menuBar()});
            assertThat(owner.get().chrome().menuBar().menus()).noneMatch(JMenu::isSelected);

            section.get().set(twoDownloads("a.zip \u2014 40%"));
            assertThat(menu(owner.get(), "Downloads")).as("no rebuild while the selection path is set").isSameAs(shown.get());
            assertThat(items(shown.get())).containsExactly("Clear All", "---", "submenu:a.zip \u2014 40%");
            section.get().set(twoDownloads("a.zip \u2014 50%"));
            assertThat(((JMenu) shown.get().getMenuComponent(2)).getText()).as("a label still changes in place")
                .isEqualTo("a.zip \u2014 50%");
            // Clearing a path that selected no menu tells no menu it closed: the next change applies what waited (D14).
            MenuSelectionManager.defaultManager().clearSelectedPath();
            assertThat(menu(owner.get(), "Downloads")).as("no close event came").isSameAs(shown.get());
            section.get().set(twoDownloads("a.zip \u2014 51%"));
            JMenu rebuilt = menu(owner.get(), "Downloads");
            assertThat(rebuilt).as("rebuilt at the next change").isNotSameAs(shown.get());
            assertThat(items(rebuilt)).containsExactly("Clear All", "---", "submenu:a.zip \u2014 51%", "submenu:b.zip \u2014 1.2 MB");
            shown.set(rebuilt);
        });
        edt(() -> {
            JMenu rebuilt = menu(owner.get(), "Downloads");
            assertThat(rebuilt).isSameAs(shown.get());

            // macOS's screen menu bar: JBR selects the open top-level menu, and Swing's selection path stays empty.
            rebuilt.setSelected(true);
            assertThat(MenuSelectionManager.defaultManager().getSelectedPath()).isEmpty();
            section.get().set(downloads("a.zip \u2014 Finished", true, () -> { }));
            assertThat(menu(owner.get(), "Downloads")).as("no rebuild while a top-level menu is selected").isSameAs(rebuilt);
            rebuilt.setSelected(false);
        });
        edt(() -> {
            JMenu rebuilt = menu(owner.get(), "Downloads");
            assertThat(rebuilt).as("rebuilt once it closed").isNotSameAs(shown.get());
            assertThat(items(rebuilt)).containsExactly("Clear All", "---", "submenu:a.zip \u2014 Finished");

            WindowContent other = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[]{other.menuBar(), other.chrome().menuBar().menus().getFirst()});
            section.get().set(twoDownloads("a.zip \u2014 Finished"));
            assertThat(menu(owner.get(), "Downloads")).as("another window's open menu holds nothing back").isNotSameAs(rebuilt);
            assertThat(items(menu(owner.get(), "Downloads"))).hasSize(4);
        });
    }

    /**
     * Review round 1 (D14a): on Windows and Linux a narrow window folds its menus into More, which opens and closes
     * without any folded menu opening; its closing still lets a held change through, a turn later, its menus back in the bar.
     */
    @Test void aClosedOverflowMenuLetsAHeldChangeThrough() throws Exception {
        var model = new Contributions();
        var owner = new AtomicReference<WindowContent>();
        var section = new AtomicReference<MenuSection>();
        var shown = new AtomicReference<JMenu>();
        edt(() -> {
            owner.set(window(model));
            section.set(model.addMenuSection(MenuTarget.topLevel("dev.x.downloads", "Downloads")));
            section.get().set(downloads("a.zip \u2014 40%", true, () -> { }));
            shown.set(menu(owner.get(), "Downloads"));
            CollapsingMenuBar bar = owner.get().chrome().menuBar();
            MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[]{bar, bar.overflowMenu()});
            section.get().set(twoDownloads("a.zip \u2014 40%"));
            assertThat(menu(owner.get(), "Downloads")).as("held while More is open").isSameAs(shown.get());
            MenuSelectionManager.defaultManager().clearSelectedPath();
        });
        edt(() -> {
            CollapsingMenuBar bar = owner.get().chrome().menuBar();
            JMenu rebuilt = menu(owner.get(), "Downloads");
            assertThat(rebuilt).as("rebuilt once More closed").isNotSameAs(shown.get());
            assertThat(items(rebuilt)).hasSize(4);
            assertThat(bar.overflowMenu().getMenuComponentCount()).as("the folded menus went back").isZero();
            assertThat(bar.menus()).allSatisfy(each -> assertThat(each.getParent()).isSameAs(bar));
        });
    }

    /**
     * Review round 1 (D14b): a held pass runs at the next change even when the model came back to the shape the menus were
     * built from, as a placed action removed and registered again under its id does; the item then has the new action.
     */
    @Test void aHeldPassRunsAtTheNextChangeEvenWhenTheShapeCameBack() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window(model);
            var run = model.addAction("dev.x.run", "Run", null, List.of(), Optional.empty(), invocation -> { });
            model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.TOOLS)).set(List.of(new MenuEntry.Item("dev.x.run")));
            JMenuItem before = (JMenuItem) menu(owner, "Tools").getMenuComponent(0);
            // A menu that closes without telling its menus: the path alone.
            MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[]{owner.menuBar()});
            run.close();
            model.addAction("dev.x.run", "Run Again", null, List.of(), Optional.empty(), invocation -> { });
            MenuSelectionManager.defaultManager().clearSelectedPath();
            assertThat(before.isEnabled()).as("the removed action").isFalse();

            model.addAction("dev.x.unplaced", "Unplaced", null, List.of(), Optional.empty(), invocation -> { });
            JMenuItem now = (JMenuItem) menu(owner, "Tools").getMenuComponent(0);
            assertThat(now).as("rebuilt at the next change").isNotSameAs(before);
            assertThat(now.getAction()).isNotSameAs(before.getAction());
            assertThat(now.isEnabled()).isTrue();
            assertThat(now.getText()).isEqualTo("Run Again");
        });
    }

    /** Review round 1 (Minor 1): a kept entry that left the model while a menu is open is disabled at once. */
    @Test void aVanishedEntryIsDisabledAtOnce() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window(model);
            MenuSection section = model.addMenuSection(MenuTarget.topLevel("dev.x.downloads", "Downloads"));
            section.set(twoDownloads("a.zip \u2014 40%"));
            JMenu shown = menu(owner, "Downloads");
            JMenu file = (JMenu) shown.getMenuComponent(2), second = (JMenu) shown.getMenuComponent(3);
            JMenuItem stop = (JMenuItem) file.getMenuComponent(0);
            MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[]{owner.menuBar(), shown});
            section.set(List.of(new MenuEntry.Command("dev.x/1/clear", "Clear All", true, () -> { }), new MenuEntry.Separator(),
                new MenuEntry.Submenu("dev.x/1/a", "a.zip \u2014 Finished", true,
                    List.of(new MenuEntry.Command("dev.x/1/a.open", "Open", true, () -> { })))));
            assertThat(menu(owner, "Downloads")).as("held").isSameAs(shown);
            assertThat(file.getText()).isEqualTo("a.zip \u2014 Finished");
            assertThat(stop.isEnabled()).as("Stop left the model").isFalse();
            assertThat(second.isEnabled()).as("so did the second download").isFalse();
            assertThat(((JMenuItem) shown.getMenuComponent(0)).isEnabled()).as("Clear All stays").isTrue();
        });
    }

    /**
     * Review round 1 (Minor 2): a change made during a pass, once a menu of the window has opened meanwhile, waits for that
     * menu as any other change does, and the next change applies it.
     */
    @Test void aChangeDuringAPassWaitsWhenAMenuOpensMeanwhile() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window(model);
            MenuSection section = model.addMenuSection(MenuTarget.topLevel("dev.x.downloads", "Downloads"));
            section.set(downloads("a.zip \u2014 40%", true, () -> { }));
            boolean[] once = {false};
            owner.menuBar().addContainerListener(new java.awt.event.ContainerAdapter() {
                @Override public void componentAdded(java.awt.event.ContainerEvent event) {
                    if (once[0]) return;
                    once[0] = true;
                    MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[]{owner.menuBar()});
                    section.set(twoDownloads("a.zip \u2014 40%"));
                }
            });
            owner.chrome().renderContributedMenus();
            assertThat(once[0]).isTrue();
            assertThat(items(menu(owner, "Downloads"))).as("the nested change waits for the open menu").hasSize(3);
            MenuSelectionManager.defaultManager().clearSelectedPath();
            section.set(twoDownloads("a.zip \u2014 41%"));
            assertThat(items(menu(owner, "Downloads"))).containsExactly("Clear All", "---", "submenu:a.zip \u2014 41%",
                "submenu:b.zip \u2014 1.2 MB");
        });
    }

    /** Review round 1 (Minor 4, D13): renaming and disabling a placed action keeps the built menu and item. */
    @Test void aPlacedActionsRenameAndDisableKeepItsItem() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window(model);
            var run = model.addAction("dev.x.run", "Run", null, List.of(), Optional.empty(), invocation -> { });
            model.addMenuSection(MenuTarget.topLevel("dev.x.tool", "Tool")).set(List.of(new MenuEntry.Item("dev.x.run")));
            JMenu tool = menu(owner, "Tool");
            JMenuItem item = (JMenuItem) tool.getMenuComponent(0);
            run.setTitle("Run Again");
            run.setEnabled(false);
            assertThat(menu(owner, "Tool")).isSameAs(tool);
            assertThat(tool.getMenuComponent(0)).isSameAs(item);
            assertThat(item.getText()).isEqualTo("Run Again");
            assertThat(item.isEnabled()).isFalse();
        });
    }

    /** Review round 1 (Minor 4, D13): an action a menu places, registered while a menu is open, shows once it closes. */
    @Test void aPlacedActionAddedWhileAMenuIsOpenWaits() throws Exception {
        var model = new Contributions();
        var owner = new AtomicReference<WindowContent>();
        var shown = new AtomicReference<JMenu>();
        edt(() -> {
            owner.set(window(model));
            model.addAction("dev.x.run", "Run", null, List.of(), Optional.empty(), invocation -> { });
            model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.TOOLS))
                .set(List.of(new MenuEntry.Item("dev.x.run"), new MenuEntry.Item("dev.x.later")));
            shown.set(menu(owner.get(), "Tools"));
            MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[]{owner.get().menuBar(), shown.get()});
            model.addAction("dev.x.later", "Later", null, List.of(), Optional.empty(), invocation -> { });
            assertThat(menu(owner.get(), "Tools")).as("held").isSameAs(shown.get());
            assertThat(items(shown.get())).containsExactly("Run");
            MenuSelectionManager.defaultManager().clearSelectedPath();
        });
        edt(() -> {
            assertThat(menu(owner.get(), "Tools")).as("rebuilt once it closed").isNotSameAs(shown.get());
            assertThat(items(menu(owner.get(), "Tools"))).containsExactly("Run", "Later");
        });
    }

    @Test void aLabelThatStartsWithHtmlShowsAsText() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window(model);
            MenuSection section = model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.TOOLS));
            section.set(html("<html><b>Bold</b>", "<html><i>Nested</i>"));
            JMenu tools = menu(owner, "Tools");
            JMenuItem item = (JMenuItem) tools.getMenuComponent(0);
            JMenu submenu = (JMenu) tools.getMenuComponent(1);
            JMenuItem child = (JMenuItem) submenu.getMenuComponent(0);
            assertThat(item.getText()).isEqualTo("<html><b>Bold</b>");
            assertThat(submenu.getText()).isEqualTo("<html><i>Nested</i>");
            for (JMenuItem shown : List.of(item, submenu, child))
                assertThat(shown.getClientProperty(BasicHTML.propertyKey)).as("no HTML view for %s", shown.getText()).isNull();

            section.set(html("<html><u>Bold</u>", "<html><u>Nested</u>"));
            assertThat(tools.getMenuComponent(0)).isSameAs(item);
            assertThat(item.getText()).isEqualTo("<html><u>Bold</u>");
            assertThat(submenu.getText()).isEqualTo("<html><u>Nested</u>");
            for (JMenuItem shown : List.of(item, submenu))
                assertThat(shown.getClientProperty(BasicHTML.propertyKey)).as("still no HTML view for %s", shown.getText()).isNull();
        });
    }

    private static List<MenuEntry> html(String label, String title) {
        return List.of(new MenuEntry.Command("dev.x/1/bold", label, true, () -> { }),
            new MenuEntry.Submenu("dev.x/1/nested", title, true, List.of(new MenuEntry.Command("dev.x/1/child", "<html>Child", true, () -> { }))));
    }

    @Test void anEntryThatThrowsBecomesThePluginsIncident() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window(model);
            var plugin = new MenuEntriesTestBridge(model, "dev.x");
            plugin.throwingEntry("dev.x.downloads", "Downloads", "Fails");
            JMenuItem fails = (JMenuItem) menu(owner, "Downloads").getMenuComponent(0);
            assertThat(fails.getText()).isEqualTo("Fails");
            fails.doClick();
            assertThat(plugin.incidents()).isEqualTo(1);
            fails.doClick();
            assertThat(plugin.incidents()).as("the window carries on").isEqualTo(2);
        });
    }
}
