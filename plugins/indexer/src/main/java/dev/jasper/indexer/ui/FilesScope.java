package dev.jasper.indexer.ui;

import dev.jasper.indexer.index.Digests;
import dev.jasper.indexer.index.FileRef;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.Projects;
import dev.jasper.indexer.search.FileMatcher;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.palette.ScopeSpec;
import dev.jasper.sdk.terminal.WindowHandle;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * "Files": every indexed project's files, ranked by {@link FileMatcher} with the current project first. Enter opens a
 * file in the Editor, Cmd/Ctrl+Enter copies its path, Shift+Enter opens a terminal in its folder. UI thread; no I/O.
 */
public final class FilesScope implements PaletteScope {
    /** The scope id. */
    public static final String ID = "dev.jasper.indexer.files";
    /** Opens the file in the Editor. */
    public static final PaletteVerb OPEN = new PaletteVerb("open", "Open");
    /** Copies the file's path. */
    public static final PaletteVerb COPY = new PaletteVerb("copy-path", "Copy path");
    /** Opens a terminal tab in the file's folder. */
    public static final PaletteVerb TERMINAL = new PaletteVerb("terminal", "Open terminal here");
    static final Duration BUDGET = Duration.ofMillis(50);
    static final int RECENT = 20;

    /**
     * What the scope asks of the plugin.
     *
     * @param current the project the palette's pane or window is in
     * @param openBlocked why Open cannot run for a project now, such as a missing Editor; empty when it can
     * @param reachable whether a terminal can be opened in a project's folders now
     * @param host the host name a remote project's rows show; empty for a local project
     * @param connected whether a project's host is connected; always true for a local project
     * @param open opens a file for a window
     * @param terminal opens a terminal in a file's folder for a window
     * @param copyText what Copy path puts on the clipboard
     * @param clipboard the clipboard
     */
    public record Hooks(Function<PaletteQuery, Optional<ProjectKey>> current, Function<ProjectKey, Optional<String>> openBlocked,
                        Predicate<ProjectKey> reachable, Function<ProjectKey, Optional<String>> host, Predicate<ProjectKey> connected,
                        BiConsumer<FileRef, WindowHandle> open, BiConsumer<FileRef, WindowHandle> terminal,
                        Function<FileRef, String> copyText, Consumer<String> clipboard) {
        /** Rejects nulls. */
        public Hooks {
            Objects.requireNonNull(current); Objects.requireNonNull(openBlocked); Objects.requireNonNull(reachable);
            Objects.requireNonNull(host); Objects.requireNonNull(connected); Objects.requireNonNull(open);
            Objects.requireNonNull(terminal); Objects.requireNonNull(copyText); Objects.requireNonNull(clipboard);
        }
    }

    private final Projects projects;
    private final Hooks hooks;
    private final String shortcutActionId;
    private final String icon;
    private final Deque<FileRef> recent = new ArrayDeque<>();

    /**
     * @param projects the indexed projects
     * @param hooks what the scope asks of the plugin
     * @param shortcutActionId the Go to File action, registered before the scope
     * @param icon the tab icon, or null
     */
    public FilesScope(Projects projects, Hooks hooks, String shortcutActionId, String icon) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        this.shortcutActionId = Objects.requireNonNull(shortcutActionId, "shortcutActionId");
        this.icon = icon;
    }

    @Override public ScopeSpec spec() {
        return ScopeSpec.of(ID, "Files", "Search files", List.of(OPEN, COPY, TERMINAL))
            .withDescription("Open a file from an indexed project").withIcon(icon)
            .withShortcutActionId(shortcutActionId).withInAll(true).withInRecents(true);
    }

    @Override public PaletteResults search(String query, PaletteQuery context) {
        Optional<ProjectKey> current = hooks.current().apply(context);
        if (query.isBlank()) {
            if (current.isEmpty()) return PaletteResults.none();
            List<PaletteRow> rows = recent.stream().filter(ref -> ref.key().equals(current.get()))
                .limit(Math.max(0, context.maxResults())).map(this::row).toList();
            return new PaletteResults(rows, Optional.of("Recently opened"), Optional.empty());
        }
        List<FileMatcher.Hit> hits = FileMatcher.search(query, projects.indexes(), current, context.maxResults(), BUDGET, System::nanoTime);
        return PaletteResults.of(hits.stream().map(hit -> row(hit.ref())).toList());
    }

    @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (!(row.token() instanceof FileRef ref)) return false;
        if (verb.equals(COPY)) return true;
        if (verb.equals(TERMINAL)) return hooks.reachable().test(ref.key());
        return verb.equals(OPEN) && hooks.openBlocked().apply(ref.key()).isEmpty();
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (!(row.token() instanceof FileRef ref)) return;
        if (verb.equals(COPY)) hooks.clipboard().accept(hooks.copyText().apply(ref));
        else if (verb.equals(TERMINAL)) hooks.terminal().accept(ref, context.window());
        else if (verb.equals(OPEN)) {
            hooks.open().accept(ref, context.window());
            recent.remove(ref);
            recent.addFirst(ref);
            while (recent.size() > RECENT) recent.removeLast();
        }
    }

    @Override public Subscription onChanged(Runnable listener) { return projects.onChanged(listener); }

    /**
     * @param ref a file
     * @return its row id: a SHA-256 of the project and path, since row ids are at most 256 characters
     */
    public static String rowId(FileRef ref) { return Digests.sha256Hex(ref.key().id() + "\n" + ref.path()); }

    private PaletteRow row(FileRef ref) {
        String name = projects.get(ref.key()).map(entry -> entry.project().name()).orElse(ref.key().name());
        Optional<String> blocked = hooks.openBlocked().apply(ref.key());
        String detail = blocked.map(reason -> reason + " · " + ref.path())
            .orElseGet(() -> hooks.connected().test(ref.key()) ? ref.path() : "Connect to open · " + ref.path());
        String tag = hooks.host().apply(ref.key()).map(host -> name + " · " + host).orElse(name);
        return PaletteRow.of(rowId(ref), ref.name()).withDetail(detail).withTag(tag).withEnabled(blocked.isEmpty()).withToken(ref);
    }
}
