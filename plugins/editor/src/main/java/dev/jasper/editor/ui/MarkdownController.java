package dev.jasper.editor.ui;

import dev.jasper.editor.markdown.MarkdownLimits;
import dev.jasper.editor.markdown.MarkdownMode;
import dev.jasper.editor.markdown.MarkdownRenderer;
import dev.jasper.editor.markdown.MarkdownResult;
import dev.jasper.editor.session.DocumentSession;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import static org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_MARKDOWN;

/** EDT-owned preview lifetime and a single worker with one replaceable immutable snapshot. */
final class MarkdownController implements AutoCloseable {
    private final EditorTab tab;
    private final dev.jasper.editor.markdown.resources.ResourceSession resources;
    private final Executor worker, ui;
    private final Runnable changed;
    private final Timer debounce;
    private final HierarchyListener hierarchy;
    private boolean closed, active, requiresShowing, dirty = true;
    private long generation;
    private MarkdownMode mode = MarkdownMode.SOURCE;
    private Job running, pending, installing;
    private MarkdownResult current;
    private String failure;
    private Object identity;
    private String language;
    private DocumentSession.State state;

    MarkdownController(EditorTab tab, Executor worker, Executor ui, Runnable changed) {
        this(tab, worker, ui, changed, null);
    }
    MarkdownController(EditorTab tab, Executor worker, Executor ui, Runnable changed,
                       dev.jasper.editor.markdown.resources.ImageLoader loader) {
        this.tab = tab; this.worker = worker; this.ui = ui; this.changed = changed;
        resources = loader == null ? null : new dev.jasper.editor.markdown.resources.ResourceSession(loader,
            () -> tab.session().document().orElse(null), tab.surface().preview()::images, changed);
        debounce = new Timer(250, event -> flushDebounce());
        debounce.setRepeats(false);
        hierarchy = event -> {
            if ((event.getChangeFlags() & (HierarchyEvent.SHOWING_CHANGED | HierarchyEvent.PARENT_CHANGED)) != 0) {
                if (tab.surface().isDisplayable()) requiresShowing = true;
                invalidate();
                if (eligible()) flushDebounce();
            }
        };
        tab.markdown(this);
        tab.surface().addHierarchyListener(hierarchy);
        tab.surface().controllerGeometry = this::geometryChanged;
        tab.surface().styleChanged = this::styleChanged;
        rememberSession();
    }

    Optional<MarkdownResult> currentResult() { return Optional.ofNullable(current); }
    Optional<String> failure() { return Optional.ofNullable(failure); }
    MarkdownMode mode() { return mode; }
    boolean active() { return active; }
    boolean needsImageConsent() { return resources != null && resources.needsConsent(); }
    boolean imageFailures() { return resources != null && resources.hasFailures(); }
    void allowImages() { if (resources != null) {resources.allowExternal(); changed.run();} }
    void retryImages() { if (resources != null) {resources.retryFailed(); changed.run();} }
    void sourceReloaded() { if (resources != null) resources.reload(); sourceChanged(); }
    void sourceChanged() {
        if (closed) return;
        invalidate();
        if (eligible()) debounce.restart();
    }
    void styleChanged() {
        if (closed) return;
        invalidate();
        if (eligible()) flushDebounce();
    }
    void sessionChanged() {
        if (closed) return;
        var session = tab.session();
        if (identity == session.document().orElse(null) && java.util.Objects.equals(language, session.style()) && state == session.state()) return;
        if (resources != null && identity != session.document().orElse(null)) resources.identityChanged();
        rememberSession();
        if (!SYNTAX_STYLE_MARKDOWN.equals(language)) mode(MarkdownMode.SOURCE);
        invalidate();
        if (eligible()) flushDebounce();
    }
    private void rememberSession() {
        identity = tab.session().document().orElse(null); language = tab.session().style(); state = tab.session().state();
    }
    void active(boolean value) {
        if (closed || active == value) return;
        active = value;
        if (!value) invalidate();
        else if (dirty && eligible()) flushDebounce();
    }
    void mode(MarkdownMode value) {
        if (closed || mode == value) return;
        mode = value;
        tab.surface().mode(value);
        if (value == MarkdownMode.SOURCE) invalidate();
        else if (dirty && eligible()) flushDebounce();
        changed.run();
    }
    void retry() { if (!closed) { invalidate(); if (eligible()) flushDebounce(); } }

