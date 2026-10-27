package dev.jasper.app.persistence;

import static dev.jasper.app.persistence.WorkspaceFileTest.BOUNDS;
import static dev.jasper.app.persistence.WorkspaceFileTest.group;
import static dev.jasper.app.persistence.WorkspaceFileTest.groupId;
import static dev.jasper.app.persistence.WorkspaceFileTest.one;
import static dev.jasper.app.persistence.WorkspaceFileTest.parentTabId;
import static dev.jasper.app.persistence.WorkspaceFileTest.parentTabs;
import static dev.jasper.app.persistence.WorkspaceFileTest.snapshot;
import static dev.jasper.app.persistence.WorkspaceFileTest.single;
import static dev.jasper.app.persistence.WorkspaceFileTest.tab;
import static dev.jasper.app.persistence.WorkspaceFileTest.terminals;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.dock.Slot;
import dev.jasper.app.layout.Child;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.persistence.WorkspaceFile.Loaded;
import dev.jasper.app.persistence.WorkspaceFile.Snapshot;
import dev.jasper.app.persistence.WorkspaceFile.TabRecord;
import dev.jasper.app.persistence.WorkspaceFile.WindowRecord;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Workspace tabs spec 8 and 17, tabs and panes spec 4 and 12: reading the workspace file back, dropping bad entries alone,
 * setting a bad file aside.
 */
class WorkspaceFileReadTest {
    @TempDir Path dir;

    Path file() {
        return dir.resolve("workspace.toml");
    }

    Path bad() {
        return dir.resolve("workspace.toml.bad");
    }

    Loaded read(String text) throws Exception {
        Files.writeString(file(), text, StandardCharsets.UTF_8);
        return WorkspaceFile.read(file());
    }

    /** A restore of {@code snapshot} that dropped nothing. */
    static Loaded.Restore clean(Snapshot snapshot) {
        return new Loaded.Restore(snapshot, Optional.empty());
    }

    /** A restore of {@code snapshot} that dropped a tab or an item entry and kept the original file as {@code workspace.toml.bad}. */
    Loaded.Restore keptAside(Snapshot snapshot) {
        return new Loaded.Restore(snapshot, Optional.of(bad()));
    }

    /** {@code text} with {@code anchor}, which must occur exactly once, replaced. */
    static String replaceOnce(String text, String anchor, String replacement) {
        assertThat(text.split(Pattern.quote(anchor), -1)).as("occurrences of %s", anchor).hasSize(2);
        return text.replace(anchor, replacement);
    }

    static String id(TabId tab) {
        return tab.value().toString();
    }

    /** {@link WorkspaceFileTest#snapshot()} with its window's one parent tab, or what it keeps, changed. */
    static Snapshot withMain(Optional<UiState.Bounds> bounds, int extendedState, Layout layout, Map<TabId, TabRecord> tabs) {
        return new Snapshot(new WindowRecord(bounds, extendedState, one(1, layout), tabs, ToolWindows.BUILT_IN));
    }

    /** The layout a window shows: its shown parent tab's. */
    static Layout shown(WindowRecord window) { return window.layout().activeParentTab().layout(); }

    static Map<TabId, TabRecord> without(Map<TabId, TabRecord> tabs, TabId tab) {
        var rest = new HashMap<>(tabs);
        rest.remove(tab);
        return rest;
    }

    /**
     * A window whose one parent tab, numbered {@code first}, holds groups {@code first} and {@code second} side by side,
     * each holding the tab of its number.
     */
    static WindowRecord window(int first, int second) {
        ParentTabs layout = one(first, new Layout(new Split(Orientation.HORIZONTAL, List.of(new Child(group(first, first), 0.5),
            new Child(group(second, second), 0.5))), groupId(first), Optional.empty()));
        return new WindowRecord(Optional.of(BOUNDS), 0, layout, terminals(layout), ToolWindows.BUILT_IN);
    }

    @Test void aWrittenWorkspaceReadsBackEqual() throws Exception {
        WorkspaceFile.write(file(), snapshot());
        assertThat(WorkspaceFile.read(file())).isEqualTo(clean(snapshot()));
    }

