package dev.jasper.terminal.search;

/**
 * Immutable outcome of a search or match navigation.
 * @param count number of matches
 * @param current one-based current match, or zero when there are none
 * @param error regex error description, or null on success
 */
public record FindResult(int count, int current, String error) {
}
