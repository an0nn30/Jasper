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

    CodeHighlighter(String language, SyntaxPalette palette) {
        for (var role : SyntaxRole.values()) {
            var style = palette.style(role);
            openings[role.ordinal()] = "<font color=\"" + String.format(Locale.ROOT, "#%06x", style.color().getRGB() & 0xffffff) + "\">"
                + (style.bold() ? "<b>" : "") + (style.italic() ? "<i>" : "");
            closings[role.ordinal()] = (style.italic() ? "</i>" : "") + (style.bold() ? "</b>" : "") + "</font>";
        }
        String key = language.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
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

    void line(String line, MarkdownLimits.Html out) {
        if (maker == null) { MarkdownRenderer.text(out, line); return; }
        char[] chars = line.toCharArray();
        var segment = new Segment(chars, 0, chars.length);
        int next = maker.getLastTokenTypeOnLine(segment, state);
        var token = maker.getTokenList(segment, state, 0);
        for (; token != null && token.isPaintable(); token = token.getNextToken()) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException("Markdown highlighting interrupted");
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