    @Test void theEmptyWorkspaceReadsBack() throws Exception {
        var empty = new Snapshot(new WindowRecord(Optional.of(BOUNDS), 0, one(1, Layout.empty()), Map.of(), ToolWindows.BUILT_IN));
        WorkspaceFile.write(file(), empty);
        assertThat(WorkspaceFile.read(file())).isEqualTo(clean(empty));
    }

    @Test void stringsWithQuotesBackslashesNewlinesAndOddKeysRoundTrip() throws Exception {
        ParentTabs layout = window(1, 2).layout();
        var odd = new Snapshot(new WindowRecord(Optional.of(BOUNDS), 0, layout, Map.of(
            tab(1), new TabRecord.Terminal("say \"hi\"\tnow\nplease", "C:\\Users\\me\\caf\u00e9", "pwsh 7"),
            tab(2), new TabRecord.Content("", "dev.jasper.sample", "about",
                Map.of("a b", "x\\y", "dotted.key", "\"quoted\"", "plain", "line\nbreak", "_-9", ""))), ToolWindows.BUILT_IN));
        WorkspaceFile.write(file(), odd);
        assertThat(Files.readString(file()))
            .contains("state = { _-9 = \"\", \"a b\" = \"x\\\\y\", \"dotted.key\" = \"\\\"quoted\\\"\", plain = \"line\\u000Abreak\" }");
        assertThat(WorkspaceFile.read(file())).isEqualTo(clean(odd));
    }

    @Test void rewritingAReadFileWritesTheSameText() throws Exception {
        Layout drifting = new Layout(new Split(Orientation.HORIZONTAL, List.of(new Child(group(1, 1), 1), new Child(group(2, 2), 1),
            new Child(new Split(Orientation.VERTICAL, List.of(new Child(group(3, 3), 0.1), new Child(group(4, 4), 0.2),
                new Child(group(5, 5), 0.7))), 1))), groupId(1), Optional.empty());
        var saved = new Snapshot(new WindowRecord(Optional.of(BOUNDS), 0, one(1, drifting), terminals(one(1, drifting)), ToolWindows.BUILT_IN));
        WorkspaceFile.write(file(), saved);
        String first = Files.readString(file());
        var restored = (Loaded.Restore) WorkspaceFile.read(file());
        assertThat(restored.snapshot()).isEqualTo(saved);
        WorkspaceFile.write(file(), restored.snapshot());
        assertThat(Files.readString(file())).isEqualTo(first);
    }

    @Test void weightsAreNormalizedOnRead() throws Exception {
        String text = WorkspaceFile.encode(new Snapshot(window(1, 2)));
        text = replaceOnce(text, "weight = 0.5\nitem = \"" + id(tab(1)), "weight = 3\nitem = \"" + id(tab(1)));
        text = replaceOnce(text, "weight = 0.5\nitem = \"" + id(tab(2)), "weight = 1.0\nitem = \"" + id(tab(2)));
        var restored = (Loaded.Restore) read(text);
        assertThat(((Split) shown(restored.snapshot().window()).root()).children()).extracting(Child::weight).containsExactly(0.75, 0.25);
    }

    @Test void boundsAndTheChoicesFallBackAlone() throws Exception {
        String text = WorkspaceFile.encode(snapshot());
        WindowRecord main = snapshot().window();
        Layout layout = shown(main);
        String g1 = groupId(1).value().toString(), g2 = groupId(2).value().toString();
        assertThat(read(replaceOnce(text, "width = 1400", "width = 0"))).as("bounds")
            .isEqualTo(clean(withMain(Optional.empty(), 6, layout, main.tabs())));
        assertThat(read(replaceOnce(text, "extended_state = 6", "extended_state = 1"))).as("extended state")
            .isEqualTo(clean(withMain(main.bounds(), 0, layout, main.tabs())));
        assertThat(read(replaceOnce(text, "active_pane = \"" + g2 + "\"", "active_pane = \"nope\""))).as("the first pane; the maximize no longer names it")
            .isEqualTo(clean(withMain(main.bounds(), 6, new Layout(layout.root(), groupId(1), Optional.empty()), main.tabs())));
        assertThat(read(replaceOnce(text, "maximized = \"" + g2 + "\"", "maximized = \"" + g1 + "\""))).as("a maximize of a group that is not active")
            .isEqualTo(clean(withMain(main.bounds(), 6, new Layout(layout.root(), groupId(2), Optional.empty()), main.tabs())));
    }

