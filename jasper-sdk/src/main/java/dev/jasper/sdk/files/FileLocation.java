package dev.jasper.sdk.files;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A file or folder the chooser returned or opens at: a path on this machine, or a path in a root of a registered
 * {@link FileSource}.
 *
 * @since 0.10.0
 */
public sealed interface FileLocation {
    /**
     * A path on this machine.
     *
     * @param path absolute and normalized
     */
    record Local(Path path) implements FileLocation {
        /** Makes the path absolute and normalized. */
        public Local {
            path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        }
    }

    /**
     * A path in one root of a registered source.
     *
     * @param sourceId the source's {@link FileSourceSpec#id()}
     * @param rootId   the root's {@link FileRoot#id()}
     * @param path     an absolute path in that root, using {@code /}
     */
    record Sourced(String sourceId, String rootId, String path) implements FileLocation {
        /** Rejects blank ids and a path that does not start with {@code /}. */
        public Sourced {
            if (sourceId == null || sourceId.isBlank()) throw new IllegalArgumentException("A location needs a source id");
            if (rootId == null || rootId.isBlank()) throw new IllegalArgumentException("A location needs a root id");
            if (path == null || !path.startsWith("/")) throw new IllegalArgumentException("A source path must start with /: " + path);
        }
    }
}
