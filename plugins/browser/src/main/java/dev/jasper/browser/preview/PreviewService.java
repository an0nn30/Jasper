package dev.jasper.browser.preview;

import dev.jasper.browser.api.MarkdownPreview;
import dev.jasper.browser.api.MarkdownPreviews;
import dev.jasper.browser.api.PreviewEvent;
import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.engine.PreviewScheme;
import dev.jasper.browser.page.PreviewPage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * The Markdown previews' service the Browser publishes: each preview it makes is a {@link PagePreview} on an origin of
 * its own, drawn by a {@link PreviewPage} on Chromium's preview client, whose browser is made only once its canvas first
 * shows. Making a preview, and feeding one that never shows, loads no Chromium class. When Chromium's start fails, or
 * Chromium stops, every live preview is unavailable. UI thread.
 */
public final class PreviewService implements MarkdownPreviews {
    private final PreviewScheme scheme;
    private final Chromium chromium;
    private final Executor background;
    private final Set<PagePreview> live = new LinkedHashSet<>();

    /**
     * The service.
     *
     * @param scheme the previews' origins
     * @param chromium the plugin's Chromium, asked for nothing until a preview first shows
     * @param background encodes the previews' documents and images
     */
    public PreviewService(PreviewScheme scheme, Chromium chromium, Executor background) {
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        this.chromium = Objects.requireNonNull(chromium, "chromium");
        this.background = Objects.requireNonNull(background, "background");
    }

    @Override public boolean available() {
        return chromium.unavailable().isEmpty();
    }

    @Override public MarkdownPreview create(Consumer<PreviewEvent> events) {
        Objects.requireNonNull(events, "events");
        PreviewScheme.Origin origin = scheme.open();
        var wiring = new Wiring();
        var page = new PreviewPage(chromium, origin.shell(), wiring::created, wiring::received, wiring::failed);
        var preview = new PagePreview(origin, new PagePreview.Hooks(page.view(), page::run, page::background, page::copy,
            page::selectAll, page::focus, page::close), background, events);
        wiring.preview = preview;
        live.add(preview);
        preview.onClose(() -> live.remove(preview));
        return preview;
    }

    /**
     * Chromium cannot draw previews any more: its start failed, or it stopped. Every live preview is unavailable, for
     * {@code cause}'s reason. UI thread.
     *
     * @param cause why
     */
    public void unavailable(Throwable cause) {
        Objects.requireNonNull(cause, "cause");
        String reason = reason(cause);
        List<PagePreview> told = new ArrayList<>(live);
        for (PagePreview preview : told) preview.unavailable(reason);
    }

    /** {@code cause}'s message, or its name for none, as the user reads it. */
    static String reason(Throwable cause) {
        return cause.getMessage() == null || cause.getMessage().isBlank() ? cause.toString() : cause.getMessage();
    }

    /** What a preview's page tells its preview, made before the preview it reaches. UI thread. */
    private static final class Wiring {
        private PagePreview preview;

        void created() {
            if (preview != null) preview.created();
        }

        void received(String request) {
            if (preview != null) preview.received(request);
        }

        void failed(Throwable cause) {
            if (preview != null) preview.unavailable(reason(cause));
        }
    }
}