    @Test void aBadTabEntryIsDroppedAlone() throws Exception {
        String text = WorkspaceFile.encode(snapshot());
        WindowRecord main = snapshot().window();
        String t1 = "id = \"" + id(tab(1)) + "\"\n";
        var droppedTerminal = keptAside(withMain(main.bounds(), 6, shown(main).remove(tab(1)), without(main.tabs(), tab(1))));
        for (String[] change : new String[][] {
                {"directory = \"/Users/me/src/jasper\"", "directory = 5"},
                {t1 + "kind = \"terminal\"", t1 + "kind = \"telnet\""},
                {"title = \"build\"\n", ""},
                {"profile = \"zsh\"", "profile = [\"zsh\"]"},
                {t1, "id = \"one\"\n"}})
            assertThat(read(replaceOnce(text, change[0], change[1]))).as(change[1]).isEqualTo(droppedTerminal);
        String state = "state = { path = \"/Users/me/src/jasper/README.md\" }";
        String kind = "\"content:dev.jasper.editor:document\"";
        var droppedContent = keptAside(withMain(main.bounds(), 6, shown(main).remove(tab(2)), without(main.tabs(), tab(2))));
        for (String[] change : new String[][] {
                {state, "state = { path = 1 }"},
                {state, "state = \"/Users/me/src/jasper/README.md\""},
                {kind, "\"content:dev.jasper.editor\""},
                {kind, "\"content::document\""},
                {kind, "\"content:dev.jasper.editor:\""}})
            assertThat(read(replaceOnce(text, change[0], change[1]))).as(change[1]).isEqualTo(droppedContent);
        String second = "\n[[window.items]]\nid = \"" + id(tab(2)) + "\"\nkind = \"terminal\"\ntitle = \"\"\ndirectory = \"\"\nprofile = \"\"\n";
        assertThat(read(text + second))
            .as("the first entry for an id wins; the second is a dropped entry").isEqualTo(keptAside(snapshot()));
    }

    @Test void aGroupEmptiedByADroppedTabGoesAsRemoveWould() throws Exception {
        WindowRecord main = snapshot().window();
        Layout expected = shown(main).remove(tab(3));
        Split left = new Split(Orientation.VERTICAL, List.of(new Child(group(1, 1), 0.5), new Child(group(5, 2), 0.5)));
        assertThat(expected.root()).isEqualTo(new Split(Orientation.HORIZONTAL, List.of(new Child(left, 0.5), new Child(group(3, 4), 0.5))));
        assertThat(expected.activeGroup()).isEqualTo(groupId(1));
        assertThat(expected.maximized()).isEmpty();
        String t3 = "id = \"" + id(tab(3)) + "\"\n";
        assertThat(read(replaceOnce(WorkspaceFile.encode(snapshot()), t3 + "kind = \"terminal\"", t3 + "kind = \"telnet\"")))
            .isEqualTo(keptAside(withMain(main.bounds(), 6, expected, without(main.tabs(), tab(3)))));
    }

    @Test void aTabWithoutAnEntryIsDroppedAndAnEntryNoGroupListsIsIgnored() throws Exception {
        WindowRecord main = snapshot().window();
        Layout expected = shown(main).remove(tab(4));
        Split left = new Split(Orientation.VERTICAL, List.of(new Child(group(1, 1), 0.5), new Child(group(5, 2), 0.5)));
        assertThat(expected.root()).isEqualTo(new Split(Orientation.HORIZONTAL, List.of(new Child(left, 0.5), new Child(group(2, 3), 0.5))));
        assertThat(read(replaceOnce(WorkspaceFile.encode(snapshot()), "id = \"" + id(tab(4)) + "\"\n", "id = \"" + id(tab(9)) + "\"\n")))
            .isEqualTo(keptAside(withMain(main.bounds(), 6, expected, without(main.tabs(), tab(4)))));
    }

