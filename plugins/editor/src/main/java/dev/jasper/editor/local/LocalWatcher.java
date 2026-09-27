package dev.jasper.editor.local;

import dev.jasper.editor.api.Revision;
import dev.jasper.editor.session.DocumentSession;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * Notices outside changes to open local files (spec 5). The Editor window calls {@link #tick()} every 2 s
 * while it is open; polling behaves the same on network mounts and under a fake clock.
 */
public final class LocalWatcher {
    private final Executor background;
    private final Executor ui;
    private final Supplier<List<DocumentSession>> sessions;
    private boolean running;

    /**
     * @param background where the stats run
     * @param ui where results are delivered
     * @param sessions the open tabs
     */
    public LocalWatcher(Executor background, Executor ui, Supplier<List<DocumentSession>> sessions) {
        this.background = Objects.requireNonNull(background);
        this.ui = Objects.requireNonNull(ui);
        this.sessions = Objects.requireNonNull(sessions);
    }

    /** UI thread: stats every ready local tab's file in the background, then reports each revision to its tab. */
    public void tick() {
        if (running) return;
        List<DocumentSession> watched = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        for (DocumentSession session : sessions.get())
            if (session.state() == DocumentSession.State.READY && session.document().orElse(null) instanceof LocalDocument local) {
                watched.add(session);
                files.add(local.file());
            }
        if (watched.isEmpty()) return;
        running = true;
        try {
            background.execute(() -> {
                List<Optional<Revision>> revisions = new ArrayList<>();
                for (Path file : files) {
                    try { revisions.add(LocalFiles.revision(file)); }
                    catch (IOException unreadable) { revisions.add(null); }
                }
                ui.execute(() -> {
                    running = false;
                    for (int i = 0; i < watched.size(); i++) if (revisions.get(i) != null) watched.get(i).externalChange(revisions.get(i));
                });
            });
        } catch (RejectedExecutionException stopped) {
            running = false;
        }
    }
}
