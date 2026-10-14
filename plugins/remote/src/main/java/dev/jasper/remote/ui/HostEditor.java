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
import dev.jasper.vault.api.CredentialPickRequest;
import dev.jasper.vault.api.CredentialPickResult;
import dev.jasper.vault.api.CredentialPresentation;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.UiAnchor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Add or edit one host on a host-rendered kit form. Validation is inline; Save hands back a {@link RemoteHost} and the
 * dialog closes it. Add and Connect, offered when adding with a connect callback, saves the same way and then hands the
 * saved host to that callback. The visual primary opts out of submission, so Enter saves nothing (the former dialog had
 * no default button); Escape cancels. Changing the authentication keeps the chosen credential and keys until Save. A
 * Vault choice completing after {@link #close()} is ignored. UI thread only.
 */
public final class HostEditor implements AutoCloseable {
    public static final String FORM = "host", NAME = "host.name", HOSTNAME = "host.hostname", PORT = "host.port", USERNAME = "host.username",
        GROUP = "host.group", GROUP_HINT = "host.group.hint", FAVORITE = "host.favorite", FOLLOW = "host.follow", AUTH = "host.auth",
        CREDENTIAL = "host.credential", CHOOSE = "host.choose", KEYS_LABEL = "host.keys.label", KEYS = "host.keys", ADD_KEY = "host.keys.add",
        JUMP = "host.jump", MESSAGE = "host.message", SAVE = "host.save", SAVE_CONNECT = "host.save-connect", CANCEL = "host.cancel";
    public static final String VAULT = "vault", AGENT = "agent", NO_JUMP = "none";

    private final List<RemoteHost> others;
    private final Optional<RemoteHost> editing;
    private final boolean vaultPresent;
    private final Function<UUID, Optional<String>> credentialName;
    private final Function<UUID, Optional<CredentialDescriptor>> metadata;
    private final Executor ui;
    private final Function<CredentialPickRequest, CompletableFuture<CredentialPickResult>> pick;
    private final Supplier<CompletableFuture<Optional<CredentialDescriptor>>> legacyPick;
    private final Function<RemoteHost, CompletableFuture<Void>> persist;
    private final Runnable onSaved;
    /** Connects to a host Add and Connect saved; null when the form offers no Add and Connect. */
    private final Consumer<RemoteHost> onConnect;
    private final java.util.Map<UUID, CredentialDescriptor> chosenMetadata = new java.util.HashMap<>();
    private PickOperation pending;
    private long pickerRevision;
    private Object saving;
    /** Whether the save in progress came from Add and Connect. */
    private boolean connectAfterSave;
    private UUID selectedKey;
    private static final class PickOperation {
        final boolean append;
        final long revision;
        UiAnchor anchor;
        Subscription invalidation;
        CompletableFuture<?> future;
        PickOperation(boolean append, long revision) { this.append = append; this.revision = revision; }
    }
    private final List<RemoteHost> jumps;
    private final List<String> groups;
    private final UiView view;
    private String name = "", hostname = "", port = "22", username = "", group = "", jump = NO_JUMP, message = "", credentialLabel = "none chosen", defaultGroup = HostRows.OTHER;
    private boolean favorite, followDirectory = true, vaultAuth, closed;
    private UUID credentialId;
    private final List<UUID> managedIds = new ArrayList<>();

    /** Compatibility constructor for embedders using the legacy modal producer and synchronous persistence. */
    public HostEditor(UiKit kit, Executor ui, List<RemoteHost> others, Optional<RemoteHost> editing, boolean vaultPresent,
                      Function<UUID, Optional<String>> names, Supplier<CompletableFuture<Optional<CredentialDescriptor>>> pick,
                      Consumer<RemoteHost> onSave, Runnable onCancel) {
        this(kit, ui, others, editing, vaultPresent, names, id -> Optional.empty(), null, pick,
            host -> { onSave.accept(host); return CompletableFuture.completedFuture(null); }, () -> {}, onCancel, null);
    }

    /** Mounted default-density picker and real persistence. Completion may arrive on any thread. */
    public HostEditor(UiKit kit, Executor ui, List<RemoteHost> others, Optional<RemoteHost> editing, boolean vaultPresent,
                      Function<UUID, Optional<CredentialDescriptor>> metadata,
                      Function<CredentialPickRequest, CompletableFuture<CredentialPickResult>> pick,
                      Function<RemoteHost, CompletableFuture<Void>> persist, Runnable onSaved, Runnable onCancel) {
        this(kit, ui, others, editing, vaultPresent, metadata, pick, persist, onSaved, onCancel, null);
    }

    /**
     * As the mounted form, with Add and Connect beside Add Host when adding: after {@code onSaved}, {@code connect}
     * receives the saved host. Null offers no Add and Connect.
     */
    public HostEditor(UiKit kit, Executor ui, List<RemoteHost> others, Optional<RemoteHost> editing, boolean vaultPresent,
                      Function<UUID, Optional<CredentialDescriptor>> metadata,
                      Function<CredentialPickRequest, CompletableFuture<CredentialPickResult>> pick,
                      Function<RemoteHost, CompletableFuture<Void>> persist, Runnable onSaved, Runnable onCancel,
                      Consumer<RemoteHost> connect) {
        this(kit, ui, others, editing, vaultPresent, vaultPresent ? id -> metadata.apply(id).map(CredentialDescriptor::name) : id -> Optional.empty(), metadata,
            pick, null, persist, onSaved, onCancel, connect);
    }

    private HostEditor(UiKit kit, Executor ui, List<RemoteHost> others, Optional<RemoteHost> editing, boolean vaultPresent,
                       Function<UUID, Optional<String>> names, Function<UUID, Optional<CredentialDescriptor>> metadata,
                       Function<CredentialPickRequest, CompletableFuture<CredentialPickResult>> pick,
                       Supplier<CompletableFuture<Optional<CredentialDescriptor>>> legacyPick,
                       Function<RemoteHost, CompletableFuture<Void>> persist, Runnable onSaved, Runnable onCancel,
                       Consumer<RemoteHost> connect) {
        this.others = List.copyOf(others); this.editing = editing; this.vaultPresent = vaultPresent; this.credentialName = names;
        this.metadata = metadata; this.ui = ui; this.pick = pick; this.legacyPick = legacyPick;
        this.persist = persist; this.onSaved = onSaved; this.onConnect = editing.isEmpty() ? connect : null;
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
            if (saving != null && !(event instanceof UiEvent.Action a && a.id().equals(CANCEL))) return;
            switch (event) {
                case UiEvent.Edited edited -> edited(edited.id(), edited.text());
                case UiEvent.Toggled toggled when toggled.id().equals(FAVORITE) -> { favorite = toggled.checked(); render(); }
                case UiEvent.Toggled toggled when toggled.id().equals(FOLLOW) -> { followDirectory = toggled.checked(); render(); }
                case UiEvent.Selected selected when selected.id().equals(AUTH) && !selected.ids().isEmpty() -> {
                    // Without the Vault its option shows but cannot be chosen, as the disabled radio button could not.
                    String chosen = selected.ids().iterator().next();
                    boolean next = chosen.equals(VAULT) && vaultPresent || !chosen.equals(AGENT) && vaultAuth;
                    if (next != vaultAuth) { vaultAuth = next; cancelPick(); message = ""; }
                    render();
                }
                case UiEvent.Selected selected when selected.id().equals(JUMP) && !selected.ids().isEmpty() -> { jump = selected.ids().iterator().next(); render(); }
                case UiEvent.Selected selected when selected.id().equals(KEYS) -> {
                    selectedKey = managedIds.stream().filter(id -> selected.ids().contains(id.toString())).findFirst().orElse(null); render();
                }
                case UiEvent.Reordered moved when moved.id().equals(KEYS) -> move(UUID.fromString(moved.itemId()), moved.destination());
                case UiEvent.Removed removed when removed.id().equals(KEYS) -> remove(UUID.fromString(removed.itemId()));
                case UiEvent.Action action when action.id().equals(CHOOSE) -> pick(false);
                case UiEvent.Action action when action.id().equals(ADD_KEY) -> pick(true);
                case UiEvent.Action action when action.id().equals(SAVE) -> save(false);
                case UiEvent.Action action when action.id().equals(SAVE_CONNECT) && onConnect != null -> save(true);
                case UiEvent.Action action when action.id().equals(CANCEL) -> { cancelPick(); onCancel.run(); }
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
        message = ""; render();
    }

    private void pick(boolean append) {
        if (!vaultAuth || !vaultPresent || saving != null || closed) return;
        // Publish the successor before cancellation: withdrawal can synchronously reenter this editor.
        var operation = new PickOperation(append, ++pickerRevision);
        var predecessor = pending; pending = operation; release(predecessor, true);
        if (closed || pending != operation) return;
        message = "";
        try {
            if (legacyPick != null) {
                var legacy = java.util.Objects.requireNonNull(legacyPick.get());
                if (pending != operation || closed) { legacy.cancel(false); return; }
                operation.future = legacy;
                legacy.whenComplete((result, failure) -> ui.execute(() -> finishPick(operation,
                    result == null || result.isEmpty() ? null : result.get(), false, failure)));
            } else {
                operation.anchor = view.anchor(append ? ADD_KEY : CHOOSE);
                operation.invalidation = operation.anchor.onClosed(() -> { if (pending == operation) cancelPick(); });
                if (pending != operation || closed) { release(operation, true); return; }
                var result = java.util.Objects.requireNonNull(pick.apply(new CredentialPickRequest(operation.anchor,
                    append ? Optional.empty() : Optional.ofNullable(credentialId), !append)));
                if (pending != operation || closed) { result.cancel(false); return; }
                operation.future = result;
                result.whenComplete((value, failure) -> ui.execute(() -> finishPick(operation,
                    value instanceof CredentialPickResult.Chosen chosen ? chosen.credential() : null,
                    value instanceof CredentialPickResult.Cleared, failure)));
            }
        } catch (RuntimeException failure) { finishPick(operation, null, false, failure); }
    }

    private void finishPick(PickOperation operation, CredentialDescriptor chosen, boolean clear, Throwable failure) {
        if (closed || pending != operation) return;
        pending = null; release(operation, false);
        if (closed || pickerRevision != operation.revision) return;
        if (failure != null) {
            if (!(failure instanceof java.util.concurrent.CancellationException)) showError(failureMessage(failure));
        } else if (chosen != null) {
            if (operation.append) addKey(chosen); else chose(chosen);
        } else if (clear && !operation.append) {
            credentialId = null; credentialLabel = managedIds.isEmpty() ? "none chosen" : managedIds.size() + " Vault key(s)"; render();
        }
    }

    private void cancelPick() { pickerRevision++; var operation = pending; pending = null; release(operation, true); }
    private static void release(PickOperation operation, boolean cancel) {
        if (operation == null) return;
        if (operation.invalidation != null) operation.invalidation.close();
        if (cancel && operation.future != null) operation.future.cancel(false);
        if (operation.anchor != null) operation.anchor.close();
    }

    private void chose(CredentialDescriptor descriptor) {
        if (closed) return;
        chosenMetadata.put(descriptor.id(), descriptor); message = ""; selectedKey = null; managedIds.clear(); credentialId = descriptor.id(); credentialLabel = descriptor.name(); render();
    }

    private void addKey(CredentialDescriptor descriptor) {
        if (closed) return;
        if (descriptor.kind() != dev.jasper.vault.api.Kind.SSH_KEY || !descriptor.managedKey()) { message = "Choose a managed SSH key stored in Vault"; render(); return; }
        chosenMetadata.put(descriptor.id(), descriptor); message = ""; credentialId = null; selectedKey = descriptor.id();
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
        int index = managedIds.indexOf(id);
        if (index < 0) return;
        managedIds.remove(index);
        if (id.equals(selectedKey)) selectedKey = managedIds.isEmpty() ? null : managedIds.get(Math.min(index, managedIds.size()-1));
        if (managedIds.isEmpty()) credentialLabel = "none chosen";
        keysChanged();
    }

    private void keysChanged() {
        if (!managedIds.isEmpty()) credentialLabel = managedIds.size() + " Vault key(s)";
        render();
    }

    private RemoteHost draft() {
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
        return host.withFavorite(favorite).withFollowDirectory(followDirectory);
    }

    private boolean ready() { try { draft(); return true; } catch (IllegalArgumentException invalid) { return false; } }

    private void save(boolean connect) {
        if (saving != null || closed) return;
        final RemoteHost host;
        try { host = draft(); } catch (IllegalArgumentException invalid) { showError(invalid.getMessage()); return; }
        Object operation = new Object(); saving = operation; connectAfterSave = connect; cancelPick();
        if (closed || saving != operation) return;
        message = ""; render();
        try {
            java.util.Objects.requireNonNull(persist.apply(host)).whenComplete((ignored, failure) -> ui.execute(() -> {
                if (closed || saving != operation) return;
                saving = null;
                if (failure == null) { render(); onSaved.run(); if (connect) onConnect.accept(host); }
                else showError(failureMessage(failure));
            }));
        } catch (RuntimeException failure) {
            if (!closed && saving == operation) { saving = null; showError(failureMessage(failure)); }
        }
    }

    private static String failureMessage(Throwable failure) {
        while ((failure instanceof java.util.concurrent.CompletionException || failure instanceof java.util.concurrent.ExecutionException) && failure.getCause() != null) failure = failure.getCause();
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private void render() { if (!closed) view.update(new ViewSpec(form())); }

    private static ControlState state(boolean visible, boolean enabled) { return new ControlState(visible, enabled, false, ""); }

    private Optional<CredentialDescriptor> descriptor(UUID id) {
        if (!vaultPresent || id == null) return Optional.empty();
        return Optional.ofNullable(chosenMetadata.get(id)).or(() -> metadata.apply(id));
    }

    private Nodes.Field field(String id, Nodes.FieldKind kind, String value, String hint, String label) {
        return new Nodes.Field(id, state(true, saving == null), kind, value, hint, label, "", Optional.empty(), id.equals(HOSTNAME) ? Nodes.FieldPresentation.TECHNICAL : Nodes.FieldPresentation.STANDARD);
    }
    private Nodes.FormRow row(String id, String label, String caption, Nodes.Node control) {
        return new Nodes.FormRow(id, ControlState.READY, label, caption, control, Optional.empty());
    }
    private Nodes.Section section(String id, String title, Nodes.CardKind kind, List<Nodes.Node> children) {
        return new Nodes.Section(id, ControlState.READY, title, new Nodes.Card(id + ".card", ControlState.READY, kind, children));
    }

    private ListItem keyItem(UUID id) {
        String label = credentialName.apply(id).orElse("credential missing"), caption = "", tooltip = "";
        if (vaultPresent) {
            var value = descriptor(id);
            if (value.isPresent()) {
                var entry = value.get(); label = entry.name();
                caption = entry.managedKey() ? "Stored key" : CredentialPresentation.caption(entry);
                tooltip = CredentialPresentation.caption(entry);
            }
        }
        var title = new ArrayList<Nodes.Node>();
        title.add(new Nodes.Text("key." + id, ControlState.READY, label, Nodes.TextRole.MONO));
        if (!caption.isEmpty()) title.add(new Nodes.Text("key.caption." + id, ControlState.READY, caption, Nodes.TextRole.CAPTION));
        return new ListItem(id.toString(), Optional.of(new Nodes.Icon("key.icon." + id, ControlState.READY, "key", Nodes.Hue.NONE, Nodes.IconSize.CAPTION, "SSH key")),
            title, List.of(), List.of(), false, Optional.empty(), tooltip);
    }

    private Nodes.Form form() {
        boolean editable = saving == null;
        var jumpOptions = new ArrayList<Option>();
        jumpOptions.add(new Option(NO_JUMP, "None", Optional.empty()));
        for (var other : jumps) jumpOptions.add(new Option(other.id().toString(), other.name(), Optional.empty()));
        var connection = section("host.connection", "Connection", Nodes.CardKind.FORM, List.of(
            row("row." + NAME, "Name", "", field(NAME, Nodes.FieldKind.TEXT, name, "e.g. homelab", "Name")),
            new Nodes.FormRow("row." + HOSTNAME, ControlState.READY, "Host", "", field(HOSTNAME, Nodes.FieldKind.TEXT, hostname, "hostname or IP address", "Host"),
                Optional.of(field(PORT, Nodes.FieldKind.PORT, port, "", "Port"))),
            row("row." + USERNAME, "Username", "", field(USERNAME, Nodes.FieldKind.TEXT, username, "root", "Username")),
            row("row." + JUMP, "Jump Host", "", new Nodes.Choice(JUMP, state(true, editable), Nodes.ChoiceKind.DROPDOWN, jumpOptions, jump, "Jump Host", ""))));
        boolean keysEnabled = vaultAuth && vaultPresent && editable;
        var keys = managedIds.stream().map(this::keyItem).toList();
        // Do not link Vault-only presentation when the optional plugin is absent.
        var face = vaultPresent ? CredentialPresentation.face(descriptor(credentialId).orElse(null), Nodes.PickerDensity.DEFAULT)
            : new Nodes.PickerFace("", "", "lock", Nodes.Hue.NONE, Nodes.PickerDensity.DEFAULT);
        if (credentialId != null && face.primary().isEmpty()) face = new Nodes.PickerFace(credentialName.apply(credentialId).orElse("credential missing"), "", "lock", Nodes.Hue.NONE, Nodes.PickerDensity.DEFAULT);
        var authentication = section("host.authentication", "Authentication", Nodes.CardKind.MIXED, List.of(
            new Nodes.Choice(AUTH, state(true, editable), Nodes.ChoiceKind.SEGMENTED, List.of(
                new Option(VAULT, vaultPresent ? "Vault Credential" : "Vault credential (needs Credential Vault)", Optional.empty()),
                new Option(AGENT, "SSH Agent", Optional.empty())), vaultAuth ? VAULT : AGENT, "Authentication", ""),
            row("row." + CREDENTIAL, "Credential", "", new Nodes.Choice(CHOOSE, state(true, keysEnabled), Nodes.ChoiceKind.PICKER, List.of(), "", "Choose…", CHOOSE, "lock", Optional.empty(), Optional.of(face))),
            row(KEYS_LABEL, "Keys", "Tried in order", new Nodes.ItemList(KEYS, state(true, keysEnabled), Nodes.ListKind.ORDERED, keys,
                selectedKey == null ? Set.of() : Set.of(selectedKey.toString()), "", List.of(), Nodes.SelectionMode.SINGLE,
                Optional.of(new Nodes.Button(ADD_KEY, state(true, keysEnabled), "Add Stored Key…", "add", Nodes.ButtonKind.LINK, ADD_KEY, "")), OptionalInt.of(Math.max(2, Math.min(3, keys.size())))))));
        var options = section("host.options", "Options", Nodes.CardKind.SETTINGS, List.of(
            new Nodes.SettingRow("row." + GROUP, ControlState.READY, "Group", "",
                new Nodes.Choice(GROUP, state(true, editable), Nodes.ChoiceKind.SEARCHABLE,
                    groups.stream().map(g -> new Option("group." + g, g, Optional.empty())).toList(), "", "Group. Blank: " + defaultGroup + " (default)", "", "",
                    Optional.of(new Nodes.ChoiceSearch(0, "No other groups", Optional.of(group), defaultGroup))), false),
            new Nodes.SettingRow("row." + FAVORITE, ControlState.READY, "Add to Favorites", "",
                new Nodes.Toggle(FAVORITE, state(true, editable), "Favorite", favorite, FAVORITE, false), false),
            new Nodes.SettingRow("row." + FOLLOW, ControlState.READY, "Track shell folder", "SFTP follows the terminal's current directory",
                new Nodes.Toggle(FOLLOW, state(true, editable), "Track shell folder for SFTP follow", followDirectory, FOLLOW, false), false)));
        boolean savingPlain = !editable && !connectAfterSave, savingConnect = !editable && connectAfterSave;
        var actions = new ArrayList<Nodes.Button>();
        actions.add(new Nodes.Button(CANCEL, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, ""));
        if (onConnect != null) actions.add(new Nodes.Button(SAVE_CONNECT, new ControlState(true, editable && ready(), savingConnect, ""),
            savingConnect ? "Saving…" : "Add and Connect", "", Nodes.ButtonKind.SECONDARY, SAVE_CONNECT, ""));
        actions.add(new Nodes.Button(SAVE, new ControlState(true, editable && ready(), savingPlain, ""), savingPlain ? "Saving…" : editing.isPresent() ? "Save" : "Add Host", "", Nodes.ButtonKind.PRIMARY, SAVE, ""));
        String command = "ssh " + (username.isBlank() ? "user" : username.strip()) + "@" + (hostname.isBlank() ? "host" : hostname.strip());
        if (!port.strip().equals("22")) command += " -p " + port.strip();
        Nodes.Node preview = message.isEmpty() ? new Nodes.Text("host.preview", ControlState.READY, command, Nodes.TextRole.MONO_CAPTION)
            : new Nodes.Text(MESSAGE, ControlState.READY, message, Nodes.TextRole.BODY, Optional.of(Nodes.Severity.DANGER), OptionalInt.empty());
        return new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.FULL, editing.isPresent() ? "Edit host" : "Add Host", Optional.empty(),
            List.of(connection, authentication, options), Optional.of(preview), actions, CANCEL, Nodes.FormSubmission.NONE);
    }

    @Override public void close() { if (closed) return; closed = true; saving = null; cancelPick(); view.close(); }
}