    /** A tab whose tree is broken is dropped; the window's only tab gone, the window cannot be read and is set aside. */
    @Test void aBrokenTreeSetsTheFileAside() throws Exception {
        String text = WorkspaceFile.encode(new Snapshot(window(1, 2)));
        var setAside = new Loaded.Fresh(Optional.of(bad()));
        String g1 = groupId(1).value().toString(), g2 = groupId(2).value().toString(), t1 = id(tab(1)), t2 = id(tab(2));
        String split = "kind = \"split\"\norientation = \"horizontal\"\nparent = \"\"\nweight = 1.0\n\n[[window.tabs.nodes]]\nid = \"" + g1 + "\"";
        String g2Node = "id = \"" + g2 + "\"\nkind = \"pane\"\nparent = \"" + WorkspaceFile.splitId("") + "\"";
        String g2Weight = "weight = 0.5\nitem = \"" + t2 + "\"";
        String g1Item = "item = \"" + t1 + "\"";
        for (String[] change : new String[][] {
                {split, split.replace("kind = \"split\"", "kind = \"group\"")},
                {split, split.replace("horizontal", "diagonal")},
                {g2Weight, g2Weight.replace("0.5", "-1.0")},
                {g2Weight, g2Weight.replace("0.5", "\"half\"")},
                {g2Node, "id = \"" + g2 + "\"\nkind = \"pane\"\nparent = \"\""},
                {g2Node, "id = \"" + g2 + "\"\nkind = \"pane\"\nparent = \"" + g1 + "\""},
                {g2Node, "id = \"" + g2 + "\"\nkind = \"pane\"\nparent = \"00000000-0000-4000-9000-000000000099\""},
                {g1Item, "item = [\"" + t1 + "\"]"},
                {g1Item, "item = 3"},
                {g1Item, "item = \"three\""},
                {g1Item, "item = \"\""},
                {g1Item, "note = \"no item key\""},
                {"id = \"" + g1 + "\"", "id = \"pane-one\""}}) {
            String broken = replaceOnce(text, change[0], change[1]);
            assertThat(read(broken)).as(change[1]).isEqualTo(setAside);
            assertThat(Files.readString(bad())).as(change[1]).isEqualTo(broken);
        }
        // After the items, these nodes still join the window's last tab.
        String cycle = "\n[[window.tabs.nodes]]\nid = \"x\"\nkind = \"split\"\norientation = \"vertical\"\nparent = \"y\"\nweight = 0.5\n"
            + "\n[[window.tabs.nodes]]\nid = \"y\"\nkind = \"split\"\norientation = \"horizontal\"\nparent = \"x\"\nweight = 0.5\n";
        assertThat(read(text + cycle)).as("a cycle under no root").isEqualTo(setAside);
    }

    @Test void aTreeDeeperThanTheLimitDropsItsTab() throws Exception {
        for (int depth : new int[] {WorkspaceFile.MAX_DEPTH, WorkspaceFile.MAX_DEPTH + 1}) {
            Layout nested = Layout.of(tab(100));
            for (int i = 1; i <= depth; i++)
                nested = nested.split(tab(100 + i), nested.groupOf(tab(99 + i)).orElseThrow(), i % 2 == 0 ? Side.RIGHT : Side.DOWN);
            var tabs = new ParentTabs(List.of(new ParentTab(parentTabId(1), single(1)), new ParentTab(parentTabId(2), nested)), parentTabId(1));
            var saved = new Snapshot(new WindowRecord(Optional.of(BOUNDS), 0, tabs, terminals(tabs), ToolWindows.BUILT_IN));
            var kept = ParentTabs.of(new ParentTab(parentTabId(1), single(1)));
            WorkspaceFile.write(file(), saved);
            assertThat(WorkspaceFile.read(file())).as("depth %d", depth)
                .isEqualTo(depth > WorkspaceFile.MAX_DEPTH
                    ? keptAside(new Snapshot(new WindowRecord(Optional.of(BOUNDS), 0, kept, terminals(kept), ToolWindows.BUILT_IN))) : clean(saved));
        }
    }

