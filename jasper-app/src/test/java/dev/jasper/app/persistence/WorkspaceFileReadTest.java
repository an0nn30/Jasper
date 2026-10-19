package dev.jasper.app.persistence;

import static dev.jasper.app.persistence.WorkspaceFileTest.BOUNDS;
import static dev.jasper.app.persistence.WorkspaceFileTest.group;
import static dev.jasper.app.persistence.WorkspaceFileTest.groupId;
import static dev.jasper.app.persistence.WorkspaceFileTest.snapshot;
import static dev.jasper.app.persistence.WorkspaceFileTest.tab;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.layout.Child;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.persistence.WorkspaceFile.Loaded;
import dev.jasper.app.persistence.WorkspaceFile.Role;
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

/** Workspace tabs spec 8 and 17: reading the workspace file back, dropping bad entries alone, setting a bad file aside. */
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

    /** {@code text} with {@code anchor}, which must occur exactly once, replaced. */
    static String replaceOnce(String text, String anchor, String replacement) {
        assertThat(text.split(Pattern.quote(anchor), -1)).as("occurrences of %s", anchor).hasSize(2);
        return text.replace(anchor, replacement);
    }

    static String id(TabId tab) {
        return tab.value().toString();
    }

    /** {@link WorkspaceFileTest#snapshot()} with its main window changed. */
    static Snapshot withMain(Optional<UiState.Bounds> bounds, int extendedState, Layout layout, Map<TabId, TabRecord> tabs) {
        return new Snapshot(List.of(new WindowRecord(Role.MAIN, bounds, extendedState, layout, tabs), snapshot().children().getFirst()));
    }

    static Map<TabId, TabRecord> without(Map<TabId, TabRecord> tabs, TabId tab) {
        var rest = new HashMap<>(tabs);
        rest.remove(tab);
        return rest;
    }

    static Map<TabId, TabRecord> terminals(Layout layout) {
        var tabs = new HashMap<TabId, TabRecord>();
        for (TabId tab : layout.tabs()) tabs.put(tab, new TabRecord.Terminal("", "/tmp", ""));
        return tabs;
    }

    /** A window of groups {@code first} and {@code second} side by side, each holding the tab of its number. */
    static WindowRecord window(Role role, int first, int second) {
        Layout layout = new Layout(new Split(Orientation.HORIZONTAL, List.of(new Child(group(first, first, first), 0.5),
            new Child(group(second, second, second), 0.5))), groupId(first), Optional.empty());
        return new WindowRecord(role, Optional.of(BOUNDS), 0, layout, terminals(layout));
    }

    @Test void aWrittenWorkspaceReadsBackEqual() throws Exception {
        WorkspaceFile.write(file(), snapshot());
        assertThat(WorkspaceFile.read(file())).isEqualTo(new Loaded.Restore(snapshot()));
    }

    @Test void theEmptyMainWorkspaceReadsBack() throws Exception {
        var empty = new Snapshot(List.of(new WindowRecord(Role.MAIN, Optional.of(BOUNDS), 0, Layout.empty(), Map.of())));
        WorkspaceFile.write(file(), empty);
        assertThat(WorkspaceFile.read(file())).isEqualTo(new Loaded.Restore(empty));
    }

    @Test void stringsWithQuotesBackslashesNewlinesAndOddKeysRoundTrip() throws Exception {
        Layout layout = window(Role.MAIN, 1, 2).layout();
        var odd = new Snapshot(List.of(new WindowRecord(Role.MAIN, Optional.of(BOUNDS), 0, layout, Map.of(
            tab(1), new TabRecord.Terminal("say \"hi\"\tnow\nplease", "C:\\Users\\me\\caf\u00e9", "pwsh 7"),
            tab(2), new TabRecord.Content("", "dev.jasper.sample", "about",
                Map.of("a b", "x\\y", "dotted.key", "\"quoted\"", "plain", "line\nbreak", "_-9", ""))))));
        WorkspaceFile.write(file(), odd);
        assertThat(Files.readString(file()))
            .contains("state = { _-9 = \"\", \"a b\" = \"x\\\\y\", \"dotted.key\" = \"\\\"quoted\\\"\", plain = \"line\\u000Abreak\" }");
        assertThat(WorkspaceFile.read(file())).isEqualTo(new Loaded.Restore(odd));
    }

    @Test void rewritingAReadFileWritesTheSameText() throws Exception {
        Layout drifting = new Layout(new Split(Orientation.HORIZONTAL, List.of(new Child(group(1, 1, 1), 1), new Child(group(2, 2, 2), 1),
            new Child(new Split(Orientation.VERTICAL, List.of(new Child(group(3, 3, 3), 0.1), new Child(group(4, 4, 4), 0.2),
                new Child(group(5, 5, 5), 0.7))), 1))), groupId(1), Optional.empty());
        var saved = new Snapshot(List.of(new WindowRecord(Role.MAIN, Optional.of(BOUNDS), 0, drifting, terminals(drifting))));
        WorkspaceFile.write(file(), saved);
        String first = Files.readString(file());
        var restored = (Loaded.Restore) WorkspaceFile.read(file());
        assertThat(restored.snapshot()).isEqualTo(saved);
        WorkspaceFile.write(file(), restored.snapshot());
        assertThat(Files.readString(file())).isEqualTo(first);
    }

    @Test void weightsAreNormalizedOnRead() throws Exception {
        String text = WorkspaceFile.encode(new Snapshot(List.of(window(Role.MAIN, 1, 2))));
        text = replaceOnce(text, "weight = 0.5\ntabs = [\"" + id(tab(1)), "weight = 3\ntabs = [\"" + id(tab(1)));
        text = replaceOnce(text, "weight = 0.5\ntabs = [\"" + id(tab(2)), "weight = 1.0\ntabs = [\"" + id(tab(2)));
        var restored = (Loaded.Restore) read(text);
        assertThat(((Split) restored.snapshot().main().layout().root()).children()).extracting(Child::weight).containsExactly(0.75, 0.25);
    }

    @Test void boundsAndTheChoicesFallBackAlone() throws Exception {
        String text = WorkspaceFile.encode(snapshot());
        WindowRecord main = snapshot().main();
        Layout layout = main.layout();
        String g1 = groupId(1).value().toString(), g2 = groupId(2).value().toString();
        assertThat(read(replaceOnce(text, "width = 1400", "width = 0"))).as("bounds")
            .isEqualTo(new Loaded.Restore(withMain(Optional.empty(), 6, layout, main.tabs())));
        assertThat(read(replaceOnce(text, "extended_state = 6", "extended_state = 1"))).as("extended state")
            .isEqualTo(new Loaded.Restore(withMain(main.bounds(), 0, layout, main.tabs())));
        assertThat(read(replaceOnce(text, "active_group = \"" + g2 + "\"", "active_group = \"nope\""))).as("the first group; the maximize no longer names it")
            .isEqualTo(new Loaded.Restore(withMain(main.bounds(), 6, new Layout(layout.root(), groupId(1), Optional.empty()), main.tabs())));
        assertThat(read(replaceOnce(text, "maximized = \"" + g2 + "\"", "maximized = \"" + g1 + "\""))).as("a maximize of a group that is not active")
            .isEqualTo(new Loaded.Restore(withMain(main.bounds(), 6, new Layout(layout.root(), groupId(2), Optional.empty()), main.tabs())));
        Split root = (Split) layout.root();
        var firstTab = new Split(Orientation.HORIZONTAL, List.of(new Child(group(1, 1, 1, 2), 0.5), root.children().get(1)));
        assertThat(read(replaceOnce(text, "selected = \"" + id(tab(2)) + "\"", "selected = \"" + id(tab(9)) + "\""))).as("the first tab")
            .isEqualTo(new Loaded.Restore(withMain(main.bounds(), 6, new Layout(firstTab, groupId(2), Optional.of(groupId(2))), main.tabs())));
    }

    @Test void aBadTabEntryIsDroppedAlone() throws Exception {
        String text = WorkspaceFile.encode(snapshot());
        WindowRecord main = snapshot().main();
        String t1 = "id = \"" + id(tab(1)) + "\"\n";
        var droppedTerminal = new Loaded.Restore(withMain(main.bounds(), 6, main.layout().remove(tab(1)), without(main.tabs(), tab(1))));
        for (String[] change : new String[][] {
                {"directory = \"/Users/me/src/jasper\"", "directory = 5"},
                {t1 + "kind = \"terminal\"", t1 + "kind = \"telnet\""},
                {"title = \"build\"\n", ""},
                {"profile = \"zsh\"", "profile = [\"zsh\"]"},
                {t1, "id = \"one\"\n"}})
            assertThat(read(replaceOnce(text, change[0], change[1]))).as(change[1]).isEqualTo(droppedTerminal);
        String state = "state = { path = \"/Users/me/src/jasper/README.md\" }";
        String kind = "\"content:dev.jasper.editor:document\"";
        var droppedContent = new Loaded.Restore(withMain(main.bounds(), 6, main.layout().remove(tab(2)), without(main.tabs(), tab(2))));
        for (String[] change : new String[][] {
                {state, "state = { path = 1 }"},
                {state, "state = \"/Users/me/src/jasper/README.md\""},
                {kind, "\"content:dev.jasper.editor\""},
                {kind, "\"content::document\""},
                {kind, "\"content:dev.jasper.editor:\""}})
            assertThat(read(replaceOnce(text, change[0], change[1]))).as(change[1]).isEqualTo(droppedContent);
        String second = "\n[[windows.tabs]]\nid = \"" + id(tab(2)) + "\"\nkind = \"terminal\"\ntitle = \"\"\ndirectory = \"\"\nprofile = \"\"\n";
        assertThat(read(replaceOnce(text, "\n[[windows]]\nrole = \"child\"", second + "\n[[windows]]\nrole = \"child\"")))
            .as("the first entry for an id wins").isEqualTo(new Loaded.Restore(snapshot()));
    }

    @Test void aGroupEmptiedByADroppedTabGoesAsRemoveWould() throws Exception {
        WindowRecord main = snapshot().main();
        Layout expected = main.layout().remove(tab(3));
        assertThat(expected.root()).isEqualTo(new Split(Orientation.HORIZONTAL, List.of(new Child(group(1, 2, 1, 2), 0.5), new Child(group(3, 4, 4), 0.5))));
        assertThat(expected.activeGroup()).isEqualTo(groupId(1));
        assertThat(expected.maximized()).isEmpty();
        String t3 = "id = \"" + id(tab(3)) + "\"\n";
        assertThat(read(replaceOnce(WorkspaceFile.encode(snapshot()), t3 + "kind = \"terminal\"", t3 + "kind = \"telnet\"")))
            .isEqualTo(new Loaded.Restore(withMain(main.bounds(), 6, expected, without(main.tabs(), tab(3)))));
    }

    @Test void aTabWithoutAnEntryIsDroppedAndAnEntryNoGroupListsIsIgnored() throws Exception {
        WindowRecord main = snapshot().main();
        Layout expected = main.layout().remove(tab(4));
        assertThat(expected.root()).isEqualTo(new Split(Orientation.HORIZONTAL, List.of(new Child(group(1, 2, 1, 2), 0.5), new Child(group(2, 3, 3), 0.5))));
        assertThat(read(replaceOnce(WorkspaceFile.encode(snapshot()), "id = \"" + id(tab(4)) + "\"\n", "id = \"" + id(tab(9)) + "\"\n")))
            .isEqualTo(new Loaded.Restore(withMain(main.bounds(), 6, expected, without(main.tabs(), tab(4)))));
    }

    @Test void aBrokenTreeDropsItsWindowAlone() throws Exception {
        String text = WorkspaceFile.encode(new Snapshot(List.of(window(Role.MAIN, 1, 2), window(Role.CHILD, 3, 4))));
        var mainOnly = new Loaded.Restore(new Snapshot(List.of(window(Role.MAIN, 1, 2))));
        String g3 = groupId(3).value().toString(), g4 = groupId(4).value().toString(), t3 = id(tab(3)), t4 = id(tab(4));
        String split = "kind = \"split\"\norientation = \"horizontal\"\nparent = \"\"\nweight = 1.0\n\n[[windows.nodes]]\nid = \"" + g3 + "\"";
        String g4Node = "id = \"" + g4 + "\"\nkind = \"group\"\nparent = \"" + WorkspaceFile.splitId("") + "\"";
        String g4Weight = "weight = 0.5\ntabs = [\"" + t4 + "\"]";
        String g3Tabs = "tabs = [\"" + t3 + "\"]";
        for (String[] change : new String[][] {
                {"role = \"child\"", "role = \"side\""},
                {split, split.replace("kind = \"split\"", "kind = \"pane\"")},
                {split, split.replace("horizontal", "diagonal")},
                {g4Weight, g4Weight.replace("0.5", "-1.0")},
                {g4Weight, g4Weight.replace("0.5", "\"half\"")},
                {g4Node, "id = \"" + g4 + "\"\nkind = \"group\"\nparent = \"\""},
                {g4Node, "id = \"" + g4 + "\"\nkind = \"group\"\nparent = \"" + g3 + "\""},
                {g4Node, "id = \"" + g4 + "\"\nkind = \"group\"\nparent = \"00000000-0000-4000-9000-000000000099\""},
                {g3Tabs, "tabs = [\"" + t3 + "\", \"" + t3 + "\"]"},
                {g3Tabs, "tabs = \"" + t3 + "\""},
                {g3Tabs, "tabs = [\"three\"]"},
                {g3Tabs + "\nselected = \"" + t3 + "\"", "tabs = []\nselected = \"\""},
                {"id = \"" + g3 + "\"", "id = \"group-three\""}})
            assertThat(read(replaceOnce(text, change[0], change[1]))).as(change[1]).isEqualTo(mainOnly);
        String cycle = "\n[[windows.nodes]]\nid = \"x\"\nkind = \"split\"\norientation = \"vertical\"\nparent = \"y\"\nweight = 0.5\n"
            + "\n[[windows.nodes]]\nid = \"y\"\nkind = \"split\"\norientation = \"horizontal\"\nparent = \"x\"\nweight = 0.5\n";
        assertThat(read(text + cycle)).as("a cycle under no root").isEqualTo(mainOnly);
    }

    @Test void aTreeDeeperThanTheLimitDropsItsWindow() throws Exception {
        var main = new WindowRecord(Role.MAIN, Optional.of(BOUNDS), 0, Layout.of(tab(1)), Map.of(tab(1), new TabRecord.Terminal("", "/tmp", "")));
        for (int depth : new int[] {WorkspaceFile.MAX_DEPTH, WorkspaceFile.MAX_DEPTH + 1}) {
            Layout nested = Layout.of(tab(100));
            for (int i = 1; i <= depth; i++)
                nested = nested.split(tab(100 + i), nested.groupOf(tab(99 + i)).orElseThrow(), i % 2 == 0 ? Side.RIGHT : Side.DOWN);
            var saved = new Snapshot(List.of(main, new WindowRecord(Role.CHILD, Optional.empty(), 0, nested, terminals(nested))));
            WorkspaceFile.write(file(), saved);
            assertThat(WorkspaceFile.read(file())).as("depth %d", depth)
                .isEqualTo(new Loaded.Restore(depth > WorkspaceFile.MAX_DEPTH ? new Snapshot(List.of(main)) : saved));
        }
    }

    @Test void windowsWhoseRootSplitsShareAnIdAreReadApart() throws Exception {
        var both = new Snapshot(List.of(window(Role.MAIN, 1, 2), window(Role.CHILD, 3, 4)));
        WorkspaceFile.write(file(), both);
        assertThat(Files.readString(file()).split(Pattern.quote(WorkspaceFile.splitId("")), -1))
            .as("each window's root split names itself and is the parent of its two groups").hasSize(7);
        assertThat(WorkspaceFile.read(file())).isEqualTo(new Loaded.Restore(both));
    }

    @Test void aSecondMainWindowAnEmptiedChildAndAWindowHoldingAnotherWindowsTabAreDropped() throws Exception {
        String text = WorkspaceFile.encode(snapshot());
        var mainOnly = new Loaded.Restore(new Snapshot(List.of(snapshot().main())));
        assertThat(read(replaceOnce(text, "role = \"child\"", "role = \"main\""))).as("a second main window").isEqualTo(mainOnly);
        String t5 = "id = \"" + id(tab(5)) + "\"\n";
        assertThat(read(replaceOnce(text, t5 + "kind = \"terminal\"", t5 + "kind = \"x\""))).as("a child window left empty").isEqualTo(mainOnly);
        assertThat(text.split(Pattern.quote(id(tab(5))), -1)).hasSize(4);
        assertThat(read(text.replace(id(tab(5)), id(tab(1))))).as("a tab the main window holds").isEqualTo(mainOnly);
    }

    /** {@code text}, a main window then a child window, with the child window listed first. */
    static String childFirst(String text) {
        String head = "version = 1\n";
        int child = text.indexOf("\n[[windows]]\nrole = \"child\"");
        assertThat(child).as("the child window's entry").isGreaterThan(head.length());
        return head + text.substring(child) + text.substring(head.length(), child);
    }

    @Test void aChildListedBeforeTheMainWindowIsTheOneDroppedWhenTheirIdsClash() throws Exception {
        String text = WorkspaceFile.encode(new Snapshot(List.of(window(Role.MAIN, 1, 2), window(Role.CHILD, 3, 4))));
        var mainOnly = new Loaded.Restore(new Snapshot(List.of(window(Role.MAIN, 1, 2))));
        assertThat(read(childFirst(text))).as("no clash: both windows, the main window first")
            .isEqualTo(new Loaded.Restore(new Snapshot(List.of(window(Role.MAIN, 1, 2), window(Role.CHILD, 3, 4)))));
        assertThat(read(childFirst(text.replace(id(tab(3)), id(tab(1)))))).as("a tab the main window holds").isEqualTo(mainOnly);
        assertThat(read(childFirst(text.replace(groupId(3).value().toString(), groupId(1).value().toString()))))
            .as("a group the main window holds").isEqualTo(mainOnly);
    }

    @Test void aChildSharingAGroupIdWithTheMainWindowIsDroppedAlone() throws Exception {
        String text = WorkspaceFile.encode(new Snapshot(List.of(window(Role.MAIN, 1, 2), window(Role.CHILD, 3, 4))));
        var mainOnly = new Loaded.Restore(new Snapshot(List.of(window(Role.MAIN, 1, 2))));
        assertThat(read(text.replace(groupId(3).value().toString(), groupId(1).value().toString())))
            .as("the groups clash and no tab does").isEqualTo(mainOnly);
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
        cases.put("version 2", replaceOnce(valid, "version = 1", "version = 2").getBytes(StandardCharsets.UTF_8));
        cases.put("no version", replaceOnce(valid, "version = 1\n", "").getBytes(StandardCharsets.UTF_8));
        cases.put("no windows", "version = 1\n".getBytes(StandardCharsets.UTF_8));
        cases.put("no main window", replaceOnce(valid, "role = \"main\"", "role = \"child\"").getBytes(StandardCharsets.UTF_8));
        cases.put("a bad main window", replaceOnce(valid, "role = \"main\"", "role = \"boss\"").getBytes(StandardCharsets.UTF_8));
        cases.put("invalid UTF-8", new byte[] {'v', (byte) 0xC3, (byte) 0x28});
        for (var entry : cases.entrySet()) {
            Files.write(file(), entry.getValue());
            assertThat(WorkspaceFile.read(file())).as(entry.getKey()).isEqualTo(new Loaded.Fresh(Optional.of(bad())));
            assertThat(file()).as(entry.getKey()).doesNotExist();
            assertThat(Files.readAllBytes(bad())).as("%s, replacing the older copy", entry.getKey()).isEqualTo(entry.getValue());
        }
    }

    @Test void aNestingThatOverflowsTheParserIsSetAside() throws Exception {
        String deep = "version = 1\nx = " + "[".repeat(250_000);
        assertThat(deep.length()).as("under the cap: the parser, not the cap, refuses it").isLessThan(WorkspaceFile.MAX_BYTES);
        assertThat(read(deep)).isEqualTo(new Loaded.Fresh(Optional.of(bad())));
    }

    @Test void theSizeCapSetsAsideALargerFile() throws Exception {
        String valid = WorkspaceFile.encode(snapshot());
        String atCap = valid + "#" + " ".repeat(WorkspaceFile.MAX_BYTES - valid.length() - 2) + "\n";
        assertThat(atCap.getBytes(StandardCharsets.UTF_8)).hasSize(WorkspaceFile.MAX_BYTES);
        assertThat(read(atCap)).isEqualTo(new Loaded.Restore(snapshot()));
        assertThat(read(atCap.replace("#", "# "))).isEqualTo(new Loaded.Fresh(Optional.of(bad())));
    }
}
