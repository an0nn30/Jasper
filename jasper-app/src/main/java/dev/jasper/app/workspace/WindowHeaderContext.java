package dev.jasper.app.workspace;

import dev.jasper.app.terminals.PaneSnapshot;
import java.nio.file.Path;

/** Stable focused-pane context, independent of terminal OSC titles and manual tab names. */
record WindowHeaderContext(String label, String tooltip, String icon) {
    static WindowHeaderContext from(PaneSnapshot pane, Path home) {
        if (pane == null) return new WindowHeaderContext("Jasper", "Jasper", "TERMINAL");
        if (pane.providerId().isPresent() || pane.remoteDirectory().isPresent()) {
            var remote = pane.remoteDirectory();
            // The provider's saved session name identifies an SSH host before any OSC 7 arrives.
            String host = pane.providerId().isPresent() ? clean(pane.shell())
                : clean(remote.orElseThrow().host());
            if (host.isBlank()) host = remote.map(location -> clean(location.host())).orElse("Remote");
            if (host.isBlank()) host = "Remote";
            String path = remote.map(location -> clean(location.path())).orElse("");
            String text = host + (path.isBlank() ? "" : " · " + path);
            return new WindowHeaderContext(text, "Session: " + text, "SERVER");
        }
        Path directory = pane.workingDirectory().orElse(home).toAbsolutePath().normalize();
        String label = directory.equals(home.toAbsolutePath().normalize()) ? "~"
            : directory.getFileName() == null ? directory.toString() : directory.getFileName().toString();
        return new WindowHeaderContext(clean(label), clean(directory.toString()), "FOLDER");
    }

    private static String clean(String text) {
        var result = new StringBuilder();
        text.codePoints().limit(1024).forEach(point -> result.appendCodePoint(Character.isISOControl(point) ? ' ' : point));
        return result.toString().strip();
    }
}
