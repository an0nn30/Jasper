package dev.jasper.remote.client;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The exec command that starts the user's login shell in a folder. The user's own shell parses it, so the folder is
 * written as {@code \0nnn} octal escapes (which read the same in POSIX shells, fish and tcsh) and decoded by
 * {@code printf %b} in a fixed {@code sh} script. A folder that cannot be entered leaves the shell in the home folder.
 */
public final class StartFolder {
    private static final String SCRIPT = "cd -- \"$(printf %b \"$0\")\" 2>/dev/null; exec \"${SHELL:-/bin/sh}\" -l";

    private StartFolder() { }

    /**
     * @param folder an absolute remote folder
     * @return the command for an exec channel with a PTY
     */
    public static String command(String folder) {
        var octal = new StringBuilder();
        for (byte b : folder.getBytes(StandardCharsets.UTF_8)) octal.append('\\').append('0').append(String.format(Locale.ROOT, "%03o", b & 0xff));
        return "exec sh -c '" + SCRIPT + "' '" + octal + "'";
    }
}
