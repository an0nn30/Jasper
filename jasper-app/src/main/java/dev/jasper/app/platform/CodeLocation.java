package dev.jasper.app.platform;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;

/**
 * Where a class was loaded from: its jar, or its classes directory during development.
 *
 * <p>Read from the class's own resource URL, never from its protection domain: a class the JDK 25 AOT cache loads
 * reports the location the cache was trained at, or none once that location is gone (JDK-8376576, fixed for JDK 28),
 * while the class path's resource lookup always names the file that is installed.
 */
public final class CodeLocation {
    private CodeLocation() { }

    /** The jar or classes directory {@code type} was loaded from; null when it cannot be determined. */
    public static Path of(Class<?> type) {
        String entry = type.getName().replace('.', '/') + ".class";
        return of(type.getResource("/" + entry), entry);
    }

    /**
     * The jar or classes directory holding the class file {@code entry} that was found at {@code resource}:
     * {@code jar:file:<jar>!/<entry>} gives the jar and {@code file:<dir>/<entry>} the directory; anything else, null.
     */
    static Path of(URL resource, String entry) {
        if (resource == null) return null;
        try {
            if (resource.getProtocol().equals("jar")) {
                String path = resource.getPath();
                int separator = path.lastIndexOf("!/");
                if (separator < 0 || !path.substring(separator + 2).equals(entry)) return null;
                URI jar = new URI(path.substring(0, separator));
                return "file".equals(jar.getScheme()) ? Path.of(jar).toAbsolutePath().normalize() : null;
            }
            if (resource.getProtocol().equals("file")) {
                Path file = Path.of(resource.toURI()).toAbsolutePath().normalize();
                Path relative = Path.of(entry);
                if (!file.endsWith(relative)) return null;
                Path root = file;
                for (int i = 0; i < relative.getNameCount(); i++) root = root.getParent();
                return root;
            }
            return null;
        } catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException unusable) {
            return null;
        }
    }
}
