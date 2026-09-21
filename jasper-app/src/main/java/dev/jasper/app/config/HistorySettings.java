package dev.jasper.app.config;

/** Saved history preferences with a defensive command list. */
public record HistorySettings(boolean enabled, java.util.List<String> trivialCommands) {
    public HistorySettings { trivialCommands = java.util.List.copyOf(trivialCommands); }
    public static HistorySettings defaults() {
        return new HistorySettings(true, java.util.List.of("exit", "clear", "ls", "ll", "la", "cd", "pwd", "c", "q", "logout"));
    }
}
