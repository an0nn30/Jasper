package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.auth.AuthorizedKeys;
import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.PortRange;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.Option;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/** Add, edit or save a temporary server in one owned kit form. Validation hands back a definition. UI thread. */
public final class ServerEditor implements AutoCloseable {
    public record Account(UUID id, String name) { }
    public record BindChoice(Bind bind, String label) { @Override public String toString() { return label; } }
    /** The file and clipboard providers are invoked only by their explicit buttons. */
    public record KeySources(Supplier<Optional<String>> fromFile, Supplier<Optional<String>> fromClipboard) {
        public static final KeySources NONE = new KeySources(Optional::empty, Optional::empty);
    }

    public static final String NAME = "server.name", PROTOCOL = "server.protocol", FOLDER = "server.folder",
        CHOOSE_FOLDER = "server.folder.choose", AUTOSTART = "server.autostart", REACHABLE = "server.reachable",
        PORT = "server.port", PASSIVE = "server.passive", WRITABLE = "server.writable", LOGIN = "server.login",
        CHOOSE_ACCOUNT = "server.account.choose", KEYS = "server.keys", ADD_KEY = "server.key.file",
        PASTE_KEY = "server.key.paste", REMOVE_KEY = "server.key.remove", TLS = "server.tls", REQUIRE_TLS = "server.tls.require",
        SAVE = "server.save", CANCEL = "server.cancel", MESSAGE = "server.message";

    private final UiView view;
    private final List<ServerDefinition> others;
    private final Optional<ServerDefinition> initial;
    private final boolean existing, vaultPresent, linux;
    private final List<Protocol> protocols;
    private final List<BindChoice> binds;
    private final Function<UUID, Optional<String>> accountName;
    private final Supplier<CompletableFuture<Optional<Account>>> accountPicker;
    private final Supplier<Optional<Path>> folderChooser;
    private final KeySources keySources;
    private final Consumer<ServerDefinition> onSave;
    private final Runnable onCancel;
    private final List<String> keyLines = new ArrayList<>();
    private final Set<String> selectedKeys = new HashSet<>();
    private String name = "", folder = "", port, passive = "", accountLabel = "none chosen", message = "";
    private Protocol protocol, previous;
    private Bind bind = Bind.LOOPBACK;
    private UUID accountId;
    private boolean autostart, writable, useTls, requireTls, closed;
    private String login = "anon";

    public ServerEditor(UiKit kit, List<ServerDefinition> others, Optional<ServerDefinition> initial, boolean existing,
                        Set<Protocol> supported, List<ServerAddresses.Nic> interfaces, boolean vaultPresent,
                        Function<UUID, Optional<String>> accountName,
                        Supplier<CompletableFuture<Optional<Account>>> accountPicker, Supplier<Optional<Path>> folderChooser,
                        KeySources keySources, boolean linux, Consumer<ServerDefinition> onSave, Runnable onCancel) {
        this.others = List.copyOf(others); this.initial = initial; this.existing = existing;
        this.vaultPresent = vaultPresent; this.linux = linux; this.accountName = accountName;
        this.accountPicker = accountPicker; this.folderChooser = folderChooser; this.keySources = keySources;
        this.onSave = onSave; this.onCancel = onCancel;
        var offered = new ArrayList<Protocol>();
        for (Protocol each : Protocol.values())
            if (supported.contains(each) || initial.map(definition -> definition.protocol() == each).orElse(false)) offered.add(each);
        if (offered.isEmpty()) throw new IllegalArgumentException("No supported server protocols");
        protocols = List.copyOf(offered);
        var choices = new ArrayList<BindChoice>();
        choices.add(new BindChoice(Bind.LOOPBACK, Bind.LOOPBACK.label()));
        choices.add(new BindChoice(Bind.ALL, Bind.ALL.label()));
        for (ServerAddresses.Nic nic : interfaces)
            if (nic.up() && !nic.loopback() && !nic.addresses().isEmpty())
                choices.add(new BindChoice(new Bind.Interface(nic.name()), nic.label()));
        if (initial.isPresent()) {
            ServerDefinition definition = initial.get();
            name = definition.name(); folder = definition.folder().toString(); protocol = definition.protocol();
            port = String.valueOf(definition.port()); passive = definition.passiveRange().map(PortRange::token).orElse("");
            autostart = definition.autostart(); writable = definition.writable(); bind = definition.bind();
            useTls = definition.tls(); requireTls = definition.requireTls(); keyLines.addAll(definition.authorizedKeys());
            switch (definition.login()) {
                case Login.Anonymous ignored -> login = "anon";
                case Login.Session ignored -> login = "session";
                case Login.Vault account -> {
                    login = "vault"; accountId = account.credentialId();
                    accountLabel = accountName.apply(accountId).orElse(vaultPresent ? "account missing" : "needs the Credential Vault");
                }
            }
        } else {
            protocol = protocols.getFirst(); port = String.valueOf(protocol.defaultPort()); defaultLogin(protocol);
        }
        previous = protocol;
        if (choices.stream().noneMatch(choice -> choice.bind().equals(bind))) {
            String label = bind instanceof Bind.Interface named ? named.name() + " (not available)" : bind.label();
            choices.add(new BindChoice(bind, label));
        }
        binds = List.copyOf(choices);
        view = kit.create(new ViewSpec(form()), this::event);
    }

