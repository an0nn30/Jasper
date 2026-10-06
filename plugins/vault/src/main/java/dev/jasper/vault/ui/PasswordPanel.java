package dev.jasper.vault.ui;

import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import dev.jasper.vault.service.UnlockPrompt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * The master-password form: unlock (one field) or create (two paired fields and the bind switch). It owns a kit view
 * in its own dialog, or renders inside the manager's view under an id prefix. The typed password lives only in the
 * view's wipeable storage until a submit hands a copy to its owner. UI thread only.
 */
public final class PasswordPanel implements AutoCloseable {
    static final int MIN_LENGTH = 8;
    static final String REVEAL_GROUP = "master";

    /** Where the form renders: its own view, or a section of a larger one. */
    interface Host {
        UiView view();
        void render();
        /** False once the view is disposed (its storage is then already erased). */
        boolean open();
    }

    final String form, intro, password, confirm, bind, message, submit, cancel;
    private final boolean creating;
    private final String primaryTitle, introText;
    private final Map<String, String> errors = new LinkedHashMap<>();
    private boolean bound, busy, closed;
    private String status = "";
    private Runnable onSubmit = () -> { }, onCancel = () -> { };
    private Host host;
    private UiView own;

    private PasswordPanel(String prefix, String primaryTitle, boolean creating, boolean bindDefault, String introText) {
        form = prefix + "form"; intro = prefix + "intro"; password = prefix + "password"; confirm = prefix + "confirm";
        bind = prefix + "bind"; message = prefix + "message"; submit = prefix + "submit"; cancel = prefix + "cancel";
        this.primaryTitle = primaryTitle; this.creating = creating; this.bound = bindDefault; this.introText = introText;
    }

    /** The unlock form in its own view, bound to {@code prompt}: Enter submits, errors show, the prompt's dismissal is the dialog's. */
    public static PasswordPanel unlock(UiKit kit, UnlockPrompt prompt) {
        var panel = unlockPanel("", prompt);
        panel.own(kit);
        return panel;
    }

    /** The unlock form rendered inside {@code host} under {@code prefix}. */
    static PasswordPanel unlockIn(Host host, String prefix, UnlockPrompt prompt) {
        var panel = unlockPanel(prefix, prompt);
        panel.host = Objects.requireNonNull(host);
        return panel;
    }

    private static PasswordPanel unlockPanel(String prefix, UnlockPrompt prompt) {
        var panel = new PasswordPanel(prefix, "Unlock", false, false, "Enter the master password to unlock the vault.");
        prompt.onError(text -> {
            if (panel.closed || !panel.host.open()) return;
            panel.status = text; panel.busy = false; panel.render();
            panel.host.view().focus(panel.password);
        });
        panel.onSubmit = () -> {
            char[] typed = panel.copy(panel.password);
            if (typed.length == 0) { Arrays.fill(typed, (char) 0); return; }
            panel.busy = true; panel.status = "Unlocking...";
            panel.host.view().clearSecret(panel.password);
            panel.render();
            prompt.submit(typed);
        };
        panel.onCancel = prompt::cancel;
        return panel;
    }

    /** The create form: two matching passwords of at least {@value #MIN_LENGTH} characters and the bind choice. */
    public static PasswordPanel create(UiKit kit, boolean bindDefault, BiConsumer<char[], Boolean> onCreate, Runnable onCancel) {
        var panel = new PasswordPanel("", "Create Vault", true, bindDefault, "Choose a master password. It cannot be recovered if forgotten.");
        panel.onSubmit = () -> {
            char[] typed = panel.copy(panel.password), again = panel.copy(panel.confirm);
            try {
                if (typed.length < MIN_LENGTH) { panel.errors.put(panel.password, "Use at least " + MIN_LENGTH + " characters"); panel.render(); return; }
                if (!Arrays.equals(typed, again)) { panel.errors.put(panel.confirm, "The passwords do not match"); panel.render(); return; }
                panel.host.view().clearSecret(panel.password); panel.host.view().clearSecret(panel.confirm);
                onCreate.accept(typed.clone(), panel.bound);
            } finally { Arrays.fill(typed, (char) 0); Arrays.fill(again, (char) 0); }
        };
        panel.onCancel = onCancel;
        panel.own(kit);
        return panel;
    }

    private void own(UiKit kit) {
        own = kit.create(new ViewSpec(node()), this::handle);
        host = new Host() {
            @Override public UiView view() { return own; }
            @Override public void render() { own.update(new ViewSpec(node())); }
            @Override public boolean open() { return !closed; }
        };
    }

