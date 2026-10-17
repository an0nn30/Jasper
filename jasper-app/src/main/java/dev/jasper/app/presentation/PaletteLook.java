package dev.jasper.app.presentation;

import java.util.Objects;

/**
 * The palette's projection (spec 9.1).
 *
 * @param view the palette card, or the Run window
 * @param home where the palette opens when it has no saved place
 */
public record PaletteLook(View view, Home home) {
    /** The card or the Run window. */
    public enum View { CARD, RUN_WINDOW }
    /** Under the title field, or in a corner. */
    public enum Home { TITLE_FIELD, CORNER }

    /** Both parts are required. */
    public PaletteLook { Objects.requireNonNull(view); Objects.requireNonNull(home); }

    /** The default look's palette: the card dropped from the title field. */
    public static final PaletteLook DEFAULT = new PaletteLook(View.CARD, Home.TITLE_FIELD);
    /** The Run window at the bottom-left of the dock work area (spec 11.5). */
    public static final PaletteLook RUN_WINDOW = new PaletteLook(View.RUN_WINDOW, Home.CORNER);
}
