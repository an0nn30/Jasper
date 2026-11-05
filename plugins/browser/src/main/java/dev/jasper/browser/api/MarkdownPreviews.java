package dev.jasper.browser.api;

import java.util.function.Consumer;

/**
 * Published by the Browser plugin with {@code Services.publish}: makes the Markdown previews Chromium draws. Chromium
 * starts when the first preview, or the first browser item, shows. UI thread only.
 */
public interface MarkdownPreviews {
    /**
     * Whether Chromium can draw previews.
     *
     * @return false once Chromium has failed to start or has stopped; it stays false for the rest of Jasper's run
     */
    boolean available();

    /**
     * Makes a preview showing nothing. It asks nothing of Chromium until its component first shows. When Chromium cannot
     * draw it, or stops, it reports {@link PreviewEvent.Unavailable}.
     *
     * @param events told of what happens in the preview, on the UI thread, until it closes
     * @return the preview, which its caller closes
     */
    MarkdownPreview create(Consumer<PreviewEvent> events);
}
