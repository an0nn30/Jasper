package dev.jasper.indexer.index;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One project's files at one moment, with the lower-case keys the search compares against. Immutable; built off the
 * UI thread.
 */
public final class ProjectIndex {
    private final ProjectKey key;
    private final String[] files;
    private final String[] names;
    private final String[] lowerNames;
    private final String[] lowerPaths;
    private final Strategy strategy;
    private final Instant indexedAt;
    private final boolean truncated;

    /**
     * @param key the project
     * @param files paths relative to the root, {@code /}-separated
     * @param strategy how they were found
     * @param indexedAt when
     * @param truncated whether the listing stopped at the file limit
     */
    public ProjectIndex(ProjectKey key, List<String> files, Strategy strategy, Instant indexedAt, boolean truncated) {
        this.key = Objects.requireNonNull(key, "key");
        this.files = files.toArray(String[]::new);
        this.names = new String[this.files.length];
        this.lowerNames = new String[this.files.length];
        this.lowerPaths = new String[this.files.length];
        for (int i = 0; i < this.files.length; i++) {
            String path = this.files[i];
            names[i] = path.substring(path.lastIndexOf('/') + 1);
            lowerNames[i] = names[i].toLowerCase(Locale.ROOT);
            lowerPaths[i] = path.toLowerCase(Locale.ROOT);
        }
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.indexedAt = Objects.requireNonNull(indexedAt, "indexedAt");
        this.truncated = truncated;
    }

    /** @return the project */
    public ProjectKey key() { return key; }
    /** @return how many files */
    public int size() { return files.length; }
    /** @param i a file's position @return its path relative to the root */
    public String file(int i) { return files[i]; }
    /** @param i a file's position @return its own name */
    public String name(int i) { return names[i]; }
    /** @param i a file's position @return its name in lower case */
    public String lowerName(int i) { return lowerNames[i]; }
    /** @param i a file's position @return its path in lower case */
    public String lowerPath(int i) { return lowerPaths[i]; }
    /** @return every path, in index order */
    public List<String> files() { return List.of(files); }
    /** @return how the files were found */
    public Strategy strategy() { return strategy; }
    /** @return when */
    public Instant indexedAt() { return indexedAt; }
    /** @return whether the listing stopped at the file limit */
    public boolean truncated() { return truncated; }
}
