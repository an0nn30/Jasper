package dev.jasper.buddy.config;

/** Immutable screen coordinates. Negative coordinates are valid on secondary monitors. */
public record BuddyPosition(int x, int y) {}
