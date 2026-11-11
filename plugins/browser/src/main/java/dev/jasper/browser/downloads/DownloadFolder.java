package dev.jasper.browser.downloads;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The Downloads folder setting (spec section 17, ruling F37): empty for the system's own Downloads folder, {@code ~} or
 * {@code ~/folder} under the home folder ({@code ~\folder} too on Windows), or an absolute folder, as typed. Pure: it
 * reads no file and creates no folder.
 */
public final class DownloadFolder {
    /** A Windows drive's absolute path, {@code C:\} or {@code C:/}. */
    private static final Pattern DRIVE = Pattern.compile("[A-Za-z]:[\\\\/].*", Pattern.DOTALL);
    /** A Windows share, {@code \\server\share}, either slash; not the {@code \\?\} or {@code \\.\} namespaces. */
    private static final Pattern SHARE = Pattern.compile("[\\\\/]{2}[^\\\\/.?][^\\\\/]*[\\\\/][^\\\\/]+.*", Pattern.DOTALL);
    /** What no Windows path holds outside its drive's colon. */
    private static final String NOT_ON_WINDOWS = "<>:\"|?*";

    private DownloadFolder() {}

    /**
     * The folder {@code setting} names, on this system.
     *
     * @param setting the Downloads folder setting
     * @param system the system's Downloads folder
     * @param home the home folder
     * @return {@code system} for an empty setting, and for one {@link #valid(String)} refuses or this system cannot read
     *     as a path, as a refused setting reads its default; {@code home} or a folder under it for {@code ~} and
     *     {@code ~/folder}; otherwise the folder as typed
     */
    public static Path resolve(String setting, Path system, Path home) {
        return resolve(setting, system, home, os());
    }

    /**
     * {@link #resolve(String, Path, Path)} on the system {@code osName} names.
     *
     * @param setting the Downloads folder setting
     * @param system the system's Downloads folder
     * @param home the home folder
     * @param osName the {@code os.name} system property
     * @return the folder
     */
    static Path resolve(String setting, Path system, Path home, String osName) {
        return resolve(setting, system, home, osName, Path::of);
    }

    /**
     * {@link #resolve(String, Path, Path, String)} with {@code paths} reading a path's text, as {@link Path#of} does.
     *
     * @param setting the Downloads folder setting
     * @param system the system's Downloads folder
     * @param home the home folder
     * @param osName the {@code os.name} system property
     * @param paths reads a path's text; it may refuse it with {@link InvalidPathException}
     * @return the folder; {@code system} for a path {@code paths} refuses
     */
    static Path resolve(String setting, Path system, Path home, String osName, Function<String, Path> paths) {
        if (setting == null || setting.isEmpty() || !valid(setting, osName)) return system;
        boolean windows = windows(osName);
        try {
            return homeRelative(setting, windows) ? home.resolve(paths.apply(underHome(setting, windows))) : paths.apply(setting);
        } catch (InvalidPathException unreadable) {
            // Not a path here, though valid took it: the system's folder, as for any refused setting.
            return system;
        }
    }

    /**
     * Whether {@code setting} is a Downloads folder setting on this system: empty, {@code ~}, {@code ~/folder}, or an
     * absolute folder. On Windows {@code ~\folder} is one too, an absolute folder starts with a drive, such as
     * {@code C:\}, or is a share, {@code \\server\share}, neither a drive-relative {@code C:folder} nor a rooted
     * {@code \folder} without a drive is one, and no name in it ends in a space, which Windows paths refuse; elsewhere
     * {@code \} is a file name's character, so {@code ~\folder} is not.
     *
     * @param setting the setting's text
     * @return whether it is one of those forms
     */
    public static boolean valid(String setting) {
        return valid(setting, os());
    }

    /**
     * {@link #valid(String)} on the system {@code osName} names.
     *
     * @param setting the setting's text
     * @param osName the {@code os.name} system property
     * @return whether it is one of the forms
     */
    static boolean valid(String setting, String osName) {
        if (setting == null) return false;
        if (setting.isEmpty()) return true;
        if (setting.indexOf('\0') >= 0) return false;
        boolean windows = windows(osName);
        if (homeRelative(setting, windows)) return !windows || windowsPath(underHome(setting, true), 0);
        if (!windows) return setting.startsWith("/");
        if (DRIVE.matcher(setting).matches()) return windowsPath(setting, 2);
        return SHARE.matcher(setting).matches() && windowsPath(setting, 0);
    }

    /** Whether {@code setting} is {@code ~}, or starts {@code ~/}, or on Windows {@code ~\}. */
    private static boolean homeRelative(String setting, boolean windows) {
        return setting.equals("~") || setting.startsWith("~/") || windows && setting.startsWith("~\\");
    }

    /** What a home-relative {@code setting} names under the home folder, without its leading separators. */
    private static String underHome(String setting, boolean windows) {
        int start = 1;
        while (start < setting.length() && (setting.charAt(start) == '/' || windows && setting.charAt(start) == '\\')) start++;
        return setting.substring(start);
    }

    /**
     * Whether {@code path}, from index {@code from}, holds no character a Windows path refuses, and no name in it ends in
     * a space: Windows drops such a space, and Java's Windows paths refuse it ("Trailing char").
     */
    private static boolean windowsPath(String path, int from) {
        for (int i = from; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c < 0x20 || NOT_ON_WINDOWS.indexOf(c) >= 0) return false;
            if (c == ' ' && (i + 1 == path.length() || path.charAt(i + 1) == '\\' || path.charAt(i + 1) == '/')) return false;
        }
        return true;
    }

    private static boolean windows(String osName) {
        return osName.startsWith("Windows");
    }

    private static String os() {
        return System.getProperty("os.name", "");
    }
}
