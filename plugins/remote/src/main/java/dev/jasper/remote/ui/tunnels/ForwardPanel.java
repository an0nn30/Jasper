package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.Option;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

/**
 * Forward a Port on a host-rendered kit form: a remote port reached on this computer's loopback, through the SSH pane's
 * host or a chosen one. The local port follows the remote one (or the next free port) until edited. It starts a quick
 * forward, or a saved tunnel when "Keep as a saved tunnel" is on. No button is the form's primary and no field submits,
 * so Enter forwards nothing (the former dialog had no default button); Escape cancels. UI thread only.
 */
public final class ForwardPanel implements AutoCloseable {
    public static final String FORM = "forward", HEADING = "forward.heading", HOST = "forward.host", REMOTE_PORT = "forward.remote-port",
        LOCAL_PORT = "forward.local-port", MORE = "forward.more", TARGET_HOST = "forward.target-host", KEEP = "forward.keep",
        SAVE_NAME = "forward.name", MESSAGE = "forward.message", FORWARD = "forward.forward", CANCEL = "forward.cancel";

    private final List<RemoteHost> hosts;
    private final Optional<RemoteHost> paneHost;
    private final IntPredicate free;
    private final UiView view;
    private String host = "", remotePort = "", localPort = "", targetHost = "localhost", saveName = "", message = "";
    private boolean more, keep, localEdited, closed;

    /**
     * @param paneHost the host of the SSH pane it was opened from; empty asks for one
     * @param free whether a port on this computer's loopback is free
     * @param onForward receives the quick forward, or the named tunnel to save and start
     */
    public ForwardPanel(UiKit kit, List<RemoteHost> hosts, Optional<RemoteHost> paneHost, IntPredicate free, Consumer<Tunnel> onForward, Runnable onCancel) {
        this.hosts = List.copyOf(hosts);
        this.paneHost = paneHost;
        this.free = free;
        // As the former host list did, the first host is chosen until another is.
        host = this.hosts.stream().findFirst().map(via -> via.id().toString()).orElse("");
        view = kit.create(new ViewSpec(form()), event -> {
            if (closed) return;
            switch (event) {
                case UiEvent.Edited edited -> edited(edited.id(), edited.text());
                case UiEvent.Selected selected when selected.id().equals(HOST) && !selected.ids().isEmpty() -> { host = selected.ids().iterator().next(); render(); }
                case UiEvent.Toggled toggled when toggled.id().equals(KEEP) -> keep(toggled.checked());
                case UiEvent.Action action when action.id().equals(MORE) -> { more = !more; render(); }
                case UiEvent.Action action when action.id().equals(FORWARD) -> forward(onForward);
                case UiEvent.Action action when action.id().equals(CANCEL) -> onCancel.run();
                default -> { }
            }
        });
    }

    public UiView view() { return view; }

    /** The validation or save message as shown. */
    public String message() { return message; }

    public void showError(String error) { if (closed) return; message = error; render(); }

    private void edited(String id, String text) {
        switch (id) {
            case REMOTE_PORT -> { remotePort = text; suggest(); }
            case LOCAL_PORT -> { localPort = text; localEdited = true; }
            case TARGET_HOST -> targetHost = text;
            case SAVE_NAME -> saveName = text;
            default -> { return; }
        }
        render();
    }

    private void keep(boolean value) {
        keep = value;
        if (keep && saveName.isBlank()) {
            String remote = remotePort.strip();
            if (!remote.isEmpty()) saveName = "Port " + remote + " on " + via().map(RemoteHost::name).orElse("the host");
        }
        render();
    }

