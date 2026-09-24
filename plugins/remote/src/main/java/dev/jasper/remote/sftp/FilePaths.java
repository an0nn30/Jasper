package dev.jasper.remote.sftp;

import java.io.IOException;
import java.util.ArrayDeque;

/** POSIX path text only; never consults the local filesystem. */
public final class FilePaths {
    private FilePaths() {}
    public static String absolute(String path) throws IOException {
        if (path == null || !path.startsWith("/") || path.indexOf(0) >= 0) throw new IOException("An absolute path is required");
        var parts = new ArrayDeque<String>();
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) { if (!parts.isEmpty()) parts.removeLast(); }
            else parts.add(part);
        }
        return "/" + String.join("/", parts);
    }
    public static String child(String parent, String name) throws IOException {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..") || name.indexOf('/') >= 0 || name.indexOf(0) >= 0)
            throw new IOException("Invalid directory entry name");
        return absolute(parent + "/" + name);
    }
    public static String parent(String path) throws IOException {
        String normalized = absolute(path); int slash = normalized.lastIndexOf('/');
        return slash <= 0 ? "/" : normalized.substring(0, slash);
    }
    public static String name(String path) throws IOException { String value = absolute(path); return value.substring(value.lastIndexOf('/') + 1); }
    public static boolean overlaps(String first, String second) { return first.equals(second) || contains(first, second) || contains(second, first); }
    private static boolean contains(String parent, String path) { return path.startsWith(parent.endsWith("/") ? parent : parent + "/"); }
}
