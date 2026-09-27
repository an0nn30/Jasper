package dev.jasper.editor.session;

import dev.jasper.editor.api.Content;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.api.Revision;
import dev.jasper.editor.syntax.SyntaxDetector;
import dev.jasper.editor.text.DecodedText;
import dev.jasper.editor.text.TextCodec;
import dev.jasper.editor.text.TextFormat;
import dev.jasper.editor.text.UnencodableException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

/**
 * One tab's document (spec 2.3, 4, 5): loading, the saved text, saving with conflict checks, reloading and
 * outside changes. UI thread only; no Swing. The tab's text reaches it through {@link #attach}.
 */
public final class DocumentSession {
    public enum State { LOADING, READY, FAILED }
    public enum Banner { NONE, CHANGED, DELETED }

    public static final String OVERWRITE = "Overwrite", RELOAD = "Reload", SAVE_ANYWAY = "Save Anyway",
        CONVERT = "Convert to UTF-8 and Save", DISCARD = "Discard Changes";

    private final SessionEnvironment environment;
    private final String untitledName;
    private final List<Runnable> listeners = new ArrayList<>();
    private EditorDocument document;
    private State state;
    private boolean saving;
    private Optional<Revision> revision = Optional.empty();
    private Optional<Revision> reported = Optional.empty();
    private TextFormat format = TextFormat.DEFAULT;
    private boolean mixed;
    private String savedText = "";
    private boolean forcedDirty;
    private boolean lastDirty;
    private String failure = "";
    private boolean retryable = true;
    private String style = SyntaxConstants.SYNTAX_STYLE_NONE;
    private boolean styleChosen;
    private boolean tabs;
    private boolean wrap;
    private Banner banner = Banner.NONE;
    private String notice = "";
    private String reloadNotice = "";
    private CompletableFuture<Content> loading;
    private boolean silentReload;
    private String reloadBase = "";
    private CompletableFuture<Boolean> pendingSave;
    private String detached = "";
    private Supplier<String> text = () -> detached;
    private BiConsumer<String, Boolean> replace = (value, keep) -> detached = value;

    /**
     * A tab for a document; call {@link #load()} after attaching the tab's text.
     *
     * @param document the file
     * @param environment executors, the question asker and the size limit
     */
    public DocumentSession(EditorDocument document, SessionEnvironment environment) {
        this.document = Objects.requireNonNull(document);
        this.environment = Objects.requireNonNull(environment);
        this.untitledName = "";
        this.state = State.LOADING;
    }

    private DocumentSession(String untitledName, SessionEnvironment environment) {
        this.environment = Objects.requireNonNull(environment);
        this.untitledName = Objects.requireNonNull(untitledName);
        this.state = State.READY;
    }

    /**
     * @param name the tab's name, such as "Untitled 2"
     * @param environment executors, the question asker and the size limit
     * @return an empty, ready tab with no file; it is saved with {@link #saveAs}
     */
    public static DocumentSession untitled(String name, SessionEnvironment environment) { return new DocumentSession(name, environment); }

    /**
     * @param read reads the tab's current text
     * @param replace replaces the tab's text; the flag asks to keep the caret's line and scroll position
     */
    public void attach(Supplier<String> read, BiConsumer<String, Boolean> replace) {
        this.text = Objects.requireNonNull(read);
        this.replace = Objects.requireNonNull(replace);
    }

    /** @param listener runs after any change a view shows */
    public void addListener(Runnable listener) { listeners.add(Objects.requireNonNull(listener)); }

    /** Reads the document for the first time, or again after a failure. */
    public void load() {
        if (document == null) return;
        state = State.LOADING;
        failure = "";
        changed();
        fetch(false, false);
    }

    /**
     * Re-reads the document while the tab stays usable; a failure asks and keeps the current text. Does
     * nothing while a save is in flight, so a completing save cannot be overwritten by a stale read.
     */
    public void reload() { reload(false); }

    /** @param silent the watcher started it, so a failure shows the banner rather than asking */
    private void reload(boolean silent) {
        if (document == null || state != State.READY || saving) return;
        fetch(true, silent);
    }

