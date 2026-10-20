package dev.jasper.ui.kit;

import dev.jasper.ui.theme.SemanticColor;
import java.util.List;
import java.util.Objects;

/**
 * The kit file's two colour slots (Enterprise presentation spec 5.7), each one of a closed set of theme roles: the
 * surface dialog bodies stand on, and the surface of the Settings navigation tree.
 *
 * @param dialogSurface {@code surface.dialog} or {@code surface.app}
 * @param navigationSurface {@code surface.sidebar} or {@code surface.card}
 */
public record ColorSlots(SemanticColor dialogSurface, SemanticColor navigationSurface) {
    /** The roles {@code dialog_surface} may name, the default's first. */
    public static final List<SemanticColor> DIALOG_SURFACES = List.of(SemanticColor.SURFACE_DIALOG, SemanticColor.SURFACE_APP);
    /** The roles {@code navigation_surface} may name, the default's first. */
    public static final List<SemanticColor> NAVIGATION_SURFACES = List.of(SemanticColor.SURFACE_SIDEBAR, SemanticColor.SURFACE_CARD);
    /** The default look's slots. */
    public static final ColorSlots DEFAULT = new ColorSlots(SemanticColor.SURFACE_DIALOG, SemanticColor.SURFACE_SIDEBAR);
    /** The classic base's slots, Enterprise's (plan 4 Rulings 10). */
    public static final ColorSlots CLASSIC = new ColorSlots(SemanticColor.SURFACE_APP, SemanticColor.SURFACE_CARD);

    /** Each slot holds one of its allowed roles. */
    public ColorSlots {
        Objects.requireNonNull(dialogSurface, "dialogSurface"); Objects.requireNonNull(navigationSurface, "navigationSurface");
        check("dialog_surface", dialogSurface, DIALOG_SURFACES);
        check("navigation_surface", navigationSurface, NAVIGATION_SURFACES);
    }

    private static void check(String key, SemanticColor role, List<SemanticColor> allowed) {
        if (!allowed.contains(role))
            throw new IllegalArgumentException(key + " must be " + allowed.get(0).key() + " or " + allowed.get(1).key() + ", not " + role.key());
    }
}
