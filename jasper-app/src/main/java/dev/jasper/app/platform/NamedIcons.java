package dev.jasper.app.platform;

import java.util.Map;
import java.util.Set;

/**
 * App-owned modern resources for semantic SDK icon names: IntelliJ artwork, or the tinted Tabler
 * outline where IntelliJ has no equivalent. LOCK and UNLOCK stay a Tabler pair shared with Vault.
 */
final class NamedIcons {
    private static final String INTELLIJ = "dev/jasper/app/icons/intellij/";
    private static final String STANDARD = "dev/jasper/app/icons/standard/";
    private static final Map<String, String> MODERN = Map.ofEntries(
        Map.entry("ADD", INTELLIJ + "add.svg"),
        Map.entry("BOOKMARK", INTELLIJ + "bookmark.svg"),
        Map.entry("CLOSE", INTELLIJ + "close.svg"),
        Map.entry("CONNECT", STANDARD + "CONNECT.svg"),
        Map.entry("COPY", INTELLIJ + "copy.svg"),
        Map.entry("DELETE", STANDARD + "DELETE.svg"),
        Map.entry("DISCONNECT", STANDARD + "DISCONNECT.svg"),
        Map.entry("EXECUTE", INTELLIJ + "execute.svg"),
        Map.entry("FOLDER", INTELLIJ + "folder.svg"),
        Map.entry("HELP", INTELLIJ + "help.svg"),
        Map.entry("HISTORY", INTELLIJ + "history.svg"),
        Map.entry("INFO", INTELLIJ + "information.svg"),
        Map.entry("KEY", STANDARD + "KEY.svg"),
        Map.entry("LOCK", STANDARD + "LOCK.svg"),
        Map.entry("NETWORK", INTELLIJ + "web.svg"),
        Map.entry("PASTE", INTELLIJ + "menu-paste.svg"),
        Map.entry("REFRESH", INTELLIJ + "refresh.svg"),
        Map.entry("REMOVE", INTELLIJ + "remove.svg"),
        Map.entry("SAVE", INTELLIJ + "menu-saveall.svg"),
        Map.entry("SEARCH", INTELLIJ + "find.svg"),
        Map.entry("SERVER", INTELLIJ + "server.svg"),
        Map.entry("SETTINGS", INTELLIJ + "gearPlain.svg"),
        Map.entry("SPLIT", INTELLIJ + "splitVertically.svg"),
        Map.entry("TERMINAL", INTELLIJ + "console.svg"),
        Map.entry("UNLOCK", STANDARD + "UNLOCK.svg"),
        Map.entry("ZOOM", INTELLIJ + "expandComponent.svg"));
    static final Set<String> NAMES = MODERN.keySet();
    private NamedIcons() { }

    static String resource(String name) {
        if (name == null || !NAMES.contains(name)) throw new IllegalArgumentException("Unknown icon name: " + name);
        return MODERN.get(name);
    }

    /** True for the Tabler fallback, which is recoloured to the chrome foreground. */
    static boolean tinted(String name) { return resource(name).startsWith(STANDARD); }
}
