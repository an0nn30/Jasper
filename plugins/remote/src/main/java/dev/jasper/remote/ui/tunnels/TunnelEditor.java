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
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Add or edit one tunnel, or name a quick forward to keep it, on a host-rendered kit form. Validation is inline; Save
 * hands back the tunnel and the dialog closes it. No button is the form's primary and no field submits, so Enter saves
 * nothing (the former dialog had no default button); Escape cancels. A save error arriving after {@link #close()} is
 * ignored. UI thread only.
 */
public final class TunnelEditor implements AutoCloseable {
    public static final String FORM = "tunnel", NAME = "tunnel.name", HOST = "tunnel.host", TYPE = "tunnel.type",
        EXPLANATION = "tunnel.explanation", LISTEN_LABEL = "tunnel.listen.label", LISTEN_ROW = "tunnel.listen",
        LISTEN_ADDRESS = "tunnel.listen.address", LISTEN_PORT = "tunnel.listen.port", ALLOW_OTHERS = "tunnel.allow-others",
        EXPOSURE = "tunnel.exposure", FORWARD_LABEL = "tunnel.forward.label", FORWARD_ROW = "tunnel.forward",
        FORWARD_HOST = "tunnel.forward.host", FORWARD_PORT = "tunnel.forward.port", PREVIEW = "tunnel.preview",
        WARNING_ROW = "tunnel.warning.row", WARNING = "tunnel.warning", MESSAGE = "tunnel.message", FOOTER = "tunnel.footer",
        SAVE = "tunnel.save", CANCEL = "tunnel.cancel";
    /** The type choice's options. */
    public static final String LOCAL = "local", REMOTE = "remote", DYNAMIC = "dynamic";

    private final List<RemoteHost> hosts;
    private final List<Tunnel> others;
    private final Optional<Tunnel> editing;
    private final UUID editingId;
    private final UiView view;
    private String name = "", host = "", listenAddress = Tunnel.LOCALHOST, listenPort = "", forwardHost = "localhost", forwardPort = "", message = "";
    private TunnelKind kind = TunnelKind.LOCAL;
    private boolean allowOthers, closed;

    public TunnelEditor(UiKit kit, List<RemoteHost> hosts, List<Tunnel> others, Optional<Tunnel> editing, Optional<UUID> preferredHost,
                        Consumer<Tunnel> onSave, Runnable onCancel) {
        this.hosts = List.copyOf(hosts);
        this.others = List.copyOf(others);
        this.editing = editing;
        this.editingId = editing.map(Tunnel::id).orElse(null);
        UUID wanted = editing.map(Tunnel::hostId).or(() -> preferredHost).orElse(null);
        host = this.hosts.stream().filter(via -> via.id().equals(wanted)).findFirst()
            // A new tunnel goes through the first host unless another was asked for; an edited one keeps its own, or none.
            .or(() -> editing.isEmpty() ? this.hosts.stream().findFirst() : Optional.empty())
            .map(via -> via.id().toString()).orElse("");
        editing.ifPresent(tunnel -> {
            name = tunnel.name();
            kind = tunnel.kind();
            listenAddress = tunnel.bindAddress();
            listenPort = String.valueOf(tunnel.bindPort());
            allowOthers = tunnel.bindAddress().equals(Tunnel.ALL);
            if (tunnel.kind() != TunnelKind.DYNAMIC) {
                forwardHost = tunnel.targetHost();
                forwardPort = String.valueOf(tunnel.targetPort());
            }
        });
        view = kit.create(new ViewSpec(form()), event -> {
            if (closed) return;
            switch (event) {
                case UiEvent.Edited edited -> edited(edited.id(), edited.text());
                case UiEvent.Selected selected when selected.id().equals(HOST) && !selected.ids().isEmpty() -> { host = selected.ids().iterator().next(); render(); }
                case UiEvent.Selected selected when selected.id().equals(TYPE) && !selected.ids().isEmpty() -> {
                    kind = switch (selected.ids().iterator().next()) { case REMOTE -> TunnelKind.REMOTE; case DYNAMIC -> TunnelKind.DYNAMIC; default -> TunnelKind.LOCAL; };
                    render();
                }
                case UiEvent.Toggled toggled when toggled.id().equals(ALLOW_OTHERS) -> {
                    allowOthers = toggled.checked();
                    listenAddress = allowOthers ? Tunnel.ALL : Tunnel.LOCALHOST;
                    render();
                }
                case UiEvent.Action action when action.id().equals(SAVE) -> save(onSave);
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
            case NAME -> name = text;
            case LISTEN_ADDRESS -> listenAddress = text;
            case LISTEN_PORT -> listenPort = text;
            case FORWARD_HOST -> forwardHost = text;
            case FORWARD_PORT -> forwardPort = text;
            default -> { return; }
        }
        render();
    }

    private void save(Consumer<Tunnel> onSave) {
        Tunnel tunnel;
        try { tunnel = tunnel(); }
        catch (IllegalArgumentException invalid) { message = invalid.getMessage(); render(); return; }
        onSave.accept(tunnel);
    }

    private Tunnel tunnel() {
        String nameValue = name.strip();
        if (nameValue.isEmpty()) throw new IllegalArgumentException("A tunnel needs a name");
        for (Tunnel other : others)
            if (!other.id().equals(editingId) && other.name().equalsIgnoreCase(nameValue)) throw new IllegalArgumentException("A tunnel named " + other.name() + " exists");
        RemoteHost via = selectedHost().orElseThrow(() -> new IllegalArgumentException("Choose a host to go through"));
        int bindPort = port(listenPort, 0, "The listen port must be a number from 0 to 65535");
        int targetPort = kind == TunnelKind.DYNAMIC ? 0 : port(forwardPort, 1, "The target port must be a number from 1 to 65535");
        return editing.map(existing -> existing.withEdited(nameValue, via.id(), kind, listenAddress, bindPort, forwardHost, targetPort))
            .orElseGet(() -> Tunnel.create(nameValue, via.id(), kind, listenAddress, bindPort, forwardHost, targetPort));
    }

    private Optional<RemoteHost> selectedHost() { return hosts.stream().filter(via -> via.id().toString().equals(host)).findFirst(); }

    private static int port(String text, int minimum, String problem) {
        try {
            int value = Integer.parseInt(text.strip());
            if (value < minimum || value > 65535) throw new IllegalArgumentException(problem);
            return value;
        } catch (NumberFormatException bad) { throw new IllegalArgumentException(problem); }
    }

    private String preview(String via) {
        String port = listenPort.strip();
        String listen = listenAddress.strip() + ":" + (port.isEmpty() ? "?" : port.equals("0") ? "any port" : port);
        String target = forwardHost.strip() + ":" + (forwardPort.strip().isEmpty() ? "?" : forwardPort.strip());
        return switch (kind) {
            case LOCAL -> listen + " on this computer → " + target + " as seen from " + via;
            case REMOTE -> listen + " on " + via + " → " + target + " as seen from this computer";
            case DYNAMIC -> "A SOCKS5 proxy on " + listen + "; connections leave from " + via;
        };
    }

    /** Another saved tunnel on the same side, address and port: both may exist, but not run at once. */
    private Optional<Tunnel> sharedPort() {
        int port;
        try { port = Integer.parseInt(listenPort.strip()); } catch (NumberFormatException none) { return Optional.empty(); }
        if (port <= 0) return Optional.empty();
        String address = listenAddress.strip();
        UUID via = selectedHost().map(RemoteHost::id).orElse(null);
        return others.stream()
            .filter(other -> !other.id().equals(editingId) && other.bindPort() == port && other.bindAddress().equals(address))
            .filter(other -> (other.kind() == TunnelKind.REMOTE) == (kind == TunnelKind.REMOTE) && (kind != TunnelKind.REMOTE || other.hostId().equals(via)))
            .findFirst();
    }

    private void render() { if (!closed) view.update(new ViewSpec(form())); }

    private static ControlState state(boolean visible, boolean enabled) { return new ControlState(visible, enabled, false, ""); }

    private Nodes.Form form() {
        boolean server = kind == TunnelKind.REMOTE, dynamic = kind == TunnelKind.DYNAMIC;
        String via = selectedHost().map(RemoteHost::name).orElse("the host");
        var sections = new ArrayList<Nodes.Node>();
        sections.add(new Nodes.Field(NAME, ControlState.READY, Nodes.FieldKind.TEXT, name, "", "Name", ""));
        sections.add(new Nodes.Choice(HOST, ControlState.READY, Nodes.ChoiceKind.DROPDOWN,
            hosts.stream().map(option -> new Option(option.id().toString(), option.name() + " — " + option.label(), Optional.empty())).toList(),
            host, "Via host", "", ""));
        sections.add(new Nodes.Choice(TYPE, ControlState.READY, Nodes.ChoiceKind.SEGMENTED, List.of(
            new Option(LOCAL, "Local", Optional.empty()), new Option(REMOTE, "Remote", Optional.empty()), new Option(DYNAMIC, "Dynamic (SOCKS)", Optional.empty())),
            switch (kind) { case LOCAL -> LOCAL; case REMOTE -> REMOTE; case DYNAMIC -> DYNAMIC; }, "Type", ""));
        sections.add(new Nodes.Text(EXPLANATION, ControlState.READY, switch (kind) {
            case LOCAL -> "Connections to a port on this computer go through " + via + " to the target.";
            case REMOTE -> "Connections to a port on the server come back through this computer to the target.";
            case DYNAMIC -> "A SOCKS5 proxy on this computer; each connection goes out through " + via + ".";
        }, Nodes.TextRole.DESCRIPTION));
        sections.add(new Nodes.Text(LISTEN_LABEL, ControlState.READY, server ? "Listen on (the server)" : "Listen on (this computer)", Nodes.TextRole.LABEL));
        sections.add(new Nodes.Stack(LISTEN_ROW, ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Field(LISTEN_ADDRESS, state(true, !allowOthers), Nodes.FieldKind.TEXT, listenAddress, "", "Listen address", ""),
            new Nodes.Field(LISTEN_PORT, ControlState.READY, Nodes.FieldKind.PORT, listenPort, "", "Listen port", ""))));
        sections.add(new Nodes.SettingRow("row." + ALLOW_OTHERS, ControlState.READY, "Allow other computers to connect", "",
            new Nodes.Toggle(ALLOW_OTHERS, ControlState.READY, "Allow other computers to connect", allowOthers, ALLOW_OTHERS, false), false));
        sections.add(new Nodes.Text(EXPOSURE, state(allowOthers, true), allowOthers
            ? "Other computers on the network can connect to this port." + (server ? " The server's GatewayPorts setting must allow it." : "") : "",
            Nodes.TextRole.DESCRIPTION));
        sections.add(new Nodes.Text(FORWARD_LABEL, state(!dynamic, true),
            server ? "Forward to (as seen from this computer)" : "Forward to (as seen from " + via + ")", Nodes.TextRole.LABEL));
        sections.add(new Nodes.Stack(FORWARD_ROW, state(!dynamic, true), Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Field(FORWARD_HOST, ControlState.READY, Nodes.FieldKind.TEXT, forwardHost, "", "Target host", ""),
            new Nodes.Field(FORWARD_PORT, ControlState.READY, Nodes.FieldKind.PORT, forwardPort, "", "Target port", ""))));
        sections.add(new Nodes.Text(PREVIEW, ControlState.READY, preview(via), Nodes.TextRole.DESCRIPTION));
        Optional<Tunnel> shared = sharedPort();
        // The shared-port warning and the message sit beside the buttons, as the former ones sat above them.
        var footer = new Nodes.Stack(FOOTER, ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.Stack(WARNING_ROW, state(shared.isPresent(), true), Nodes.LayoutRole.INLINE, List.of(
                new Nodes.Icon(WARNING + ".icon", ControlState.READY, "warning", Nodes.Hue.NONE, Nodes.IconSize.CAPTION, "Warning", Optional.of(Nodes.Severity.WARNING)),
                new Nodes.Text(WARNING, ControlState.READY, shared.map(other -> other.name() + " listens on the same port; they cannot run at once.").orElse(""),
                    Nodes.TextRole.BODY))),
            new Nodes.Text(MESSAGE, state(!message.isEmpty(), true), message, Nodes.TextRole.BODY)));
        var actions = List.of(
            new Nodes.Button(CANCEL, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, ""),
            new Nodes.Button(SAVE, ControlState.READY, editing.isEmpty() ? "Add Tunnel" : "Save", "", Nodes.ButtonKind.SECONDARY, SAVE, ""));
        String title = editing.isEmpty() ? "New Tunnel" : editing.get().quick() ? "Save as Tunnel" : "Edit " + editing.get().name();
        return new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.FULL, title, Optional.empty(), sections, Optional.of(footer), actions, CANCEL);
    }

    @Override public void close() { if (closed) return; closed = true; view.close(); }
}
