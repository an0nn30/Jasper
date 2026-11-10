package dev.jasper.browser.tools;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The references of a stylesheet, a style element or a style attribute that Save Page As fetches and rewrites (plan 7's
 * Task 7): quoted and bare {@code url()}, {@code @import "…"} and {@code @import url(…)}, relative ones resolved against
 * the base; {@code data:} and {@code #fragment} references left alone, and neither a comment's nor a string's text read
 * as one. A reference with a local name is written with it, and any other as its absolute address. Pure.
 */
class CssLinksTest {
    private static final URI BASE = URI.create("https://example.com/css/site.css");
    private static final Function<URI, Optional<String>> NONE = uri -> Optional.empty();

    @Test void eachFormOfReferenceIsFound() {
        String css = """
            @import "reset.css";
            @import url(print.css) print;
            @IMPORT 'theme.css';
            body { background: url(bg.png) no-repeat; }
            .a { background-image: url( "a b.png" ); }
            .b { background-image: URL('/img/b.png'); }
            @font-face { src: url(https://fonts.example/f.woff2) format("woff2"), url(../fonts/f.woff); }
            """;
        assertThat(CssLinks.references(css, BASE)).containsExactly(URI.create("https://example.com/css/reset.css"),
            URI.create("https://example.com/css/print.css"), URI.create("https://example.com/css/theme.css"),
            URI.create("https://example.com/css/bg.png"), URI.create("https://example.com/css/a%20b.png"),
            URI.create("https://example.com/img/b.png"), URI.create("https://fonts.example/f.woff2"),
            URI.create("https://example.com/fonts/f.woff"));
    }

    @Test void dataAndFragmentReferencesAreLeftAlone() {
        String css = ".a { background: url(data:image/png;base64,iVBORw0KGgo=); } .b { fill: url(#gradient); }"
            + " .c { mask: url(  'DATA:image/svg+xml,<svg/>' ); }";
        assertThat(CssLinks.references(css, BASE)).isEmpty();
        assertThat(CssLinks.rewrite(css, BASE, uri -> Optional.of("never"))).isEqualTo(css);
    }

    @Test void commentsStringsAndOtherFunctionsAreNoReferences() {
        String css = "/* url(commented.png) @import 'no.css'; */ .a::before { content: \"url(text.png)\"; }"
            + " .b { background: myurl(not.png); } .c { content: 'it\\'s url(x.png)'; }";
        assertThat(CssLinks.references(css, BASE)).isEmpty();
        assertThat(CssLinks.rewrite(css, BASE, NONE)).isEqualTo(css);
    }

    @Test void escapesAreReadAndAReferenceResolvesAgainstTheBase() {
        assertThat(CssLinks.references(".a { background: url(a\\)b.png); } .b { background: url(\"c\\\"d.png\"); }"
            + " .e { background: url(\\66 .png); }", URI.create("https://example.com/")))
            .containsExactly(URI.create("https://example.com/a)b.png"), URI.create("https://example.com/c%22d.png"),
                URI.create("https://example.com/f.png"));
        assertThat(CssLinks.references("a { b: url(x.png) }", URI.create("https://example.com/dir/page"))).as("against the base's folder")
            .containsExactly(URI.create("https://example.com/dir/x.png"));
        assertThat(CssLinks.references("a { b: url(//cdn.example/x.png) }", BASE)).containsExactly(URI.create("https://cdn.example/x.png"));
    }

    /** A reference with a local name gets it, quoted; one without stays as its absolute address, so it still loads online. */
    @Test void aLocalReferenceIsRewrittenAndAFailedOneKeptAbsolute() {
        Map<URI, String> local = Map.of(URI.create("https://example.com/css/bg.png"), "bg.png",
            URI.create("https://example.com/css/reset.css"), "reset.css", URI.create("https://example.com/img/sprite.svg"), "sprite-1.svg");
        String css = "@import \"reset.css\"; @import url(gone.css); a { background: url(bg.png) } b { background: url('../img/sprite.svg#icon') }"
            + " c { background: url(missing.png) } d { background: url(#keep) }";
        assertThat(CssLinks.rewrite(css, BASE, uri -> Optional.ofNullable(local.get(uri))))
            .isEqualTo("@import \"reset.css\"; @import url(\"https://example.com/css/gone.css\"); a { background: url(\"bg.png\") }"
                + " b { background: url(\"sprite-1.svg#icon\") } c { background: url(\"https://example.com/css/missing.png\") }"
                + " d { background: url(#keep) }");
    }

    /** The local name's lookup is told the address without its fragment, which the rewritten reference keeps. */
    @Test void theLookupIsToldTheAddressWithoutItsFragment() {
        var asked = new java.util.ArrayList<URI>();
        CssLinks.rewrite("a { b: url(s.svg#one) } c { d: url(s.svg#two) }", BASE, uri -> {
            asked.add(uri);
            return Optional.of("s.svg");
        });
        assertThat(asked).containsExactly(URI.create("https://example.com/css/s.svg"), URI.create("https://example.com/css/s.svg"));
    }

    /** R46: an address a rewrite writes keeps no user name or password, though its base had them. */
    @Test void aKeptAddressHasNoUserInformation() {
        URI base = URI.create("https://user:secret@example.com/css/site.css");
        assertThat(CssLinks.references("a { b: url(x.png) }", base)).containsExactly(URI.create("https://example.com/css/x.png"));
        assertThat(CssLinks.rewrite("a { b: url(x.png) } @import 'https://u:p@cdn.example/y.css';", base, NONE))
            .isEqualTo("a { b: url(\"https://example.com/css/x.png\") } @import \"https://cdn.example/y.css\";");
    }

    /** A written address that holds a quote, a backslash or a line break stays one CSS string. */
    @Test void aWrittenAddressStaysOneString() {
        assertThat(CssLinks.rewrite("a { b: url(x.png) }", BASE, uri -> Optional.of("we\"ird\\na\nme.png")))
            .isEqualTo("a { b: url(\"we\\\"ird\\\\na\\a me.png\") }");
    }

    @Test void anUnfinishedReferenceIsNoneAndNothingThrows() {
        for (String css : new String[] {"a { b: url(", "a { b: url(x.png", "a { b: url('x.png'", "@import", "@import \"x.css", "/* open",
                "a { b: url(x y.png) }", "a { b: url(x\"y.png) }", "\\", "a { b: url(\\", "a { b: url(%zz) }"}) {
            CssLinks.references(css, BASE);
            assertThat(CssLinks.rewrite(css, BASE, NONE)).as(css).isNotNull();
        }
        assertThat(CssLinks.references("a { b: url(x y.png) }", BASE)).as("a bare address with a space is a bad url").isEmpty();
    }
}
