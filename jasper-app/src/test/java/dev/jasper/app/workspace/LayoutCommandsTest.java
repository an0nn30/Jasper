package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.persistence.LayoutsFile;
import dev.jasper.app.persistence.ToolWindows;
import dev.jasper.app.persistence.WorkspaceFile;
import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Full windows plan 4: Window › Layouts' commands, their dialogs (through the window's seams) and the palette's
 * Apply Layout entries.
 */
class LayoutCommandsTest {
    private TestWindows windows;
    private LayoutStore store;
    /** What the name dialog was asked, as "message|initial", in order. */
    private final List<String> asked = new ArrayList<>();
    /** What the confirmation was asked, as "message|button", in order. */
    private final List<String> confirmed = new ArrayList<>();
    /** What a window said went wrong, as "title|details". */
    private final List<String> errors = new ArrayList<>();

    @AfterEach void cleanup() throws Exception {
        if (windows != null) edt(() -> windows.manager.closeAll());
        closeOwners();
    }

    /** Windows with a layouts store in {@code folder}, whose window answers the name dialog with {@code answers} in turn. */
    private JasperWindow window(Path folder, Deque<String> answers, boolean confirm) {
        windows = new TestWindows();
        store = new LayoutStore(folder.resolve("layouts.toml"), Runnable::run);
        windows.manager.layouts(store);
        JasperWindow window = windows.manager.ensureWindow("cold", null).orElseThrow();
        seams(window.content(), answers, confirm);
        return window;
    }

    private void seams(WindowContent content, Deque<String> answers, boolean confirm) {
        content.askLayoutName = (title, message, initial) -> { asked.add(message + "|" + initial); return answers.poll(); };
        content.confirmLayout = (title, message, button) -> { confirmed.add(message + "|" + button); return confirm; };
        content.onError = (title, details) -> errors.add(title + "|" + details);
    }

    private static void run(WindowContent content, ActionId id) {
        content.updateActions();
        content.action(id).actionPerformed(new ActionEvent(content, ActionEvent.ACTION_PERFORMED, id.id()));
    }

    private static WorkspaceFile.WindowRecord layoutOf(String... titles) {
        var tabs = new ArrayList<ParentTab>();
        var records = new java.util.HashMap<TabId, WorkspaceFile.TabRecord>();
        for (String title : titles) {
            TabId id = TabId.random();
            tabs.add(new ParentTab(ParentTabId.random(), Layout.of(id)));
            records.put(id, new WorkspaceFile.TabRecord.Terminal(title, HOME.toString(), ""));
        }
        return new WorkspaceFile.WindowRecord(Optional.empty(), 0, new ParentTabs(tabs, tabs.getFirst().id()), records,
            new ToolWindows(false, true, Map.of()));
    }

    private static List<String> paletteTitles(WindowContent content) {
        return content.commands().entries().stream().map(entry -> entry.command().title()).filter(title -> title.startsWith("Apply Layout")).toList();
    }

    @Test void saveAsNewAsksForANameStartingAtCustomAndRefusesOneInUse(@TempDir Path folder) throws Exception {
        edt(() -> {
            JasperWindow window = window(folder, new ArrayDeque<>(List.of("Work", "Work", " Home ")), true);
            run(window.content(), ActionId.SAVE_LAYOUT_AS_NEW);
            assertThat(asked).containsExactly("Layout name:|Custom");
            assertThat(store.named()).extracting(LayoutsFile.Named::name).containsExactly("Work");
            asked.clear();
            run(window.content(), ActionId.SAVE_LAYOUT_AS_NEW);
            assertThat(asked).containsExactly("Layout name:|Custom",
                "A layout named “Work” already exists.\nLayout name:|Work");
            assertThat(store.named()).extracting(LayoutsFile.Named::name).as("trimmed").containsExactly("Work", "Home");
            assertThat(store.named().getLast().layout()).isEqualTo(windows.manager.layoutOf(window).orElseThrow());
            // Cancelled: nothing is saved.
            asked.clear();
            run(window.content(), ActionId.SAVE_LAYOUT_AS_NEW);
            assertThat(asked).hasSize(1);
            assertThat(store.named()).hasSize(2);
        });
        windows.launch();
    }

