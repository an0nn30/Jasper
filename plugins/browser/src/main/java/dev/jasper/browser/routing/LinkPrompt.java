package dev.jasper.browser.routing;

import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import dev.jasper.sdk.ui.Windows;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * "Open in Jasper?" (spec section 9): Jasper's modal inside the window a link came from, built from the SDK's nodes. Its
 * text says what Jasper can show, for a web link or a local file; under it is "Remember my choice", unticked; its buttons
 * are Use Default Browser (Use Default Editor for a file), Cancel, and Open in Jasper, the primary. Choosing either of
 * the first and last tells the caller once, with whether "Remember my choice" was ticked; Cancel, Escape and closing the
 * prompt tell it nothing. UI thread.
 */
final class LinkPrompt {
    /** What the link is. */
    enum Kind { LINK, LOCAL_FILE }

    /** What the user chose. */
    enum Answer {
        /** Use Default Browser, or Use Default Editor for a local file. */
        USE_DEFAULT,
        /** Open in Jasper. */
        OPEN_IN_JASPER
    }

    static final String TITLE = "Open in Jasper?", REMEMBER_LABEL = "Remember my choice";
    static final String LINK_MESSAGE = "Jasper can display web pages! Would you like to open this link in Jasper?";
    static final String FILE_MESSAGE = "Jasper can display files like this in its built-in web browser! Would you like to open this link in Jasper?";
    static final String FORM = "link-prompt", MESSAGE = "link-prompt.message", REMEMBER_ROW = "link-prompt.remember-row",
        REMEMBER = "link-prompt.remember", DEFAULT_BUTTON = "link-prompt.default", CANCEL_BUTTON = "link-prompt.cancel",
        OPEN_BUTTON = "link-prompt.open", USE_DEFAULT = "link-prompt.answer.default", CANCEL = "link-prompt.answer.cancel",
        OPEN = "link-prompt.answer.open";
    private final Kind kind;
    private final BiConsumer<Answer, Boolean> answered;
    private PluginDialog dialog;
    private UiView view;
    private boolean remember, done;

    private LinkPrompt(Kind kind, BiConsumer<Answer, Boolean> answered) {
        this.kind = kind;
        this.answered = answered;
    }

    /**
     * Shows the prompt inside {@code owner}'s window. A modal overlay's show returns once it has closed.
     *
     * @param kit builds its content
     * @param windows shows it
     * @param owner the window the link came from
     * @param kind a web link or a local file
     * @param answered told once what the user chose and whether "Remember my choice" was ticked; never for Cancel
     * @throws RuntimeException when it cannot show, as when the window closed or the plugin stops; then it never tells
     *     {@code answered}
     */
    static void show(UiKit kit, Windows windows, WindowOwner owner, Kind kind, BiConsumer<Answer, Boolean> answered) {
        Objects.requireNonNull(kit, "kit");
        Objects.requireNonNull(windows, "windows");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(answered, "answered");
        new LinkPrompt(kind, answered).open(kit, windows, owner);
    }

    private void open(UiKit kit, Windows windows, WindowOwner owner) {
        dialog = windows.dialog(new DialogSpec(TITLE, owner, true, DialogSpec.Presentation.OVERLAY));
        dialog.onClosed(() -> done = true);
        UiView created = null;
        try {
            created = kit.create(spec(), this::event);
            view = created;
            dialog.setContent(created);
            created = null; // the dialog's now, which disposes it as it closes
            dialog.show();
        } catch (RuntimeException failed) {
            done = true;
            if (created != null) created.close();
            dialog.close();
            throw failed;
        }
    }

    private void event(UiEvent event) {
        if (done) return;
        switch (event) {
            case UiEvent.Toggled toggled when toggled.id().equals(REMEMBER) -> {
                remember = toggled.checked();
                view.update(spec());
            }
            case UiEvent.Action action -> {
                switch (action.id()) {
                    case USE_DEFAULT -> choose(Answer.USE_DEFAULT);
                    case OPEN -> choose(Answer.OPEN_IN_JASPER);
                    case CANCEL -> dialog.close();
                    default -> { }
                }
            }
            default -> { }
        }
    }

    /** The prompt closes first, so what the choice opens is not under it. */
    private void choose(Answer answer) {
        done = true;
        dialog.close();
        answered.accept(answer, remember);
    }

    /**
     * The prompt: its text, the toggle, and the buttons, Open in Jasper the default on the right with the others in the
     * spec's order before it. Escape is Cancel.
     */
    private ViewSpec spec() {
        boolean file = kind == Kind.LOCAL_FILE;
        var toggle = new Nodes.Toggle(REMEMBER, ControlState.READY, REMEMBER_LABEL, remember, REMEMBER, false);
        List<Nodes.Button> actions = List.of(
            new Nodes.Button(DEFAULT_BUTTON, ControlState.READY, file ? "Use Default Editor" : "Use Default Browser", "",
                Nodes.ButtonKind.SECONDARY, USE_DEFAULT, ""),
            new Nodes.Button(CANCEL_BUTTON, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, ""),
            new Nodes.Button(OPEN_BUTTON, ControlState.READY, "Open in Jasper", "", Nodes.ButtonKind.PRIMARY, OPEN, ""));
        return new ViewSpec(new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.SHEET, TITLE, Optional.empty(), List.of(
                new Nodes.Text(MESSAGE, ControlState.READY, file ? FILE_MESSAGE : LINK_MESSAGE, Nodes.TextRole.BODY),
                new Nodes.SettingRow(REMEMBER_ROW, ControlState.READY, REMEMBER_LABEL, "", toggle, false)),
            Optional.empty(), actions, CANCEL));
    }
}
