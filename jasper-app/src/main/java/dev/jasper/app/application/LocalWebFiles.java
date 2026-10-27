package dev.jasper.app.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * The local files a web browser can display, which a Cmd-click (Ctrl-click elsewhere) in a terminal offers to the browser a
 * plugin provides before the Default editor (browser plan 3b, spec 9). iTerm2's types: HTML, XML, SVG, CSS, JavaScript, PDF,
 * PNG, JPEG, GIF, WebP and HEIC. Pure apart from the file check.
 */
final class LocalWebFiles {
    private static final Set<String> EXTENSIONS = Set.of("html", "htm", "xhtml", "xml", "svg", "css", "js", "mjs", "pdf",
        "png", "jpg", "jpeg", "gif", "webp", "heic");

    private LocalWebFiles() { }

    /**
     * Whether {@code file} is a regular file of a type a browser displays. The extension is the text after the last dot of
     * the file name, in any case, so {@code archive.tar.gz} is {@code gz}; a name that is a dot and a word, such as
     * {@code .html}, is a hidden file with no extension. Checks the file system, so call it off the UI thread.
     *
     * @param file the path to check
     * @return true for an existing regular file with a listed extension; false for a folder, a missing file and any other
     *     extension
     */
    static boolean matches(Path file) {
        Path name = file.getFileName();
        if (name == null) return false;
        String text = name.toString();
        int dot = text.lastIndexOf('.');
        return dot > 0 && EXTENSIONS.contains(text.substring(dot + 1).toLowerCase(Locale.ROOT)) && Files.isRegularFile(file);
    }
}
