package dev.jasper.editor.syntax;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import static java.util.Map.entry;
import static org.fife.ui.rsyntaxtextarea.SyntaxConstants.*;

/** The languages the editor can highlight: RSyntaxTextArea's styles plus the Editor's own TOML. */
public final class Languages {
    /** The TOML style key, served by {@link TomlTokenMaker}. */
    public static final String TOML = "text/toml";

    /**
     * One entry in the language picker.
     *
     * @param style RSyntaxTextArea's style key
     * @param name the display name
     */
    public record Language(String style, String name) { }

    private static final Map<String, String> NAMES = Map.ofEntries(
        entry(SYNTAX_STYLE_NONE, "Plain Text"), entry(SYNTAX_STYLE_ACTIONSCRIPT, "ActionScript"),
        entry(SYNTAX_STYLE_ASSEMBLER_X86, "Assembly (x86)"), entry(SYNTAX_STYLE_ASSEMBLER_6502, "Assembly (6502)"),
        entry(SYNTAX_STYLE_BBCODE, "BBCode"), entry(SYNTAX_STYLE_C, "C"), entry(SYNTAX_STYLE_CLOJURE, "Clojure"),
        entry(SYNTAX_STYLE_CPLUSPLUS, "C++"), entry(SYNTAX_STYLE_CSHARP, "C#"), entry(SYNTAX_STYLE_CSS, "CSS"),
        entry(SYNTAX_STYLE_CSV, "CSV"), entry(SYNTAX_STYLE_D, "D"), entry(SYNTAX_STYLE_DOCKERFILE, "Dockerfile"),
        entry(SYNTAX_STYLE_DART, "Dart"), entry(SYNTAX_STYLE_DELPHI, "Delphi"), entry(SYNTAX_STYLE_DTD, "DTD"),
        entry(SYNTAX_STYLE_FORTRAN, "Fortran"), entry(SYNTAX_STYLE_GO, "Go"), entry(SYNTAX_STYLE_GROOVY, "Groovy"),
        entry(SYNTAX_STYLE_HANDLEBARS, "Handlebars"), entry(SYNTAX_STYLE_HOSTS, "Hosts"), entry(SYNTAX_STYLE_HTACCESS, ".htaccess"),
        entry(SYNTAX_STYLE_HTML, "HTML"), entry(SYNTAX_STYLE_INI, "INI"), entry(SYNTAX_STYLE_JAVA, "Java"),
        entry(SYNTAX_STYLE_JAVASCRIPT, "JavaScript"), entry(SYNTAX_STYLE_JSON, "JSON"),
        entry(SYNTAX_STYLE_JSON_WITH_COMMENTS, "JSON with Comments"), entry(SYNTAX_STYLE_JSP, "JSP"),
        entry(SYNTAX_STYLE_KOTLIN, "Kotlin"), entry(SYNTAX_STYLE_LATEX, "LaTeX"), entry(SYNTAX_STYLE_LESS, "Less"),
        entry(SYNTAX_STYLE_LISP, "Lisp"), entry(SYNTAX_STYLE_LUA, "Lua"), entry(SYNTAX_STYLE_MAKEFILE, "Makefile"),
        entry(SYNTAX_STYLE_MARKDOWN, "Markdown"), entry(SYNTAX_STYLE_MXML, "MXML"), entry(SYNTAX_STYLE_NSIS, "NSIS"),
        entry(SYNTAX_STYLE_PERL, "Perl"), entry(SYNTAX_STYLE_PHP, "PHP"), entry(SYNTAX_STYLE_PROTO, "Protocol Buffers"),
        entry(SYNTAX_STYLE_PROPERTIES_FILE, "Properties"), entry(SYNTAX_STYLE_PYTHON, "Python"), entry(SYNTAX_STYLE_RUBY, "Ruby"),
        entry(SYNTAX_STYLE_RUST, "Rust"), entry(SYNTAX_STYLE_SAS, "SAS"), entry(SYNTAX_STYLE_SCALA, "Scala"),
        entry(SYNTAX_STYLE_SQL, "SQL"), entry(SYNTAX_STYLE_TCL, "Tcl"), entry(SYNTAX_STYLE_TYPESCRIPT, "TypeScript"),
        entry(SYNTAX_STYLE_UNIX_SHELL, "Shell (Bash)"), entry(SYNTAX_STYLE_VISUAL_BASIC, "Visual Basic"),
        entry(SYNTAX_STYLE_VHDL, "VHDL"), entry(SYNTAX_STYLE_WINDOWS_BATCH, "Windows Batch"), entry(SYNTAX_STYLE_XML, "XML"),
        entry(SYNTAX_STYLE_YAML, "YAML"), entry(TOML, "TOML"));

    private static boolean registered;

    private Languages() { }

    /** Registers the TOML lexer with RSyntaxTextArea's default factory; later calls do nothing. */
    public static synchronized void register() {
        if (registered) return;
        // RSyntaxTextArea creates lexers by class name, so it needs the plugin's own class loader.
        ((AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance())
            .putMapping(TOML, TomlTokenMaker.class.getName(), TomlTokenMaker.class.getClassLoader());
        registered = true;
    }

    /** @return every language the factory knows, plain text first, then by name */
    public static List<Language> all() {
        register();
        return TokenMakerFactory.getDefaultInstance().keySet().stream().map(style -> new Language(style, name(style)))
            .sorted(Comparator.comparing((Language language) -> !language.style().equals(SYNTAX_STYLE_NONE))
                .thenComparing(language -> language.name().toLowerCase(Locale.ROOT)))
            .toList();
    }

    /**
     * @param style a style key
     * @return its display name, or the key's subtype for a style without one
     */
    public static String name(String style) {
        return NAMES.getOrDefault(style, style.substring(style.indexOf('/') + 1));
    }
}
