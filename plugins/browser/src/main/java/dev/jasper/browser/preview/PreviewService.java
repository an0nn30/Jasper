package dev.jasper.browser.preview;

import dev.jasper.browser.api.MarkdownPreview;
import dev.jasper.browser.api.MarkdownPreviews;
import dev.jasper.browser.api.PreviewEvent;
import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.engine.PreviewScheme;
import dev.jasper.browser.page.PreviewPage;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

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
    private final Function<Wiring, PagePreview.Hooks> pages;
    private final BiFunction<Duration, Runnable, Runnable> later;
    private final Set<PagePreview> live = new LinkedHashSet<>();

    /**
     * The service.
     *
     * @param scheme the previews' origins
     * @param chromium the plugin's Chromium, asked for nothing until a preview first shows
     * @param background encodes the previews' documents and images
     */
    public PreviewService(PreviewScheme scheme, Chromium chromium, Executor background) {
        this(scheme, chromium, background, null, PagePreview::onUiAfter);
    }

    /**
     * As the public constructor, with each preview's page made by {@code pages}, given the preview's wiring, and the
     * UI-thread timer its ready watch runs on; a test's records them. Null {@code pages} makes the production
     * {@link PreviewPage}, on {@code chromium}.
     */
    PreviewService(PreviewScheme scheme, Chromium chromium, Executor background, Function<Wiring, PagePreview.Hooks> pages,
            BiFunction<Duration, Runnable, Runnable> later) {
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        this.chromium = Objects.requireNonNull(chromium, "chromium");
        this.background = Objects.requireNonNull(background, "background");
        this.pages = pages == null ? this::page : pages;
        this.later = Objects.requireNonNull(later, "later");
    }

    @Override public boolean available() {
        return chromium.unavailable().isEmpty();
    }

    @Override public MarkdownPreview create(Consumer<PreviewEvent> events) {
        Objects.requireNonNull(events, "events");
        PreviewScheme.Origin origin = scheme.open();
        var wiring = new Wiring(origin.shell());
        var preview = new PagePreview(origin, pages.apply(wiring), background, events, later);
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

    /** The production page of the preview {@code wiring} reaches: a {@link PreviewPage} on Chromium's preview client. */
    private PagePreview.Hooks page(Wiring wiring) {
        var page = new PreviewPage(chromium, wiring.shell(), wiring::created, wiring::received, wiring::failed);
        return new PagePreview.Hooks(page.view(), page::run, page::background, page::copy, page::selectAll, page::focus, page::close);
    }

    /**
     * What a preview's page tells its preview: its browser exists, its requests and its failure. Made before the preview
     * it reaches, which it reaches once made. UI thread, but for reading a request.
     */
    static final class Wiring {
        private final URI shell;
        private PagePreview preview;

        private Wiring(URI shell) {
            this.shell = shell;
        }

        /** The shell of the preview's origin, which its page loads. */
        URI shell() {
            return shell;
        }

        void created() {
            if (preview != null) preview.created();
        }

        /**
         * Reads a request of the page where it arrives, on Chromium's thread, so a layout's many numbers are never parsed
         * on the UI thread. Any thread; never throws.
         *
         * @return what the request tells the preview, run on the UI thread; empty for anything that is no message
         */
        Optional<Runnable> received(String request) {
            return PreviewMessages.read(request).map(message -> () -> {
                if (preview != null) preview.received(message);
            });
        }

        void failed(Throwable cause) {
            if (preview != null) preview.unavailable(reason(cause));
        }
    }
}
