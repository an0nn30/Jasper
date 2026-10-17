package dev.jasper.ui.swing;

import dev.jasper.ui.kit.ColorSlots;
import dev.jasper.ui.kit.ControlVariants;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.theme.PaintPair;
import java.util.List;
import java.util.Objects;

/**
 * The faces a kit's controls draw with (Enterprise presentation spec 6.4): one implementation per control point, chosen
 * once from the kit's {@link ControlVariants}. Outside the kit package it is the only reader of the variants; the faces
 * themselves never see the kit.
 */
public final class KitFaces {
    final ToggleFace toggle;
    final ButtonFace buttons;
    final FieldPolicy fields;
    final ChoiceFace exclusive, tabs;

    private KitFaces(ControlVariants controls, ColorSlots colors) {
        toggle = switch (controls.toggle()) {
            case SWITCH -> new DefaultToggleFace();
            case CHECKBOX -> new ClassicToggleFace(colors.dialogSurface());
        };
        // The text-field policy is built with the buttons point (spec 6.4).
        switch (controls.buttons()) {
            case RAISED -> { buttons = new DefaultButtonFace(); fields = new DefaultFieldPolicy(); }
            case CLASSIC -> { buttons = new ClassicButtonFace(colors.dialogSurface()); fields = new ClassicFieldPolicy(colors.dialogSurface()); }
            default -> throw new IllegalStateException("Unknown buttons variant " + controls.buttons());
        }
        exclusive = switch (controls.exclusive()) {
            case SEGMENTS -> new DefaultChoiceFace(List.of(DefaultChoiceFace.SELECTED_LABEL, DefaultChoiceFace.SELECTED_CAPTION));
            case RADIOS -> new ClassicRadiosFace(colors.dialogSurface());
        };
        tabs = new DefaultChoiceFace(List.of(DefaultChoiceFace.SELECTED_LABEL));
    }

    /** The faces {@code kit} draws with. */
    static KitFaces of(Kit kit) {
        Objects.requireNonNull(kit, "kit");
        return new KitFaces(kit.controls(), kit.colors());
    }

    /** Every face, in spec 5.2's order of control points. */
    List<Face> all() { return List.of(toggle, exclusive, tabs, buttons, fields); }

    /**
     * Every pair {@code kit}'s faces paint, in their order, for the painted-contrast suite.
     *
     * @param kit the kit whose faces are asked
     * @return the declared pairs
     */
    public static List<PaintPair> declared(Kit kit) {
        return of(kit).all().stream().flatMap(face -> face.pairs().stream()).toList();
    }
}
