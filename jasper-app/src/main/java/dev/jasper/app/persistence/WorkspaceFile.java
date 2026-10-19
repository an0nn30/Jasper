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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * The saved workspace, {@code workspace.toml} in the Jasper home (workspace tabs spec, section 8): each Jasper window's
 * role, bounds and maximized state, its layout, and what each of its tabs needs to come back. {@link #write} writes it
 * atomically; {@link #read} never throws: it drops bad tab and window entries alone and sets an unreadable file aside as
 * {@code workspace.toml.bad}. Stateless: the caller chooses the thread; the values are immutable.
 */
public final class WorkspaceFile {
    /** Whether a window is the main window or a child window. */
    public enum Role {
        /** The main window. */
        MAIN,
        /** Any other window. */
        CHILD
    }

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

    /** What {@link #read(Path)} found. */
    public sealed interface Loaded permits Loaded.Restore, Loaded.Fresh {
        /**
         * The saved workspace, its bad entries dropped. A main window whose layout is empty restores the empty workspace.
         *
         * @param snapshot the windows to restore
         */
        record Restore(Snapshot snapshot) implements Loaded {
            /** Checks the snapshot is present. */
            public Restore {
                Objects.requireNonNull(snapshot, "snapshot");
            }
        }

        /**
         * Nothing to restore: start with one terminal tab (spec 8).
         *
         * @param setAside where an unreadable file was moved ({@code workspace.toml.bad} beside it); empty when there was
         *     no file or it could not be moved
         */
        record Fresh(Optional<Path> setAside) implements Loaded {
            /** Checks the value is present. */
            public Fresh {
                Objects.requireNonNull(setAside, "setAside");
            }
        }
    }

    /** The largest file read: {@code ui-state.toml}'s cap (workspace plan 2, Rulings 15). */
    static final int MAX_BYTES = 256 * 1024;
    /** The deepest a saved tree may nest (workspace plan 2, Rulings 14); it bounds the reader's recursion. */
    static final int MAX_DEPTH = 64;
    private static final System.Logger LOG = System.getLogger(WorkspaceFile.class.getName());

    /**
     * Reads {@code file}; never throws, not even when tomlj's parser overflows the stack on deep nesting (workspace plan 2,
     * Rulings 13 and 14). A bad tab entry, or a tab a group lists without one, is dropped as {@link Layout#remove(TabId)}
     * drops it; a bad window entry is dropped; bounds and choices fall back alone. A file that cannot be read, does not
     * parse, has another version or keeps no main window is moved to {@code workspace.toml.bad} beside it, replacing an
     * older one.
     *
     * @param file the workspace file
     * @return the windows to restore, or that Jasper starts fresh and what was set aside
     */
    public static Loaded read(Path file) {
        String text;
        try {
            Optional<String> read = TomlStateFile.readBounded(file, MAX_BYTES, "Workspace state");
            if (read.isEmpty()) return new Loaded.Fresh(Optional.empty());
            text = read.get();
        } catch (IOException unreadable) {
            return setAside(file, unreadable);
        }
        try {
            return new Loaded.Restore(parse(text));
        } catch (IOException | RuntimeException | StackOverflowError invalid) {
            // tomlj's parser has no nesting guard: deeply nested arrays overflow the stack inside Toml.parse.
            return setAside(file, invalid);
        }
    }

    private static Loaded setAside(Path file, Throwable cause) {
        Path bad = file.resolveSibling(file.getFileName() + ".bad");
        try {
            Files.move(file, bad, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failed) {
            failed.addSuppressed(cause);
            LOG.log(System.Logger.Level.WARNING, "Could not set aside the unreadable workspace state " + file, failed);
            return new Loaded.Fresh(Optional.empty());
        }
        LOG.log(System.Logger.Level.WARNING, "Set aside the unreadable workspace state " + file + " as " + bad, cause);
        return new Loaded.Fresh(Optional.of(bad));
    }

    private static Snapshot parse(String text) throws IOException {
        TomlParseResult toml = Toml.parse(text);
        if (toml.hasErrors()) throw new IOException("Invalid workspace state: " + toml.errors().getFirst());
        Object version = toml.get(List.of("version"));
        if (!Long.valueOf(1).equals(version)) throw new IOException("Unknown workspace state version: " + version);
        var read = new ArrayList<WindowRecord>();
        if (toml.get(List.of("windows")) instanceof TomlArray entries)
            for (int i = 0; i < entries.size(); i++) window(entries.get(i)).ifPresent(read::add);
        // Main windows first, so that a child whose ids clash with the main window's is the one dropped.
        read.sort(Comparator.comparing(window -> window.role() != Role.MAIN));
        var windows = new ArrayList<WindowRecord>();
        var groups = new HashSet<GroupId>();
        var tabs = new HashSet<TabId>();
        for (WindowRecord window : read) {
            List<GroupId> ids = window.layout().groups().stream().map(Group::id).toList();
            if (window.role() == Role.MAIN && windows.stream().anyMatch(kept -> kept.role() == Role.MAIN)) {
                LOG.log(System.Logger.Level.WARNING, "Dropping a second main window from the workspace state");
            } else if (ids.stream().anyMatch(groups::contains) || window.layout().tabs().stream().anyMatch(tabs::contains)) {
                LOG.log(System.Logger.Level.WARNING, "Dropping a window whose groups or tabs an earlier window holds");
            } else {
                groups.addAll(ids);
                tabs.addAll(window.layout().tabs());
                windows.add(window);
            }
        }
        if (windows.stream().noneMatch(kept -> kept.role() == Role.MAIN)) throw new IOException("The workspace state keeps no main window");
        return new Snapshot(windows);
    }

    /** One window entry with its bad tabs dropped; empty, and logged, when the entry itself is bad. */
    private static Optional<WindowRecord> window(Object value) {
        try {
            if (!(value instanceof TomlTable entry)) throw new IllegalArgumentException("a window entry is not a table");
            Object name = entry.get(List.of("role"));
            Role role = "main".equals(name) ? Role.MAIN : "child".equals(name) ? Role.CHILD : null;
            if (role == null) throw new IllegalArgumentException("unknown window role " + name);
            Layout layout = layout(entry);
            Map<TabId, TabRecord> records = tabs(entry);
            for (TabId tab : layout.tabs()) {
                if (records.containsKey(tab)) continue;
                LOG.log(System.Logger.Level.WARNING, "Dropping tab " + tab + " from the workspace state: it has no readable entry");
                layout = layout.remove(tab);
            }
            records.keySet().retainAll(Set.copyOf(layout.tabs()));
            int extendedState = UiState.number(entry, "extended_state").filter(state -> (state & ~6) == 0).orElse(0);
            return Optional.of(new WindowRecord(role, UiState.bounds(entry), extendedState, layout, records));
        } catch (RuntimeException bad) {
            LOG.log(System.Logger.Level.WARNING, "Dropping a window from the workspace state: " + bad.getMessage());
            return Optional.empty();
        }
    }

    /** A window's tree from its flat nodes, with the active group and the maximize falling back alone. */
    private static Layout layout(TomlTable window) {
        if (!(window.get(List.of("nodes")) instanceof TomlArray nodes)) throw new IllegalArgumentException("a window has no nodes");
        var byId = new HashMap<String, TomlTable>();
        var children = new HashMap<String, List<String>>();
        String root = null;
        for (int i = 0; i < nodes.size(); i++) {
            if (!(nodes.get(i) instanceof TomlTable node)) throw new IllegalArgumentException("a node entry is not a table");
            String id = string(node, "id");
            if (id.isEmpty() || byId.putIfAbsent(id, node) != null) throw new IllegalArgumentException("node id " + id + " is empty or used twice");
            String parent = string(node, "parent");
            if (!parent.isEmpty()) children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(id);
            else if (root == null) root = id;
            else throw new IllegalArgumentException("a window has two root nodes");
        }
        if (root == null) throw new IllegalArgumentException("a window has no root node");
        var reached = new HashSet<String>();
        Node tree = node(root, byId, children, reached, 0);
        if (reached.size() != byId.size()) throw new IllegalArgumentException("a node is not under the window's root");
        Layout plain = new Layout(tree, first(tree), Optional.empty());
        Layout active = groupId(window.get(List.of("active_group"))).filter(id -> plain.group(id).isPresent()).map(plain::activate).orElse(plain);
        return groupId(window.get(List.of("maximized"))).filter(active.activeGroup()::equals).map(active::toggleMaximize).orElse(active);
    }

    /** The node {@code id} and everything under it; a split's children are the nodes naming it, in file order. */
    private static Node node(String id, Map<String, TomlTable> byId, Map<String, List<String>> children, Set<String> reached, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("a window's tree is deeper than " + MAX_DEPTH);
        reached.add(id);
        TomlTable table = byId.get(id);
        Object kind = table.get(List.of("kind"));
        if ("group".equals(kind)) {
            if (children.containsKey(id)) throw new IllegalArgumentException("group " + id + " is a parent");
            if (!(table.get(List.of("tabs")) instanceof TomlArray list)) throw new IllegalArgumentException("group " + id + " has no tab list");
            var tabs = new ArrayList<TabId>();
            for (int i = 0; i < list.size(); i++) {
                if (!(list.get(i) instanceof String tab)) throw new IllegalArgumentException("group " + id + " lists a tab that is not a string");
                tabs.add(TabId.parse(tab));
            }
            Optional<TabId> selected = tabId(table.get(List.of("selected"))).filter(tabs::contains).or(() -> tabs.stream().findFirst());
            return new Group(GroupId.parse(id), tabs, selected);
        }
        if (!"split".equals(kind)) throw new IllegalArgumentException("node " + id + " has an unknown kind " + kind);
        Object axis = table.get(List.of("orientation"));
        Orientation orientation = "horizontal".equals(axis) ? Orientation.HORIZONTAL : "vertical".equals(axis) ? Orientation.VERTICAL : null;
        if (orientation == null) throw new IllegalArgumentException("split " + id + " has no orientation");
        var parts = new ArrayList<Child>();
        for (String child : children.getOrDefault(id, List.of()))
            parts.add(new Child(node(child, byId, children, reached, depth + 1), weight(byId.get(child))));
        return new Split(orientation, parts);
    }

    /** The first group in tree order. */
    private static GroupId first(Node node) {
        Node current = node;
        while (current instanceof Split split) current = split.children().getFirst().node();
        return ((Group) current).id();
    }

    /** A window's tab entries by id; a bad entry is logged and left out, and the first entry for an id wins. */
    private static Map<TabId, TabRecord> tabs(TomlTable window) {
        var records = new HashMap<TabId, TabRecord>();
        if (!(window.get(List.of("tabs")) instanceof TomlArray entries)) return records;
        for (int i = 0; i < entries.size(); i++) {
            try {
                if (!(entries.get(i) instanceof TomlTable entry)) throw new IllegalArgumentException("a tab entry is not a table");
                TabId id = TabId.parse(string(entry, "id"));
                TabRecord parsed = tab(entry);
                if (records.putIfAbsent(id, parsed) != null) throw new IllegalArgumentException("tab " + id + " is listed twice");
            } catch (RuntimeException bad) {
                LOG.log(System.Logger.Level.WARNING, "Dropping a tab entry from the workspace state: " + bad.getMessage());
            }
        }
        return records;
    }

    private static TabRecord tab(TomlTable entry) {
        String kind = string(entry, "kind");
        String title = string(entry, "title");
        if (kind.equals("terminal")) return new TabRecord.Terminal(title, string(entry, "directory"), string(entry, "profile"));
        if (!kind.startsWith(CONTENT)) throw new IllegalArgumentException("unknown tab kind " + kind);
        String rest = kind.substring(CONTENT.length());
        int colon = rest.indexOf(':');
        if (colon < 0) throw new IllegalArgumentException("content kind " + kind + " names no restorer");
        if (!(entry.get(List.of("state")) instanceof TomlTable table)) throw new IllegalArgumentException("a content tab has no state table");
        var state = new HashMap<String, String>();
        for (String key : table.keySet()) {
            if (!(table.get(List.of(key)) instanceof String value)) throw new IllegalArgumentException("restore state " + key + " is not a string");
            state.put(key, value);
        }
        return new TabRecord.Content(title, rest.substring(0, colon), rest.substring(colon + 1), state);
    }

    private static String string(TomlTable table, String key) {
        if (table.get(List.of(key)) instanceof String value) return value;
        throw new IllegalArgumentException(key + " is missing or not a string");
    }

    private static double weight(TomlTable node) {
        if (node.get(List.of("weight")) instanceof Number weight) return weight.doubleValue();
        throw new IllegalArgumentException("a node's weight is not a number");
    }

    private static Optional<GroupId> groupId(Object value) {
        if (!(value instanceof String text) || text.isEmpty()) return Optional.empty();
        try {
            return Optional.of(GroupId.parse(text));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static Optional<TabId> tabId(Object value) {
        if (!(value instanceof String text) || text.isEmpty()) return Optional.empty();
        try {
            return Optional.of(TabId.parse(text));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
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
