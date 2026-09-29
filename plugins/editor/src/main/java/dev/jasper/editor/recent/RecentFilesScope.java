package dev.jasper.editor.recent;

import dev.jasper.editor.api.EditorDocument;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.palette.ScopeSpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.swing.Icon;

/**
 * "Recent files": the files the Editor opened, newest first. A query matches the name, host or location, ignoring
 * case; name matches come first. Open (Enter) reopens a file; Remove from recent files (Cmd/Ctrl+Enter) forgets it.
 * A row whose key nothing can reopen now is dimmed and can only be removed.
 */
public final class RecentFilesScope implements PaletteScope {
    /** The scope id. */
    public static final String ID = "dev.jasper.editor.recent";
    /** The action that opens the palette on this scope. */
    public static final String ACTION = "dev.jasper.editor.recent-files";
    /** Reopens the file. */
    public static final PaletteVerb OPEN = new PaletteVerb("open", "Open");
    /** Forgets the file. */
    public static final PaletteVerb REMOVE = new PaletteVerb("remove", "Remove from recent files");
    static final String UNAVAILABLE = "Can't reopen: host or plugin unavailable";

    private final RecentFiles recent;
    private final Reopeners reopeners;
    private final Supplier<Instant> clock;
    private final BiConsumer<EditorDocument, WindowOwner> open;
    private final Consumer<String> error;
    private final Icon icon;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /**
     * @param recent the list
     * @param reopeners who reopens each key
     * @param clock the current time
     * @param open opens a document in the Editor for a window
     * @param error shows an error notice
     * @param icon the tab icon, or null
     */
    public RecentFilesScope(RecentFiles recent, Reopeners reopeners, Supplier<Instant> clock, BiConsumer<EditorDocument, WindowOwner> open,
                            Consumer<String> error, Icon icon) {
        this.recent = Objects.requireNonNull(recent); this.reopeners = Objects.requireNonNull(reopeners);
        this.clock = Objects.requireNonNull(clock); this.open = Objects.requireNonNull(open); this.error = Objects.requireNonNull(error);
        this.icon = icon;
        recent.onChanged(this::changed);
        reopeners.onChanged(this::changed);
    }

    @Override public ScopeSpec spec() {
        return ScopeSpec.of(ID, "Recent files", "Search recently opened files", List.of(OPEN, REMOVE))
            .withDescription("Reopen a file the Editor opened recently, local or remote").withIcon(icon)
            .withShortcutActionId(ACTION).withInAll(true).withInRecents(true);
    }

    @Override public PaletteResults search(String query, PaletteQuery context) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        List<RecentFile> entries = recent.entries(clock.get());
        Stream<RecentFile> ordered = needle.isEmpty() ? entries.stream() : Stream.concat(
            entries.stream().filter(entry -> contains(entry.name(), needle)),
            entries.stream().filter(entry -> !contains(entry.name(), needle)
                && (entry.host().map(host -> contains(host, needle)).orElse(false) || contains(entry.location(), needle))));
        return PaletteResults.of(ordered.limit(Math.max(0, context.maxResults())).map(this::row).toList());
    }

    @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (!(row.token() instanceof RecentFile entry)) return false;
        if (verb.equals(REMOVE)) return true;
        return verb.equals(OPEN) && reopeners.resolve(entry.key()).isPresent();
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (!(row.token() instanceof RecentFile entry)) return;
        if (verb.equals(REMOVE)) { recent.remove(entry.key()); return; }
        Optional<Function<WindowOwner, EditorDocument>> factory = reopeners.resolve(entry.key());
        if (factory.isEmpty()) { error.accept("Could not reopen " + entry.location() + ": the host or plugin is unavailable."); return; }
        try {
            open.accept(factory.get().apply(context.window()), context.window());
        } catch (RuntimeException failure) {
            error.accept("Could not reopen " + entry.location() + ": "
                + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
        }
    }

    @Override public Subscription onChanged(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener));
        return () -> listeners.remove(listener);
    }

    /** Tells the palette the rows changed, for example because the retention setting did. */
    public void changed() { listeners.forEach(Runnable::run); }

    /** @return a stable row id: the key's SHA-256 in hex, since row ids are at most 256 characters and keys may be longer */
    static String rowId(String key) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException missing) { throw new IllegalStateException("The JDK has no SHA-256", missing); }
    }

    private PaletteRow row(RecentFile entry) {
        boolean reopenable = reopeners.resolve(entry.key()).isPresent();
        return PaletteRow.of(rowId(entry.key()), entry.name())
            .withDetail(reopenable ? entry.location() : UNAVAILABLE + " · " + entry.location())
            .withTag(entry.host().orElse(null)).withEnabled(reopenable).withToken(entry);
    }

    private static boolean contains(String text, String needle) { return text.toLowerCase(Locale.ROOT).contains(needle); }
}