    private void forward(Consumer<Tunnel> onForward) {
        Tunnel tunnel;
        try {
            int remote = port(remotePort, 1, "The remote port must be a number from 1 to 65535");
            int local = port(localPort, 0, "The local port must be a number from 0 to 65535");
            RemoteHost through = via().orElseThrow(() -> new IllegalArgumentException("Choose a host"));
            String name = keep ? saveName.strip() : "";
            if (keep && name.isEmpty()) throw new IllegalArgumentException("Name the tunnel to keep it");
            tunnel = Tunnel.create(name, through.id(), TunnelKind.LOCAL, Tunnel.LOCALHOST, local, targetHost, remote);
        } catch (IllegalArgumentException invalid) { message = invalid.getMessage(); render(); return; }
        onForward.accept(tunnel);
    }

    private Optional<RemoteHost> via() {
        return paneHost.or(() -> hosts.stream().filter(option -> option.id().toString().equals(host)).findFirst());
    }

    /** The local port follows the remote one until someone edits it: the same number, else the next free one. */
    private void suggest() { if (!localEdited) localPort = suggestion(remotePort.strip()); }

    private String suggestion(String remote) {
        int port;
        try { port = Integer.parseInt(remote); } catch (NumberFormatException none) { return ""; }
        if (port < 1 || port > 65535) return "";
        for (int candidate = port; candidate <= Math.min(65535, port + 100); candidate++) if (free.test(candidate)) return String.valueOf(candidate);
        return String.valueOf(port);
    }

    private static int port(String text, int minimum, String problem) {
        try {
            int value = Integer.parseInt(text.strip());
            if (value < minimum || value > 65535) throw new IllegalArgumentException(problem);
            return value;
        } catch (NumberFormatException bad) { throw new IllegalArgumentException(problem); }
    }

    private void render() { if (!closed) view.update(new ViewSpec(form())); }

    private static ControlState state(boolean visible, boolean enabled) { return new ControlState(visible, enabled, false, ""); }

    private Nodes.Form form() {
        var sections = new ArrayList<Nodes.Node>();
        sections.add(new Nodes.Text(HEADING, ControlState.READY,
            paneHost.map(via -> "Forward a port on " + via.name() + " to this computer").orElse("Forward a port on a host to this computer"), Nodes.TextRole.BODY));
        sections.add(new Nodes.Choice(HOST, state(paneHost.isEmpty(), true), Nodes.ChoiceKind.DROPDOWN,
            hosts.stream().map(option -> new Option(option.id().toString(), option.name(), Optional.empty())).toList(), host, "Host", "", ""));
        sections.add(new Nodes.Field(REMOTE_PORT, ControlState.READY, Nodes.FieldKind.PORT, remotePort, "", "Remote port", ""));
        sections.add(new Nodes.Field(LOCAL_PORT, ControlState.READY, Nodes.FieldKind.PORT, localPort, "", "Local port", ""));
        sections.add(new Nodes.Button(MORE, ControlState.READY, more ? "Less ▾" : "More ▸", "", Nodes.ButtonKind.LINK, MORE, ""));
        sections.add(new Nodes.Field(TARGET_HOST, state(more, true), Nodes.FieldKind.TEXT, targetHost, "", "Forward to host", ""));
        sections.add(new Nodes.SettingRow("row." + KEEP, ControlState.READY, "Keep as a saved tunnel", "",
            new Nodes.Toggle(KEEP, ControlState.READY, "Keep as a saved tunnel", keep, KEEP, false), false));
        sections.add(new Nodes.Field(SAVE_NAME, state(true, keep), Nodes.FieldKind.TEXT, saveName, "", "Name", ""));
        var messageLine = new Nodes.Text(MESSAGE, state(!message.isEmpty(), true), message, Nodes.TextRole.BODY);
        var actions = List.of(
            new Nodes.Button(CANCEL, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, ""),
            new Nodes.Button(FORWARD, ControlState.READY, "Forward", "", Nodes.ButtonKind.SECONDARY, FORWARD, ""));
        return new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.FULL, "Forward a Port", Optional.empty(), sections, Optional.of(messageLine), actions, CANCEL);
    }

    @Override public void close() { if (closed) return; closed = true; view.close(); }
}
