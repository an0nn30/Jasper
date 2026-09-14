package dev.jasper.app;

import java.nio.file.Path;

/** User-controlled and automatic naming state for one terminal tab. */
final class TabState {
    private String renamedTitle;

    void rename(String title) {
        renamedTitle = title == null || title.isBlank() ? null : title;
    }

    String title(String shellTitle, Path workingDirectory) {
        if (renamedTitle != null) {
            return renamedTitle;
        }
        if (shellTitle != null && !shellTitle.isBlank()) {
            return shellTitle;
        }
        if (workingDirectory != null) {
            Path name = workingDirectory.getFileName();
            if (name != null && !name.toString().isBlank()) {
                return name.toString();
            }
        }
        return "Terminal";
    }
}
