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
 * {@code reader.js} called with a command, the check's token and, to show, the CSS as one string literal (F11, F24).
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

    @Test void theReaderScriptIsAsciiWithNoControlCharacterButTheNewline() {
        assertThat(ToolScripts.reader().chars().filter(c -> (c < 0x20 && c != '\n') || c >= 0x7F)).isEmpty();
        assertThat(ToolScripts.reader()).startsWith("/*").endsWith("})\n");
    }
}
