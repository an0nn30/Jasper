package dev.jasper.editor.ui;

import dev.jasper.editor.markdown.MarkdownMode;
import dev.jasper.editor.markdown.PreviewEngine;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Plugin-wide Markdown defaults: the view a tab starts in, whose writes retain click order, including repeated choices
 * and failed writes, and what draws the preview.
 */
public final class MarkdownPreferences {
    private final Supplier<MarkdownMode> saved;
    private final Function<MarkdownMode, CompletableFuture<Void>> write;
    private final Supplier<PreviewEngine> engine;
    private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);
    private MarkdownMode selected, observed;

    /** @param saved reads the declared setting; the preview's engine is {@code markdown_preview}'s default, Chromium
     * @param write saves a choice; failures are returned to the caller for presentation */
    public MarkdownPreferences(Supplier<MarkdownMode> saved, Function<MarkdownMode, CompletableFuture<Void>> write) {
        this(saved, write, () -> PreviewEngine.CHROMIUM);
    }

    /** @param saved reads the declared view setting
     * @param write saves a choice; failures are returned to the caller for presentation
     * @param engine reads the declared {@code markdown_preview} setting */
    public MarkdownPreferences(Supplier<MarkdownMode> saved, Function<MarkdownMode, CompletableFuture<Void>> write, Supplier<PreviewEngine> engine) {
        this.saved = Objects.requireNonNull(saved); this.write = Objects.requireNonNull(write); this.engine = Objects.requireNonNull(engine);
        selected = observed = Objects.requireNonNull(saved.get());
    }
    /** @return {@code markdown_preview} now: what should draw the preview; a view draws with Chromium only while it can */
    public PreviewEngine engine() { return engine.get(); }
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