    @Test void aBlankOrTooLongNameIsAskedAgain(@TempDir Path folder) throws Exception {
        edt(() -> {
            JasperWindow window = window(folder, new ArrayDeque<>(List.of("  ", "x".repeat(65), "Fine")), true);
            run(window.content(), ActionId.SAVE_LAYOUT_AS_NEW);
            assertThat(asked).hasSize(3);
            assertThat(asked.get(1)).startsWith("A layout name has 1 to 64 characters.\nLayout name:");
            assertThat(store.named()).extracting(LayoutsFile.Named::name).containsExactly("Fine");
        });
        windows.launch();
    }

    @Test void saveAsNewSaysWhichTabsAreLeftOut(@TempDir Path folder) throws Exception {
        edt(() -> {
            JasperWindow window = window(folder, new ArrayDeque<>(List.of("Work")), true);
            windows.model.tabs().open(TabRegistry.APP_OWNER, "errors", "Errors", window.id(), new JPanel());
            run(window.content(), ActionId.SAVE_LAYOUT_AS_NEW);
            assertThat(asked).singleElement().satisfies(question ->
                assertThat(question).isEqualTo("Layout name:\n1 tab(s) can't be kept in a layout and are left out.|Custom"));
            assertThat(store.named().getFirst().layout().tabs()).hasSize(1);
        });
        windows.launch();
    }

    @Test void aLayoutTooLargeToSaveIsRefusedWithAMessage(@TempDir Path folder) throws Exception {
        edt(() -> {
            JasperWindow window = window(folder, new ArrayDeque<>(List.of("Big")), true);
            // A window larger than the room the fillers leave.
            window.content().currentTerminal().rename("w".repeat(8000));
            int filler = 0;
            while (store.saveAsNew("filler " + filler, layoutOf("t".repeat(2000))) == LayoutStore.Saved.SAVED) filler++;
            int before = store.named().size();
            run(window.content(), ActionId.SAVE_LAYOUT_AS_NEW);
            run(window.content(), ActionId.SAVE_LAYOUT_AS_DEFAULT);
            assertThat(store.named()).hasSize(before);
            assertThat(store.defaultLayout()).isEmpty();
            assertThat(errors).containsExactly("Layout not saved|This layout is too large to save.",
                "Layout not saved|This layout is too large to save.");
        });
        windows.launch();
    }

    @Test void saveAsDefaultSavesTheWindowsLayout(@TempDir Path folder) throws Exception {
        edt(() -> {
            JasperWindow window = window(folder, new ArrayDeque<>(), true);
            run(window.content(), ActionId.SAVE_LAYOUT_AS_DEFAULT);
            assertThat(store.defaultLayout()).contains(windows.manager.layoutOf(window).orElseThrow());
        });
        windows.launch();
    }

    @Test void deleteAndResetAskFirst(@TempDir Path folder) throws Exception {
        edt(() -> {
            JasperWindow window = window(folder, new ArrayDeque<>(), false);
            store.saveAsNew("Work", layoutOf("A"));
            store.saveAsDefault(layoutOf("B"));
            window.content().deleteLayout("Work");
            run(window.content(), ActionId.RESET_DEFAULT_LAYOUT);
            assertThat(confirmed).containsExactly("Delete the layout “Work”?|Delete",
                "Reset the default layout to the built-in layout?|Reset");
            assertThat(store.named()).hasSize(1);
            assertThat(store.defaultLayout()).isPresent();
            seams(window.content(), new ArrayDeque<>(), true);
            window.content().deleteLayout("Work");
            run(window.content(), ActionId.RESET_DEFAULT_LAYOUT);
            assertThat(store.named()).isEmpty();
            assertThat(store.defaultLayout()).isEmpty();
            window.content().updateActions();
            assertThat(window.content().action(ActionId.RESET_DEFAULT_LAYOUT).isEnabled()).as("nothing to reset").isFalse();
        });
        windows.launch();
    }

