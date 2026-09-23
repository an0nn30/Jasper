package dev.jasper.app.platform;

import java.util.Map;
import java.util.Set;

/** App-owned modern resources for semantic SDK icon names. */
final class NamedIcons {
    private static final Map<String, String> MODERN = Map.ofEntries(
        Map.entry("ADD", "dev/jasper/app/icons/standard/ADD.svg"),
        Map.entry("BOOKMARK", "dev/jasper/app/icons/standard/BOOKMARK.svg"),
        Map.entry("CLOSE", "dev/jasper/app/icons/standard/CLOSE.svg"),
        Map.entry("CONNECT", "dev/jasper/app/icons/standard/CONNECT.svg"),
        Map.entry("COPY", "dev/jasper/app/icons/standard/COPY.svg"),
        Map.entry("DELETE", "dev/jasper/app/icons/standard/DELETE.svg"),
        Map.entry("DISCONNECT", "dev/jasper/app/icons/standard/DISCONNECT.svg"),
        Map.entry("EXECUTE", "dev/jasper/app/icons/standard/EXECUTE.svg"),
        Map.entry("FOLDER", "dev/jasper/app/icons/standard/FOLDER.svg"),
        Map.entry("HELP", "dev/jasper/app/icons/standard/HELP.svg"),
        Map.entry("HISTORY", "dev/jasper/app/icons/standard/HISTORY.svg"),
        Map.entry("INFO", "dev/jasper/app/icons/standard/INFO.svg"),
        Map.entry("KEY", "dev/jasper/app/icons/standard/KEY.svg"),
        Map.entry("LOCK", "dev/jasper/app/icons/standard/LOCK.svg"),
        Map.entry("NETWORK", "dev/jasper/app/icons/standard/NETWORK.svg"),
        Map.entry("PASTE", "dev/jasper/app/icons/standard/PASTE.svg"),
        Map.entry("REFRESH", "dev/jasper/app/icons/standard/REFRESH.svg"),
        Map.entry("REMOVE", "dev/jasper/app/icons/standard/REMOVE.svg"),
        Map.entry("SAVE", "dev/jasper/app/icons/standard/SAVE.svg"),
        Map.entry("SEARCH", "dev/jasper/app/icons/standard/SEARCH.svg"),
        Map.entry("SETTINGS", "dev/jasper/app/icons/standard/SETTINGS.svg"),
        Map.entry("UNLOCK", "dev/jasper/app/icons/standard/UNLOCK.svg"));
    static final Set<String> NAMES = MODERN.keySet();
    private NamedIcons() { }
    static String resource(String name) {
        if (name == null || !NAMES.contains(name)) throw new IllegalArgumentException("Unknown icon name: " + name);
        return MODERN.get(name);
    }
}