    /** Stops a read in progress; its late result is ignored. */
    public void cancelLoad() {
        CompletableFuture<Content> read = loading;
        if (read == null) return;
        // Cleared first: cancel() runs the completion callback at once, and it must find the read superseded.
        loading = null;
        silentReload = false;
        read.cancel(true);
    }

    /** The tab's text changed; call shortly after typing stops. */
    public void textEdited() {
        boolean now = dirty();
        if (now == lastDirty) return;
        lastDirty = now;
        changed();
    }

    /** Revert to Saved: confirms when there are edits, then re-reads the document. Does nothing while saving. */
    public void revert() {
        if (document == null || state != State.READY || saving) return;
        if (dirty() && !DISCARD.equals(environment.ask().apply(new Question("Revert to Saved",
                "Discard your changes to " + name() + "?", List.of(DISCARD, "Cancel"), true)))) return;
        reload();
    }

    /**
     * Saves to the document, checking first whether it changed since it was read. A read in flight is
     * cancelled first: an explicit save keeps the user's edits, where a reload would discard them.
     *
     * @return completes with true when saved, false when cancelled or failed (already reported)
     */
    public CompletableFuture<Boolean> save() {
        if (document == null) throw new IllegalStateException("An untitled document is saved with saveAs");
        if (pendingSave != null) return pendingSave;
        if (state != State.READY) return CompletableFuture.completedFuture(false);
        if (loading != null) cancelLoad();
        return write(document, true);
    }

    /**
     * Saves to another document, which this tab then edits; the caller has confirmed any replacement. While
     * another save is pending, waits for it and then saves to the target, rather than joining and reporting
     * that unrelated save's result.
     *
     * @param target the new file
     * @return completes with true when saved, false when cancelled or failed (already reported)
     */
    public CompletableFuture<Boolean> saveAs(EditorDocument target) {
        Objects.requireNonNull(target);
        if (pendingSave != null) {
            CompletableFuture<Boolean> pending = pendingSave;
            var chained = new CompletableFuture<Boolean>();
            pending.whenComplete((value, error) -> environment.ui().execute(() -> saveAs(target)
                .whenComplete((v, e) -> { if (e != null) chained.completeExceptionally(e); else chained.complete(v); })));
            return chained;
        }
        if (state != State.READY) return CompletableFuture.completedFuture(false);
        if (loading != null) cancelLoad();
        return write(target, false);
    }

    /**
     * The document's revision as a watcher saw it. A clean tab reloads without asking; when that read fails,
     * the tab keeps its text and the banner offers Reload or Keep Mine, with the reason as the notice.
     *
     * @param current the revision now, or empty when the file is gone
     */
    public void externalChange(Optional<Revision> current) {
        if (document == null || state != State.READY || saving || loading != null) return;
        if (current.equals(revision) || current.equals(reported)) return;
        reported = current;
        if (current.isEmpty()) { banner = Banner.DELETED; changed(); return; }
        if (dirty()) { banner = Banner.CHANGED; changed(); return; }
        reloadNotice = "Reloaded: changed on disk";
        reload(true);
    }

    /** Keep Mine: adopts the revision seen on disk, so the next save overwrites without asking. */
    public void keepMine() {
        revision = reported;
        banner = Banner.NONE;
        changed();
    }

    /** Keep after a deletion: the tab stays unsaved, and its next save recreates the file. */
    public void keepDeleted() {
        revision = Optional.empty();
        forcedDirty = true;
        lastDirty = true;
        banner = Banner.NONE;
        changed();
    }

    /** Reload from the banner. Leaves the banner up while a save is in flight. */
    public void reloadFromBanner() {
        if (saving) return;
        banner = Banner.NONE;
        changed();
        reload();
    }

    /** @param style a style key the user chose; detection no longer changes it */
    public void setStyle(String style) {
        this.style = Objects.requireNonNull(style);
        styleChosen = true;
        changed();
    }

    /** @param wrap whether long lines wrap in this tab */
    public void setWrap(boolean wrap) {
        this.wrap = wrap;
        changed();
    }

