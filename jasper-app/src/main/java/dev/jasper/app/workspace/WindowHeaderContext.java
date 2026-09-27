package dev.jasper.app.workspace;

import dev.jasper.app.terminals.PaneSnapshot;
import java.nio.file.Path;

/** Focused-pane command context, replaced entirely by an explicit terminal title. */
record WindowHeaderContext(String label, String tooltip, String icon) {
    static WindowHeaderContext from(PaneSnapshot pane, Path home, String programTitle, String command) {
        if (pane == null) return new WindowHeaderContext("Jasper", "Jasper", "TERMINAL");
        boolean remoteSession = pane.providerId().isPresent() || pane.remoteDirectory().isPresent();
        String icon = remoteSession ? "SERVER" : "TERMINAL";
        String override = clean(TerminalTitle.singleLine(programTitle));
        if (!override.isBlank()) return new WindowHeaderContext(override, override, icon);
        String context;
        if (remoteSession) {
            var remote = pane.remoteDirectory();
            // A saved SSH identity is more useful than an unauthenticated OSC host name.
            String host = pane.providerId().isPresent() ? clean(pane.shell())
                : clean(remote.orElseThrow().host());
            if (host.isBlank()) host = remote.map(location -> clean(location.host())).orElse("Remote");
            if (host.isBlank()) host = "Remote";
            String path = remote.map(location -> clean(location.path())).orElse("");
            context = host + (path.isBlank() ? "" : ":" + path);
        } else {
            Path directory = pane.workingDirectory().orElse(home).toAbsolutePath().normalize();
            Path absoluteHome = home.toAbsolutePath().normalize();
            String path = directory.equals(absoluteHome) ? "~" : directory.startsWith(absoluteHome)
                ? "~/" + absoluteHome.relativize(directory).toString().replace('\\', '/') : directory.toString();
            context = clean(System.getProperty("user.name", "")) + "@" + clean(path);
        }
        String text = context + (command == null || command.isBlank() ? "" : " : " + clean(TerminalTitle.singleLine(command)));
        return new WindowHeaderContext(text, text, icon);
    }

    private static String clean(String text) {
        var result = new StringBuilder();
        text.codePoints().forEach(point -> result.appendCodePoint(Character.isISOControl(point) ? ' ' : point));
        return result.toString().strip();
    }
}
