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
    /** Under the title field, or centred in the window's upper part. */
    public enum Home { TITLE_FIELD, UPPER }

    /** Both parts are required. */
    public PaletteLook { Objects.requireNonNull(view); Objects.requireNonNull(home); }

    /** The default look's palette: the card dropped from the title field. */
    public static final PaletteLook DEFAULT = new PaletteLook(View.CARD, Home.TITLE_FIELD);
    /** The Run window centred in the window's upper part (spec 11.5). */
    public static final PaletteLook RUN_WINDOW = new PaletteLook(View.RUN_WINDOW, Home.UPPER);
}
