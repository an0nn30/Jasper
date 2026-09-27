package dev.jasper.editor.api;

/**
 * Modification time and size: two reads with equal revisions are treated as the same content.
 *
 * @param modifiedMillis modification time in milliseconds since the epoch
 * @param size size in bytes
 */
public record Revision(long modifiedMillis, long size) { }