    /** Full windows spec 5.1: the window may be empty, so a window whose every item is dropped keeps its empty workspace. */
    @Test void aWindowEmptiedByDroppedItemsKeepsItsEmptyWorkspace() throws Exception {
        ParentTabs held = one(5, single(5));
        String text = WorkspaceFile.encode(new Snapshot(new WindowRecord(Optional.of(BOUNDS), 0, held, terminals(held), ToolWindows.BUILT_IN)));
        String t5 = "id = \"" + id(tab(5)) + "\"\n";
        var emptied = (Loaded.Restore) read(replaceOnce(text, t5 + "kind = \"terminal\"", t5 + "kind = \"x\""));
        assertThat(emptied.keptAside()).as("a dropped item keeps the file aside").contains(bad());
        assertThat(emptied.snapshot().window().layout().isEmpty()).as("its empty workspace").isTrue();
    }

    /** {@link WorkspaceFileTest#parentTabs()}'s text, its first tab's only item an entry that cannot be read. */
    static String firstTabBroken() {
        String t1 = "id = \"" + id(tab(1)) + "\"\nkind = ";
        return replaceOnce(WorkspaceFile.encode(parentTabs()), t1 + "\"terminal\"", t1 + "\"telnet\"");
    }

    @Test void aFileThatDroppedNothingKeepsNothingAside() throws Exception {
        WorkspaceFile.write(file(), snapshot());
        assertThat(WorkspaceFile.read(file())).isEqualTo(clean(snapshot()));
        assertThat(bad()).doesNotExist();
        WindowRecord main = snapshot().window();
        assertThat(read(replaceOnce(WorkspaceFile.encode(snapshot()), "width = 1400", "width = 0"))).as("bounds fall back alone")
            .isEqualTo(clean(withMain(Optional.empty(), 6, shown(main), main.tabs())));
        assertThat(bad()).as("a fallback is not a drop").doesNotExist();
    }

    @Test void aDroppedTabKeepsTheOriginalFileAside() throws Exception {
        String broken = firstTabBroken();
        Files.writeString(bad(), "an older copy");
        assertThat(read(broken)).isEqualTo(keptAside(withParentTabs(ParentTabs.of(new ParentTab(parentTabId(2), single(2))))));
        assertThat(Files.readString(bad())).as("the original text, replacing the older copy").isEqualTo(broken);
        assertThat(Files.readString(file())).as("the file itself is left in place").isEqualTo(broken);
    }

    @Test void aDroppedTabEntryOrATabWithoutOneKeepsTheOriginalFileAside() throws Exception {
        String text = WorkspaceFile.encode(snapshot());
        WindowRecord main = snapshot().window();
        String badEntry = replaceOnce(text, "directory = \"/Users/me/src/jasper\"", "directory = 5");
        assertThat(read(badEntry)).isEqualTo(keptAside(withMain(main.bounds(), 6, shown(main).remove(tab(1)), without(main.tabs(), tab(1)))));
        assertThat(Files.readString(bad())).as("a bad tab entry").isEqualTo(badEntry);
        String noEntry = replaceOnce(text, "id = \"" + id(tab(4)) + "\"\n", "id = \"" + id(tab(9)) + "\"\n");
        assertThat(read(noEntry)).isEqualTo(keptAside(withMain(main.bounds(), 6, shown(main).remove(tab(4)), without(main.tabs(), tab(4)))));
        assertThat(Files.readString(bad())).as("a tab a group lists without an entry").isEqualTo(noEntry);
    }

    @Test void aFailureToKeepTheCopyIsLoggedAndReportsNothing() throws Exception {
        Files.createDirectories(bad().resolve("occupied"));
        assertThat(read(firstTabBroken())).as("a directory stands where the copy would go")
            .isEqualTo(clean(withParentTabs(ParentTabs.of(new ParentTab(parentTabId(2), single(2))))));
        assertThat(file()).exists();
    }

    /** {@link WorkspaceFileTest#parentTabs()} with its window's parent tabs changed. */
    static Snapshot withParentTabs(ParentTabs main) {
        return new Snapshot(new WindowRecord(Optional.of(BOUNDS), 0, main, terminals(main), ToolWindows.BUILT_IN));
    }

