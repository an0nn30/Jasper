package dev.jasper.app.persistence;

import dev.jasper.app.layout.Child;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.Node;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.Split;
import dev.jasper.app.layout.TabId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The saved workspace, {@code workspace.toml} in the Jasper home (workspace tabs spec, section 8): each Jasper window's
 * role, bounds and maximized state, its layout, and what each of its tabs needs to come back. Writes are atomic.
 * Stateless: the caller chooses the thread; the values are immutable.
 */
public final class WorkspaceFile {
    /** Whether a window is the main window or a child window. */
    public enum Role { MAIN, CHILD }

    /** What a tab needs to come back (spec 13): a terminal's title, directory and profile, or a plugin's restore state. */
    public sealed interface TabRecord permits TabRecord.Terminal, TabRecord.Content {
        /**
         * The tab's saved title.
         *
         * @return the title; empty for a terminal tab that was not renamed
         */
        String title();

        /**
         * A terminal tab ({@code kind = "terminal"}).
         *
         * @param title its renamed title, or empty
         * @param directory its last local directory, or empty
         * @param profile its shell profile's id, or empty
         */
        record Terminal(String title, String directory, String profile) implements TabRecord {
            /** Checks every value is present. */
            public Terminal {
                Objects.requireNonNull(title, "title");
                Objects.requireNonNull(directory, "directory");
                Objects.requireNonNull(profile, "profile");
            }
        }

        /**
         * A plugin's content tab ({@code kind = "content:<plugin id>:<restorer kind>"}).
         *
         * @param title its title when saved
         * @param pluginId the plugin that restores it: not empty, no colon
         * @param restorer the plugin's restorer kind: not empty
         * @param state the restore state the plugin gave when it opened the tab
         */
        record Content(String title, String pluginId, String restorer, Map<String, String> state) implements TabRecord {
            /** Checks the ids and copies the state. */
            public Content {
                Objects.requireNonNull(title, "title");
                if (pluginId.isEmpty() || pluginId.indexOf(':') >= 0) throw new IllegalArgumentException("Not a plugin id: " + pluginId);
                if (restorer.isEmpty()) throw new IllegalArgumentException("A content tab names its restorer kind");
                state = Map.copyOf(state);
            }
        }
    }

    /**
     * One window as saved.
     *
     * @param role main or child
     * @param bounds its normal bounds; empty places it as a new window
     * @param extendedState its maximized bits only (2 and 4), as {@code UiState.MainWindow} keeps them
     * @param layout its workspace; only the main window's may be empty
     * @param tabs a record for exactly each tab of the layout
     */
    public record WindowRecord(Role role, Optional<UiState.Bounds> bounds, int extendedState, Layout layout, Map<TabId, TabRecord> tabs) {
        /** Copies the records and checks them against the layout. */
        public WindowRecord {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(layout, "layout");
            if ((extendedState & ~6) != 0) throw new IllegalArgumentException("Only the maximized bits are kept: " + extendedState);
            tabs = Map.copyOf(tabs);
            if (!tabs.keySet().equals(Set.copyOf(layout.tabs())))
                throw new IllegalArgumentException("A window's tab records name exactly its layout's tabs");
            if (role == Role.CHILD && layout.isEmpty()) throw new IllegalArgumentException("Only the main window's workspace may be empty");
        }
    }

    /**
     * Every Jasper window to restore, the main window first.
     *
     * @param windows exactly one main window and any child windows; no group or tab in two windows
     */
    public record Snapshot(List<WindowRecord> windows) {
        /** Puts the main window first and checks the ids. */
        public Snapshot {
            var ordered = new ArrayList<WindowRecord>();
            for (WindowRecord window : windows) if (window.role() == Role.MAIN) ordered.add(window);
            if (ordered.size() != 1) throw new IllegalArgumentException("A workspace has exactly one main window, not " + ordered.size());
            for (WindowRecord window : windows) if (window.role() == Role.CHILD) ordered.add(window);
            var groups = new HashSet<GroupId>();
            var tabs = new HashSet<TabId>();
            for (WindowRecord window : ordered) {
                for (Group group : window.layout().groups())
                    if (!groups.add(group.id())) throw new IllegalArgumentException("Group " + group.id() + " is in two windows");
                for (TabId tab : window.layout().tabs())
                    if (!tabs.add(tab)) throw new IllegalArgumentException("Tab " + tab + " is in two windows");
            }
            windows = List.copyOf(ordered);
        }

        /**
         * The main window.
         *
         * @return the first window
         */
        public WindowRecord main() {
            return windows.getFirst();
        }

        /**
         * The child windows.
         *
         * @return every window after the main window, in order
         */
        public List<WindowRecord> children() {
            return windows.subList(1, windows.size());
        }
    }

