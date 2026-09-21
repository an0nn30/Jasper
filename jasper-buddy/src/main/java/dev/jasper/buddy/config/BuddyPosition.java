package dev.jasper.buddy.config;

/**
 * Immutable AWT desktop coordinates; negative coordinates are valid on secondary monitors.
 * Presentation clamps the initial position to usable screens when first realized.
 * @param x horizontal desktop coordinate
 * @param y vertical desktop coordinate
 */
public record BuddyPosition(int x, int y) {}