    /** Clears the status line's notice. */
    public void clearNotice() {
        notice = "";
        changed();
    }

    public Optional<EditorDocument> document() { return Optional.ofNullable(document); }
    public boolean untitled() { return document == null; }
    public String key() { return document != null ? document.key() : "untitled:" + untitledName; }
    public String name() { return document != null ? document.name() : untitledName; }
    public String location() { return document != null ? document.location() : untitledName; }
    public Optional<String> host() { return document != null ? document.host() : Optional.empty(); }
    public State state() { return state; }
    public boolean saving() { return saving; }
    public boolean dirty() { return state == State.READY && (forcedDirty || !text.get().equals(savedText)); }
    public String failure() { return failure; }
    public boolean retryable() { return retryable; }
    public TextFormat format() { return format; }
    public boolean mixed() { return mixed; }
    public String style() { return style; }
    public boolean tabs() { return tabs; }
    public boolean wrap() { return wrap; }
    public Banner banner() { return banner; }
    public Optional<Revision> revision() { return revision; }
    public Optional<String> notice() { return notice.isEmpty() ? Optional.empty() : Optional.of(notice); }
    public Optional<CompletableFuture<Boolean>> pendingSave() { return Optional.ofNullable(pendingSave); }

    /** @return the name of the folder holding the file, or empty */
    public String parentName() {
        String where = location();
        int slash = Math.max(where.lastIndexOf('/'), where.lastIndexOf('\\'));
        if (slash <= 0) return "";
        String folder = where.substring(0, slash);
        int previous = Math.max(Math.max(folder.lastIndexOf('/'), folder.lastIndexOf('\\')), folder.lastIndexOf(':'));
        return folder.substring(previous + 1);
    }

    private void fetch(boolean again, boolean silent) {
        cancelLoad();
        silentReload = silent;
        if (again) reloadBase = text.get();
        CompletableFuture<Content> read = document.read(environment.limitBytes().getAsLong());
        loading = read;
        read.whenComplete((content, error) -> {
            if (error != null) { environment.ui().execute(() -> fetched(read, null, null, error, again)); return; }
            background(() -> {
                try {
                    DecodedText decoded = TextCodec.decode(content.bytes());
                    environment.ui().execute(() -> fetched(read, decoded, content.revision(), null, again));
                } catch (IOException binary) {
                    environment.ui().execute(() -> fetched(read, null, null, binary, again));
                }
            }, problem -> fetched(read, null, null, problem, again));
        });
    }

    private void fetched(CompletableFuture<Content> read, DecodedText decoded, Revision at, Throwable error, boolean again) {
        if (loading != read) return;
        loading = null;
        boolean silent = silentReload;
        silentReload = false;
        if (error != null) {
            reloadNotice = "";
            if (silent) {
                // Asking here would open a dialog nobody asked for, and a steadily growing file would open one on
                // every watcher tick; the banner offers Reload (which asks on failure) or Keep Mine instead.
                banner = Banner.CHANGED;
                notice = "Could not reload: " + Failures.reason(error);
                changed();
                return;
            }
            if (again) {
                environment.ask().apply(new Question("Reload failed", "Could not reload " + name() + ": " + Failures.sentence(Failures.reason(error)), List.of("OK")));
                changed();
                return;
            }
            failure = Failures.describe(name(), error);
            retryable = Failures.retryable(error);
            state = State.FAILED;
            changed();
            return;
        }
        if (again && !text.get().equals(reloadBase)) {
            // Typed during the read: keep the tab's text and revision, and let the banner offer the reload instead.
            reported = Optional.of(at);
            banner = Banner.CHANGED;
            reloadNotice = "";
            notice = "";
            changed();
            return;
        }
        format = decoded.format();
        mixed = decoded.mixed();
        savedText = decoded.text();
        forcedDirty = false;
        revision = Optional.of(at);
        reported = revision;
        if (!styleChosen) style = SyntaxDetector.detect(document.name(), firstLine(savedText));
        tabs = TextCodec.indentsWithTabs(savedText) || SyntaxConstants.SYNTAX_STYLE_MAKEFILE.equals(style);
        banner = Banner.NONE;
        notice = again ? reloadNotice : "";
        reloadNotice = "";
        replace.accept(savedText, again);
        state = State.READY;
        lastDirty = dirty();
        changed();
    }

