package dev.jasper.app.pluginmanager;

/** What each capability means to a user. Unknown capabilities are shown by name: a newer plugin may declare one. */
final class Capabilities {
    private Capabilities() { }

    static String describe(String capability) {
        String meaning = switch (capability) {
            case "terminal.observe" -> "See your terminals: titles, folders, the commands you run and how they end";
            case "terminal.selection" -> "Read the text you select in a terminal";
            case "terminal.inject" -> "Type into your terminals";
            case "terminal.open" -> "Open terminal tabs, splits and windows";
            case "session.provide" -> "Run its own terminal sessions, such as remote connections";
            default -> null;
        };
        return meaning == null ? capability : meaning + " (" + capability + ")";
    }
}