    private boolean eligible() {
        var surface = tab.surface();
        return !closed && active && mode != MarkdownMode.SOURCE && tab.showingText()
            && SYNTAX_STYLE_MARKDOWN.equals(tab.session().style())
            && (!requiresShowing || surface.isShowing()) && surface.previewStyle() != null;
    }
    private void invalidate() {
        if (closed) return;
        generation++;
        tab.surface().scrollLink.invalidate();
        if (resources != null) { if (eligible()) resources.invalidate(); else resources.active(false); }
        debounce.stop(); pending = null; installing = null; current = null; dirty = true;
        tab.surface().preview().cancelInstall();
        if (running != null) running.cancel();
    }
    void flushDebounce() {
        debounce.stop();
        if (!eligible() || !dirty) return;
        if (tab.area().getDocument().getLength() > MarkdownLimits.SOURCE_UNITS) {
            fail("This document is too large to preview"); return;
        }
        var job = new Job(generation, tab.session().document().orElse(null), tab.area().getText(), tab.surface().previewStyle());
        dirty = false; failure = null;
        if (running == null) start(job); else pending = job;
    }
    private void start(Job job) {
        running = job;
        try { worker.execute(() -> render(job)); }
        catch (RuntimeException rejected) { complete(job, null, rejected); }
    }
    private void render(Job job) {
        PreviewPane.Prepared result = null;
        RuntimeException failure = null;
        job.begin();
        try {
            if (job.cancelled) throw new CancellationException();
            result = PreviewPane.prepare(new MarkdownRenderer().render(job.source, job.style.syntax()));
        } catch (RuntimeException error) { failure = error; }
        finally { job.end(); }
        var prepared = result; var error = failure;
        try { ui.execute(() -> complete(job, prepared, error)); }
        catch (RuntimeException rejected) {
            // A rejecting executor must not strand the running slot or touch Swing from this worker.
            SwingUtilities.invokeLater(() -> complete(job, null, rejected));
        }
    }
    private boolean current(Job job) {
        return !closed && job.generation == generation && job.identity == tab.session().document().orElse(null) && eligible();
    }
    private void complete(Job job, PreviewPane.Prepared prepared, RuntimeException error) {
        if (running != job) return;
        running = null;
        if (current(job)) {
            if (error != null) fail(error.getMessage());
            else {
                installing = job;
                try {
                    job.result = prepared.result();
                    // Source and syntax belong to the job; geometry-only inputs may have changed while it ran.
                    var style = tab.surface().previewStyle();
                    tab.surface().preview().install(prepared, style.typography(), style.palette(), style.syntax(), tab.surface().getFont(), tab.surface().opacity());
                } catch (RuntimeException installError) { installing = null; fail(installError.getMessage()); }
            }
        }
        var next = pending; pending = null;
        if (next != null && current(next)) start(next);
    }
    private void geometryChanged() {
        var job = installing;
        var pane = tab.surface().preview();
        if (job == null) { if (current != null) tab.surface().scrollLink.geometryChanged(); return; }
        if (!current(job) || pane.installing()) return;
        if (pane.installFailure().isPresent()) {
            installing = null; fail(pane.installFailure().get().getMessage());
        } else if (pane.installedResult().orElse(null) == job.result) {
            current = job.result; installing = null; failure = null;
            tab.surface().scrollLink.install(job.generation, current);
            if (resources != null) { resources.refresh(current); resources.active(true); }
            changed.run();
        }
    }
    private void fail(String message) {
        tab.surface().scrollLink.invalidate();
        failure = message == null || message.isBlank() ? "Could not preview this document" : message;
        dirty = true;
        changed.run();
    }
    @Override public void close() {
        if (closed) return;
        invalidate(); closed = true;
        if (resources != null) resources.close();
        tab.surface().removeHierarchyListener(hierarchy);
        tab.surface().controllerGeometry = () -> {};
        tab.surface().styleChanged = () -> {};
    }
    private static final class Job {
        final long generation;
        final Object identity;
        final String source;
        final DocumentArea.PreviewStyle style;
        volatile Thread thread;
        volatile boolean cancelled;
        MarkdownResult result;
        Job(long generation, Object identity, String source, DocumentArea.PreviewStyle style) {
            this.generation = generation; this.identity = identity; this.source = source; this.style = style;
        }
        synchronized void begin() { thread = Thread.currentThread(); }
        synchronized void end() { thread = null; if (cancelled) Thread.interrupted(); }
        synchronized void cancel() { cancelled = true; if (thread != null) thread.interrupt(); }
    }
}
