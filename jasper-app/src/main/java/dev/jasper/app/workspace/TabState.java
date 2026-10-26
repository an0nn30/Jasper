package dev.jasper.app.workspace;

/** The name the user gave one terminal tab, kept apart from its automatic name. */
final class TabState {
    private String renamedTitle;

    void rename(String title) {
        renamedTitle = title == null || title.isBlank() ? null : title;
    }

    /** The name the user gave, if any. */
    java.util.Optional<String> renamed() { return java.util.Optional.ofNullable(renamedTitle); }
}
