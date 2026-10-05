package dev.jasper.remote.projects;

/** POSIX shell quoting for text placed in a script. */
final class ShellText {
    private ShellText() { }

    /** @return the text in single quotes, each {@code '} written as {@code '\''} */
    static String quote(String text) { return "'" + text.replace("'", "'\\''") + "'"; }

    /** @return the pattern quoted except for its {@code *}, which stays a live glob */
    static String glob(String pattern) {
        var out = new StringBuilder();
        String[] parts = pattern.split("\\*", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) out.append('*');
            out.append(quote(parts[i]));
        }
        return out.toString();
    }
}