    @Test void renameUsesTheSameRules(@TempDir Path folder) throws Exception {
        edt(() -> {
            JasperWindow window = window(folder, new ArrayDeque<>(List.of("Home", "Play")), true);
            store.saveAsNew("Work", layoutOf("A"));
            store.saveAsNew("Home", layoutOf("B"));
            window.content().renameLayout("Work");
            assertThat(asked).containsExactly("Layout name:|Work",
                "A layout named “Home” already exists.\nLayout name:|Home");
            assertThat(store.named()).extracting(LayoutsFile.Named::name).containsExactly("Play", "Home");
        });
        windows.launch();
    }

    @Test void renamingALayoutRenamesItsPaletteEntry(@TempDir Path folder) throws Exception {
        edt(() -> {
            JasperWindow window = window(folder, new ArrayDeque<>(List.of("Home")), true);
            store.saveAsNew("Work", layoutOf("A"));
        });
        edt(() -> assertThat(paletteTitles(windows.first().content())).containsExactly("Apply Layout: Work"));
        edt(() -> windows.first().content().renameLayout("Work"));
        edt(() -> assertThat(paletteTitles(windows.first().content())).containsExactly("Apply Layout: Home"));
        edt(() -> {
            seams(windows.first().content(), new ArrayDeque<>(), true);
            windows.first().content().deleteLayout("Home");
        });
        edt(() -> assertThat(paletteTitles(windows.first().content())).isEmpty());
        windows.launch();
    }

    @Test void applyingANamedLayoutFromThePaletteReplacesTheActiveWindow(@TempDir Path folder) throws Exception {
        edt(() -> {
            JasperWindow window = window(folder, new ArrayDeque<>(), true);
            store.saveAsNew("Work", layoutOf("C", "D"));
        });
        edt(() -> {
            WindowContent content = windows.first().content();
            List<Tab> before = content.tabs();
            var command = content.commands().find(LayoutPalette.id("Work")).orElseThrow();
            command.action().actionPerformed(new ActionEvent(content, ActionEvent.ACTION_PERFORMED, "palette"));
            assertThat(content.tabs()).extracting(Tab::title).containsExactly("C", "D");
            assertThat(before).allSatisfy(tab -> assertThat(tab.closed()).isTrue());
            assertThat(content.toolWindows().railVisible()).isFalse();
            assertThat(errors).isEmpty();
        });
        windows.launch();
    }

    @Test void defaultAppliesTheDefaultLayout(@TempDir Path folder) throws Exception {
        edt(() -> {
            JasperWindow window = window(folder, new ArrayDeque<>(), true);
            store.saveAsDefault(layoutOf("E"));
            run(window.content(), ActionId.APPLY_DEFAULT_LAYOUT);
            assertThat(window.content().tabs()).extracting(Tab::title).containsExactly("E");
        });
        windows.launch();
    }

    /** Ruling 3: a layout that fails after the guards agreed says so. */
    @Test void aLayoutThatCannotBeAppliedSaysSo(@TempDir Path folder) throws Exception {
        edt(() -> {
            JasperWindow window = window(folder, new ArrayDeque<>(), true);
            store.saveAsNew("Broken", layoutOf("C"));
            // Closing the window's tabs fails after their guards agreed.
            windows.model.tabs().open("dev.example.editor", "b", "B", window.id(), new JPanel())
                .onClosed(() -> { throw new IllegalStateException("closing failed"); });
            window.content().applyLayout("Broken");
            assertThat(errors).containsExactly("Layout not applied|Jasper could not apply the layout \u201cBroken\u201d.");
        });
        windows.launch();
    }
}
