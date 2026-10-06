package dev.jasper.vault.ui;

import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import dev.jasper.vault.service.GrantPrompt;
import java.util.List;
import java.util.Optional;

/**
 * "SSH wants to use deploy@prod from the vault." with Deny, Always allow for SSH and Allow once. No action is the
 * form's primary, so Enter never grants (the former dialog had no default button); Escape denies.
 */
public final class GrantPanel implements AutoCloseable {
    static final String FORM = "grant", QUESTION = "question", ONCE = "allow-once", ALWAYS = "allow-always", DENY = "deny";
    private final UiView view;
    private boolean closed;

    public GrantPanel(UiKit kit, GrantPrompt prompt) {
        String what = prompt.descriptor().subtitle().isBlank() ? prompt.descriptor().name() : prompt.descriptor().subtitle() + "@" + prompt.descriptor().name();
        // Literal text: names are data, never markup.
        var question = new Nodes.Text(QUESTION, ControlState.READY, prompt.consumerName() + " wants to use " + what + " from the vault.", Nodes.TextRole.BODY);
        var actions = List.of(
            new Nodes.Button(DENY, ControlState.READY, "Deny", "", Nodes.ButtonKind.SECONDARY, DENY, ""),
            new Nodes.Button(ALWAYS, ControlState.READY, "Always allow for " + prompt.consumerName(), "", Nodes.ButtonKind.SECONDARY, ALWAYS, ""),
            new Nodes.Button(ONCE, ControlState.READY, "Allow once", "", Nodes.ButtonKind.SECONDARY, ONCE, ""));
        var form = new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.FULL, "Allow access", Optional.empty(), List.of(question), Optional.empty(), actions, DENY);
        view = kit.create(new ViewSpec(form), event -> {
            if (closed || !(event instanceof UiEvent.Action action)) return;
            switch (action.id()) {
                case ONCE -> prompt.answer(GrantPrompt.Decision.ALLOW_ONCE);
                case ALWAYS -> prompt.answer(GrantPrompt.Decision.ALWAYS);
                case DENY -> prompt.answer(GrantPrompt.Decision.DENY);
                default -> { }
            }
        });
    }

    public UiView view() { return view; }

    @Override public void close() { if (closed) return; closed = true; view.close(); }
}