    public UiView view() { return view; }
    public void showError(String error) { if (!closed) { message = error; render(); } }
    @Override public void close() { if (closed) return; closed = true; view.close(); }

    private void event(UiEvent event) {
        if (closed) return;
        switch (event) {
            case UiEvent.Edited edit -> {
                switch (edit.id()) {
                    case NAME -> name = edit.text(); case FOLDER -> folder = edit.text();
                    case PORT -> port = edit.text(); case PASSIVE -> passive = edit.text();
                    default -> { return; }
                }
                render();
            }
            case UiEvent.Selected selected -> {
                String id = selected.ids().stream().findFirst().orElse("");
                switch (selected.id()) {
                    case PROTOCOL -> {
                        Protocol chosen = Protocol.valueOf(id);
                        if (existing || !protocols.contains(chosen)) return;
                        protocolChanged(chosen);
                    }
                    case REACHABLE -> {
                        int index = Integer.parseInt(id.substring("bind.".length()));
                        bind = binds.get(index).bind();
                    }
                    case LOGIN -> {
                        if (id.equals("vault") && !vaultPresent && accountId == null) return;
                        if (!Set.of("anon", "session", "vault").contains(id)) return;
                        login = id;
                    }
                    case KEYS -> { selectedKeys.clear(); selectedKeys.addAll(selected.ids()); }
                    default -> { return; }
                }
                render();
            }
            case UiEvent.Toggled toggle -> {
                switch (toggle.id()) {
                    case AUTOSTART -> autostart = toggle.checked();
                    case WRITABLE -> writable = toggle.checked();
                    case TLS -> { if (!protocol.allowsTls()) return; useTls = toggle.checked(); }
                    case REQUIRE_TLS -> { if (protocol != Protocol.FTP || !useTls) return; requireTls = toggle.checked(); }
                    default -> { return; }
                }
                render();
            }
            case UiEvent.Action action -> act(action.id());
            default -> { }
        }
    }

