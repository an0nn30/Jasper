/**
 * The Browser's exported API: Markdown previews Chromium draws. {@link dev.jasper.browser.api.MarkdownPreviews},
 * published with {@code Services.publish}, makes each {@link dev.jasper.browser.api.MarkdownPreview}, a canvas that shows
 * the HTML its caller wrote in its caller's {@link dev.jasper.browser.api.PreviewTheme}, finds text in it with a
 * {@link dev.jasper.browser.api.PreviewFind}, and reports what happens in it as
 * {@link dev.jasper.browser.api.PreviewEvent}s. Exported by the plugin descriptor; nothing else in the plugin is. May
 * depend on: the JDK and the SDK.
 */
package dev.jasper.browser.api;
