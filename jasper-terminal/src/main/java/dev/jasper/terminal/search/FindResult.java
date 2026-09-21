package dev.jasper.terminal.search;

/** The outcome of a find: how many matches, which one is current (1-based, 0 when none), and a regex error or null. */
public record FindResult(int count, int current, String error) {
}
