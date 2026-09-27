package dev.jasper.editor.syntax;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static java.util.Map.entry;
import static org.fife.ui.rsyntaxtextarea.SyntaxConstants.*;

/**
 * Chooses a language from a file name and its first line (spec 3.1). This is the only place a language is
 * decided, which is where a TextMate lexer would plug in.
 */
public final class SyntaxDetector {
    private static final Map<String, String> NAMES = Map.ofEntries(
        entry("cargo.lock", Languages.TOML), entry("pipfile", Languages.TOML), entry("poetry.lock", Languages.TOML),
        entry(".bashrc", SYNTAX_STYLE_UNIX_SHELL), entry(".bash_profile", SYNTAX_STYLE_UNIX_SHELL),
        entry(".profile", SYNTAX_STYLE_UNIX_SHELL), entry(".zshrc", SYNTAX_STYLE_UNIX_SHELL),
        entry("dockerfile", SYNTAX_STYLE_DOCKERFILE), entry("makefile", SYNTAX_STYLE_MAKEFILE),
        entry("gnumakefile", SYNTAX_STYLE_MAKEFILE), entry(".htaccess", SYNTAX_STYLE_HTACCESS), entry("hosts", SYNTAX_STYLE_HOSTS));

    private static final Map<String, String> EXTENSIONS = Map.ofEntries(
        entry("toml", Languages.TOML), entry("json", SYNTAX_STYLE_JSON), entry("jsonc", SYNTAX_STYLE_JSON_WITH_COMMENTS),
        entry("py", SYNTAX_STYLE_PYTHON), entry("pyi", SYNTAX_STYLE_PYTHON), entry("pyw", SYNTAX_STYLE_PYTHON),
        entry("sh", SYNTAX_STYLE_UNIX_SHELL), entry("bash", SYNTAX_STYLE_UNIX_SHELL), entry("zsh", SYNTAX_STYLE_UNIX_SHELL),
        entry("yml", SYNTAX_STYLE_YAML), entry("yaml", SYNTAX_STYLE_YAML),
        entry("go", SYNTAX_STYLE_GO), entry("rs", SYNTAX_STYLE_RUST), entry("js", SYNTAX_STYLE_JAVASCRIPT),
        entry("mjs", SYNTAX_STYLE_JAVASCRIPT), entry("cjs", SYNTAX_STYLE_JAVASCRIPT), entry("ts", SYNTAX_STYLE_TYPESCRIPT),
        entry("java", SYNTAX_STYLE_JAVA), entry("kt", SYNTAX_STYLE_KOTLIN), entry("kts", SYNTAX_STYLE_KOTLIN),
        entry("groovy", SYNTAX_STYLE_GROOVY), entry("gradle", SYNTAX_STYLE_GROOVY), entry("scala", SYNTAX_STYLE_SCALA),
        entry("c", SYNTAX_STYLE_C), entry("h", SYNTAX_STYLE_C), entry("cpp", SYNTAX_STYLE_CPLUSPLUS), entry("cc", SYNTAX_STYLE_CPLUSPLUS),
        entry("cxx", SYNTAX_STYLE_CPLUSPLUS), entry("hpp", SYNTAX_STYLE_CPLUSPLUS), entry("cs", SYNTAX_STYLE_CSHARP),
        entry("css", SYNTAX_STYLE_CSS), entry("less", SYNTAX_STYLE_LESS), entry("html", SYNTAX_STYLE_HTML), entry("htm", SYNTAX_STYLE_HTML),
        entry("xml", SYNTAX_STYLE_XML), entry("xsd", SYNTAX_STYLE_XML), entry("svg", SYNTAX_STYLE_XML), entry("plist", SYNTAX_STYLE_XML),
        entry("sql", SYNTAX_STYLE_SQL), entry("md", SYNTAX_STYLE_MARKDOWN), entry("markdown", SYNTAX_STYLE_MARKDOWN),
        entry("rb", SYNTAX_STYLE_RUBY), entry("pl", SYNTAX_STYLE_PERL), entry("pm", SYNTAX_STYLE_PERL), entry("php", SYNTAX_STYLE_PHP),
        entry("lua", SYNTAX_STYLE_LUA), entry("dart", SYNTAX_STYLE_DART), entry("proto", SYNTAX_STYLE_PROTO),
        entry("properties", SYNTAX_STYLE_PROPERTIES_FILE), entry("ini", SYNTAX_STYLE_INI), entry("bat", SYNTAX_STYLE_WINDOWS_BATCH),
        entry("cmd", SYNTAX_STYLE_WINDOWS_BATCH), entry("csv", SYNTAX_STYLE_CSV), entry("tcl", SYNTAX_STYLE_TCL),
        entry("vb", SYNTAX_STYLE_VISUAL_BASIC), entry("tex", SYNTAX_STYLE_LATEX), entry("clj", SYNTAX_STYLE_CLOJURE),
        entry("dtd", SYNTAX_STYLE_DTD), entry("jsp", SYNTAX_STYLE_JSP), entry("hbs", SYNTAX_STYLE_HANDLEBARS));

    private static final Set<String> SHELLS = Set.of("sh", "bash", "zsh", "dash", "ksh");

    private SyntaxDetector() { }

    /**
     * @param fileName the file's name, without folders
     * @param firstLine the file's first line, or empty
     * @return a style key; plain text when nothing matches
     */
    public static String detect(String fileName, String firstLine) {
        String name = fileName.toLowerCase(Locale.ROOT);
        String byName = NAMES.get(name);
        if (byName != null) return byName;
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            String byExtension = EXTENSIONS.get(name.substring(dot + 1));
            if (byExtension != null) return byExtension;
        }
        return shebang(firstLine).orElse(SYNTAX_STYLE_NONE);
    }

    private static Optional<String> shebang(String line) {
        if (!line.startsWith("#!")) return Optional.empty();
        String[] words = line.substring(2).trim().split("\\s+");
        String program = words[0].substring(words[0].lastIndexOf('/') + 1);
        if (program.equals("env")) {
            program = "";
            for (int i = 1; i < words.length; i++) if (!words[i].startsWith("-")) { program = words[i]; break; }
        }
        if (program.matches("python[0-9.]*")) return Optional.of(SYNTAX_STYLE_PYTHON);
        if (SHELLS.contains(program)) return Optional.of(SYNTAX_STYLE_UNIX_SHELL);
        return Optional.empty();
    }
}
