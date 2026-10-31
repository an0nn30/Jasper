package dev.jasper.editor.markdown;

import dev.jasper.editor.syntax.Languages;
import dev.jasper.editor.syntax.TokenRoles;
import dev.jasper.sdk.ui.SyntaxPalette;
import dev.jasper.sdk.ui.SyntaxRole;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import javax.swing.text.Segment;
import org.fife.ui.rsyntaxtextarea.TokenMaker;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.TokenTypes;

/** A render-owned lexer; state is carried across code lines, never shared with a live editor. */
final class CodeHighlighter {
    private static final Map<String, String> ALIASES = Map.ofEntries(
        Map.entry("js", "javascript"), Map.entry("ts", "typescript"), Map.entry("py", "python"),
        Map.entry("sh", "unix"), Map.entry("bash", "unix"), Map.entry("shell", "unix"),
        Map.entry("c++", "cpp"), Map.entry("c#", "cs"), Map.entry("html", "html"));
    private final TokenMaker maker;
    private final String[] openings = new String[SyntaxRole.values().length];
    private final String[] closings = new String[SyntaxRole.values().length];
    private int state = TokenTypes.NULL;

    CodeHighlighter(String language, SyntaxPalette palette, MarkdownRenderer.Dialect dialect) {
        for (var role : SyntaxRole.values()) {
            if (dialect == MarkdownRenderer.Dialect.WEB) {
                // The preview's stylesheet gives each class its colour, weight and style.
                openings[role.ordinal()] = "<span class=\"tok-" + role.name().toLowerCase(Locale.ROOT).replace('_', '-') + "\">";
                closings[role.ordinal()] = "</span>";
            } else {
                var style = palette.style(role);
                openings[role.ordinal()] = "<font color=\"" + String.format(Locale.ROOT, "#%06x", style.color().getRGB() & 0xffffff) + "\">"
                    + (style.bold() ? "<b>" : "") + (style.italic() ? "<i>" : "");
                closings[role.ordinal()] = (style.italic() ? "</i>" : "") + (style.bold() ? "</b>" : "") + "</font>";
            }
        }
        String key = firstWord(language);
        key = ALIASES.getOrDefault(key, key);
        TokenMaker found = null;
        for (var known : Languages.all()) {
            String style = known.style();
            if (!key.isEmpty() && (style.equals("text/" + key) || known.name().equalsIgnoreCase(key))) {
                found = TokenMakerFactory.getDefaultInstance().getTokenMaker(style);
                break;
            }
        }
        maker = found;
    }

    /** True for a fence the WEB dialect leaves to Mermaid: the first word of its info string is {@code mermaid}, in any case. */
    static boolean mermaid(String info) { return info != null && firstWord(info).equals("mermaid"); }

    private static String firstWord(String info) { return info.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT); }

    void line(String line, MarkdownLimits.Html out) {
        if (maker == null) { MarkdownRenderer.text(out, line); return; }
        char[] chars = line.toCharArray();
        var segment = new Segment(chars, 0, chars.length);
        var token = maker.getTokenList(segment, state, 0);
        int next = TokenTypes.NULL;
        for (; token != null; token = token.getNextToken()) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException("Markdown highlighting interrupted");
            // The final token carries continuation state even when it has no paintable text.
            next = token.getType();
            if (!token.isPaintable()) continue;
            var role = TokenRoles.role(token.getType());
            if (role == null) MarkdownRenderer.text(out, token.getLexeme());
            else {
                out.append(openings[role.ordinal()]);
                MarkdownRenderer.text(out, token.getLexeme());
                out.append(closings[role.ordinal()]);
            }
        }
        state = next;
    }
}
