package dev.jasper.app.platform;

import java.util.Set;

/** The sole mapping from Jasper semantic meanings to module glyphs. */
final class NamedIcons {
    static final Set<String> NAMES = Set.of("ADD", "BOOKMARK", "CLOSE", "CLOSE_HOVERED", "CONNECT", "COPY", "DELETE", "DISCONNECT", "DOWNLOAD", "DROPDOWN", "EDIT", "EXECUTE", "EXIT", "FILE", "FOLDER", "HELP", "HISTORY", "INFO", "KEY", "LINK", "LOCK", "MATCH_CASE", "MORE", "NETWORK", "NEW_FOLDER", "NEW_WINDOW", "NEXT", "NEXT_MATCH", "PASTE", "PAUSE", "PREVIOUS", "PREVIOUS_MATCH", "REFRESH", "REGEX", "REMOVE", "RESUME", "SAVE", "SEARCH", "SEARCH_HISTORY", "SERVER", "SETTINGS", "SPLIT", "TERMINAL", "UNLOCK", "UP", "UPLOAD", "ZOOM");
    private NamedIcons() { }

    /** Named module glyphs for app-owned native affordances. */
    static String kitName(String name) {
        if (name == null || !NAMES.contains(name)) throw new IllegalArgumentException("Unknown icon name: " + name);
        return switch (name) {
            case "CLOSE_HOVERED" -> "close";
            case "CONNECT", "EXECUTE", "RESUME" -> "play";
            case "DISCONNECT", "EXIT" -> "logout";
            case "DROPDOWN", "NEXT_MATCH" -> "chevron.down";
            case "PREVIOUS_MATCH", "UP" -> "chevron.up";
            case "NEXT" -> "forward";
            case "PREVIOUS" -> "back";
            case "NETWORK" -> "network";
            case "NEW_FOLDER" -> "folder.add";
            case "NEW_WINDOW" -> "window.add";
            case "SEARCH_HISTORY" -> "history";
            default -> name.toLowerCase(java.util.Locale.ROOT).replace('_', '.');
        };
    }

}