    @Test void parentTabsReadBackInBarOrderWithTheOneThatShows() throws Exception {
        WorkspaceFile.write(file(), parentTabs());
        assertThat(WorkspaceFile.read(file())).isEqualTo(clean(parentTabs()));
    }

    @Test void parentTabsWithSplitsWeightsAndAMaximizeReadBackAndRewriteTheSameText() throws Exception {
        Layout columns = new Layout(new Split(Orientation.HORIZONTAL, List.of(new Child(group(1, 2), 0.3),
            new Child(new Split(Orientation.VERTICAL, List.of(new Child(group(2, 3), 0.6), new Child(group(3, 4), 0.4))), 0.7))),
            groupId(3), Optional.of(groupId(3)));
        var main = new ParentTabs(List.of(new ParentTab(parentTabId(1), single(5)), new ParentTab(parentTabId(2), columns),
            new ParentTab(parentTabId(3), single(6))), parentTabId(2));
        var saved = new Snapshot(new WindowRecord(Optional.of(BOUNDS), 6, main, terminals(main), ToolWindows.BUILT_IN));
        WorkspaceFile.write(file(), saved);
        String first = Files.readString(file());
        var restored = WorkspaceFile.read(file());
        assertThat(restored).isEqualTo(clean(saved));
        WorkspaceFile.write(file(), ((Loaded.Restore) restored).snapshot());
        assertThat(Files.readString(file())).isEqualTo(first);
    }

    @Test void aBadParentTabEntryIsDroppedAloneAndAParentTabItsTabsLeaveGoes() throws Exception {
        String text = WorkspaceFile.encode(parentTabs());
        ParentTabs second = ParentTabs.of(new ParentTab(parentTabId(2), single(2)));
        String p1 = "id = \"" + parentTabId(1).value() + "\"\n";
        assertThat(read(replaceOnce(text, p1, "id = \"not an id\"\n"))).as("a parent tab whose id cannot be read")
            .isEqualTo(keptAside(withParentTabs(second)));
        String t2 = "id = \"" + id(tab(2)) + "\"\nkind = \"terminal\"";
        assertThat(read(replaceOnce(text, t2, "id = \"" + id(tab(2)) + "\"\nkind = \"telnet\""))).as("the shown one's only tab dropped: the other shows")
            .isEqualTo(keptAside(withParentTabs(ParentTabs.of(new ParentTab(parentTabId(1), single(1))))));
        String active = "shown_tab = \"" + parentTabId(2).value() + "\"";
        assertThat(read(replaceOnce(text, active, "shown_tab = \"nope\""))).as("a choice that falls back to the first, not a drop")
            .isEqualTo(clean(withParentTabs(new ParentTabs(List.of(new ParentTab(parentTabId(1), single(1)), new ParentTab(parentTabId(2), single(2))),
                parentTabId(1)))));
    }

    /** Workspace plan 6a Rulings 26: only a window's only parent tab may be empty; one beside others drops alone. */
    @Test void anEmptyParentTabBesideOthersIsDroppedAlone() throws Exception {
        String text = WorkspaceFile.encode(parentTabs());
        String t1 = id(tab(1));
        assertThat(read(replaceOnce(text, "item = \"" + t1 + "\"", "item = \"\"")))
            .as("its window keeps its other parent tab").isEqualTo(keptAside(withParentTabs(ParentTabs.of(new ParentTab(parentTabId(2), single(2))))));
    }

    /** Workspace plan 6a Rulings 26: the later of two parent tabs whose ids clash is dropped alone. */
    @Test void aParentTabRepeatingAnEarlierOnesIdsIsDroppedAlone() throws Exception {
        String text = WorkspaceFile.encode(parentTabs());
        String p2 = parentTabId(2).value().toString(), g2 = groupId(2).value().toString(), t2 = id(tab(2));
        assertThat(text.split(Pattern.quote(g2), -1)).as("the second tab's active pane and its pane").hasSize(3);
        assertThat(text.split(Pattern.quote(t2), -1)).as("its pane's item and the item's entry").hasSize(3);
        var cases = new LinkedHashMap<String, String>();
        cases.put("the first one's id", replaceOnce(text, "id = \"" + p2 + "\"\n", "id = \"" + parentTabId(1).value() + "\"\n"));
        cases.put("the first one's pane", text.replace(g2, groupId(1).value().toString()));
        cases.put("the first one's item", text.replace(t2, id(tab(1))));
        for (var change : cases.entrySet())
            assertThat(read(change.getValue())).as("the window keeps its first tab when its second repeats %s", change.getKey())
                .isEqualTo(keptAside(withParentTabs(ParentTabs.of(new ParentTab(parentTabId(1), single(1))))));
    }

