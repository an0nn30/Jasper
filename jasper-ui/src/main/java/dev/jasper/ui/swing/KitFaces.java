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
    final GroupFace group;
    final LabelPolicy labels;
    final StepperFace stepper;
    final TableFace table;
    final TreeFace tree;
    final PromptFace prompt;

    private KitFaces(ControlVariants controls, ColorSlots colors) {
        toggle = switch (controls.toggle()) {
            case SWITCH -> new DefaultToggleFace();
            case CHECKBOX -> new ClassicToggleFace(colors.dialogSurface());
        };
        // The text-field policy is built with the buttons point (spec 6.4).
        buttons = switch (controls.buttons()) {
            case RAISED -> new DefaultButtonFace();
            case CLASSIC -> new ClassicButtonFace(colors.dialogSurface());
        };
        fields = switch (controls.buttons()) {
            case RAISED -> new DefaultFieldPolicy();
            case CLASSIC -> new ClassicFieldPolicy(colors.dialogSurface());
        };
        exclusive = switch (controls.exclusive()) {
            case SEGMENTS -> new DefaultChoiceFace(List.of(DefaultChoiceFace.SELECTED_LABEL, DefaultChoiceFace.SELECTED_CAPTION));
            case RADIOS -> new ClassicRadiosFace(colors.dialogSurface());
        };
        tabs = switch (controls.tabs()) {
            case SEGMENTS -> new DefaultChoiceFace(List.of(DefaultChoiceFace.SELECTED_LABEL));
            case FOLDER -> new ClassicFolderTabsFace(colors.dialogSurface());
        };
        group = switch (controls.group()) {
            case CARD -> new DefaultGroupFace();
            case TITLED_BOX -> new ClassicGroupFace(colors.dialogSurface());
        };
        labels = switch (controls.formLabel()) {
            case PLAIN -> new DefaultLabelPolicy();
            case COLON -> new ClassicLabelPolicy(colors.dialogSurface());
        };
        stepper = switch (controls.stepper()) {
            case STEPPER -> new DefaultStepperFace();
            case SPINNER -> new ClassicStepperFace();
        };
        table = switch (controls.table()) {
            case PLAIN -> new DefaultTableFace();
            case CLASSIC -> new ClassicTableFace(colors.dialogSurface());
        };
        tree = switch (controls.tree()) {
            case PLAIN -> new DefaultTreeFace();
            case CLASSIC -> new ClassicTreeFace(colors.dialogSurface());
        };
        prompt = switch (controls.prompt()) {
            case TILE -> new DefaultPromptFace();
            case PLAIN -> new ClassicPromptFace(colors.dialogSurface());
        };
    }

    /** The faces {@code kit} draws with. */
    static KitFaces of(Kit kit) {
        Objects.requireNonNull(kit, "kit");
        return new KitFaces(kit.controls(), kit.colors());
    }

    /** Every face, in spec 5.2's order of control points. */
    List<Face> all() { return List.of(toggle, exclusive, tabs, group, labels, table, tree, stepper, buttons, fields, prompt); }

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
