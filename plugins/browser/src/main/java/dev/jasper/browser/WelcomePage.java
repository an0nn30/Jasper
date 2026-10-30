package dev.jasper.browser;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.sdk.ui.Appearance;
import dev.jasper.sdk.ui.WorkspacePalette;
import java.awt.Color;
import java.util.Locale;

/**
 * {@code jasper-about:welcome}, the default start page, in the workspace theme's colours: the heading "Welcome" and
 * Quick Actions, whose Settings opens Settings › Browser through the {@code jasper-about:settings} action. The page is
 * built on the UI thread when the plugin starts and again after each theme change, so Chromium's IO thread, which asks
 * for it, only reads the last one built. A page already open shows a theme change when it loads again.
 */
final class WelcomePage {
    /** The page's name, and the name of the action that opens Settings › Browser. */
    static final String NAME = "welcome", SETTINGS = "settings";
    private final Appearance appearance;
    private volatile String html;

    /** Builds the page now and after each theme change. UI thread, while the plugin starts. */
    WelcomePage(Appearance appearance) {
        this.appearance = appearance;
        rebuild();
        appearance.onChanged(variant -> rebuild());
    }

    /** The page's HTML. Any thread. */
    String html() {
        return html;
    }

    private void rebuild() {
        html = render(appearance.workspacePalette(), appearance.workspaceVariant() == dev.jasper.sdk.Variant.DARK);
    }

    /** The page in {@code palette}'s colours. No transitions or animations. */
    static String render(WorkspacePalette palette, boolean dark) {
        return """
            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="color-scheme" content="%s">
            <title>Welcome</title>
            <style>
            :root {
              --background: %s;
              --card: %s;
              --text: %s;
              --muted: %s;
              --border: %s;
              --accent: %s;
            }
            html, body { margin: 0; background: var(--background); color: var(--text); }
            body { font: 15px/1.5 system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", sans-serif; }
            main { max-width: 640px; margin: 0 auto; padding: 72px 24px; }
            h1 { margin: 0 0 40px; font-size: 32px; font-weight: 600; }
            h2 { margin: 0 0 12px; font-size: 13px; font-weight: 600; color: var(--muted); }
            ul { display: flex; flex-wrap: wrap; gap: 12px; margin: 0; padding: 0; list-style: none; }
            a { display: block; min-width: 120px; padding: 12px 16px; border: 1px solid var(--border); border-radius: 8px;
                background: var(--card); color: var(--text); text-decoration: none; }
            a:hover { border-color: var(--accent); }
            a:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
            </style>
            </head>
            <body>
            <main>
            <h1>Welcome</h1>
            <section aria-labelledby="quick-actions">
            <h2 id="quick-actions">Quick Actions</h2>
            <ul>
            <li><a href="%s">Settings</a></li>
            </ul>
            </section>
            </main>
            </body>
            </html>
            """.formatted(dark ? "dark" : "light", css(palette.background()), css(palette.surfaceRaised()), css(palette.text()),
            css(palette.textMuted()), css(palette.border()), css(palette.accent()), AboutScheme.NAME + ":" + SETTINGS);
    }

    /** {@code color} as CSS, its alpha kept. */
    private static String css(Color color) {
        if (color.getAlpha() == 255) return String.format(Locale.ROOT, "#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        return String.format(Locale.ROOT, "rgba(%d, %d, %d, %.3f)", color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() / 255.0);
    }
}
