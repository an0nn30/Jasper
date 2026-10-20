package dev.jasper.indexer.index;

/** How a listing was made. */
public enum Strategy {
    /** {@code git ls-files}: tracked and untracked files Git does not ignore. */
    GIT,
    /** A local folder walk that skips excluded folder names. */
    WALK,
    /** A remote {@code find}. */
    FIND,
    /** A remote SFTP walk. */
    SFTP
}
