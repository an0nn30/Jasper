package dev.jasper.browser.tools;

import dev.jasper.browser.json.StrictJson;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The scripts the page tools run in a page's top frame (spec section 10), as {@link PageTools} asks for them: reader
 * mode's, {@code reader.js} called with a command, the token of the check it answers, and to show, the overlay's CSS;
 * and distraction removal's and Remove Element's, {@code distractions.js} called with a command, the token its Escape
 * reports or a point in CSS pixels.
 *
 * <p>The check and the show carry Readability.js, {@code @mozilla/readability} 0.6.0 bundled unmodified, inside
 * {@code (function (module) { … })(undefined)}: its function stays inside, a page's own {@code module} stays untouched,
 * and its own guard, which exports it only where {@code module} is an object, exports nothing. The wrapper keeps it
 * under {@code window[Symbol.for("dev.jasper.browser.readability")]}, where {@code reader.js} finds it. The CSS goes in
 * as one string literal, as {@link StrictJson#quote} writes it, so nothing in it ends the literal or the call. No
 * script makes a style element, which a strict {@code style-src} would refuse (plan 7's F11). Pure; any thread.
 */
public final class ToolScripts {
    /** The address reader mode's scripts go by in a page's console and stack traces. */
    public static final String READER_URL = "jasper-about:reader.js";
    /** The address distraction removal's and Remove Element's script goes by in a page's console and stack traces. */
    public static final String DISTRACTIONS_URL = "jasper-about:distractions.js";
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

    /**
     * Distraction removal's start: {@code distractions.js}'s {@code start}, which gives the page a crosshair, outlines
     * the overlay under the pointer in red and hides the one clicked, with its backdrops, until a stop or Escape, which
     * reports {@code token}.
     *
     * @param token the token of the check whose offer the user took, from 1 to 2<sup>53</sup> - 1
     * @return the script
     * @throws IllegalArgumentException for a token outside that range
     */
    public static String distractionsStart(long token) {
        return Sources.DISTRACTIONS + "\n(\"start\", " + token(token) + ");";
    }

    /**
     * Distraction removal's stop: {@code distractions.js}'s {@code stop}, which takes the crosshair, the outline and its
     * listeners away; what it hid stays hidden.
     *
     * @return the script
     */
    public static String distractionsStop() {
        return Sources.DISTRACTIONS + "\n(\"stop\");";
    }

    /**
     * Remove Element: {@code distractions.js}'s {@code removeAt}, which hides the overlay of the element at a point of
     * the page's viewport. Each number is written as {@link Double#toString(double)} writes it, whatever the locale,
     * which JavaScript reads as the same number.
     *
     * @param x the point's distance from the viewport's left, in CSS pixels
     * @param y its distance from the viewport's top, in CSS pixels
     * @return the script
     * @throws IllegalArgumentException for a number that is not finite
     */
    public static String distractionsRemoveAt(double x, double y) {
        return Sources.DISTRACTIONS + "\n(\"removeAt\", " + number(x) + ", " + number(y) + ");";
    }

    /** {@code distractions.js}, as bundled. */
    static String distractions() {
        return Sources.DISTRACTIONS;
    }

    /** {@code reader.js}, as bundled. */
    static String reader() {
        return Sources.READER;
    }

    private static String token(long token) {
        if (token < 1 || token > MAX_TOKEN) throw new IllegalArgumentException("A token from 1 to 2^53 - 1: " + token);
        return Long.toString(token);
    }

    private static String number(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("A finite number: " + value);
        return Double.toString(value);
    }

    /** The bundled scripts, read the first time a script is asked for. */
    private static final class Sources {
        static final String READABILITY = read("/dev/jasper/browser/third_party/Readability.js");
        static final String READER = read("reader.js");
        static final String DISTRACTIONS = read("distractions.js");
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
