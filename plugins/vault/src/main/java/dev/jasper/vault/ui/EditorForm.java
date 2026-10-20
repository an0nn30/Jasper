package dev.jasper.vault.ui;

import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.Option;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import dev.jasper.vault.workstation.Layout;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;

/**
 * Shared modal form behavior on a host-rendered kit view: labelled rows, Save/Cancel, failure recovery and
 * deterministic secret cleanup. Secrets live only in the view's wipeable storage; the form reads them through
 * {@link UiView#withSecret} into copies its callers own and erase. UI thread only.
 */
public class EditorForm implements AutoCloseable {
    static final String FORM = "form", SAVE = "save", CANCEL = "cancel", MESSAGE = "message", BROWSE = "browse.";

    /** A synchronous validation problem that belongs to one field: shown on that field until the next edit. */
    static final class FieldError extends IllegalArgumentException {
        final String field;
        FieldError(String field, String message) { super(message); this.field = field; }
    }

    private sealed interface Row permits TextRow, SecretRow, ChoiceRow, ToggleRow, DisplayRow { String id(); String label(); }
    private record TextRow(String id, String label, Nodes.FieldKind kind) implements Row { }
    private record SecretRow(String id, String label, String revealGroup, boolean multiline) implements Row { }
    private record ChoiceRow(String id, String label, List<Option> options, Nodes.ChoiceKind kind) implements Row { }
    private record ToggleRow(String id, String label) implements Row { }
    private record DisplayRow(String id, String label, String text, Nodes.TextRole role) implements Row { }

    private final UiKit kit;
    private final Runnable dismiss;
    private final List<Row> rows = new ArrayList<>();
    private final Map<String, String> values = new HashMap<>(), choices = new HashMap<>(), errors = new LinkedHashMap<>();
    private final Map<String, String> hints = new HashMap<>();
    private final Map<String, Boolean> toggles = new HashMap<>();
    private final Map<String, BooleanSupplier> visibility = new HashMap<>();
    private record Group(String id, String title, List<String> rows) { }
    private final List<Group> groups = new ArrayList<>();
    private boolean discreet;
    protected final void discreetInputs() { discreet=true; }
    private Layout layout = Layout.ROWS;
    private String classicTitle = "";
    private final Map<String, String> classicLabels = new HashMap<>();
    /** The composition this form takes (plan 10b); its presenter sets it before the form's view is made. */
    final void layout(Layout value) { layout = Objects.requireNonNull(value); render(); }
    /** Under CLASSIC, the title of the form's untitled group (plan 10b Rulings 10). */
    protected final void classicGroup(String title) { classicTitle = Objects.requireNonNull(title); }
    /** Under CLASSIC, row {@code id}'s label in the label column (plan 10b Rulings 7 and 10). */
    protected final void classicLabel(String id, String label) { classicLabels.put(id, label); }
    /** The entry's hue for its sheet's swatch; NONE without an identity. */
    final Nodes.Hue swatch() { return icon.isEmpty() ? Nodes.Hue.NONE : hue; }
    private String label(Row row) { return layout == Layout.CLASSIC ? classicLabels.getOrDefault(row.id(), row.label()) : row.label(); }
    private BooleanSupplier ready = () -> true;
    private String icon = "";
    private Nodes.Hue hue = Nodes.Hue.NEUTRAL;
    private Supplier<CompletableFuture<Void>> action = () -> CompletableFuture.completedFuture(null);
    private String title = "Vault entry", message = "", saveLabel = "Save", cancelLabel = "Cancel";
    private boolean closed, busy, cancelEnabled = true;
    private UiView view;
    private BiFunction<String, Optional<Path>, Optional<Path>> fileChooser = (title, initial) -> Optional.empty();

    protected EditorForm(UiKit kit, Runnable dismiss) { this.kit = Objects.requireNonNull(kit); this.dismiss = Objects.requireNonNull(dismiss); }

    final void setTitle(String title) { this.title = Objects.requireNonNull(title); render(); }

    final void setFileChooser(BiFunction<String, Optional<Path>, Optional<Path>> chooser) { fileChooser = Objects.requireNonNull(chooser); }

    /** The owned view, created on first use. The surface that mounts it disposes it when that surface closes. */
    public final UiView view() {
        if (view == null) view = kit.create(new ViewSpec(form()), this::handle);
        return view;
    }

