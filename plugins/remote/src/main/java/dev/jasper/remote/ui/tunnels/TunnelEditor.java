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
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Add or edit one tunnel, or name a quick forward to keep it, on a host-rendered grouped kit form: a Tunnel card (name,
 * the host it goes through, its type and what that type does) and a Ports card (where it listens and where it forwards
 * to, a preview of the route, and whether other computers may connect). The footer previews the equivalent ssh command,
 * or shows a save error or the shared-port warning. The primary Add Tunnel (or Save) is enabled while the draft is a
 * valid tunnel; a name another tunnel has, or a port out of range, is marked on its field. As in Add Host the form
 * opts out of submission, so Enter saves nothing (the former dialog had no default button); Escape cancels. Saving
 * hands the tunnel to the owner, which closes the dialog or reports an error here; until it does the form waits. A save
 * error arriving after {@link #close()} is ignored. UI thread only.
 */
public final class TunnelEditor implements AutoCloseable {
    public static final String FORM = "tunnel", NAME = "tunnel.name", HOST = "tunnel.host", TYPE = "tunnel.type",
        EXPLANATION = "tunnel.explanation", LISTEN_ROW = "tunnel.listen", LISTEN_ADDRESS = "tunnel.listen.address", LISTEN_PORT = "tunnel.listen.port",
        ALLOW_OTHERS = "tunnel.allow-others", FORWARD_ROW = "tunnel.forward", FORWARD_HOST = "tunnel.forward.host",
        FORWARD_PORT = "tunnel.forward.port", ROUTE = "tunnel.route", PREVIEW = "tunnel.preview", WARNING_ROW = "tunnel.warning.row",
        WARNING = "tunnel.warning", MESSAGE = "tunnel.message", SAVE = "tunnel.save", CANCEL = "tunnel.cancel";
    /** The type choice's options. */
    public static final String LOCAL = "local", REMOTE = "remote", DYNAMIC = "dynamic";
    /** The two cards, as sections and their cards. */
    static final String TUNNEL_SECTION = "tunnel.section", PORTS_SECTION = "tunnel.ports";

    private final List<RemoteHost> hosts;
    private final List<Tunnel> others;
    private final Optional<Tunnel> editing;
    private final UUID editingId;
    private final UiView view;
    private String name = "", host = "", listenAddress = Tunnel.LOCALHOST, listenPort = "", forwardHost = "localhost", forwardPort = "", message = "";
    private TunnelKind kind = TunnelKind.LOCAL;
    private boolean allowOthers, saving, closed;

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
            // While the owner saves, only Cancel acts: the draft it holds stays the one shown.
            if (saving && !(event instanceof UiEvent.Action action && action.id().equals(CANCEL))) return;
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

    /** The owner could not save the tunnel: the form shows why and can be saved again. */
    public void showError(String error) { if (closed) return; saving = false; message = error; render(); }

    private void edited(String id, String text) {
        switch (id) {
            case NAME -> name = text;
            case LISTEN_ADDRESS -> listenAddress = text;
            case LISTEN_PORT -> listenPort = text;
            case FORWARD_HOST -> forwardHost = text;
            case FORWARD_PORT -> forwardPort = text;
            default -> { return; }
        }
        message = "";
        render();
    }

    private void save(Consumer<Tunnel> onSave) {
        Tunnel tunnel;
        try { tunnel = tunnel(); }
        catch (IllegalArgumentException invalid) { message = invalid.getMessage(); render(); return; }
        message = ""; saving = true; render();
        onSave.accept(tunnel);
    }

    private Tunnel tunnel() {
        String nameValue = name.strip();
        if (nameValue.isEmpty()) throw new IllegalArgumentException("A tunnel needs a name");
        duplicate().ifPresent(other -> { throw new IllegalArgumentException("A tunnel named " + other.name() + " exists"); });
        RemoteHost via = selectedHost().orElseThrow(() -> new IllegalArgumentException("Choose a host to go through"));
        int bindPort = port(listenPort, 0, "The listen port must be a number from 0 to 65535");
        int targetPort = kind == TunnelKind.DYNAMIC ? 0 : port(forwardPort, 1, "The target port must be a number from 1 to 65535");
        return editing.map(existing -> existing.withEdited(nameValue, via.id(), kind, listenAddress, bindPort, forwardHost, targetPort))
            .orElseGet(() -> Tunnel.create(nameValue, via.id(), kind, listenAddress, bindPort, forwardHost, targetPort));
    }

    /** Whether the draft is a tunnel that can be saved, which enables the primary action. */
    private boolean ready() {
        try { tunnel(); return true; } catch (IllegalArgumentException invalid) { return false; }
    }

    /** Another tunnel already has this name, ignoring case. */
    private Optional<Tunnel> duplicate() {
        String nameValue = name.strip();
        return others.stream().filter(other -> !other.id().equals(editingId) && !nameValue.isEmpty() && other.name().equalsIgnoreCase(nameValue)).findFirst();
    }

    private Optional<RemoteHost> selectedHost() { return hosts.stream().filter(via -> via.id().toString().equals(host)).findFirst(); }

    private static int port(String text, int minimum, String problem) {
        try {
            int value = Integer.parseInt(text.strip());
            if (value < minimum || value > 65535) throw new IllegalArgumentException(problem);
            return value;
        } catch (NumberFormatException bad) { throw new IllegalArgumentException(problem); }
    }

    /** A port field's inline problem: nothing while it is blank (the primary waits for it), else a value out of range. */
    private static String portProblem(String text, int minimum) {
        if (text.isBlank()) return "";
        try { int value = Integer.parseInt(text.strip()); return value < minimum || value > 65535 ? "Use " + minimum + " to 65535" : ""; }
        catch (NumberFormatException bad) { return "Use " + minimum + " to 65535"; }
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

    /** The equivalent OpenSSH command, with placeholders for what is still missing. */
    String command() {
        String bind = allowOthers || !listenAddress.strip().equals(Tunnel.LOCALHOST) ? bracketed(listenAddress.strip()) + ":" : "";
        String listen = bind + (listenPort.isBlank() ? "<port>" : listenPort.strip());
        String target = bracketed(forwardHost.strip().isEmpty() ? "<host>" : forwardHost.strip()) + ":" + (forwardPort.isBlank() ? "<port>" : forwardPort.strip());
        String destination = selectedHost().map(via -> (via.username().isEmpty() ? "" : via.username() + "@") + via.hostname() + (via.port() == 22 ? "" : " -p " + via.port()))
            .orElse("<host>");
        return switch (kind) {
            case LOCAL -> "ssh -L " + listen + ":" + target + " " + destination;
            case REMOTE -> "ssh -R " + listen + ":" + target + " " + destination;
            case DYNAMIC -> "ssh -D " + listen + " " + destination;
        };
    }

    /** A host as it reads before {@code :port}: an IPv6 literal goes in brackets, anything else as it is. */
    private static String bracketed(String value) { return value.contains(":") ? "[" + value + "]" : value; }

    /** An endpoint as the route shows it, {@code ?} for a port not chosen yet; a listening port 0 is any free port. */
    private static String endpoint(String address, String port, boolean listening) {
        String shown = port.isBlank() ? "?" : listening && port.strip().equals("0") ? "any port" : port.strip();
        return bracketed(address.strip().isEmpty() ? "?" : address.strip()) + ":" + shown;
    }

    private void render() { if (!closed) view.update(new ViewSpec(form())); }

    private static ControlState state(boolean visible, boolean enabled) { return new ControlState(visible, enabled, false, ""); }

    private Nodes.Field field(String id, Nodes.FieldKind fieldKind, String value, String hint, String label, boolean enabled, String error, Nodes.FieldPresentation presentation) {
        return new Nodes.Field(id, new ControlState(true, enabled && !saving, false, error), fieldKind, value, hint, label, "", Optional.empty(), presentation);
    }

    private static Nodes.FormRow row(String id, String label, String caption, Nodes.Node control, Optional<Nodes.Node> trailing) {
        return new Nodes.FormRow(id, ControlState.READY, label, caption, control, trailing);
    }

    private static Nodes.Section section(String id, String title, Nodes.CardKind kind, List<Nodes.Node> children) {
        return new Nodes.Section(id, ControlState.READY, title, new Nodes.Card(id + ".card", ControlState.READY, kind, children));
    }

    /** A host's face and option caption: its endpoint, then its group when it has one. */
    private static String caption(RemoteHost via) { return via.label() + (via.group().isEmpty() ? "" : " · " + via.group()); }

    /** One route step: its tile's artwork and hue, what it is, and where (a technical endpoint in mono, else plain text). */
    private static Nodes.Node step(String id, String icon, Nodes.Hue hue, String what, String where, boolean technical, String name) {
        return new Nodes.Stack(id, ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Icon(id + ".icon", ControlState.READY, icon, hue, Nodes.IconSize.CONTROL, name),
            new Nodes.Text(id + ".what", ControlState.READY, what, Nodes.TextRole.DESCRIPTION),
            new Nodes.Text(id + ".where", ControlState.READY, where, technical ? Nodes.TextRole.MONO_SMALL : Nodes.TextRole.BODY)));
    }

    /** The route the tunnel's connections take, left to right, as the strip under the ports shows it. */
    private Nodes.Node route(String via) {
        String listen = endpoint(listenAddress, listenPort, true), target = endpoint(forwardHost, forwardPort, false);
        var steps = switch (kind) {
            case LOCAL -> List.of(step(ROUTE + ".from", "laptop", Nodes.Hue.NEUTRAL, "This computer", listen, true, "This computer"),
                step(ROUTE + ".via", "server", Nodes.Hue.BLUE, "Via", via, true, "Host"),
                step(ROUTE + ".to", "target", Nodes.Hue.GREEN, "Target on " + via, target, true, "Target"));
            case REMOTE -> List.of(step(ROUTE + ".from", "server", Nodes.Hue.BLUE, "On " + via, listen, true, "Host"),
                step(ROUTE + ".via", "laptop", Nodes.Hue.NEUTRAL, "Via", "this computer", false, "This computer"),
                step(ROUTE + ".to", "target", Nodes.Hue.GREEN, "Target", target, true, "Target"));
            case DYNAMIC -> List.of(step(ROUTE + ".from", "laptop", Nodes.Hue.NEUTRAL, "SOCKS5 proxy", listen, true, "This computer"),
                step(ROUTE + ".via", "server", Nodes.Hue.BLUE, "Via", via, true, "Host"),
                step(ROUTE + ".to", "target", Nodes.Hue.GREEN, "Destinations", "as each client asks", false, "Target"));
        };
        return new Nodes.Stack(ROUTE, ControlState.READY, Nodes.LayoutRole.ROUTE, steps);
    }

    private Nodes.Form form() {
        boolean server = kind == TunnelKind.REMOTE, dynamic = kind == TunnelKind.DYNAMIC, editable = !saving;
        Optional<RemoteHost> chosen = selectedHost();
        String via = chosen.map(RemoteHost::name).orElse("the host");
        var hostOptions = hosts.stream().map(option -> new Option(option.id().toString(), option.name(), "server", caption(option))).toList();
        // The closed face describes the chosen host as the Sessions panel does: its tile, its name and its endpoint.
        Optional<Nodes.PickerFace> face = chosen.map(option -> new Nodes.PickerFace(option.name(), caption(option), "server", Nodes.Hue.BLUE, Nodes.PickerDensity.DEFAULT));
        String nameProblem = duplicate().map(other -> "A tunnel named " + other.name() + " exists").orElse("");
        var tunnel = section(TUNNEL_SECTION, "Tunnel", Nodes.CardKind.FORM, List.of(
            row("row." + NAME, "Name", "", field(NAME, Nodes.FieldKind.TEXT, name, "e.g. Postgres", "Name", true, nameProblem, Nodes.FieldPresentation.STANDARD), Optional.empty()),
            row("row." + HOST, "Via Host", "", new Nodes.Choice(HOST, state(true, editable), Nodes.ChoiceKind.DROPDOWN, hostOptions, host, "Via Host", "", "",
                Optional.empty(), face), Optional.empty()),
            row("row." + TYPE, "Type", "", new Nodes.Choice(TYPE, state(true, editable), Nodes.ChoiceKind.SEGMENTED, List.of(
                new Option(LOCAL, "Local", Optional.empty()), new Option(REMOTE, "Remote", Optional.empty()), new Option(DYNAMIC, "Dynamic (SOCKS)", Optional.empty())),
                switch (kind) { case LOCAL -> LOCAL; case REMOTE -> REMOTE; case DYNAMIC -> DYNAMIC; }, "Type", ""), Optional.empty()),
            row("row." + EXPLANATION, "", "", new Nodes.Text(EXPLANATION, ControlState.READY, switch (kind) {
                case LOCAL -> "Connections to a port on this computer go through " + via + " to the target.";
                case REMOTE -> "Connections to a port on " + via + " come back through this computer to the target.";
                case DYNAMIC -> "A SOCKS5 proxy on this computer; each connection goes out through " + via + ".";
            }, Nodes.TextRole.SUPPORTING), Optional.empty())));
        var ports = new ArrayList<Nodes.Node>();
        ports.add(row(LISTEN_ROW, "Listen On", server ? "on " + via : "this computer",
            field(LISTEN_ADDRESS, Nodes.FieldKind.TEXT, listenAddress, "localhost", "Listen address", !allowOthers, "", Nodes.FieldPresentation.TECHNICAL),
            Optional.of(field(LISTEN_PORT, Nodes.FieldKind.PORT, listenPort, dynamic ? "1080" : "8080", "Listen port", true, portProblem(listenPort, 0), Nodes.FieldPresentation.STANDARD))));
        ports.add(new Nodes.FormRow(FORWARD_ROW, state(!dynamic, true), "Forward To", server ? "as seen from this computer" : "as seen from " + via,
            field(FORWARD_HOST, Nodes.FieldKind.TEXT, forwardHost, "localhost", "Target host", true, "", Nodes.FieldPresentation.TECHNICAL),
            Optional.of(field(FORWARD_PORT, Nodes.FieldKind.PORT, forwardPort, "5432", "Target port", true, portProblem(forwardPort, 1), Nodes.FieldPresentation.STANDARD))));
        ports.add(route(via));
        ports.add(new Nodes.SettingRow("row." + ALLOW_OTHERS, ControlState.READY, "Allow other computers to connect",
            allowOthers ? server ? "Listens on all of the server's interfaces; its GatewayPorts setting must allow it" : "Listens on all network interfaces"
                : "Listens on all network interfaces, not just localhost",
            new Nodes.Toggle(ALLOW_OTHERS, state(true, editable), "Allow other computers to connect", allowOthers, ALLOW_OTHERS, false), false));
        Optional<Tunnel> shared = sharedPort();
        // The footer: a save problem, else the shared-port warning, else the equivalent command.
        Nodes.Node preview = !message.isEmpty()
            ? new Nodes.Text(MESSAGE, ControlState.READY, message, Nodes.TextRole.BODY, Optional.of(Nodes.Severity.DANGER), OptionalInt.empty())
            : shared.isPresent()
            ? new Nodes.Stack(WARNING_ROW, ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
                new Nodes.Icon(WARNING + ".icon", ControlState.READY, "warning", Nodes.Hue.NONE, Nodes.IconSize.CAPTION, "Warning", Optional.of(Nodes.Severity.WARNING)),
                new Nodes.Text(WARNING, ControlState.READY, shared.get().name() + " listens on the same port; they cannot run at once.", Nodes.TextRole.BODY)))
            : new Nodes.Text(PREVIEW, ControlState.READY, command(), Nodes.TextRole.MONO_CAPTION);
        var actions = List.of(
            new Nodes.Button(CANCEL, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, ""),
            new Nodes.Button(SAVE, new ControlState(true, editable && ready(), saving, ""), saving ? "Saving…" : editing.isEmpty() ? "Add Tunnel" : "Save", "",
                Nodes.ButtonKind.PRIMARY, SAVE, ""));
        String title = editing.isEmpty() ? "New Tunnel" : editing.get().quick() ? "Save as Tunnel" : "Edit " + editing.get().name();
        return new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.FULL, title, Optional.empty(),
            List.of(tunnel, section(PORTS_SECTION, "Ports", Nodes.CardKind.MIXED, ports)), Optional.of(preview), actions, CANCEL, Nodes.FormSubmission.NONE);
    }

    @Override public void close() { if (closed) return; closed = true; saving = false; view.close(); }
}
