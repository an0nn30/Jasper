package dev.jasper.editor.ui;

import dev.jasper.browser.api.MarkdownPreviews;
import dev.jasper.sdk.services.Services;
import java.util.Objects;
import java.util.Optional;

/**
 * The Browser's Markdown previews, while the Browser plugin runs: a view draws its Markdown preview with them when the
 * Markdown preview setting is Chromium. The Browser's API, {@code dev.jasper.browser.api}, is absent while the Browser is
 * off or not installed; this class and {@link ChromiumPreview} are the only ones in the Editor that name it. UI thread.
 */
public final class ChromiumPreviews {
    private final MarkdownPreviews previews;

    /** @param previews the service the Browser publishes */
    ChromiumPreviews(MarkdownPreviews previews) { this.previews = Objects.requireNonNull(previews); }

    /**
     * @param services the Editor's services
     * @return the Browser's previews; empty when the Browser does not run, so that its API's classes are absent too
     */
    public static Optional<ChromiumPreviews> find(Services services) {
        try { return services.find(MarkdownPreviews.class).map(ChromiumPreviews::new); }
        catch (NoClassDefFoundError absent) { return Optional.empty(); }
    }

    /** @return whether Chromium can draw previews; false for the rest of Jasper's run once it failed to start or stopped */
    public boolean available() { return previews.available(); }

    /**
     * @param area the view the preview belongs to
     * @return a Chromium preview for the view, which asks nothing of Chromium until it first shows
     */
    ChromiumPreview create(DocumentArea area) { return new ChromiumPreview(area, previews); }
}
