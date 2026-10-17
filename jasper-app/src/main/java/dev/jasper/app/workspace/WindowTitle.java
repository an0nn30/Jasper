package dev.jasper.app.workspace;

import dev.jasper.app.presentation.TitleFormat;
import dev.jasper.app.terminals.PaneSnapshot;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The frame title from the look's template (Enterprise presentation spec 10.4). The text between two tokens separates
 * them: a token with no value goes with the separator before it, or after it when it comes first, and a template whose
 * tokens are all empty keeps its leading text without a trailing separator.
 */
final class WindowTitle {
    private static final Pattern TOKEN = Pattern.compile("\\{(context|session|directory)\\}");
    /** Spaces and the separators a template may end its leading text with. */
    private static final Pattern TRAILING = Pattern.compile("[\\s—–·:|-]+$");

    private WindowTitle() { }

    /** {@code format} with its tokens filled in. */
    static String format(TitleFormat format, String context, String session, String directory) {
        var values = Map.of("context", Objects.requireNonNull(context), "session", Objects.requireNonNull(session),
            "directory", Objects.requireNonNull(directory));
        String template = format.template();
        var literals = new ArrayList<String>();
        var tokens = new ArrayList<String>();
        Matcher matcher = TOKEN.matcher(template);
        int at = 0;
        while (matcher.find()) {
            literals.add(template.substring(at, matcher.start()));
            tokens.add(values.get(matcher.group(1)).strip());
            at = matcher.end();
        }
        literals.add(template.substring(at));
        // literals: the leading text, then the separator before each later token, then the trailing text.
        var out = new StringBuilder(literals.getFirst());
        boolean any = false;
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).isEmpty()) continue;
            if (any) out.append(literals.get(i));
            out.append(tokens.get(i));
            any = true;
        }
        if (!any) return TRAILING.matcher(literals.getFirst()).replaceAll("") + literals.getLast();
        return out.append(literals.getLast()).toString();
    }

    /** {@code {session}}: a provided session's saved identity, a remote pane's host, else "Local: <shell>". */
    static String session(PaneSnapshot pane) {
        if (pane == null) return "";
        if (pane.providerId().isPresent()) return clean(pane.shell());
        if (pane.remoteDirectory().isPresent()) return clean(pane.remoteDirectory().get().host());
        String shell = clean(pane.shell());
        return shell.isEmpty() ? "Local" : "Local: " + shell;
    }

    /** {@code {directory}}: a remote pane's reported path as it is, a local pane's with {@code ~} for {@code home}. */
    static String directory(PaneSnapshot pane, Path home) {
        if (pane == null) return "";
        if (pane.providerId().isPresent() || pane.remoteDirectory().isPresent())
            return pane.remoteDirectory().map(location -> clean(location.path())).orElse("");
        Path directory = pane.workingDirectory().orElse(home).toAbsolutePath().normalize();
        Path absoluteHome = home.toAbsolutePath().normalize();
        String path = directory.equals(absoluteHome) ? "~" : directory.startsWith(absoluteHome)
            ? "~/" + absoluteHome.relativize(directory).toString().replace('\\', '/') : directory.toString();
        return clean(path);
    }

    private static String clean(String text) {
        var result = new StringBuilder();
        text.codePoints().forEach(point -> result.appendCodePoint(Character.isISOControl(point) ? ' ' : point));
        return result.toString().strip();
    }
}