    protected final void hint(String id, String hint) { hints.put(id, hint); }
    protected final void text(String id, String label, String initial) { rows.add(new TextRow(id, label, Nodes.FieldKind.TEXT)); values.put(id, initial); }
    protected final void path(String id, String label, String initial) { rows.add(new TextRow(id, label, Nodes.FieldKind.PATH)); values.put(id, initial); }
    protected final void secret(String id, String label, String revealGroup) { rows.add(new SecretRow(id, label, revealGroup, false)); }
    protected final void note(String id, String label) { rows.add(new SecretRow(id, label, "", true)); }
    protected final void choice(String id, String label, List<Option> options, String selected) { rows.add(new ChoiceRow(id, label, List.copyOf(options), Nodes.ChoiceKind.DROPDOWN)); choices.put(id, selected); }
    protected final void segmented(String id, String label, List<Option> options, String selected) { rows.add(new ChoiceRow(id, label, List.copyOf(options), Nodes.ChoiceKind.EXCLUSIVE)); choices.put(id, selected); }
    protected final void visibleWhen(String id, BooleanSupplier visible) { visibility.put(id, visible); }
    protected final void readyWhen(BooleanSupplier condition) { ready = condition; }
    protected final void identity(String icon, Nodes.Hue hue) { this.icon = icon; this.hue = hue; }
    protected final void group(String id, String title, String... members) { groups.add(new Group(id, title, List.of(members))); }
    protected final void toggle(String id, String label, boolean checked) { rows.add(new ToggleRow(id, label)); toggles.put(id, checked); }
    protected final void display(String id, String label, String text) { rows.add(new DisplayRow(id, label, text, Nodes.TextRole.BODY)); }
    protected final void description(String id, String label, String text) { rows.add(new DisplayRow(id, label, text, Nodes.TextRole.DESCRIPTION)); }

    /** Copies a stored secret into the view; the caller keeps, and still owns, {@code value}. */
    protected final void replaceSecret(String id, char[] value) { if (!closed) view().replaceSecret(id, value); }

    /** A copy of a secret that the caller owns and must erase. */
    protected final char[] copySecret(String id) {
        char[][] copy = {new char[0]};
        if (!closed && view != null) view.withSecret(id, chars -> copy[0] = chars.clone());
        return copy[0];
    }

    final String value(String id) { return values.getOrDefault(id, ""); }
    final String selected(String id) { return choices.getOrDefault(id, ""); }
    final boolean checked(String id) { return toggles.getOrDefault(id, false); }
    final boolean closed() { return closed; }
    final boolean busy() { return busy; }
    final String message() { return message; }

    protected final void setSaveLabel(String label) { saveLabel = label; render(); }
    protected final void setCancelLabel(String label) { cancelLabel = label; render(); }
    protected final void setCancelEnabled(boolean enabled) { cancelEnabled = enabled; render(); }
    protected final void setMessage(String text) { message = text; render(); }

    static FieldError invalid(String field, String message) { return new FieldError(field, message); }

    /** Save runs {@code operation}; a {@link FieldError} marks its field, any other failure shows in the message line. */
    protected final void submit(Supplier<CompletableFuture<Void>> operation) { action = Objects.requireNonNull(operation); }

    private void save() {
        if (closed || busy || !ready.getAsBoolean()) return;
        errors.clear();
        busy = true; message = "Saving..."; render();
        CompletableFuture<Void> pending;
        try { pending = action.get(); }
        catch (FieldError invalid) {
            busy = false; message = ""; errors.put(invalid.field, invalid.getMessage()); render(); return;
        } catch (RuntimeException failure) { pending = CompletableFuture.failedFuture(failure); }
        pending.whenComplete((ignored, failure) -> onUi(() -> {
            busy = false;
            if (closed) return;
            if (failure == null) { finish(); return; }
            message = message(failure); render();
        }));
    }

