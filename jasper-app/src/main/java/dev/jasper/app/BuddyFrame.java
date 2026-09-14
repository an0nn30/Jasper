package dev.jasper.app;

/** Columns of the desk-buddy sprite strip, in the committed left-to-right order. */
enum BuddyFrame {
    IDLE, BLINK, WINK, WAVE_A, WAVE_B, HOP, LEAN_LEFT, LEAN_RIGHT,
    SIT, SIT_BLINK, TUCK, SLEEP_A, SLEEP_B, SLEEP_C;

    int column() { return ordinal(); }
}