    private static final String CONTENT = "content:";
    /** Restore-state keys written bare; any other key is quoted (workspace plan 2, Rulings 16). */
    private static final Pattern BARE = Pattern.compile("[A-Za-z0-9_-]+");

    private WorkspaceFile() { }

    /**
     * Writes {@code snapshot} to {@code file} atomically, through a temporary file beside it.
     *
     * @param file the workspace file
     * @param snapshot what to save
     * @throws IOException when the file cannot be written
     */
    public static void write(Path file, Snapshot snapshot) throws IOException {
        TomlStateFile.writeAtomically(file, ".workspace-", encode(snapshot));
    }

    /** The file's text: the windows in order, each window's nodes parents first, then its tabs in workspace order. */
    static String encode(Snapshot snapshot) {
        var text = new StringBuilder("version = 1\n");
        for (WindowRecord window : snapshot.windows()) {
            Layout layout = window.layout();
            text.append("\n[[windows]]\nrole = \"").append(window.role() == Role.MAIN ? "main" : "child").append("\"\n");
            window.bounds().ifPresent(bounds -> text.append("x = ").append(bounds.x()).append("\ny = ").append(bounds.y())
                .append("\nwidth = ").append(bounds.width()).append("\nheight = ").append(bounds.height()).append('\n'));
            text.append("extended_state = ").append(window.extendedState())
                .append("\nactive_group = \"").append(layout.activeGroup().value())
                .append("\"\nmaximized = \"").append(layout.maximized().map(id -> id.value().toString()).orElse("")).append("\"\n");
            appendNode(text, layout.root(), "", 1.0, "");
            for (TabId tab : layout.tabs()) appendTab(text, tab, window.tabs().get(tab));
        }
        return text.toString();
    }

    /**
     * A split's id in the file (workspace plan 2, Rulings 3): the name-based UUID of {@code "jasper.split:"} and its path, the child indexes
     * from the root joined with dots ({@code ""} for the root, {@code "1.0"} for the first child of the root's second).
     */
    static String splitId(String path) {
        return UUID.nameUUIDFromBytes(("jasper.split:" + path).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static void appendNode(StringBuilder text, Node node, String parent, double weight, String path) {
        switch (node) {
            case Split split -> {
                String id = splitId(path);
                text.append("\n[[windows.nodes]]\nid = \"").append(id).append("\"\nkind = \"split\"\norientation = \"")
                    .append(split.orientation() == Orientation.HORIZONTAL ? "horizontal" : "vertical")
                    .append("\"\nparent = \"").append(parent).append("\"\nweight = ").append(weight).append('\n');
                for (int i = 0; i < split.children().size(); i++) {
                    Child child = split.children().get(i);
                    appendNode(text, child.node(), id, child.weight(), path.isEmpty() ? Integer.toString(i) : path + "." + i);
                }
            }
            case Group group -> text.append("\n[[windows.nodes]]\nid = \"").append(group.id().value())
                .append("\"\nkind = \"group\"\nparent = \"").append(parent).append("\"\nweight = ").append(weight)
                .append("\ntabs = ").append(group.tabs().stream().map(tab -> "\"" + tab.value() + "\"").collect(Collectors.joining(", ", "[", "]")))
                .append("\nselected = \"").append(group.selected().map(tab -> tab.value().toString()).orElse("")).append("\"\n");
        }
    }

    private static void appendTab(StringBuilder text, TabId id, TabRecord saved) {
        text.append("\n[[windows.tabs]]\nid = \"").append(id.value()).append("\"\n");
        switch (saved) {
            case TabRecord.Terminal terminal -> text.append("kind = \"terminal\"\ntitle = ").append(UiState.quote(terminal.title()))
                .append("\ndirectory = ").append(UiState.quote(terminal.directory()))
                .append("\nprofile = ").append(UiState.quote(terminal.profile())).append('\n');
            case TabRecord.Content content -> text.append("kind = ").append(UiState.quote(CONTENT + content.pluginId() + ":" + content.restorer()))
                .append("\ntitle = ").append(UiState.quote(content.title()))
                .append("\nstate = ").append(inline(content.state())).append('\n');
        }
    }

    /** A restore state as one inline table, in key order. */
    private static String inline(Map<String, String> state) {
        if (state.isEmpty()) return "{}";
        return new TreeMap<>(state).entrySet().stream()
            .map(entry -> (BARE.matcher(entry.getKey()).matches() ? entry.getKey() : UiState.quote(entry.getKey())) + " = " + UiState.quote(entry.getValue()))
            .collect(Collectors.joining(", ", "{ ", " }"));
    }
}
