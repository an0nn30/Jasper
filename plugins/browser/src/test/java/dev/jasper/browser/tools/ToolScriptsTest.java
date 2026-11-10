package dev.jasper.browser.tools;

import dev.jasper.browser.json.StrictJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The page scripts of reader mode (spec section 10, plan 7's Task 5), as Java can check them without a JavaScript
 * engine: Readability.js verbatim inside a function whose {@code module} is undefined, kept under its symbol, then
 * {@code reader.js} called with a command, the check's token and, to show, the CSS as one string literal (F11, F24); and
 * Task 6's distraction removal, {@code distractions.js} called with a command, a token or a point.
 */
class ToolScriptsTest {
    private static final String WRAPPER_START = "(function (module) {\n",
        WRAPPER_END = "\nwindow[Symbol.for(\"dev.jasper.browser.readability\")] = Readability;\n})(undefined);\n";

    /**
     * The check holds Readability.js verbatim, its licence header first, inside {@code (function (module) { … })(undefined)},
     * so the page's own {@code module} stays untouched and Readability's guard exports nothing, and keeps it under
     * {@code Symbol.for("dev.jasper.browser.readability")}; then {@code reader.js}'s check, with the token.
     */
    @Test void theCheckHoldsReadabilityVerbatimInsideTheModuleShadowThenTheReadersCheck() throws IOException {
        String readability = new String(ReadabilityBundleTest.bundled(), StandardCharsets.UTF_8);
        String check = ToolScripts.readerCheck(7);
        assertThat(check).startsWith(WRAPPER_START + readability + WRAPPER_END)
            .contains(WRAPPER_START + "/*\n * Copyright (c) 2010 Arc90 Inc\n")
            .endsWith(WRAPPER_END + ToolScripts.reader() + "\n(\"check\", 7);");
        assertThat(check.length()).isEqualTo(WRAPPER_START.length() + readability.length() + WRAPPER_END.length()
            + ToolScripts.reader().length() + "\n(\"check\", 7);".length());
    }

    /**
     * {@code readerShow}'s CSS goes into the script as one escaped string literal, as {@link StrictJson#quote} writes it,
     * so no quote, backslash, {@code </script>} or line separator in it ends the literal or the call; Readability.js comes
     * with it, as the page's copy may be gone.
     */
    @Test void theCssStaysOneStringLiteral() throws IOException {
        String css = ".reader { --text: \"#fff\"; content: '\\\\'; } </script><script>alert(1)</script>\n" + (char) 0x2028 + "end";
        String show = ToolScripts.readerShow(css, 9);
        String readability = new String(ReadabilityBundleTest.bundled(), StandardCharsets.UTF_8);
        assertThat(show).startsWith(WRAPPER_START + readability + WRAPPER_END + ToolScripts.reader() + "\n(\"show\", 9, ");
        String call = "\n(\"show\", 9, " + StrictJson.quote(css) + ");";
        assertThat(show).endsWith(call);
        String literal = call.substring("\n(\"show\", 9, ".length(), call.length() - ");".length());
        assertThat(StrictJson.array("[" + literal + "]", 1)).as("one string, the CSS itself").hasValue(List.of(css));
        assertThat(literal).doesNotContain("\n", String.valueOf((char) 0x2028));
    }

    /** Each script carries its token; hiding needs neither Readability nor a token. */
    @Test void theTokenIsInTheCheckAndTheShowAndHideTakesNone() {
        assertThat(ToolScripts.readerCheck(1)).endsWith("\n(\"check\", 1);");
        assertThat(ToolScripts.readerCheck(9_007_199_254_740_991L)).as("JavaScript's largest safe integer")
            .endsWith("\n(\"check\", 9007199254740991);");
        assertThat(ToolScripts.readerShow("", 42)).endsWith("\n(\"show\", 42, \"\");");
        assertThat(ToolScripts.readerHide()).isEqualTo(ToolScripts.reader() + "\n(\"hide\");").doesNotContain("Arc90");
        for (long bad : new long[] {0, -1, 9_007_199_254_740_992L, Long.MAX_VALUE}) {
            assertThatThrownBy(() -> ToolScripts.readerCheck(bad)).as(Long.toString(bad)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ToolScripts.readerShow("", bad)).as(Long.toString(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * F11: a strict {@code style-src} refuses a style element, in the document or a shadow root, so the reader's overlay
     * styles its shadow root with a constructed sheet ({@code replaceSync}, {@code adoptedStyleSheets}), which no content
     * security policy governs, and neither script makes a style element.
     */
    @Test void noScriptMakesAStyleElement() {
        for (String script : List.of(ToolScripts.readerCheck(1), ToolScripts.readerShow(".reader { }", 1), ToolScripts.readerHide()))
            assertThat(script).doesNotContainIgnoringCase("<style").doesNotContainIgnoringCase("createElement('style'")
                .doesNotContainIgnoringCase("createElement(\"style\"");
        // F24: the overlay inserts Readability's node, never HTML text (Readability's own parse uses innerHTML on its copy).
        assertThat(ToolScripts.reader()).contains("replaceSync", "adoptedStyleSheets", "attachShadow", "{mode: 'open'}",
            "serializer: node => node").doesNotContain("innerHTML", "insertAdjacentHTML", "outerHTML", "document.write");
    }

    /**
     * The overlay's root takes the theme's colours under {@link ToolScripts#READER_ROOT}, the selector the item's CSS
     * uses; with no title the article is called "Article" (spec section 10); and the script names itself as Jasper's.
     */
    @Test void theReadersRootIsTheOneTheCssSelects() {
        assertThat(ToolScripts.READER_ROOT).isEqualTo(".reader");
        assertThat(ToolScripts.reader()).contains("const ROOT = 'reader';", "'Article'", "'Escape'", "op: 'reader'",
            "requestIdleCallback", "maxElemsToParse: LIMIT", "const LIMIT = 50000;", "Symbol.for('dev.jasper.browser.readability')",
            "Symbol.for('dev.jasper.browser.reader')");
        assertThat(ToolScripts.READER_URL).isEqualTo("jasper-about:reader.js");
    }

    /**
     * D29 and D30, as far as Java can see them: while the reader shows, keys stop at its window's capture listeners and
     * the page's body is inert; the article drops SMIL's {@code animate} and {@code set}, and its custom elements are
     * unwrapped, so none of the page's code runs inside the overlay. The stub harness and Task 9 check the behaviour.
     */
    @Test void theReaderKeepsThePageOutOfTheOverlay() {
        assertThat(ToolScripts.reader()).contains("['keydown', onKeyDown, true]", "['keypress', onKey, true]", "['keyup', onKey, true]",
            "inertOf.set", "animate, set", "replaceWith", "'is'", "preventScroll: true");
    }

    /**
     * D31 and D32: while the reader shows, Jasper's Find searches the article in its shadow root, which {@code reader.js}
     * hands {@code find.js} through the reader's {@code root()}; the overlay's own sheet styles find's highlights there,
     * with find's names and colours; and pointer, mouse, click and touch events stop at the shadow root, wheel aside.
     */
    @Test void theReaderAndFindShareTheArticlesRootAndTheHighlights() throws IOException {
        String find;
        try (var in = ToolScriptsTest.class.getResourceAsStream("/dev/jasper/browser/page/find.js")) {
            assertThat(in).isNotNull();
            find = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(find).contains("const ALL = 'jasper-find', CURRENT = 'jasper-find-current';",
            "'::highlight(' + ALL + ') { background-color: #FFFF00; color: #000000; }\\n'",
            "'::highlight(' + CURRENT + ') { background-color: #FF9632; color: #000000; }'",
            "Symbol.for('dev.jasper.browser.reader')", ".root()");
        assertThat(ToolScripts.reader()).contains("::highlight(jasper-find) { background-color: #FFFF00; color: #000000; }",
            "::highlight(jasper-find-current) { background-color: #FF9632; color: #000000; }", "root()",
            "'pointerdown'", "'mousedown'", "'click'", "'auxclick'", "'dblclick'", "'contextmenu'", "'touchstart'")
            .doesNotContain("'wheel'");
    }

    @Test void theReaderScriptIsAsciiWithNoControlCharacterButTheNewline() {
        assertThat(ToolScripts.reader().chars().filter(c -> (c < 0x20 && c != '\n') || c >= 0x7F)).isEmpty();
        assertThat(ToolScripts.reader()).startsWith("/*").endsWith("})\n");
    }

    /**
     * Task 6: distraction removal's scripts are {@code distractions.js} called with a command: start with the token its
     * Escape reports, stop, and removeAt with a point in CSS pixels, each number as Java writes a double, whatever the
     * locale, so a comma never splits one. They need no Readability.
     */
    @Test void distractionRemovalsScriptsCallDistractionsJsWithTheirCommand() {
        assertThat(ToolScripts.DISTRACTIONS_URL).isEqualTo("jasper-about:distractions.js");
        assertThat(ToolScripts.distractionsStart(7)).isEqualTo(ToolScripts.distractions() + "\n(\"start\", 7);");
        assertThat(ToolScripts.distractionsStart(9_007_199_254_740_991L)).endsWith("\n(\"start\", 9007199254740991);");
        assertThat(ToolScripts.distractionsStop()).isEqualTo(ToolScripts.distractions() + "\n(\"stop\");");
        assertThat(ToolScripts.distractionsRemoveAt(200.0, 100.0)).isEqualTo(ToolScripts.distractions() + "\n(\"removeAt\", 200.0, 100.0);");
        assertThat(ToolScripts.distractionsRemoveAt(0.5, -3.25)).endsWith("\n(\"removeAt\", 0.5, -3.25);");
        assertThat(ToolScripts.distractionsRemoveAt(1e21, 2.0)).as("JavaScript reads Java's exponent").endsWith("\n(\"removeAt\", 1.0E21, 2.0);");
        for (String script : List.of(ToolScripts.distractionsStart(1), ToolScripts.distractionsStop(), ToolScripts.distractionsRemoveAt(1, 2)))
            assertThat(script).doesNotContain("Arc90");
        for (long bad : new long[] {0, -1, 9_007_199_254_740_992L, Long.MAX_VALUE})
            assertThatThrownBy(() -> ToolScripts.distractionsStart(bad)).as(Long.toString(bad)).isInstanceOf(IllegalArgumentException.class);
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThatThrownBy(() -> ToolScripts.distractionsRemoveAt(bad, 1)).as(Double.toString(bad)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ToolScripts.distractionsRemoveAt(1, bad)).as(Double.toString(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * F11: distraction removal's crosshair is a constructed sheet the document adopts, which also keeps the pointer out
     * of the page's frames; its outline and its hiding are inline declarations set through {@code setProperty} with the
     * important priority, never a style element, which a strict {@code style-src} would refuse, and never HTML text.
     */
    @Test void distractionRemovalMakesNoStyleElement() {
        assertThat(ToolScripts.distractions()).doesNotContainIgnoringCase("<style").doesNotContainIgnoringCase("createElement('style'")
            .doesNotContainIgnoringCase("createElement(\"style\"").doesNotContain("createElement", "innerHTML", "insertAdjacentHTML", "outerHTML",
                "document.write", ".cssText")
            .contains("replaceSync", "adoptedStyleSheets", "'*{cursor:crosshair!important}'", "'2px solid #FF0000'", "'important'",
                "['display', 'none', 'important']");
        // A click over a frame, as a cookie banner's, reaches the element holding it in the page, not the frame's document.
        assertThat(ToolScripts.distractions()).contains("'iframe,frame,object,embed{pointer-events:none!important}'",
            "[CROSSHAIR + '\\n' + FRAMES]");
    }

    /**
     * Task 6's {@code distractions.js}, as far as Java can see it: kept under its symbol, made once with the built-ins it
     * calls; a pointer's press, release and click stop at the window's capture listeners; Escape reports the mode off with
     * its token; and {@code removeAt} looks at {@code document.elementFromPoint}. The stub harness and Task 9 check the
     * behaviour.
     */
    @Test void distractionRemovalStopsThePagesClicksAndReportsEscape() {
        assertThat(ToolScripts.distractions()).contains("Symbol.for('dev.jasper.browser.distractions')", "op: 'distractions'",
            "state: 'off'", "'Escape'", "elementFromPoint", "getComputedStyle", "'fixed'", "'sticky'", "['pointerdown', onPress, true]",
            "['pointerup', onPress, true]", "['mousedown', onPress, true]", "['mouseup', onPress, true]", "['click', onClick, true]",
            "['keydown', onKeyDown, true]", "['pagehide', onPageHide, true]", "stopImmediatePropagation", "preventDefault",
            "Symbol.for('dev.jasper.browser.reader')", "const COVERING = 0.9;");
        // Task 6's review: one pointer click hides (D34); Escape's keyup and repeats stop at the window after it ends; a
        // hidden cookie wall leaves no scroll lock (D35).
        assertThat(ToolScripts.distractions()).contains("getter(UiEvents, 'detail')", "getter(KeyEvents, 'repeat')",
            "['keyup', onKeyUp, true]", "['overflow-y', 'visible', 'important']", "'clip'");
        assertThat(ToolScripts.distractions().chars().filter(c -> (c < 0x20 && c != '\n') || c >= 0x7F)).isEmpty();
        assertThat(ToolScripts.distractions()).startsWith("/*").endsWith("})\n");
    }
}
