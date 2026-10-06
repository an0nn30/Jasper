package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.Option;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import dev.jasper.vault.api.CredentialDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Add or edit one host on a host-rendered kit form. Validation is inline; Save hands back a {@link RemoteHost} and the
 * dialog closes it. No button is the form's primary and no field submits, so Enter saves nothing (the former dialog had
 * no default button); Escape cancels. Changing the authentication keeps the chosen credential and keys until Save. A
 * Vault choice completing after {@link #close()} is ignored. UI thread only.
 */
public final class HostEditor implements AutoCloseable {
    public static final String FORM = "host", NAME = "host.name", HOSTNAME = "host.hostname", PORT = "host.port", USERNAME = "host.username",
        GROUP = "host.group", GROUP_HINT = "host.group.hint", FAVORITE = "host.favorite", FOLLOW = "host.follow", AUTH = "host.auth",
        CREDENTIAL = "host.credential", CHOOSE = "host.choose", KEYS_LABEL = "host.keys.label", KEYS = "host.keys", ADD_KEY = "host.keys.add",
        JUMP = "host.jump", MESSAGE = "host.message", SAVE = "host.save", CANCEL = "host.cancel";
    public static final String VAULT = "vault", AGENT = "agent", NO_JUMP = "none";

    private final List<RemoteHost> others;
    private final Optional<RemoteHost> editing;
    private final boolean vaultPresent;
    private final Function<UUID, Optional<String>> credentialName;
    private final List<RemoteHost> jumps;
    private final List<String> groups;
    private final UiView view;
    private String name = "", hostname = "", port = "22", username = "", group = "", jump = NO_JUMP, message = "", credentialLabel = "none chosen", defaultGroup = HostRows.OTHER;
    private boolean favorite, followDirectory = true, vaultAuth, closed;
    private UUID credentialId;
    private final List<UUID> managedIds = new ArrayList<>();

    public HostEditor(UiKit kit, List<RemoteHost> others, Optional<RemoteHost> editing, boolean vaultPresent, Function<UUID, Optional<String>> credentialName,
                      Supplier<CompletableFuture<Optional<CredentialDescriptor>>> pick, Consumer<RemoteHost> onSave, Runnable onCancel) {
        this.others = List.copyOf(others); this.editing = editing; this.vaultPresent = vaultPresent; this.credentialName = credentialName;
        jumps = others.stream().filter(other -> editing.map(host -> !host.id().equals(other.id())).orElse(true)).toList();
        groups = List.copyOf(new TreeSet<>(others.stream().map(RemoteHost::group).filter(g -> !g.isEmpty()).toList()));
        editing.ifPresentOrElse(host -> {
            name = host.name(); hostname = host.hostname(); port = String.valueOf(host.port()); username = host.username();
            group = host.group(); favorite = host.favorite(); followDirectory = host.followDirectory();
            switch (host.auth()) {
                case Auth.Vault vault -> { vaultAuth = true; credentialId = vault.credentialId(); credentialLabel = credentialName.apply(credentialId).orElse("credential missing"); }
                case Auth.VaultKeys keys -> { vaultAuth = true; managedIds.addAll(keys.credentialIds()); credentialLabel = managedIds.size() + " Vault key(s)"; }
                case Auth.Agent agent -> vaultAuth = false;
            }
            host.jump().filter(id -> jumps.stream().anyMatch(other -> other.id().equals(id))).ifPresent(id -> jump = id.toString());
        }, () -> vaultAuth = vaultPresent);
        view = kit.create(new ViewSpec(form()), event -> {
            if (closed) return;
            switch (event) {
                case UiEvent.Edited edited -> edited(edited.id(), edited.text());
                case UiEvent.Toggled toggled when toggled.id().equals(FAVORITE) -> { favorite = toggled.checked(); render(); }
                case UiEvent.Toggled toggled when toggled.id().equals(FOLLOW) -> { followDirectory = toggled.checked(); render(); }
                case UiEvent.Selected selected when selected.id().equals(AUTH) && !selected.ids().isEmpty() -> {
                    // Without the Vault its option shows but cannot be chosen, as the disabled radio button could not.
                    String chosen = selected.ids().iterator().next();
                    if (chosen.equals(VAULT) && vaultPresent) vaultAuth = true; else if (chosen.equals(AGENT)) vaultAuth = false;
                    render();
                }
                case UiEvent.Selected selected when selected.id().equals(JUMP) && !selected.ids().isEmpty() -> { jump = selected.ids().iterator().next(); render(); }
                case UiEvent.Reordered moved when moved.id().equals(KEYS) -> move(UUID.fromString(moved.itemId()), moved.destination());
                case UiEvent.Removed removed when removed.id().equals(KEYS) -> remove(UUID.fromString(removed.itemId()));
                case UiEvent.Action action when action.id().equals(CHOOSE) -> { if (vaultAuth) pick.get().thenAccept(chosen -> chosen.ifPresent(this::chose)); }
                case UiEvent.Action action when action.id().equals(ADD_KEY) -> { if (vaultAuth && vaultPresent) pick.get().thenAccept(chosen -> chosen.ifPresent(this::addKey)); }
                case UiEvent.Action action when action.id().equals(SAVE) -> save(onSave);
                case UiEvent.Action action when action.id().equals(CANCEL) -> onCancel.run();
                default -> { }
            }
        });
    }

    public UiView view() { return view; }

    /** The validation or save message as shown. */
    public String message() { return message; }

    /** The chosen credential or key count as shown. */
    public String credentialLabel() { return credentialLabel; }

    public void setDefaultGroupName(String name) { defaultGroup = name; render(); }

    public void showError(String error) { if (closed) return; message = error; render(); }

    private void edited(String id, String text) {
        switch (id) {
            case NAME -> name = text;
            case HOSTNAME -> hostname = text;
            case PORT -> port = text;
            case USERNAME -> username = text;
            case GROUP -> group = text;
            default -> { return; }
        }
        render();
    }

    private void chose(CredentialDescriptor descriptor) {
        if (closed) return;
        managedIds.clear(); credentialId = descriptor.id(); credentialLabel = descriptor.name(); render();
    }

    private void addKey(CredentialDescriptor descriptor) {
        if (closed) return;
        if (descriptor.kind() != dev.jasper.vault.api.Kind.SSH_KEY || !descriptor.managedKey()) { message = "Choose a managed SSH key stored in Vault"; render(); return; }
        credentialId = null;
        if (!managedIds.contains(descriptor.id())) managedIds.add(descriptor.id());
        keysChanged();
    }

    private void move(UUID id, int destination) {
        int at = managedIds.indexOf(id);
        if (at < 0 || destination < 0 || destination >= managedIds.size()) return;
        managedIds.add(destination, managedIds.remove(at));
        keysChanged();
    }

    private void remove(UUID id) {
        managedIds.remove(id);
        if (managedIds.isEmpty()) credentialLabel = "none chosen";
        keysChanged();
    }

    private void keysChanged() {
        if (!managedIds.isEmpty()) credentialLabel = managedIds.size() + " Vault key(s)";
        render();
    }

    private void save(Consumer<RemoteHost> onSave) {
        try {
            if (name.isBlank()) throw new IllegalArgumentException("A host needs a name");
            int portValue;
            try { portValue = Integer.parseInt(port.strip()); } catch (NumberFormatException bad) { throw new IllegalArgumentException("The port must be a number from 1 to 65535"); }
            Auth chosen;
            if (vaultAuth) {
                if (credentialId == null && managedIds.isEmpty()) throw new IllegalArgumentException("Choose a credential, or use the SSH agent");
                chosen = managedIds.isEmpty() ? new Auth.Vault(credentialId) : new Auth.VaultKeys(managedIds);
            } else chosen = Auth.AGENT;
            Optional<UUID> jumpValue = jump.equals(NO_JUMP) ? Optional.empty() : Optional.of(UUID.fromString(jump));
            for (RemoteHost other : others)
                if (other.name().equalsIgnoreCase(name.strip()) && editing.map(host -> !host.id().equals(other.id())).orElse(true)) throw new IllegalArgumentException("A host named " + other.name() + " exists");
            RemoteHost host = editing.map(existing -> existing.withEdited(name, hostname, portValue, username, chosen, group, jumpValue))
                .orElseGet(() -> RemoteHost.create(name, hostname, portValue, username, chosen, group, jumpValue));
            onSave.accept(host.withFavorite(favorite).withFollowDirectory(followDirectory));
        } catch (IllegalArgumentException invalid) { if (!closed) { message = invalid.getMessage(); render(); } }
    }

    private void render() { if (!closed) view.update(new ViewSpec(form())); }

    private static ControlState state(boolean visible, boolean enabled) { return new ControlState(visible, enabled, false, ""); }

    private Nodes.Form form() {
        var sections = new ArrayList<Nodes.Node>();
        sections.add(new Nodes.Field(NAME, ControlState.READY, Nodes.FieldKind.TEXT, name, "", "Name", ""));
        sections.add(new Nodes.Field(HOSTNAME, ControlState.READY, Nodes.FieldKind.TEXT, hostname, "", "Hostname", ""));
        sections.add(new Nodes.Field(PORT, ControlState.READY, Nodes.FieldKind.PORT, port, "", "Port", ""));
        sections.add(new Nodes.Field(USERNAME, ControlState.READY, Nodes.FieldKind.TEXT, username, "", "Username", ""));
        sections.add(new Nodes.Choice(GROUP, ControlState.READY, Nodes.ChoiceKind.SEARCHABLE,
            groups.stream().map(g -> new Option("group." + g, g, Optional.empty())).toList(), "", "Group", "", "",
            Optional.of(new Nodes.ChoiceSearch(0, "No other groups", Optional.of(group)))));
        sections.add(new Nodes.Text(GROUP_HINT, ControlState.READY, "Blank: " + defaultGroup + " (default)", Nodes.TextRole.CAPTION));
        sections.add(new Nodes.SettingRow("row." + FAVORITE, ControlState.READY, "Favorite", "",
            new Nodes.Toggle(FAVORITE, ControlState.READY, "Favorite", favorite, FAVORITE, false), false));
        sections.add(new Nodes.SettingRow("row." + FOLLOW, ControlState.READY, "Track shell folder for SFTP follow", "",
            new Nodes.Toggle(FOLLOW, ControlState.READY, "Track shell folder for SFTP follow", followDirectory, FOLLOW, false), false));
        sections.add(new Nodes.Choice(AUTH, ControlState.READY, Nodes.ChoiceKind.SEGMENTED, List.of(
            new Option(VAULT, vaultPresent ? "Vault credential" : "Vault credential (needs Credential Vault)", Optional.empty()),
            new Option(AGENT, "SSH agent", Optional.empty())), vaultAuth ? VAULT : AGENT, "Authentication", ""));
        sections.add(new Nodes.Stack("row." + CREDENTIAL, ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Text(CREDENTIAL, ControlState.READY, credentialLabel, Nodes.TextRole.BODY),
            new Nodes.Button(CHOOSE, state(true, vaultAuth), "Choose…", "", Nodes.ButtonKind.SECONDARY, CHOOSE, ""))));
        boolean keysEnabled = vaultAuth && vaultPresent;
        var keys = managedIds.stream().map(id -> new ListItem(id.toString(), Optional.empty(),
            List.<Nodes.Node>of(new Nodes.Text("key." + id, ControlState.READY, credentialName.apply(id).orElse("credential missing"), Nodes.TextRole.BODY)),
            List.of(), List.of())).toList();
        sections.add(new Nodes.Text(KEYS_LABEL, ControlState.READY, "Ordered Vault keys", Nodes.TextRole.LABEL));
        sections.add(new Nodes.ItemList(KEYS, state(true, keysEnabled), Nodes.ListKind.ORDERED, keys, Set.of(), "", List.of(), Nodes.SelectionMode.SINGLE,
            Optional.of(new Nodes.Button(ADD_KEY, state(true, keysEnabled), "Add stored key…", "add", Nodes.ButtonKind.GHOST, ADD_KEY, "")), OptionalInt.of(3)));
        var jumpOptions = new ArrayList<Option>();
        jumpOptions.add(new Option(NO_JUMP, "none", Optional.empty()));
        for (var other : jumps) jumpOptions.add(new Option(other.id().toString(), other.name(), Optional.empty()));
        sections.add(new Nodes.Choice(JUMP, ControlState.READY, Nodes.ChoiceKind.DROPDOWN, jumpOptions, jump, "Jump host", ""));
        sections.add(new Nodes.Text(MESSAGE, state(!message.isEmpty(), true), message, Nodes.TextRole.BODY));
        var actions = List.of(
            new Nodes.Button(CANCEL, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, ""),
            new Nodes.Button(SAVE, ControlState.READY, editing.isPresent() ? "Save" : "Add Host", "", Nodes.ButtonKind.SECONDARY, SAVE, ""));
        return new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.FULL, editing.isPresent() ? "Edit host" : "Add Host", Optional.empty(), sections, Optional.empty(), actions, CANCEL);
    }

    @Override public void close() { if (closed) return; closed = true; view.close(); }
}