    /**
     * Workspace plan 6a Rulings 32 (plan 8a Rulings 2): a pane names its one item, so a tab whose pane names a list of
     * items is a bad entry, dropped alone, and the original is kept aside.
     */
    @Test void aPaneNamingAListOfItemsDropsItsTabAlone() throws Exception {
        String text = WorkspaceFile.encode(parentTabs());
        String t2 = id(tab(2));
        String twoItems = replaceOnce(text, "item = \"" + t2 + "\"", "item = [\"" + t2 + "\", \"" + id(tab(9)) + "\"]");
        assertThat(read(twoItems)).as("the window keeps its first tab, which shows")
            .isEqualTo(keptAside(withParentTabs(ParentTabs.of(new ParentTab(parentTabId(1), single(1))))));
        assertThat(Files.readString(bad())).isEqualTo(twoItems);
    }

    /**
     * Workspace plan 6a Rulings 32: a file in the parent-tabs words Task 3 wrote (a window's {@code parent_tabs}, its
     * items under {@code tabs}) holds no tab this build reads, so it is set aside and starts fresh, never misread.
     */
    @Test void aFileInTheParentTabsWordsIsSetAside() throws Exception {
        String old = WorkspaceFile.encode(parentTabs())
            .replace("[[window.tabs]]", "[[window.parent_tabs]]")
            .replace("[[window.tabs.nodes]]", "[[window.parent_tabs.nodes]]")
            .replace("[[window.items]]", "[[window.tabs]]")
            .replace("shown_tab = ", "active_parent_tab = ")
            .replace("active_pane = ", "active_group = ");
        assertThat(read(old)).isEqualTo(new Loaded.Fresh(Optional.of(bad())));
        assertThat(Files.readString(bad())).as("kept as it was").isEqualTo(old);
    }

    @Test void anAbsentFileStartsFreshAndCreatesNothing() throws Exception {
        assertThat(WorkspaceFile.read(file())).isEqualTo(new Loaded.Fresh(Optional.empty()));
        try (var listing = Files.list(dir)) {
            assertThat(listing).isEmpty();
        }
    }

    @Test void anUnreadableFileIsSetAsideAndStartsFresh() throws Exception {
        String valid = WorkspaceFile.encode(snapshot());
        var cases = new LinkedHashMap<String, byte[]>();
        cases.put("not TOML", "not toml = = =".getBytes(StandardCharsets.UTF_8));
        cases.put("empty", new byte[0]);
        cases.put("version 3", replaceOnce(valid, "version = 2", "version = 3").getBytes(StandardCharsets.UTF_8));
        cases.put("no version", replaceOnce(valid, "version = 2\n", "").getBytes(StandardCharsets.UTF_8));
        cases.put("no window", "version = 2\n".getBytes(StandardCharsets.UTF_8));
        cases.put("no window that reads", "version = 2\n\n[window]\nx = 40\n".getBytes(StandardCharsets.UTF_8));
        cases.put("the window as an array", replaceOnce(valid, "\n[window]\n", "\n[[window]]\n").getBytes(StandardCharsets.UTF_8));
        cases.put("invalid UTF-8", new byte[] {'v', (byte) 0xC3, (byte) 0x28});
        for (var entry : cases.entrySet()) {
            Files.write(file(), entry.getValue());
            assertThat(WorkspaceFile.read(file())).as(entry.getKey()).isEqualTo(new Loaded.Fresh(Optional.of(bad())));
            assertThat(file()).as(entry.getKey()).doesNotExist();
            assertThat(Files.readAllBytes(bad())).as("%s, replacing the older copy", entry.getKey()).isEqualTo(entry.getValue());
        }
    }

