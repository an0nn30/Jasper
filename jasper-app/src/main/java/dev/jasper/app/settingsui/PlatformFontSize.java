package dev.jasper.app.settingsui;

import static dev.jasper.app.config.CoreSettings.UI_FONT_SIZE;

import dev.jasper.app.settings.SettingKind;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import java.util.List;
import java.util.function.Supplier;

/**
 * A look-size switch and a manual stepper, kept mounted while inheritance controls visibility. The switch's copy is the
 * look's ({@code SettingsChrome.fontRowCopy}, spec 12.1); the first untick starts the stepper at the look's standard size
 * (spec 12.2: the kit's body size when the look's row offers the look's default, the setting's example otherwise).
 */
final class PlatformFontSize implements ValueEditor<Float> {
    /** The default look's font row copy: the row offers the platform's size, not a size the look's kit names. */
    static final String PLATFORM_COPY = "Use platform font size";

    private final Editors.NumberField<Float> number;
    private final SettingsDraft draft;
    private final String copy;
    private final Supplier<Float> standard;
    private final PageContext context;
    private boolean inherited = true, chosen;

    static Editors.Factory<Float> factory() {
        // The words are the shown copy, the toggle's and then the row's, so the default look's matches stay as they were.
        return new Editors.Factory<>((setting, context) -> new PlatformFontSize(context), List.of(), context -> {
            String copy = context.chrome().fontRowCopy();
            return List.of(toggleCopy(copy), copy);
        });
    }

    /** The toggle's label for the row's {@code copy}: "Use platform size" for "Use platform font size". */
    static String toggleCopy(String copy) { return copy.replace(" font size", " size"); }

    /**
     * The look's standard interface size: the kit's body size when the look's font row offers the look's default (spec
     * 9.1 words it so exactly when the kit names a UI face), else the setting's example. Only the first reads the kit, so a
     * context without a kit (its 5- and 6-argument forms) keeps working under the default look.
     */
    static float standardSize(PageContext context) {
        if (context.chrome().fontRowCopy().equals(PLATFORM_COPY)) return UI_FONT_SIZE.example().orElse(13f);
        return context.kit().get().kit().type().body();
    }

    PlatformFontSize(PageContext context) {
        this.context = context;
        this.draft = context.draft();
        this.copy = context.chrome().fontRowCopy();
        this.standard = () -> standardSize(context);
        var kind = (SettingKind.NumberRange) UI_FONT_SIZE.kind();
        number = new Editors.NumberField<>("Font size", new Nodes.Numeric(kind.min(), kind.max(), .5, 2), "pt", Float::valueOf, Nodes.FieldPresentation.STEPPER);
        number.show(UI_FONT_SIZE.example().orElse(13f));
    }

    @Override public boolean fullSpan() { return true; }
    @Override public boolean cardRows() { return true; }
    @Override public Nodes.Node node(String id, ControlState state) { return node(id, state, false); }
    @Override public Nodes.Node node(String id, ControlState state, boolean highlighted) {
        var toggle = new Nodes.Toggle(id + ":default", state, toggleCopy(copy), inherited, id + ":default", false);
        var reset = new Nodes.Button(id + ":reset", new ControlState(!inherited, state.enabled(), false, ""), "", "reset", Nodes.ButtonKind.GHOST,
            id + ":reset", "Reset interface font size to platform default");
        var control = new Nodes.Stack(id + ":toggle-controls", state, Nodes.LayoutRole.SETTINGS_TRAILING,
            context.chrome().inlineReset() ? List.of(toggle, reset) : List.of(toggle));
        var manual = new ControlState(!inherited, state.enabled(), state.busy(), state.error());
        return new Nodes.Stack(id + ":optional", state, Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.SettingRow("row:" + id, state, copy, "", control, false, highlighted, "", SchemaPage.resetMenu(context, id + ":reset", !inherited)),
            new Nodes.SettingRow(id + ":manual-row", manual, "Font size", "", number.node(id, manual), true, highlighted)));
    }
    @Override public Float value() { return inherited ? Editors.effectiveDefault(draft,UI_FONT_SIZE) : number.value(); }
    @Override public void show(Float value) {
        inherited = value.equals(Editors.effectiveDefault(draft,UI_FONT_SIZE));
        if (!inherited) { number.show(value); chosen = true; }
    }
    @Override public boolean event(String id, UiEvent event) {
        if (event instanceof UiEvent.Toggled toggle && toggle.id().equals(id + ":default")) {
            inherited = toggle.checked();
            if (!inherited && !chosen) { number.show(standard.get()); chosen = true; }
            return true;
        }
        return number.event(id, event);
    }
    @Override public boolean removesAtDefault() { return true; }
}
