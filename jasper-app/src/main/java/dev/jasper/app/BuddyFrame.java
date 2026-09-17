package dev.jasper.app;

/**
 * Columns of the desk-buddy sprite strip, in the committed left-to-right order.
 *
 * <p>The three {@code SPARKLE_} cells are overlays, not poses: they are transparent except for the
 * stars and are painted on top of a body frame during the spawn.
 *
 * <p>The three {@code TYPE_} cells are sitting poses behind an open laptop, cycled while a command
 * has been running past the notification threshold.
 */
enum BuddyFrame {
    IDLE, BLINK, WINK, WAVE_A, WAVE_B, HOP, LEAN_LEFT, LEAN_RIGHT,
    SIT, SIT_BLINK, TUCK, SLEEP_A, SLEEP_B, SLEEP_C,
    SPARKLE_A, SPARKLE_B, SPARKLE_C,
    TYPE_A, TYPE_B, TYPE_REST;

    int column() { return ordinal(); }
}
