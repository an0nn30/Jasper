/**
 * The Markdown previews behind the exported {@code api} package. {@link dev.jasper.browser.preview.PreviewService}, the
 * published {@code MarkdownPreviews}, makes each {@code PagePreview}: the documents, images and theme its caller gives
 * go to the preview's {@code jasper-preview:} origin and, as calls, to its page's {@code preview.js}, and what the page
 * tells comes back, read strictly by {@code PreviewMessages}, as the API's events. {@code PreviewCss} writes the theme
 * as the CSS custom properties {@code preview.css} draws with, and as Mermaid's theme variables. The preview's Find runs
 * the {@code page} package's {@code find.js}. Depends on the {@code api}, {@code engine}, {@code page} and {@code json}
 * packages, the SDK and the JDK, and names no Chromium type, so making and feeding a preview that never shows loads no
 * Chromium class.
 */
package dev.jasper.browser.preview;
