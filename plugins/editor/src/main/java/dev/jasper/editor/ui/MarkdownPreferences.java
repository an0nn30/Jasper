package dev.jasper.editor.ui;

import dev.jasper.editor.markdown.MarkdownMode;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/** Plugin-wide Markdown default; writes retain click order, including repeated choices and failed writes. */
public final class MarkdownPreferences {
    private final Supplier<MarkdownMode> saved;
    private final Function<MarkdownMode, CompletableFuture<Void>> write;
    private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);
    private MarkdownMode selected, observed;

    /** @param saved reads the declared setting
     * @param write saves a choice through the existing settings error path */
    public MarkdownPreferences(Supplier<MarkdownMode> saved, Function<MarkdownMode, CompletableFuture<Void>> write) {
        this.saved = Objects.requireNonNull(saved); this.write = Objects.requireNonNull(write);
        selected = observed = Objects.requireNonNull(saved.get());
    }
    /** @return the immediate session default, also observing configuration edits when writes have settled */
    public synchronized MarkdownMode current() {
        var value = saved.get();
        if (tail.isDone() && value != observed) selected = observed = value;
        return selected;
    }
    /** Saves every explicit choice and recovers the queue after an unsuccessful write.
     * @param mode explicit choice
     * @return completion of this individual write, preserving its failure */
    public synchronized CompletableFuture<Void> select(MarkdownMode mode) {
        selected = Objects.requireNonNull(mode);
        var next = tail.handle((ignored, failure) -> null).thenCompose(ignored -> write.apply(mode));
        tail = next.handle((ignored, failure) -> {
            synchronized (this) { observed = saved.get(); }
            return null;
        });
        return next;
    }
}