    private void act(String id) {
        switch (id) {
            case CHOOSE_FOLDER -> folderChooser.get().ifPresent(path -> { if (!closed) { folder = path.toString(); render(); } });
            case CHOOSE_ACCOUNT -> { if (!vaultPresent || !protocol.hasLogin()) return; accountPicker.get().whenComplete((chosen, failure) -> {
                Runnable apply = () -> {
                    if (closed) return;
                    if (failure != null) { message = "Could not choose a Vault account"; render(); return; }
                    if (chosen != null) chosen.ifPresent(account -> {
                        accountId = account.id(); accountLabel = account.name(); login = "vault"; render();
                    });
                };
                if (SwingUtilities.isEventDispatchThread()) apply.run(); else SwingUtilities.invokeLater(apply);
            }); }
            case ADD_KEY -> { if (protocol == Protocol.SFTP) addKeys(keySources.fromFile().get()); }
            case PASTE_KEY -> { if (protocol == Protocol.SFTP) addKeys(keySources.fromClipboard().get()); }
            case REMOVE_KEY -> {
                var indexes = selectedKeys.stream().map(idKey -> Integer.parseInt(idKey.substring(4))).sorted(Collections.reverseOrder()).toList();
                for (int index : indexes) if (index >= 0 && index < keyLines.size()) keyLines.remove(index);
                selectedKeys.clear(); render();
            }
            case CANCEL -> onCancel.run();
            case SAVE -> {
                try { onSave.accept(build()); }
                catch (IllegalArgumentException invalid) { message = invalid.getMessage(); render(); }
            }
            default -> { }
        }
    }

    /** Domain validation is deliberately last: opening the editor never probes the shared folder. */
    ServerDefinition build() {
        String nameText = name.strip();
        if (nameText.isEmpty()) throw new IllegalArgumentException("A server needs a name");
        for (ServerDefinition other : others)
            if (other.name().equalsIgnoreCase(nameText) && !(existing && initial.orElseThrow().id().equals(other.id())))
                throw new IllegalArgumentException("A server named " + other.name() + " exists");
        String folderText = folder.strip();
        if (folderText.isEmpty()) throw new IllegalArgumentException("Choose a folder to share");
        Path folderPath;
        try { folderPath = Path.of(folderText).toAbsolutePath().normalize(); }
        catch (InvalidPathException bad) { throw new IllegalArgumentException("The folder is not a valid path"); }
        if (!Files.isDirectory(folderPath)) throw new IllegalArgumentException("The folder " + folderText + " does not exist");
        int portValue = parsedPort();
        if (portValue < 1) throw new IllegalArgumentException("The port must be a number from 1 to 65535");
        Login selectedLogin;
        if (!protocol.hasLogin() || login.equals("anon")) selectedLogin = Login.ANONYMOUS;
        else if (login.equals("vault")) {
            if (accountId == null) throw new IllegalArgumentException("Choose a Vault account, or use a session password");
            selectedLogin = new Login.Vault(accountId);
        } else selectedLogin = Login.SESSION;
        boolean tlsOn = protocol.allowsTls() && useTls;
        boolean requireOn = protocol == Protocol.FTP && tlsOn && requireTls;
        String range = passive.strip();
        Optional<PortRange> passiveRange = protocol == Protocol.FTP && !range.isEmpty() ? Optional.of(PortRange.parse(range)) : Optional.empty();
        return new ServerDefinition(existing ? initial.orElseThrow().id() : UUID.randomUUID(), nameText, protocol, folderPath, bind, portValue,
            passiveRange, writable, selectedLogin, protocol == Protocol.SFTP ? List.copyOf(keyLines) : List.of(), tlsOn, requireOn, autostart);
    }

    private void addKeys(Optional<String> text) {
        if (text.isEmpty()) return;
        try {
            for (AuthorizedKeys.Key key : AuthorizedKeys.parseAll(text.get())) if (!keyLines.contains(key.line())) keyLines.add(key.line());
            message = "";
        } catch (IllegalArgumentException bad) { message = bad.getMessage(); }
        render();
    }

    private static String keySummary(String line) {
        try { return AuthorizedKeys.parse(line).summary(); }
        catch (IllegalArgumentException unreadable) { return line; }
    }

    private void protocolChanged(Protocol chosen) {
        if (port.strip().equals(String.valueOf(previous.defaultPort()))) port = String.valueOf(chosen.defaultPort());
        protocol = chosen; previous = chosen; defaultLogin(chosen);
    }

    private void defaultLogin(Protocol chosen) {
        if (chosen == Protocol.HTTP || !chosen.hasLogin()) login = "anon";
        else login = vaultPresent ? "vault" : "session";
    }

