package dev.jasper.app.workspace;

/** Shared one-line wording for automatic tab titles and terminal-produced notifications. */
final class TerminalTitle {
    private TerminalTitle() { }

    /** Short automatic tab label; an explicit program title replaces it entirely. */
    static String tab(String programTitle, String job) {
        String title = singleLine(programTitle);
        if (!title.isBlank()) return title;
        return job == null || job.isBlank() ? "Terminal" : singleLine(job);
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
