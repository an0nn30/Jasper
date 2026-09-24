package dev.jasper.app.platform;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Freedesktop Icon Naming candidates for Jasper's application and semantic icon names; first found wins. */
final class FreedesktopNames {
    private static final Map<String, List<String>> CANDIDATES = Map.ofEntries(
        Map.entry("square-plus", List.of("tab-new", "list-add")),
        Map.entry("app-window", List.of("window-new")),
        Map.entry("columns-2", List.of("view-split-left-right", "view-dual", "view-column")),
        Map.entry("maximize", List.of("view-fullscreen", "window-maximize")),
        Map.entry("search", List.of("edit-find", "system-search")),
        Map.entry("settings", List.of("preferences-system", "emblem-system")),
        Map.entry("refresh", List.of("view-refresh")),
        Map.entry("command", List.of("system-run", "utilities-terminal")),
        Map.entry("history", List.of("document-open-recent")),
        Map.entry("bookmark", List.of("bookmark-new", "user-bookmarks")),
        Map.entry("close", List.of("window-close")),
        Map.entry("exit", List.of("application-exit", "system-log-out")),
        Map.entry("LOCK", List.of("changes-prevent", "system-lock-screen")),
        Map.entry("UNLOCK", List.of("changes-allow")),
        Map.entry("KEY", List.of("dialog-password", "channel-secure")),
        Map.entry("FOLDER", List.of("folder")),
        Map.entry("SAVE", List.of("document-save")),
        Map.entry("SEARCH", List.of("edit-find", "system-search")),
        Map.entry("HISTORY", List.of("document-open-recent")),
        Map.entry("BOOKMARK", List.of("bookmark-new", "user-bookmarks")),
        Map.entry("ADD", List.of("list-add")),
        Map.entry("REMOVE", List.of("list-remove")),
        Map.entry("DELETE", List.of("edit-delete")),
        Map.entry("COPY", List.of("edit-copy")),
        Map.entry("PASTE", List.of("edit-paste")),
        Map.entry("REFRESH", List.of("view-refresh")),
        Map.entry("SETTINGS", List.of("preferences-system", "emblem-system")),
        Map.entry("EXECUTE", List.of("system-run", "media-playback-start")),
        Map.entry("CONNECT", List.of("network-connect", "network-transmit-receive")),
        Map.entry("DISCONNECT", List.of("network-disconnect", "network-offline")),
        Map.entry("NETWORK", List.of("network-workgroup", "network-server", "network-wired")),
        Map.entry("INFO", List.of("dialog-information")),
        Map.entry("HELP", List.of("help-browser", "help-contents")),
        Map.entry("CLOSE", List.of("window-close")));
    static final Set<String> NAMES = CANDIDATES.keySet();
    private FreedesktopNames() {}

    static List<String> of(String name) {
        var candidates = name == null ? null : CANDIDATES.get(name);
        if (candidates == null) throw new IllegalArgumentException("No freedesktop mapping for icon: " + name);
        return candidates;
    }
}