    private CompletableFuture<Boolean> write(EditorDocument target, boolean check) {
        var result = new CompletableFuture<Boolean>();
        pendingSave = result;
        saving = true;
        changed();
        encode(target, check, text.get(), format, result);
        return result;
    }

    private void encode(EditorDocument target, boolean check, String snapshot, TextFormat as, CompletableFuture<Boolean> result) {
        background(() -> {
            try {
                byte[] bytes = TextCodec.encode(snapshot, as);
                environment.ui().execute(() -> encoded(target, check, snapshot, as, bytes, result));
            } catch (UnencodableException unencodable) {
                environment.ui().execute(() -> {
                    String answer = environment.ask().apply(new Question("Cannot encode", "Line " + unencodable.line() + " of " + name()
                        + " has a character that " + unencodable.charset().name() + " cannot store.", List.of(CONVERT, "Cancel")));
                    if (CONVERT.equals(answer)) encode(target, check, snapshot, new TextFormat(StandardCharsets.UTF_8, false, as.separator()), result);
                    else finish(result, false);
                });
            }
        }, problem -> failed(result, problem));
    }

    private void encoded(EditorDocument target, boolean check, String snapshot, TextFormat as, byte[] bytes, CompletableFuture<Boolean> result) {
        if (!check) { store(target, snapshot, as, bytes, result); return; }
        target.revision().whenComplete((current, error) -> environment.ui().execute(() -> {
            if (error != null) { failed(result, error); return; }
            if (current.isEmpty() && revision.isPresent()) {
                String answer = environment.ask().apply(new Question("File deleted", name() + " no longer exists " + where() + ".",
                    List.of(SAVE_ANYWAY, "Cancel"), true));
                if (!SAVE_ANYWAY.equals(answer)) { finish(result, false); return; }
            } else if (current.isPresent() && !current.equals(revision)) {
                String answer = environment.ask().apply(new Question("File changed", name() + " changed " + where() + " since you opened it.",
                    List.of(OVERWRITE, RELOAD, "Cancel"), true));
                if (RELOAD.equals(answer)) { finish(result, false); reload(); return; }
                if (!OVERWRITE.equals(answer)) { finish(result, false); return; }
            }
            store(target, snapshot, as, bytes, result);
        }));
    }

    private void store(EditorDocument target, String snapshot, TextFormat as, byte[] bytes, CompletableFuture<Boolean> result) {
        target.write(bytes).whenComplete((written, error) -> environment.ui().execute(() -> {
            if (error != null) { failed(result, error); return; }
            boolean renamed = target != document;
            document = target;
            format = as;
            mixed = false;
            revision = Optional.of(written);
            reported = revision;
            savedText = snapshot;
            forcedDirty = false;
            banner = Banner.NONE;
            if (renamed && !styleChosen) style = SyntaxDetector.detect(target.name(), firstLine(snapshot));
            lastDirty = dirty();
            finish(result, true);
        }));
    }

    private void failed(CompletableFuture<Boolean> result, Throwable error) {
        environment.ask().apply(new Question("Save failed", "Could not save " + name() + ": " + Failures.sentence(Failures.reason(error)), List.of("OK")));
        finish(result, false);
    }

    private void finish(CompletableFuture<Boolean> result, boolean saved) {
        if (pendingSave == result) { pendingSave = null; saving = false; }
        changed();
        result.complete(saved);
    }

    private String where() { return host().map(name -> "on " + name).orElse("on disk"); }

    private void background(Runnable work, Consumer<Throwable> rejected) {
        try { environment.background().execute(work); }
        catch (RejectedExecutionException stopped) { environment.ui().execute(() -> rejected.accept(stopped)); }
    }

    private void changed() { for (Runnable listener : List.copyOf(listeners)) listener.run(); }

    private static String firstLine(String text) {
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(0, newline);
    }
}
