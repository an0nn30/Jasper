package dev.jasper.app.workspace;

import java.awt.Graphics;

/**
 * How a window's status bar lays out and paints its one model (Enterprise presentation spec 10.6): today's slash
 * segments or ruled cells. One instance per bar. EDT only.
 */
sealed interface StatusCells permits SlashSegments, RuledCells {
    /** Adds the bar's children this layout shows. */
    void install(WindowStatusBar bar);
    /** Lays the children out. */
    void layout(WindowStatusBar bar);
    /** Paints what the children do not. */
    void paint(WindowStatusBar bar, Graphics graphics);
    /** Takes the bar's current texts, states and theme. */
    void refresh(WindowStatusBar bar);
    /** The space between two contributed items, in device pixels. */
    int itemGap();
}
