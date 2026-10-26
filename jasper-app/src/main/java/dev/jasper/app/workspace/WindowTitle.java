package dev.jasper.app.workspace;

import dev.jasper.app.config.TitleTemplate;
import dev.jasper.app.presentation.TitleFormat;
import dev.jasper.app.terminals.PaneSnapshot;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * The frame title from the look's template (Enterprise presentation spec 10.4). The text between two tokens separates
 * them: a token with no value goes with the separator before it, or after it when it comes first, and a template whose
 * tokens are all empty keeps its leading and trailing text without the separators between them. A template without
 * tokens is the title as it is.
 */
final class WindowTitle {
    private WindowTitle() { }

    /** {@code format} with its tokens filled in; {@code {context}} is used as it is, the others stripped. */
    static String format(TitleFormat format, String context, String session, String directory) {
        var values = Map.of("context", Objects.requireNonNull(context), "session", Objects.requireNonNull(session).strip(),
            "directory", Objects.requireNonNull(directory).strip());
        return TitleTemplate.parse(format.template()).fill(values::get);
    }

    /**
     * {@code {session}}: a provided session's saved identity, a remote pane's host (a provided session without a saved
     * identity included, as the header names it), else {@code "Local: "} and the shell's label.
     */
    static String session(PaneSnapshot pane) {
        if (pane == null) return "";
        if (pane.providerId().isPresent()) {
            String identity = clean(pane.shell());
            if (!identity.isEmpty()) return identity;
            return pane.remoteDirectory().map(location -> clean(location.host())).orElse("");
        }
        if (pane.remoteDirectory().isPresent()) return clean(pane.remoteDirectory().get().host());
        String shell = clean(pane.shell());
        return shell.isEmpty() ? "Local" : "Local: " + shell;
    }

    /** The status bar's session cell (plan 6b Rulings 8): a local pane's shell, else {@link #session}. */
    static String sessionCell(PaneSnapshot pane) {
        if (pane == null) return "";
        if (pane.providerId().isEmpty() && pane.remoteDirectory().isEmpty()) return clean(pane.shell());
        return session(pane);
    }

    /** {@code {directory}}: a remote pane's reported path as it is, a local pane's with {@code ~} for {@code home}. */
    static String directory(PaneSnapshot pane, Path home) {
        if (pane == null) return "";
        if (pane.providerId().isPresent() || pane.remoteDirectory().isPresent())
            return pane.remoteDirectory().map(location -> clean(location.path())).orElse("");
        return homeRelative(pane.workingDirectory().orElse(home), home);
    }

    /** A local directory as titles write it: the home folder as {@code ~}, and {@code /} after it. */
    static String homeRelative(Path directory, Path home) {
        Path absolute = directory.toAbsolutePath().normalize();
        Path absoluteHome = home.toAbsolutePath().normalize();
        String path = absolute.equals(absoluteHome) ? "~" : absolute.startsWith(absoluteHome)
            ? "~/" + absoluteHome.relativize(absolute).toString().replace('\\', '/') : absolute.toString();
        return clean(path);
    }

    private static String clean(String text) {
        var result = new StringBuilder();
        text.codePoints().forEach(point -> result.appendCodePoint(Character.isISOControl(point) ? ' ' : point));
        return result.toString().strip();
    }
}
