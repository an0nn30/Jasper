package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.auth.AuthorizedKeys;
import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.PortRange;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.UiAnchor;
import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.api.CredentialPickRequest;
import dev.jasper.vault.api.CredentialPickResult;
import dev.jasper.vault.api.CredentialPresentation;
import dev.jasper.vault.api.Kind;
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
    private final Supplier<CompletableFuture<Optional<Account>>> accountPicker;
    private final Supplier<Optional<Path>> folderChooser;
    private final KeySources keySources;
    private final Function<ServerDefinition, CompletableFuture<Void>> persist;
    private final Runnable onSaved;
    private final Function<UUID, Optional<CredentialDescriptor>> metadata;
    private final Function<CredentialPickRequest, CompletableFuture<CredentialPickResult>> anchoredPicker;
    private CredentialDescriptor chosenMetadata;
    private PickOperation pending;
    private long pickerRevision;
    private Object saving;
    private static final class PickOperation {
        final long revision;
        UiAnchor anchor;
        Subscription invalidation;
        CompletableFuture<?> future;
        PickOperation(long revision) { this.revision = revision; }
    }
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
        this(kit, others, initial, existing, supported, interfaces, vaultPresent, accountName, id -> Optional.empty(), null,
            accountPicker, folderChooser, keySources, linux, definition -> { onSave.accept(definition); return CompletableFuture.completedFuture(null); }, () -> {}, onCancel);
    }

    /** Real mounted picker and asynchronous persistence; returned futures may complete on any thread. */
    public ServerEditor(UiKit kit, List<ServerDefinition> others, Optional<ServerDefinition> initial, boolean existing,
                        Set<Protocol> supported, List<ServerAddresses.Nic> interfaces, boolean vaultPresent,
                        Function<UUID, Optional<CredentialDescriptor>> metadata,
                        Function<CredentialPickRequest, CompletableFuture<CredentialPickResult>> picker, Supplier<Optional<Path>> folderChooser,
                        KeySources keySources, boolean linux, Function<ServerDefinition, CompletableFuture<Void>> persist, Runnable onSaved, Runnable onCancel) {
        this(kit, others, initial, existing, supported, interfaces, vaultPresent,
            vaultPresent ? id -> metadata.apply(id).map(CredentialDescriptor::name) : id -> Optional.empty(), metadata,
            picker, null, folderChooser, keySources, linux, persist, onSaved, onCancel);
    }

    private ServerEditor(UiKit kit, List<ServerDefinition> others, Optional<ServerDefinition> initial, boolean existing,
                         Set<Protocol> supported, List<ServerAddresses.Nic> interfaces, boolean vaultPresent,
                         Function<UUID, Optional<String>> accountName, Function<UUID, Optional<CredentialDescriptor>> metadata,
                         Function<CredentialPickRequest, CompletableFuture<CredentialPickResult>> anchoredPicker,
                         Supplier<CompletableFuture<Optional<Account>>> accountPicker, Supplier<Optional<Path>> folderChooser,
                         KeySources keySources, boolean linux, Function<ServerDefinition, CompletableFuture<Void>> persist, Runnable onSaved, Runnable onCancel) {
        this.others = List.copyOf(others); this.initial = initial; this.existing = existing;
        this.vaultPresent = vaultPresent; this.linux = linux;
        this.accountPicker = accountPicker; this.folderChooser = folderChooser; this.keySources = keySources;
        this.persist = persist; this.onSaved = onSaved; this.onCancel = onCancel;
        this.metadata = metadata; this.anchoredPicker = anchoredPicker;
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
    @Override public void close() { if (closed) return; closed = true; saving = null; cancelPick(); view.close(); }

    private void event(UiEvent event) {
        if (closed) return;
        if (saving != null && !(event instanceof UiEvent.Action action && action.id().equals(CANCEL))) return;
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
                        if (!id.equals(login)) { login = id; cancelPick(); }
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
            case CHOOSE_ACCOUNT -> pick();
            case ADD_KEY -> { if (protocol == Protocol.SFTP) addKeys(keySources.fromFile().get()); }
            case PASTE_KEY -> { if (protocol == Protocol.SFTP) addKeys(keySources.fromClipboard().get()); }
            case REMOVE_KEY -> {
                var indexes = selectedKeys.stream().map(idKey -> Integer.parseInt(idKey.substring(4))).sorted(Collections.reverseOrder()).toList();
                for (int index : indexes) if (index >= 0 && index < keyLines.size()) keyLines.remove(index);
                selectedKeys.clear(); render();
            }
            case CANCEL -> onCancel.run();
            case SAVE -> save();
            default -> { }
        }
    }

    private static void onUi(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run(); else SwingUtilities.invokeLater(action);
    }

    private void pick() {
        if (closed || saving != null || !vaultPresent || !protocol.hasLogin() || !login.equals("vault")) return;
        var operation = new PickOperation(++pickerRevision);
        var predecessor = pending; pending = operation; release(predecessor, true);
        if (closed || pending != operation) return;
        message = "";
        try {
            if (accountPicker != null) {
                var result = java.util.Objects.requireNonNull(accountPicker.get());
                if (closed || pending != operation) { result.cancel(false); return; }
                operation.future = result;
                result.whenComplete((value, failure) -> onUi(() -> finishPick(operation,
                    value == null ? null : value.orElse(null), null, false, failure)));
            } else {
                operation.anchor = view.anchor(CHOOSE_ACCOUNT);
                operation.invalidation = operation.anchor.onClosed(() -> { if (pending == operation) cancelPick(); });
                if (closed || pending != operation) { release(operation, true); return; }
                var result = java.util.Objects.requireNonNull(anchoredPicker.apply(new CredentialPickRequest(operation.anchor, Optional.ofNullable(accountId), true)));
                if (closed || pending != operation) { result.cancel(false); return; }
                operation.future = result;
                result.whenComplete((value, failure) -> onUi(() -> finishPick(operation, null,
                    value instanceof CredentialPickResult.Chosen chosen ? chosen.credential() : null,
                    value instanceof CredentialPickResult.Cleared, failure)));
            }
        } catch (RuntimeException failure) { finishPick(operation, null, null, false, failure); }
    }

    private void finishPick(PickOperation operation, Account legacy, CredentialDescriptor chosen, boolean clear, Throwable failure) {
        if (closed || pending != operation) return;
        pending = null; release(operation, false);
        if (closed || pickerRevision != operation.revision) return;
        if (failure != null) {
            if (!(failure instanceof java.util.concurrent.CancellationException)) showError(failureMessage(failure));
        } else if (chosen != null) {
            if ((chosen.kind() != Kind.ACCOUNT_PASSWORD && chosen.kind() != Kind.ACCOUNT_KEY_AND_PASSWORD) || chosen.subtitle().isBlank()) {
                showError("Choose an account with a username and password"); return;
            }
            chosenMetadata = chosen; accountId = chosen.id(); accountLabel = chosen.name(); render();
        } else if (legacy != null) { accountId = legacy.id(); accountLabel = legacy.name(); render(); }
        else if (clear) { accountId = null; chosenMetadata = null; accountLabel = "none chosen"; render(); }
    }

    private void cancelPick() { pickerRevision++; var operation = pending; pending = null; release(operation, true); }
    private static void release(PickOperation operation, boolean cancel) {
        if (operation == null) return;
        if (operation.invalidation != null) operation.invalidation.close();
        if (cancel && operation.future != null) operation.future.cancel(false);
        if (operation.anchor != null) operation.anchor.close();
    }

    private void save() {
        if (closed || saving != null) return;
        final ServerDefinition definition;
        try { definition = build(); } catch (IllegalArgumentException invalid) { showError(invalid.getMessage()); return; }
        Object operation = new Object(); saving = operation; cancelPick();
        if (closed || saving != operation) return;
        message = ""; render();
        try {
            java.util.Objects.requireNonNull(persist.apply(definition)).whenComplete((ignored, failure) -> onUi(() -> {
                if (closed || saving != operation) return;
                saving = null;
                if (failure != null) showError(failureMessage(failure));
                else { render(); onSaved.run(); }
            }));
        } catch (RuntimeException failure) {
            if (!closed && saving == operation) { saving = null; showError(failureMessage(failure)); }
        }
    }
    private static String failureMessage(Throwable failure) {
        while ((failure instanceof java.util.concurrent.CompletionException || failure instanceof java.util.concurrent.ExecutionException) && failure.getCause() != null) failure = failure.getCause();
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    /** Domain validation is deliberately last: opening the editor never probes the shared folder. */
    ServerDefinition build() { return draft(true); }

    private boolean ready() { try { draft(false); return true; } catch (IllegalArgumentException invalid) { return false; } }

    private ServerDefinition draft(boolean inspectFolder) {
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
        if (inspectFolder && !Files.isDirectory(folderPath)) throw new IllegalArgumentException("The folder " + folderText + " does not exist");
        int portValue = parsedPort();
        if (portValue < 1) throw new IllegalArgumentException("The port must be a number from 1 to 65535");
        Login selectedLogin;
        if (!protocol.hasLogin() || login.equals("anon")) selectedLogin = Login.ANONYMOUS;
        else if (login.equals("vault")) {
            if (accountId == null) throw new IllegalArgumentException("Choose a Vault account, or use a session password");
            selectedLogin = new Login.Vault(accountId);
        } else selectedLogin = Login.SESSION;
        boolean tlsOn = protocol.allowsTls() && useTls;
        boolean requireOn = protocol == Protocol.FTP && requireTls;
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
        protocol = chosen; previous = chosen; defaultLogin(chosen); cancelPick();
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
        boolean editable = saving == null;
        boolean tls = protocol.allowsTls() && useTls;
        String loginLabel = !protocol.hasLogin() ? "No login (TFTP has none)" : sftp ? "Keys Only" : "Anonymous";
        var loginOptions = new ArrayList<Option>();
        if (!protocol.hasLogin() || protocol.allowsAnonymous() || sftp) loginOptions.add(new Option("anon", loginLabel, Optional.empty()));
        if (protocol.hasLogin()) {
            if (vaultPresent || (initial.isPresent() && initial.get().login() instanceof Login.Vault))
                loginOptions.add(new Option("vault", vaultPresent ? "Vault Account" : "Vault account (needs the Credential Vault)", Optional.empty()));
            loginOptions.add(new Option("session", "Session Password", Optional.empty()));
        }
        String shownLogin = loginOptions.stream().anyMatch(option -> option.id().equals(login)) ? login : loginOptions.getFirst().id();
        Login warningLogin = shownLogin.equals("anon") ? Login.ANONYMOUS : shownLogin.equals("vault")
            ? new Login.Vault(new UUID(0, 0)) : Login.SESSION;
        var warnings = ServerRows.warnings(protocol, tls, warningLogin, writable, bind);
        String portHint = linux && parsedPort() > 0 && parsedPort() < 1024
            ? "Ports below 1024 need root or CAP_NET_BIND_SERVICE on Linux — try " + protocol.suggestedPort(parsedPort()) : "";
        var server = new ArrayList<Nodes.Node>();
        server.add(row("server.name.row", "Name", new Nodes.Field(NAME, ControlState.READY, Nodes.FieldKind.TEXT, name, "e.g. Downloads", "Name", "")));
        server.add(row("server.protocol.row", "Protocol", new Nodes.Choice(PROTOCOL, state(true, !existing && protocols.size() > 1), Nodes.ChoiceKind.SEGMENTED,
            protocols.stream().map(value -> new Option(value.name(), value.label(), Optional.of(switch(value) {
                case HTTP -> Nodes.Hue.BLUE; case FTP -> Nodes.Hue.AMBER; case SFTP -> Nodes.Hue.GREEN; case TFTP -> Nodes.Hue.PURPLE;
            }))).toList(), protocol.name(), "Protocol", "")));
        server.add(new Nodes.FormRow("server.folder.row", ControlState.READY, "Folder", "",
            new Nodes.Field(FOLDER, ControlState.READY, Nodes.FieldKind.PATH, folder, "No folder selected", "Folder", ""),
            Optional.of(new Nodes.Button(CHOOSE_FOLDER, ControlState.READY, "Choose…", "", Nodes.ButtonKind.SECONDARY, CHOOSE_FOLDER, ""))));
        server.add(new Nodes.FormRow("server.reachable.row", ControlState.READY, "Reachable From", "",
            new Nodes.Choice(REACHABLE, ControlState.READY, Nodes.ChoiceKind.DROPDOWN,
                java.util.stream.IntStream.range(0, binds.size()).mapToObj(i -> new Option("bind." + i, binds.get(i).label(), Optional.empty())).toList(),
                "bind." + java.util.stream.IntStream.range(0, binds.size()).filter(i -> binds.get(i).bind().equals(bind)).findFirst().orElse(0), "Reachable From", ""),
            Optional.of(new Nodes.Field(PORT, ControlState.READY, Nodes.FieldKind.PORT, port, "", "Port", ""))));
        server.add(text("server.port.hint", portHint, Nodes.TextRole.DESCRIPTION));
        server.add(new Nodes.FormRow("server.passive.row", state(ftp, true), "Passive ports", "",
            new Nodes.Field(PASSIVE, state(ftp, ftp), Nodes.FieldKind.TEXT, passive, "Empty uses the range in Settings", "Passive ports", "", Optional.empty(), Nodes.FieldPresentation.TECHNICAL), Optional.empty()));
        var access = new ArrayList<Nodes.Node>();
        var selected = accountId == null || !vaultPresent ? null
            : chosenMetadata != null && chosenMetadata.id().equals(accountId) ? chosenMetadata : metadata.apply(accountId).orElse(null);
        var face = vaultPresent ? CredentialPresentation.face(selected, Nodes.PickerDensity.DEFAULT)
            : new Nodes.PickerFace("", "", "lock", Nodes.Hue.NONE, Nodes.PickerDensity.DEFAULT);
        if (accountId != null && face.primary().isEmpty()) face = new Nodes.PickerFace(accountLabel, "", "lock", Nodes.Hue.NONE, Nodes.PickerDensity.DEFAULT);
        // The Access card is the login's own card, so its segments need no label: they span the card, as Add Host's do.
        access.add(new Nodes.Choice(LOGIN, state(true, protocol.hasLogin()), Nodes.ChoiceKind.SEGMENTED, loginOptions, shownLogin, "Login", ""));
        access.add(new Nodes.FormRow("server.account.row", state(protocol.hasLogin() && shownLogin.equals("vault"), true), "Account", "",
            new Nodes.Choice(CHOOSE_ACCOUNT, state(true, vaultPresent), Nodes.ChoiceKind.PICKER, List.of(), "", "Choose…", CHOOSE_ACCOUNT, "lock", Optional.empty(),
                Optional.of(face)), Optional.empty()));
        String explanation = !protocol.hasLogin() ? "TFTP has no login. Anyone who can reach the server can connect."
            : shownLogin.equals("vault") ? "Clients sign in with the username and password stored in this Vault account."
            : shownLogin.equals("session") ? "Jasper generates a temporary password when the server starts."
            : sftp ? "Clients sign in with an authorized SSH key."
            : "Anyone who can reach the server can connect without a password.";
        access.add(text("server.login.help", explanation, Nodes.TextRole.SUPPORTING));
        var keys = new ArrayList<ListItem>();
        for (int i = 0; i < keyLines.size(); i++) keys.add(new ListItem("key." + i, Optional.empty(),
            List.of(new Nodes.Text("server.key." + i + ".summary", ControlState.READY, keySummary(keyLines.get(i)), Nodes.TextRole.MONO_SMALL)),
            List.of(), List.of()));
        access.add(new Nodes.Stack("server.keys.section", state(sftp, true), Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.Text("server.keys.label", ControlState.READY, "Authorized keys", Nodes.TextRole.LABEL),
            new Nodes.ItemList(KEYS, ControlState.READY, Nodes.ListKind.COMPACT, keys, selectedKeys, "", List.of(),
                Nodes.SelectionMode.MULTIPLE, Optional.empty(), OptionalInt.of(3)),
            new Nodes.Stack("server.keys.actions", ControlState.READY, Nodes.LayoutRole.ACTIONS, List.of(
                new Nodes.Button(ADD_KEY, ControlState.READY, "Add from File…", "add", Nodes.ButtonKind.SECONDARY, ADD_KEY, ""),
                new Nodes.Button(PASTE_KEY, ControlState.READY, "Paste", "paste", Nodes.ButtonKind.SECONDARY, PASTE_KEY, ""),
                new Nodes.Button(REMOVE_KEY, state(true, !selectedKeys.isEmpty()), "Remove", "remove", Nodes.ButtonKind.SECONDARY, REMOVE_KEY, ""))))));
        access.add(new Nodes.SettingRow("server.writable.row", ControlState.READY, "Allow changes", "Clients can upload, rename and delete files",
            new Nodes.Toggle(WRITABLE, ControlState.READY, "Allow changes", writable, WRITABLE, false), false));
        var security = List.<Nodes.Node>of(
            new Nodes.SettingRow("server.tls.row", state(protocol.allowsTls(), true), "Use TLS", "",
                new Nodes.Toggle(TLS, ControlState.READY, "Use TLS", useTls, TLS, false), false),
            new Nodes.SettingRow("server.require-tls.row", state(protocol.allowsTls(), ftp && tls), "Require TLS", "Refuse logins and transfers without it",
                new Nodes.Toggle(REQUIRE_TLS, state(true, ftp && tls), "Require TLS", ftp && requireTls, REQUIRE_TLS, false), true),
            new Nodes.SettingRow("server.autostart.row", ControlState.READY, "Start when Jasper starts", "",
                new Nodes.Toggle(AUTOSTART, ControlState.READY, "Start when Jasper starts", autostart, AUTOSTART, false), false));
        var sections = List.<Nodes.Node>of(section("server.general", "Server", Nodes.CardKind.FORM, server),
            section("server.access", "Access", Nodes.CardKind.MIXED, access),
            section("server.security", "Security & Startup", Nodes.CardKind.SETTINGS, security),
            text("server.warning", warnings.isEmpty() ? "" : "Warning · " + String.join(" · ", warnings), Nodes.TextRole.DESCRIPTION),
            text("server.tls.dependency", ftp && requireTls && !tls ? "Require TLS needs Use TLS. Turn Use TLS on to change this requirement." : "", Nodes.TextRole.DESCRIPTION));
        var actions = List.of(
            new Nodes.Button(CANCEL, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, ""),
            new Nodes.Button(SAVE, new ControlState(true, editable && ready(), !editable, ""), !editable ? "Saving…" : existing ? "Save" : "Add Server", "", Nodes.ButtonKind.PRIMARY, SAVE, ""));
        String address = bind instanceof Bind.Loopback ? "127.0.0.1" : bind instanceof Bind.All ? "0.0.0.0" : ((Bind.Interface)bind).name();
        String scheme = protocol == Protocol.HTTP && tls ? "https" : protocol.scheme();
        Nodes.Node preview = message.isEmpty() ? text("server.preview", scheme + "://" + address + ":" + port, Nodes.TextRole.MONO_CAPTION)
            : new Nodes.Text(MESSAGE, ControlState.READY, message, Nodes.TextRole.BODY, Optional.of(Nodes.Severity.DANGER), OptionalInt.empty());
        return new Nodes.Form("server.editor", ControlState.READY, Nodes.FormKind.FULL,
            initial.isEmpty() ? "New File Server" : existing ? "Edit File Server" : "Save File Server", Optional.empty(),
            sections, Optional.of(preview), actions, CANCEL, Nodes.FormSubmission.NONE);
    }

    private static Nodes.FormRow row(String id, String label, Nodes.Node control) {
        return new Nodes.FormRow(id, ControlState.READY, label, "", control, Optional.empty());
    }
    private Nodes.Section section(String id, String title, Nodes.CardKind kind, List<Nodes.Node> children) {
        return new Nodes.Section(id, state(true, saving == null), title, new Nodes.Card(id + ".card", ControlState.READY, kind, children));
    }
}
