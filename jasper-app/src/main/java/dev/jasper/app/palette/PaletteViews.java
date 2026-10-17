package dev.jasper.app.palette;

import dev.jasper.app.presentation.PaletteLook;
import dev.jasper.ui.swing.KitContext;

/**
 * Builds the palette's view (Enterprise presentation spec 3.3 and 11.1): the card or the Run window, as the look's
 * {@link PaletteLook#view()} says. The only reader of that variant.
 */
public final class PaletteViews {
    private PaletteViews() { }

    /** The view {@code look} names, calling back {@code host}, over the chrome context {@code kit}. */
    public static PaletteView of(PaletteLook look, KitContext kit, boolean macOs, PaletteView.Host host) {
        return switch (look.view()) {
            case CARD -> new CardView(kit, macOs, host);
            case RUN_WINDOW -> new RunWindowView(kit, macOs, host);
        };
    }
}
