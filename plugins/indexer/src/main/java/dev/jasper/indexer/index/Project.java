package dev.jasper.indexer.index;

import java.util.Objects;

/**
 * A registered project.
 *
 * @param key its identity
 * @param name what rows and cards call it: the root folder's name unless the list says otherwise
 * @param manual whether the user added it by hand (Index Folder…) rather than by answering a card
 */
public record Project(ProjectKey key, String name, boolean manual) {
    /** Rejects nulls and a blank name. */
    public Project {
        Objects.requireNonNull(key, "key");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A project needs a name");
    }
}
