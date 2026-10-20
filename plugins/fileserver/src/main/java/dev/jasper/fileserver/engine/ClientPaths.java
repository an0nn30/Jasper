package dev.jasper.fileserver.engine;

import dev.jasper.fileserver.share.PathRefusedException;
import dev.jasper.fileserver.share.ShareRoot;
import java.util.ArrayList;

/**
 * Paths as FTP and SFTP clients send them: absolute and {@code /}-separated, resolved against a working directory,
 * with {@code ..} stopping at the share's root as an FTP or SFTP server's chroot does. {@code ~} is the root. Names
 * no platform can share are refused.
 */
public final class ClientPaths {
    private ClientPaths() { }

    public static String absolute(String workingDirectory, String argument) throws PathRefusedException {
        String text = argument == null ? "" : argument;
        if (text.equals("~") || text.startsWith("~/")) text = "/" + text.substring(1);
        var names = new ArrayList<String>();
        if (!text.startsWith("/") && !text.startsWith("\\"))
            for (String name : workingDirectory.split("/")) if (!name.isEmpty()) names.add(name);
        for (String name : text.split("[/\\\\]")) {
            if (name.isEmpty() || name.equals(".")) continue;
            if (name.equals("..")) {
                if (!names.isEmpty()) names.removeLast();
                continue;
            }
            names.add(name);
        }
        String path = "/" + String.join("/", names);
        ShareRoot.segments(path); // refuses names no platform can share
        return path;
    }

    /** The path the share takes: the absolute path without its leading slash. */
    public static String relative(String absolute) { return absolute.substring(1); }

    /** The last name, or {@code /} for the root. */
    public static String name(String absolute) {
        return absolute.equals("/") ? "/" : absolute.substring(absolute.lastIndexOf('/') + 1);
    }
}
