package dev.jasper.app.platform;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Where a class was loaded from: its jar, or its classes directory during development.
 *
 * <p>Read from the class's own resource URL, which the class path's resource lookup resolves to the file that is
 * installed.
 */
public final class CodeLocation {
    /** The prefix of a multi-release jar's versioned section, which a class served from it carries in its resource. */
    private static final Pattern VERSIONED_SECTION = Pattern.compile("META-INF/versions/[0-9]+/");

    private CodeLocation() { }

    /** The jar or classes directory {@code type} was loaded from; null when it cannot be determined. */
    public static Path of(Class<?> type) {
        String entry = type.getName().replace('.', '/') + ".class";
        return of(type.getResource("/" + entry), entry);
    }

    /**
     * The jar or classes directory holding the class file {@code entry} that was found at {@code resource}:
     * {@code jar:file:<jar>!/<entry>} gives the jar, also when {@code <entry>} is preceded by the versioned section
     * {@code META-INF/versions/<n>/} of a multi-release jar, and {@code file:<dir>/<entry>} gives the directory;
     * anything else, null.
     */
    static Path of(URL resource, String entry) {
        if (resource == null) return null;
        try {
            if (resource.getProtocol().equals("jar")) {
                String path = resource.getPath();
                int separator = path.lastIndexOf("!/");
                if (separator < 0 || !isEntry(path.substring(separator + 2), entry)) return null;
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

    /** Whether {@code inJar}, the part of a jar resource after {@code !/}, is {@code entry} or its versioned copy. */
    private static boolean isEntry(String inJar, String entry) {
        if (inJar.equals(entry)) return true;
        var versioned = VERSIONED_SECTION.matcher(inJar);
        return versioned.lookingAt() && inJar.substring(versioned.end()).equals(entry);
    }
}
