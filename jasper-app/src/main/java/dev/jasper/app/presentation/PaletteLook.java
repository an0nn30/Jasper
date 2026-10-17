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
}
