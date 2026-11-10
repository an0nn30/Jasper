package dev.jasper.browser.tools;

import dev.jasper.browser.json.StrictJson;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The scripts reader mode runs in a page's top frame (spec section 10), as {@link PageTools} asks for them:
 * {@code reader.js} called with a command, the token of the check it answers, and to show, the overlay's CSS.
 *
 * <p>The check and the show carry Readability.js, {@code @mozilla/readability} 0.6.0 bundled unmodified, inside
 * {@code (function (module) { … })(undefined)}: its function stays inside, a page's own {@code module} stays untouched,
 * and its own guard, which exports it only where {@code module} is an object, exports nothing. The wrapper keeps it
 * under {@code window[Symbol.for("dev.jasper.browser.readability")]}, where {@code reader.js} finds it. The CSS goes in
 * as one string literal, as {@link StrictJson#quote} writes it, so nothing in it ends the literal or the call. Neither
 * script makes a style element, which a strict {@code style-src} would refuse (plan 7's F11). Pure; any thread.
 */
public final class ToolScripts {
    /** The address reader mode's scripts go by in a page's console and stack traces. */
    public static final String READER_URL = "jasper-about:reader.js";
    /** The overlay's root, as a CSS selector: what the overlay's CSS gives the theme's colours to. */
    public static final String READER_ROOT = ".reader";
    // JavaScript's largest safe integer: a larger token would not reach the page as written.
    private static final long MAX_TOKEN = (1L << 53) - 1;

    private ToolScripts() { }

    /**
     * Reader mode's check: Readability.js, then {@code reader.js}'s {@code check}, which tells Jasper, under
     * {@code token}, how much text Readability finds once the page is idle.
     *
     * @param token the check's token, from 1 to 2<sup>53</sup> - 1
     * @return the script
     * @throws IllegalArgumentException for a token outside that range
     */
    public static String readerCheck(long token) {
        return Sources.WITH_READABILITY + Sources.READER + "\n(\"check\", " + token(token) + ");";
    }

    /**
     * Reader mode's show: Readability.js, then {@code reader.js}'s {@code show}, which lays the article over the page in
     * {@code css} and the overlay's own; its Escape reports {@code token}.
     *
     * @param css the overlay's CSS, the theme's colours on {@link #READER_ROOT}
     * @param token the token of the check whose offer the user took, from 1 to 2<sup>53</sup> - 1
     * @return the script
     * @throws IllegalArgumentException for a token outside that range
     */
    public static String readerShow(String css, long token) {
        return Sources.WITH_READABILITY + Sources.READER + "\n(\"show\", " + token(token) + ", " + StrictJson.quote(css) + ");";
    }

    /**
     * Reader mode's hide: {@code reader.js}'s {@code hide}, which takes the overlay away.
     *
     * @return the script
     */
    public static String readerHide() {
        return Sources.READER + "\n(\"hide\");";
    }

    /** {@code reader.js}, as bundled. */
    static String reader() {
        return Sources.READER;
    }

    private static String token(long token) {
        if (token < 1 || token > MAX_TOKEN) throw new IllegalArgumentException("A token from 1 to 2^53 - 1: " + token);
        return Long.toString(token);
    }

    /** The bundled scripts, read the first time a script is asked for. */
    private static final class Sources {
        static final String READABILITY = read("/dev/jasper/browser/third_party/Readability.js");
        static final String READER = read("reader.js");
        static final String WITH_READABILITY = "(function (module) {\n" + READABILITY
            + "\nwindow[Symbol.for(\"dev.jasper.browser.readability\")] = Readability;\n})(undefined);\n";

        private static String read(String name) {
            try (InputStream in = ToolScripts.class.getResourceAsStream(name)) {
                if (in == null) throw new IllegalStateException("The Browser plugin has no " + name);
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException unreadable) {
                throw new UncheckedIOException(unreadable);
            }
        }
    }
}