    @Test void aNestingThatOverflowsTheParserIsSetAside() throws Exception {
        String deep = "version = 2\nx = " + "[".repeat(250_000);
        assertThat(deep.length()).as("under the cap: the parser, not the cap, refuses it").isLessThan(WorkspaceFile.MAX_BYTES);
        assertThat(read(deep)).isEqualTo(new Loaded.Fresh(Optional.of(bad())));
    }

    @Test void theSizeCapSetsAsideALargerFile() throws Exception {
        String valid = WorkspaceFile.encode(snapshot());
        String atCap = valid + "#" + " ".repeat(WorkspaceFile.MAX_BYTES - valid.length() - 2) + "\n";
        assertThat(atCap.getBytes(StandardCharsets.UTF_8)).hasSize(WorkspaceFile.MAX_BYTES);
        assertThat(read(atCap)).isEqualTo(clean(snapshot()));
        assertThat(read(atCap.replace("#", "# "))).isEqualTo(new Loaded.Fresh(Optional.of(bad())));
    }

    /** Full windows spec 6.1: Jasper keeps no migration; a version 1 file (several windows) is no saved state, left as it is. */
    @Test void aVersionOneFileIsReadAsNoSavedStateAndLeftInPlace() throws Exception {
        String old = WorkspaceFile.encode(snapshot()).replace("version = 2\n", "version = 1\n")
            .replace("\n[window.tool_windows]\nrail_visible = true\nzen_mode = false\n", "")
            .replace("\n[window]\n", "\n[[windows]]\n").replace("[[window.", "[[windows.");
        assertThat(old).startsWith("version = 1\n\n[[windows]]\n").doesNotContain("\n[window]", "[[window.", "tool_windows");
        assertThat(read(old)).isEqualTo(new Loaded.Fresh(Optional.empty()));
        assertThat(Files.readString(file())).as("left in place, unchanged").isEqualTo(old);
        assertThat(bad()).doesNotExist();
    }

    /** Full windows spec 6.1: a bad tool-window entry falls back alone, as it did in ui-state.toml; nothing is kept aside. */
    @Test void aBadToolWindowEntryFallsBackAlone() throws Exception {
        WindowRecord main = snapshot().window();
        var tools = new ToolWindows(false, false, Map.of("", new ToolWindows.Look(Map.of(
            "dev.x.good", new UiState.Panel(Slot.LEFT_TOP, 0, true),
            "dev.x.bad", new UiState.Panel(Slot.RIGHT_TOP, 1, true)), new UiState.Dock(300, 280, 180, 0.25, 0.5, 0.75))));
        String text = WorkspaceFile.encode(new Snapshot(new WindowRecord(main.bounds(), main.extendedState(), main.layout(), main.tabs(), tools)));
        String broken = replaceOnce(replaceOnce(text, "slot = \"RIGHT_TOP\"", "slot = \"NOWHERE\""), "left_width = 300", "left_width = \"wide\"");
        var expected = new ToolWindows(false, false, Map.of("", new ToolWindows.Look(Map.of("dev.x.good", new UiState.Panel(Slot.LEFT_TOP, 0, true)),
            new UiState.Dock(UiState.Dock.DEFAULT.leftWidth(), 280, 180, 0.25, 0.5, 0.75))));
        assertThat(read(broken)).as("the panel with an unknown slot and the bad width fall back; the rest and the tabs come back")
            .isEqualTo(clean(new Snapshot(new WindowRecord(main.bounds(), main.extendedState(), main.layout(), main.tabs(), expected))));
        assertThat(read(replaceOnce(text, "zen_mode = false\n", "zen_mode = 5\n"))).as("a key of the wrong type")
            .isEqualTo(clean(new Snapshot(new WindowRecord(main.bounds(), main.extendedState(), main.layout(), main.tabs(), tools))));
        String scalar = replaceOnce(WorkspaceFile.encode(snapshot()), "\n[window.tool_windows]\nrail_visible = true\nzen_mode = false\n", "tool_windows = 5\n");
        assertThat(read(scalar)).as("a scalar where the table belongs: the built-in tool windows").isEqualTo(clean(snapshot()));
        assertThat(bad()).doesNotExist();
    }
}