    /** The view this panel owns; only for a panel made by {@link #unlock} or {@link #create}. */
    public UiView view() { return Objects.requireNonNull(own, "An embedded form has no view of its own"); }

    private char[] copy(String id) {
        char[][] result = {new char[0]};
        host.view().withSecret(id, chars -> result[0] = chars.clone());
        return result[0];
    }

    private void render() { if (!closed && host.open()) host.render(); }

    /** Routes one event; true when it belonged to this form. */
    boolean handle(UiEvent event) {
        if (closed) return false;
        switch (event) {
            case UiEvent.Action action when action.id().equals(submit) -> { if (!busy) { errors.clear(); onSubmit.run(); } return true; }
            case UiEvent.Action action when action.id().equals(cancel) -> { onCancel.run(); return true; }
            case UiEvent.Toggled toggled when toggled.id().equals(bind) -> { bound = toggled.checked(); render(); return true; }
            case UiEvent.SecretChanged changed when changed.id().equals(password) || changed.id().equals(confirm) -> {
                if (!errors.isEmpty()) { errors.clear(); render(); }
                return true;
            }
            default -> { return false; }
        }
    }

    private ControlState state(String id) { return new ControlState(true, !busy, false, errors.getOrDefault(id, "")); }

    /** The form node: intro, password fields, the bind switch when creating, the status line and Cancel / primary. */
    Nodes.Form node() {
        var sections = new ArrayList<Nodes.Node>();
        sections.add(new Nodes.Text(intro, ControlState.READY, introText, Nodes.TextRole.BODY));
        sections.add(secret(password, "Master password:"));
        if (creating) {
            sections.add(secret(confirm, "Confirm:"));
            sections.add(new Nodes.SettingRow(bind + ".row", ControlState.READY, "Bind to this device (recommended)", "",
                new Nodes.Toggle(bind, new ControlState(true, !busy, false, ""), "Bind to this device (recommended)", bound, bind, false), false));
        }
        sections.add(statusLine());
        return new Nodes.Form(form, ControlState.READY, Nodes.FormKind.FULL, primaryTitle, Optional.empty(), sections, Optional.empty(),
            List.of(cancelButton(), submitButton()), cancel);
    }

    /**
     * The unlock form inside a larger view: its fields and Cancel. The host puts {@link #submitButton()} among its own
     * actions as the one primary, as the former form was its window's default button.
     */
    Nodes.Node embedded() {
        return new Nodes.Stack(form, ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.Text(intro, ControlState.READY, introText, Nodes.TextRole.BODY), secret(password, "Master password:"), statusLine(),
            new Nodes.Stack(form + ".actions", ControlState.READY, Nodes.LayoutRole.ACTIONS, List.of(cancelButton()))));
    }

    /** Enter in a password field submits, as the former fields' action listeners did. */
    private Nodes.Secret secret(String id, String label) {
        return new Nodes.Secret(id, state(id), "", label, REVEAL_GROUP, Nodes.SecretKind.INPUT, "", submit);
    }

    private Nodes.Text statusLine() { return new Nodes.Text(message, new ControlState(!status.isEmpty(), true, false, ""), status, Nodes.TextRole.BODY); }

    private Nodes.Button cancelButton() { return new Nodes.Button(cancel, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, cancel, ""); }

    /** The primary submit button (Unlock or Create Vault). */
    Nodes.Button submitButton() {
        return new Nodes.Button(submit, new ControlState(true, true, busy, ""), primaryTitle, "", Nodes.ButtonKind.PRIMARY, submit, "");
    }

    boolean busy() { return busy; }
    boolean bound() { return bound; }
    String status() { return status; }
    Map<String, String> errors() { return Map.copyOf(errors); }

    /** Erases the typed passwords while the form stays open (for example when its prompt is dismissed). */
    public void clear() {
        if (closed || !host.open()) return;
        host.view().clearSecret(password);
        if (creating) host.view().clearSecret(confirm);
    }

    /** Prevents duplicate submits while a background create is running. */
    public void setBusy(boolean value) { busy = value; render(); }

    /**
     * Ends the form; a view it owns is disposed with its secret storage. Late prompt results are ignored. A surface
     * that closed the owned view calls this before anything else can reach the form.
     */
    @Override public void close() {
        if (closed) return;
        // An embedded form erases its fields in the shared view; an owned view erases everything as it closes,
        // which its surface may already have done, so the owned view is never touched before closing.
        if (own == null) clear();
        closed = true;
        if (own != null) own.close();
    }
}