    private int parsedPort() {
        try { int value = Integer.parseInt(port.strip()); return value >= 1 && value <= 65535 ? value : -1; }
        catch (NumberFormatException invalid) { return -1; }
    }

    private void render() { if (!closed) view.update(new ViewSpec(form())); }
    private static ControlState state(boolean visible, boolean enabled) { return new ControlState(visible, enabled, false, ""); }
    private static Nodes.Text text(String id, String value, Nodes.TextRole role) {
        return new Nodes.Text(id, state(!value.isBlank(), true), value, role);
    }

    private Nodes.Form form() {
        boolean ftp = protocol == Protocol.FTP, sftp = protocol == Protocol.SFTP;
        boolean tls = protocol.allowsTls() && useTls;
        String loginLabel = !protocol.hasLogin() ? "No login (TFTP has none)" : sftp ? "Keys only (no password)" : "Anonymous";
        var loginOptions = new ArrayList<Option>();
        if (!protocol.hasLogin() || protocol.allowsAnonymous() || sftp) loginOptions.add(new Option("anon", loginLabel, Optional.empty()));
        if (protocol.hasLogin()) {
            if (vaultPresent || (initial.isPresent() && initial.get().login() instanceof Login.Vault))
                loginOptions.add(new Option("vault", vaultPresent ? "Vault account" : "Vault account (needs the Credential Vault)", Optional.empty()));
            loginOptions.add(new Option("session", "Session password", Optional.empty()));
        }
        String shownLogin = loginOptions.stream().anyMatch(option -> option.id().equals(login)) ? login : loginOptions.getFirst().id();
        Login warningLogin = shownLogin.equals("anon") ? Login.ANONYMOUS : shownLogin.equals("vault")
            ? new Login.Vault(new UUID(0, 0)) : Login.SESSION;
        var warnings = ServerRows.warnings(protocol, tls, warningLogin, writable, bind);
        String portHint = linux && parsedPort() > 0 && parsedPort() < 1024
            ? "Ports below 1024 need root or CAP_NET_BIND_SERVICE on Linux — try " + protocol.suggestedPort(parsedPort()) : "";
        var sections = new ArrayList<Nodes.Node>();
        sections.add(new Nodes.Field(NAME, ControlState.READY, Nodes.FieldKind.TEXT, name, "", "Name", ""));
        sections.add(new Nodes.Choice(PROTOCOL, state(true, !existing && protocols.size() > 1), Nodes.ChoiceKind.DROPDOWN,
            protocols.stream().map(value -> new Option(value.name(), value.label(), Optional.empty())).toList(), protocol.name(), "Protocol", ""));
        sections.add(new Nodes.FormRow("server.folder.row", ControlState.READY, "Folder", "",
            new Nodes.Field(FOLDER, ControlState.READY, Nodes.FieldKind.PATH, folder, "", "Folder", ""),
            Optional.of(new Nodes.Button(CHOOSE_FOLDER, ControlState.READY, "Choose…", "folder", Nodes.ButtonKind.SECONDARY, CHOOSE_FOLDER, ""))));
        sections.add(new Nodes.SettingRow("server.autostart.row", ControlState.READY, "Start when Jasper starts", "",
            new Nodes.Toggle(AUTOSTART, ControlState.READY, "Start when Jasper starts", autostart, AUTOSTART, false), false));
        sections.add(new Nodes.Choice(REACHABLE, ControlState.READY, Nodes.ChoiceKind.DROPDOWN,
            java.util.stream.IntStream.range(0, binds.size()).mapToObj(i -> new Option("bind." + i, binds.get(i).label(), Optional.empty())).toList(),
            "bind." + java.util.stream.IntStream.range(0, binds.size()).filter(i -> binds.get(i).bind().equals(bind)).findFirst().orElse(0),
            "Reachable from", ""));
        sections.add(new Nodes.Field(PORT, ControlState.READY, Nodes.FieldKind.TEXT, port, "", "Port", ""));
        sections.add(text("server.port.hint", portHint, Nodes.TextRole.DESCRIPTION));
        sections.add(new Nodes.Field(PASSIVE, state(ftp, ftp), Nodes.FieldKind.TEXT, passive, "FTP only; empty uses the range in Settings", "Passive ports", ""));
        sections.add(new Nodes.SettingRow("server.writable.row", ControlState.READY, "Allow changes (upload, rename, delete)", "",
            new Nodes.Toggle(WRITABLE, ControlState.READY, "Allow changes", writable, WRITABLE, false), false));
        sections.add(new Nodes.Choice(LOGIN, state(true, protocol.hasLogin()), Nodes.ChoiceKind.DROPDOWN, loginOptions, shownLogin, "Login", ""));
        sections.add(new Nodes.FormRow("server.account.row", state(protocol.hasLogin(), true), "Vault account", accountLabel,
            new Nodes.Text("server.account.name", ControlState.READY, accountLabel, Nodes.TextRole.BODY),
            Optional.of(new Nodes.Button(CHOOSE_ACCOUNT, state(true, vaultPresent), "Choose…", "key", Nodes.ButtonKind.SECONDARY, CHOOSE_ACCOUNT, ""))));
        var keys = new ArrayList<ListItem>();
        for (int i = 0; i < keyLines.size(); i++) keys.add(new ListItem("key." + i, Optional.empty(),
            List.of(new Nodes.Text("server.key." + i + ".summary", ControlState.READY, keySummary(keyLines.get(i)), Nodes.TextRole.MONO_SMALL)),
            List.of(), List.of()));
        sections.add(new Nodes.Stack("server.keys.section", state(sftp, true), Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.Text("server.keys.label", ControlState.READY, "Authorized keys", Nodes.TextRole.LABEL),
            new Nodes.ItemList(KEYS, ControlState.READY, Nodes.ListKind.COMPACT, keys, selectedKeys, "", List.of(),
                Nodes.SelectionMode.MULTIPLE, Optional.empty(), OptionalInt.of(3)),
            new Nodes.Stack("server.keys.actions", ControlState.READY, Nodes.LayoutRole.ACTIONS, List.of(
                new Nodes.Button(ADD_KEY, ControlState.READY, "Add from File…", "add", Nodes.ButtonKind.SECONDARY, ADD_KEY, ""),
                new Nodes.Button(PASTE_KEY, ControlState.READY, "Paste", "paste", Nodes.ButtonKind.SECONDARY, PASTE_KEY, ""),
                new Nodes.Button(REMOVE_KEY, state(true, !selectedKeys.isEmpty()), "Remove", "remove", Nodes.ButtonKind.SECONDARY, REMOVE_KEY, ""))))));
        sections.add(new Nodes.SettingRow("server.tls.row", state(protocol.allowsTls(), true), "Use TLS", "",
            new Nodes.Toggle(TLS, ControlState.READY, "Use TLS", useTls, TLS, false), false));
        sections.add(new Nodes.SettingRow("server.require-tls.row", state(ftp, true), "Require TLS (refuse logins and transfers without it)", "",
            new Nodes.Toggle(REQUIRE_TLS, state(true, ftp && tls), "Require TLS", requireTls && tls, REQUIRE_TLS, false), true));
        sections.add(text("server.warning", warnings.isEmpty() ? "" : "Warning · " + String.join(" · ", warnings), Nodes.TextRole.DESCRIPTION));
        var actions = List.of(
            new Nodes.Button(CANCEL, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, ""),
            new Nodes.Button(SAVE, ControlState.READY, existing ? "Save" : "Add Server", "", Nodes.ButtonKind.SECONDARY, SAVE, ""));
        return new Nodes.Form("server.editor", ControlState.READY, Nodes.FormKind.FULL,
            initial.isEmpty() ? "New File Server" : existing ? "Edit File Server" : "Save File Server", Optional.empty(),
            sections, Optional.of(new Nodes.Text(MESSAGE, state(!message.isBlank(), true), message, Nodes.TextRole.BODY,
                Optional.of(Nodes.Severity.DANGER), OptionalInt.empty())), actions, CANCEL);
    }
}
