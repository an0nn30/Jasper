package dev.jasper.app.platform;

import java.util.Map;
import java.util.Set;

/** The sole mapping from Jasper semantic meanings to bundled New UI artwork. */
final class NamedIcons {
    private static final Map<String, String> ARTWORK = Map.ofEntries(
        Map.entry("ADD", "general/add"),
        Map.entry("BOOKMARK", "bookmarks/bookmark"),
        Map.entry("CLOSE", "general/closeSmall"),
        Map.entry("CLOSE_HOVERED", "general/closeSmallHovered"),
        Map.entry("CONNECT", "run/resume"),
        Map.entry("COPY", "general/copy"),
        Map.entry("DELETE", "general/delete"),
        Map.entry("DISCONNECT", "general/exit"),
        Map.entry("DOWNLOAD", "general/download"),
        Map.entry("DROPDOWN", "general/chevronDown"),
        Map.entry("EDIT", "general/edit"),
        Map.entry("EXECUTE", "run/run"),
        Map.entry("EXIT", "general/exit"),
        Map.entry("FILE", "fileTypes/text"),
        Map.entry("FOLDER", "nodes/folder"),
        Map.entry("HELP", "general/help"),
        Map.entry("HISTORY", "general/history"),
        Map.entry("INFO", "general/informationDialog"),
        Map.entry("KEY", "standard/KEY"),
        Map.entry("LINK", "nodes/symlink"),
        Map.entry("LOCK", "general/locked"),
        Map.entry("MATCH_CASE", "inline/matchCase"),
        Map.entry("MORE", "general/moreHorizontal"),
        Map.entry("NETWORK", "toolwindows/web"),
        Map.entry("NEW_FOLDER", "actions/newFolder"),
        Map.entry("NEW_WINDOW", "actions/moveToWindow"),
        Map.entry("NEXT", "general/chevronRight"),
        Map.entry("NEXT_MATCH", "general/down"),
        Map.entry("PASTE", "general/paste"),
        Map.entry("PAUSE", "run/pause"),
        Map.entry("PREVIOUS", "general/chevronLeft"),
        Map.entry("PREVIOUS_MATCH", "general/up"),
        Map.entry("REFRESH", "general/refresh"),
        Map.entry("REGEX", "inline/regex"),
        Map.entry("REMOVE", "general/remove"),
        Map.entry("RESUME", "run/resume"),
        Map.entry("SAVE", "general/save"),
        Map.entry("SEARCH", "general/search"),
        Map.entry("SEARCH_HISTORY", "inline/searchHistory"),
        Map.entry("SERVER", "webReferences/server"),
        Map.entry("SETTINGS", "general/settings"),
        Map.entry("SPLIT", "general/splitVertically"),
        Map.entry("TERMINAL", "terminal/toolwindow/terminal"),
        Map.entry("UNLOCK", "general/unlocked"),
        Map.entry("UP", "general/up"),
        Map.entry("UPLOAD", "general/upload"),
        Map.entry("ZOOM", "inline/expand"));
    static final Set<String> NAMES = ARTWORK.keySet();
    private NamedIcons() { }

    /** Named module glyphs; catalog ids below remain only for native assets and explicit catalog choices. */
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
            case "NETWORK" -> "server";
            case "NEW_FOLDER" -> "folder.add";
            case "NEW_WINDOW" -> "window.add";
            case "SEARCH_HISTORY" -> "history";
            default -> name.toLowerCase(java.util.Locale.ROOT).replace('_', '.');
        };
    }

    static String id(String name) {
        if (name == null || !NAMES.contains(name)) throw new IllegalArgumentException("Unknown icon name: " + name);
        return ARTWORK.get(name);
    }
}
