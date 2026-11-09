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
            MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[]{owner.get().menuBar(), shown.get()});

            section.get().set(twoDownloads("a.zip \u2014 40%"));
            assertThat(menu(owner.get(), "Downloads")).as("no rebuild while the selection path is set").isSameAs(shown.get());
            assertThat(items(shown.get())).containsExactly("Clear All", "---", "submenu:a.zip \u2014 40%");
            section.get().set(twoDownloads("a.zip \u2014 50%"));
            assertThat(((JMenu) shown.get().getMenuComponent(2)).getText()).as("a label still changes in place")
                .isEqualTo("a.zip \u2014 50%");
            MenuSelectionManager.defaultManager().clearSelectedPath();
            assertThat(menu(owner.get(), "Downloads")).as("the rebuild waits for the turn after the menu closed").isSameAs(shown.get());
        });
        edt(() -> {
            JMenu rebuilt = menu(owner.get(), "Downloads");
            assertThat(rebuilt).as("rebuilt once the menu closed").isNotSameAs(shown.get());
            assertThat(items(rebuilt)).containsExactly("Clear All", "---", "submenu:a.zip \u2014 50%", "submenu:b.zip \u2014 1.2 MB");
            shown.set(rebuilt);

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
