package dev.jasper.app.launch;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A WSL distribution's paths as Windows sees them, and back. {@code /mnt/c/rest} is {@code C:\rest}; every other
 * path is on the distribution's share, {@code \\wsl.localhost\NAME\path}. The older {@code \\wsl$\NAME} share is
 * accepted when reading. Only the default {@code /mnt} automount root is recognised. Pure: nothing touches the
 * file system, so asking never starts a WSL 2 VM.
 */
public final class WslPaths {
    private static final List<String> SHARES = List.of("\\\\wsl.localhost\\", "\\\\wsl$\\");
    private static final Pattern DRIVE = Pattern.compile("/mnt/([A-Za-z])(/.*)?");

    private WslPaths() { }

    /** The Windows path of {@code linuxPath} in {@code distro}; empty for a relative path or one Windows cannot name. */
    public static Optional<Path> toWindows(String distro, String linuxPath) {
        if (linuxPath == null || !linuxPath.startsWith("/") || linuxPath.indexOf('\0') >= 0) return Optional.empty();
        String path = linuxPath;
        while (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        Matcher drive = DRIVE.matcher(path);
        String windows = drive.matches()
            ? Character.toUpperCase(drive.group(1).charAt(0)) + ":\\" + (drive.group(2) == null ? "" : drive.group(2).substring(1).replace('/', '\\'))
            : SHARES.getFirst() + distro + path.replace('/', '\\');
        try {
            return Optional.of(Path.of(windows));
        } catch (InvalidPathException unnameable) {
            return Optional.empty();
        }
    }

    /** The Linux path of {@code windowsPath} when it is on {@code distro}'s share; empty for any other path. */
    public static Optional<String> toLinux(String distro, Path windowsPath) {
        String text = windowsPath.toString();
        for (String share : SHARES) {
            if (!text.regionMatches(true, 0, share, 0, share.length())) continue;
            String rest = text.substring(share.length());
            int slash = rest.indexOf('\\');
            String name = slash < 0 ? rest : rest.substring(0, slash);
            if (!name.equalsIgnoreCase(distro)) return Optional.empty();
            String path = slash < 0 ? "" : rest.substring(slash).replace('\\', '/');
            while (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
            return Optional.of(path.isEmpty() ? "/" : path);
        }
        return Optional.empty();
    }

    /** The distribution whose share holds {@code windowsPath}; empty when it is not on a WSL share. */
    public static Optional<String> distroOf(Path windowsPath) {
        String text = windowsPath.toString();
        for (String share : SHARES) {
            if (!text.regionMatches(true, 0, share, 0, share.length())) continue;
            String rest = text.substring(share.length());
            int slash = rest.indexOf('\\');
            String name = slash < 0 ? rest : rest.substring(0, slash);
            return name.isEmpty() ? Optional.empty() : Optional.of(name);
        }
        return Optional.empty();
    }
}
