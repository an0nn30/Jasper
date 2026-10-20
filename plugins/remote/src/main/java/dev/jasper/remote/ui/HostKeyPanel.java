package dev.jasper.remote.ui;

import dev.jasper.remote.client.HostKeyVerifier;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The unknown-host-key question: Cancel, Connect once, Trust and connect. None is the form's primary, so Enter never
 * trusts a key (the former dialog had no default button); Escape cancels, as closing the dialog does. The host, key type
 * and fingerprint are literal text, the fingerprint in the code face. UI thread only.
 */
public final class HostKeyPanel implements AutoCloseable {
    public static final String FORM = "host-key", AUTHENTICITY = "host-key.authenticity", KEY_TYPE = "host-key.type",
        FINGERPRINT = "host-key.fingerprint", ADVICE = "host-key.advice", CANCEL = "host-key.cancel", ONCE = "host-key.once", TRUST = "host-key.trust";
    private final UiView view;
    private boolean closed;

    public HostKeyPanel(UiKit kit, HostKeyVerifier.Question question, Consumer<HostKeyVerifier.Decision> decide) {
        var sections = List.<Nodes.Node>of(
            new Nodes.Text(AUTHENTICITY, ControlState.READY, "The authenticity of " + question.host() + " port " + question.port() + " can't be established.", Nodes.TextRole.BODY),
            new Nodes.Text(KEY_TYPE, ControlState.READY, question.keyType() + " key fingerprint:", Nodes.TextRole.BODY),
            new Nodes.Text(FINGERPRINT, ControlState.READY, question.fingerprint(), Nodes.TextRole.MONO),
            new Nodes.Text(ADVICE, ControlState.READY, "Compare it with the server's before trusting.", Nodes.TextRole.BODY));
        var actions = List.of(
            new Nodes.Button(CANCEL, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, ""),
            new Nodes.Button(ONCE, ControlState.READY, "Connect once", "", Nodes.ButtonKind.SECONDARY, ONCE, ""),
            new Nodes.Button(TRUST, ControlState.READY, "Trust and connect", "", Nodes.ButtonKind.SECONDARY, TRUST, ""));
        var form = new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.FULL, "Verify host key", Optional.empty(), sections, Optional.empty(), actions, CANCEL);
        view = kit.create(new ViewSpec(form), event -> {
            if (closed || !(event instanceof UiEvent.Action action)) return;
            switch (action.id()) {
                case CANCEL -> decide.accept(HostKeyVerifier.Decision.CANCEL);
                case ONCE -> decide.accept(HostKeyVerifier.Decision.ONCE);
                case TRUST -> decide.accept(HostKeyVerifier.Decision.TRUST);
                default -> { }
            }
        });
    }

    public UiView view() { return view; }

    @Override public void close() { if (closed) return; closed = true; view.close(); }
}