    private static void onUi(Runnable action) {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) action.run();
        else javax.swing.SwingUtilities.invokeLater(action);
    }
    private void finish() {
        // A native owner must still close if content cleanup fails; its close callback is idempotent.
        try { close(); } finally { dismiss.run(); }
    }

    private void browse(TextRow row) {
        if (closed || busy) return;
        Optional<Path> initial;
        String typed = value(row.id());
        try { initial = typed.isBlank() ? Optional.empty() : Optional.of(Path.of(typed.strip())); }
        catch (InvalidPathException invalid) { initial = Optional.empty(); }
        try {
            Optional<Path> chosen = fileChooser.apply("Choose " + row.label().toLowerCase(Locale.ROOT), initial);
            if (!closed && chosen.isPresent()) { values.put(row.id(), chosen.get().toString()); render(); }
        } catch (RuntimeException failure) {
            if (!closed) { message = message(failure); render(); }
        }
    }

    private void handle(UiEvent event) {
        if (closed) return;
        switch (event) {
            case UiEvent.Action action when action.id().equals(SAVE) -> save();
            case UiEvent.Action action when action.id().equals(CANCEL) -> { if (cancelEnabled) finish(); }
            case UiEvent.Action action when action.id().startsWith(BROWSE) -> rows.stream()
                .filter(row -> row instanceof TextRow text && text.kind() == Nodes.FieldKind.PATH && (BROWSE + text.id()).equals(action.id()))
                .findFirst().ifPresent(row -> browse((TextRow) row));
            case UiEvent.Edited edited when values.containsKey(edited.id()) -> { values.put(edited.id(), edited.text()); edited(true); }
            case UiEvent.Selected selected when choices.containsKey(selected.id()) && !selected.ids().isEmpty() -> {
                choices.put(selected.id(), selected.ids().iterator().next()); edited(true);
            }
            case UiEvent.Toggled toggled when toggles.containsKey(toggled.id()) -> { toggles.put(toggled.id(), toggled.checked()); edited(true); }
            case UiEvent.SecretChanged ignored -> edited(true);
            default -> { }
        }
    }

    /** Any edit ends the previous validation (the user may correct any field); values stay as typed. */
    private void edited() { edited(false); }
    private void edited(boolean confirm) {
        boolean hadErrors = !errors.isEmpty();
        errors.clear();
        if (hadErrors || confirm) render();
    }

    private void render() { if (view != null && !closed) view.update(new ViewSpec(form())); }

    private ControlState state(String id) { return new ControlState(visibility.getOrDefault(id, () -> true).getAsBoolean(), !busy, false, errors.getOrDefault(id, "")); }

    private Nodes.Node control(Row row) {
        return switch (row) {
            case TextRow text -> new Nodes.Field(text.id(), state(text.id()), text.kind(), value(text.id()), hints.getOrDefault(text.id(), ""), text.label(),
                text.kind() == Nodes.FieldKind.PATH ? BROWSE + text.id() : "", Optional.empty(),
                discreet && text.kind() == Nodes.FieldKind.PATH ? Nodes.FieldPresentation.PLAIN_PATH : Nodes.FieldPresentation.STANDARD);
            case SecretRow secret -> new Nodes.Secret(secret.id(), state(secret.id()), "", secret.label(), secret.revealGroup(),
                secret.multiline() ? Nodes.SecretKind.MULTILINE : discreet ? Nodes.SecretKind.DISCREET_INPUT : Nodes.SecretKind.INPUT, "");
            case ChoiceRow choice -> new Nodes.Choice(choice.id(), state(choice.id()), choice.kind(), choice.options(),
                selected(choice.id()), choice.label(), "");
            case ToggleRow toggle -> new Nodes.Toggle(toggle.id(), state(toggle.id()), toggle.label(), checked(toggle.id()),
                "toggle." + toggle.id(), false);
            case DisplayRow display -> new Nodes.Text(display.id(), ControlState.READY, display.text(), display.role());
        };
    }

    private Nodes.Node section(Row row) {
        return switch (row) {
            case DisplayRow display when display.label().isBlank() -> control(display);
            // The classic label column holds the choice's label too (plan 10b Rulings 10).
            case ChoiceRow choice when choice.kind() == Nodes.ChoiceKind.EXCLUSIVE -> layout == Layout.CLASSIC
                ? new Nodes.FormRow("row." + row.id(), state(row.id()), label(row), "", control(row), Optional.empty(), false) : control(row);
            case ToggleRow toggle -> new Nodes.SettingRow("row." + toggle.id(), state(toggle.id()), toggle.label(), "", control(toggle), false);
            default -> new Nodes.FormRow("row." + row.id(), state(row.id()), label(row), "", control(row), Optional.empty(), false);
        };
    }

    private Nodes.Form form() {
        var sections = new ArrayList<Nodes.Node>();
        boolean classic = layout == Layout.CLASSIC;
        if (groups.isEmpty()) {
            Nodes.Node card = new Nodes.Card("fields", ControlState.READY, Nodes.CardKind.FORM, rows.stream().map(this::section).toList());
            sections.add(classic && !classicTitle.isEmpty() ? new Nodes.Section("fields.section", ControlState.READY, classicTitle, card) : card);
        } else for (Group group : groups) {
            Nodes.Node card = new Nodes.Card(group.id(), ControlState.READY, Nodes.CardKind.FORM,
                group.rows().stream().map(id -> rows.stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow()).map(this::section).toList());
            String heading = classic && group.title().isEmpty() ? classicTitle : group.title();
            sections.add(heading.isEmpty() ? card : new Nodes.Section(group.id() + ".section", ControlState.READY, heading, card));
        }
        sections.add(new Nodes.Text(MESSAGE, new ControlState(!message.isEmpty(), true, false, ""), message, Nodes.TextRole.BODY));
        var actions = List.of(
            new Nodes.Button(CANCEL, new ControlState(true, cancelEnabled, false, ""), cancelLabel, "", Nodes.ButtonKind.SECONDARY, CANCEL, ""),
            new Nodes.Button(SAVE, new ControlState(true, !busy && ready.getAsBoolean(), busy, ""), saveLabel, "", Nodes.ButtonKind.PRIMARY, SAVE, ""));
        // The classic dialog's header strip shows the swatch and title (spec 10.8), so the form has no hero.
        return new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.SHEET, title, icon.isEmpty() || classic ? Optional.empty() : Optional.of(
            new Nodes.Stack("identity", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
                new Nodes.Icon("identity.icon", ControlState.READY, icon, hue, Nodes.IconSize.PROMPT, title),
                new Nodes.Text("identity.title", ControlState.READY, title, Nodes.TextRole.PROMPT_TITLE)))), sections, Optional.empty(), actions,
            cancelEnabled ? CANCEL : "");
    }

    static String message(Throwable failure) {
        while (failure.getCause() != null && (failure instanceof java.util.concurrent.CompletionException
            || failure instanceof java.util.concurrent.ExecutionException)) failure = failure.getCause();
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    /** Ends the form: later completions are ignored and the view, with its secret storage, is disposed. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        if (view != null) view.close();
    }
}
