package dev.jasper.app.workspace;

/** Shared one-line wording for automatic tab titles and terminal-produced notifications. */
final class TerminalTitle {
    private TerminalTitle() { }

    /** iTerm-style session title and foreground job. Program titles are opaque text. */
    static String tab(String programTitle, java.nio.file.Path directory, String job) {
        String title = singleLine(programTitle);
        if (title.isBlank()) {
            java.nio.file.Path home = java.nio.file.Path.of(System.getProperty("user.home"));
            title = directory == null ? "Terminal" : directory.equals(home) ? "~"
                : directory.getFileName() == null ? directory.toString() : directory.getFileName().toString();
        }
        return job == null || job.isBlank() ? title : title + " (" + singleLine(job) + ")";
    }

    /** Preserve the full text; each surface clips to its own available width. */
    static String singleLine(String title) {
        return title == null ? "" : title.replace("\r", "").replace("\n", " ↵ ");
    }
    /** The window title for a shell-reported title; "Jasper" when the shell has not set one. */
    static String windowTitle(String shellTitle) {
        return shellTitle == null || shellTitle.isBlank() ? "Jasper" : shellTitle;
    }
}
