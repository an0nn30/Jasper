package dev.jasper.vault.ui;

import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.Option;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.model.Grant;
import dev.jasper.vault.model.SshKey;
import dev.jasper.vault.service.UnlockPrompt;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Searchable, single-list manager on one host-rendered kit view. Rows are keyed by entry id; only non-secret row
 * metadata is retained. Enter in the list edits the selected entry; elsewhere it presses Okay (the former default
 * button), and while an unlock prompt shows, Enter in its field unlocks. UI thread only.
 */
final class ManagerPanel implements AutoCloseable {
    static final String ROOT = "manager", SEARCH = "search", KIND = "kind", ADD = "add", EDIT = "edit", DELETE = "delete",
        COPY_PUBLIC = "copy-public", LOCK = "lock", MORE = "more", ROWS = "rows", EMPTY = "empty", LOCKED = "locked", UNLOCK = "unlock",
        GRANTS = "grants", BACK = "back", REVOKE = "revoke", STATUS = "status", CANCEL = "cancel", OKAY = "okay", CLOSE = "close",
        ADD_GENERATE = "add.generate", MORE_GRANTS = "more.grants", MORE_PASSWORD = "more.password", UNLOCK_PREFIX = "unlock.";
    static final String ALL_TYPES = "all";
    /** Full rows the former list showed in the 800×600 window (11.1 of its 42-pixel rows), measured at baseline f16ae32f. */
    static final int VISIBLE_ROWS = 11;
    private enum Card { ENTRIES, GRANTS }

    private final Function<UUID, Optional<Object>> entry;
    private final Consumer<VaultManager.Type> onAdd;
    private final Consumer<UUID> onEdit, onDelete, onCopy;
    private final Consumer<Grant> onRevoke;
    private final Runnable onGenerate, onChangePassword, onLock, onUnlock, onClose;
    private final UiView view;
    private List<VaultManager.Row> allRows = List.of(), visible = List.of();
    private List<Grant> grants = List.of();
    private boolean unlocked, closed;
    private Card card = Card.ENTRIES;
    private String query = "", kind = ALL_TYPES, status = "";
    private UUID selection;
    private int grantSelection = -1;
    private PasswordPanel unlock;

    ManagerPanel(UiKit kit, Function<UUID, Optional<Object>> entry, Consumer<VaultManager.Type> onAdd,
                 Consumer<UUID> onEdit, Consumer<UUID> onDelete, Consumer<UUID> onCopy,
                 Consumer<Grant> onRevoke, Runnable onGenerate, Runnable onChangePassword,
                 Runnable onLock, Runnable onUnlock, Runnable onClose) {
        this.entry = entry; this.onAdd = onAdd; this.onEdit = onEdit; this.onDelete = onDelete; this.onCopy = onCopy;
        this.onRevoke = onRevoke; this.onGenerate = onGenerate; this.onChangePassword = onChangePassword;
        this.onLock = onLock; this.onUnlock = onUnlock; this.onClose = onClose;
        view = kit.create(new ViewSpec(node()), this::handle);
    }

    UiView view() { return view; }
    Optional<UUID> selected() { return Optional.ofNullable(selection); }
    List<VaultManager.Row> visibleRows() { return visible; }
    PasswordPanel unlockForm() { return unlock; }

    void refresh(List<VaultManager.Row> nextRows, List<Grant> nextGrants, String text, boolean unlocked) {
        if (closed) return;
        boolean stateChanged = this.unlocked != unlocked; this.unlocked = unlocked;
        allRows = unlocked ? List.copyOf(nextRows) : List.of();
        grants = unlocked ? List.copyOf(nextGrants) : List.of();
        if (grantSelection >= grants.size()) grantSelection = -1;
        status = text;
        if (stateChanged || !unlocked) card = Card.ENTRIES;
        // An unlocked vault has no unlock form: erase it while its fields are still in the view, then drop it.
        if (unlocked && unlock != null) { unlock.close(); unlock = null; }
        filter(); render();
    }

    /** Palette navigation reveals the requested row even if a filter hid it. */
    void select(UUID id) {
        if (closed || allRows.stream().noneMatch(row -> row.id().equals(id))) return;
        if (visibleRows().stream().noneMatch(row -> row.id().equals(id))) { query = ""; kind = ALL_TYPES; }
        selection = id; card = Card.ENTRIES;
        filter(); render();
    }

    /** Routes the service's shared unlock prompt into this window's locked area. */
    PasswordPanel showUnlock(UnlockPrompt prompt) {
        if (unlock != null) unlock.close();
        unlock = PasswordPanel.unlockIn(new PasswordPanel.Host() {
            @Override public UiView view() { return view; }
            @Override public void render() { ManagerPanel.this.render(); }
            @Override public boolean open() { return !closed; }
        }, UNLOCK_PREFIX, prompt);
        render();
        if (!closed) view.focus(unlock.password);
        return unlock;
    }

    /** Back to the passive "Vault locked" state; the embedded form's fields are erased. */
    void passiveUnlock() {
        if (unlock == null) return;
        unlock.close(); unlock = null;
        render();
    }

    String grantLabel(Grant grant) {
        String name = allRows.stream().filter(row -> row.id().equals(grant.credentialId())).map(VaultManager.Row::name).findFirst()
            .orElse(grant.credentialId().toString());
        return grant.pluginId() + " — " + name;
    }

    private void handle(UiEvent event) {
        if (closed) return;
        if (unlock != null && unlock.handle(event)) return;
        switch (event) {
            case UiEvent.Edited edited when edited.id().equals(SEARCH) -> { query = edited.text(); filter(); render(); }
            case UiEvent.Selected chosen when chosen.id().equals(KIND) && !chosen.ids().isEmpty() -> {
                kind = chosen.ids().iterator().next(); filter(); render();
            }
            case UiEvent.Selected chosen when chosen.id().equals(ROWS) -> {
                selection = chosen.ids().stream().findFirst().map(UUID::fromString).orElse(null); render();
            }
            case UiEvent.Selected chosen when chosen.id().equals(GRANTS) -> {
                grantSelection = chosen.ids().stream().findFirst().map(id -> Integer.parseInt(id.substring("grant-".length()))).orElse(-1);
                render();
            }
            case UiEvent.Action action -> act(action.id());
            default -> { }
        }
    }

    private void act(String id) {
        switch (id) {
            case EDIT -> selected().ifPresent(onEdit);
            case DELETE -> selected().ifPresent(onDelete);
            case COPY_PUBLIC -> selected().ifPresent(onCopy);
            case LOCK -> onLock.run();
            case UNLOCK -> onUnlock.run();
            case ADD_GENERATE -> onGenerate.run();
            case MORE_GRANTS -> { if (unlocked) { card = Card.GRANTS; render(); } }
            case MORE_PASSWORD -> onChangePassword.run();
            case BACK -> { card = Card.ENTRIES; render(); }
            case REVOKE -> { if (grantSelection >= 0 && grantSelection < grants.size()) onRevoke.accept(grants.get(grantSelection)); }
            case CLOSE -> onClose.run();
            default -> {
                for (var type : VaultManager.Type.values()) if (id.equals("add." + type.name())) onAdd.accept(type);
            }
        }
    }

    private void filter() {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        visible = allRows.stream().filter(row -> kind.equals(ALL_TYPES) || row.type().name().equals(kind))
            .filter(row -> (row.name() + " " + row.subtitle()).toLowerCase(Locale.ROOT).contains(needle))
            .sorted(Comparator.comparing(VaultManager.Row::name, String.CASE_INSENSITIVE_ORDER)).toList();
        if (selection != null && visible.stream().noneMatch(row -> row.id().equals(selection))) selection = null;
    }

    private void render() { if (!closed) view.update(new ViewSpec(node())); }

    private String subtitle(VaultManager.Row row) {
        return entry.apply(row.id()).map(value -> switch (value) {
            case Account a -> a.username() + " · " + (a.auth() instanceof Auth.Password ? "password" : a.auth() instanceof Auth.Key ? "SSH key" : "SSH key and password");
            case dev.jasper.vault.model.ManagedSshKey k -> "Stored in Vault · " + k.fingerprint();
            case SshKey k -> k.algorithm() + " · " + k.fingerprint();
            default -> row.subtitle();
        }).orElse(row.subtitle());
    }

    private static Nodes.Button tool(String id, String icon, String tooltip, boolean enabled) {
        return new Nodes.Button(id, new ControlState(true, enabled, false, ""), "", icon, Nodes.ButtonKind.GHOST, id, tooltip);
    }

    private static MenuItem item(String id, String label) { return new MenuItem(id, label, "", id, true, false); }

    private List<Nodes.Node> entries() {
        var selectedRow = visible.stream().filter(row -> row.id().equals(selection)).findFirst().orElse(null);
        var options = new ArrayList<Option>();
        options.add(new Option(ALL_TYPES, "All Types", Optional.empty()));
        for (var type : VaultManager.Type.values()) options.add(new Option(type.name(), type.toString(), Optional.empty()));
        var adds = new ArrayList<MenuItem>();
        for (var type : VaultManager.Type.values()) adds.add(item("add." + type.name(), type.toString()));
        adds.add(new MenuItem("add.separator", "", "", "", true, true));
        adds.add(item(ADD_GENERATE, "Generate SSH Key..."));
        var toolbar = new Nodes.Stack("toolbar", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Field(SEARCH, ControlState.READY, Nodes.FieldKind.SEARCH, query, "Search names, usernames and key fingerprints", "Search vault", ""),
            new Nodes.Choice(KIND, ControlState.READY, Nodes.ChoiceKind.DROPDOWN, options, kind, "Filter credential type", ""),
            new Nodes.MenuButton(ADD, ControlState.READY, "", "add", Nodes.ButtonKind.GHOST, "", adds, "Add entry"),
            tool(EDIT, "edit", "Edit entry", selectedRow != null),
            tool(DELETE, "delete", "Delete entry", selectedRow != null),
            tool(COPY_PUBLIC, "copy", "Copy public key", selectedRow != null && selectedRow.type() == VaultManager.Type.SSH_KEY),
            tool(LOCK, "lock", "Lock vault", true),
            new Nodes.MenuButton(MORE, ControlState.READY, "", "more", Nodes.ButtonKind.GHOST, "",
                List.of(item(MORE_GRANTS, "Saved plugin grants..."), item(MORE_PASSWORD, "Change Master Password...")), "More vault actions")));
        if (visible.isEmpty()) {
            String title = allRows.isEmpty() ? "Nothing to show" : "No matching entries";
            return List.of(toolbar, new Nodes.Empty(EMPTY, ControlState.READY,
                new Nodes.Icon(EMPTY + ".icon", ControlState.READY, "search", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, title), title, "", List.of()));
        }
        var items = visible.stream().map(row -> {
            String id = row.id().toString();
            return new ListItem(id, Optional.empty(),
                List.<Nodes.Node>of(new Nodes.Text("row." + id + ".name", ControlState.READY, row.name(), Nodes.TextRole.BODY)),
                List.<Nodes.Node>of(new Nodes.Text("row." + id + ".detail", ControlState.READY, subtitle(row), Nodes.TextRole.DESCRIPTION)),
                List.<Nodes.Node>of(new Nodes.Text("row." + id + ".type", ControlState.READY, row.type().toString(), Nodes.TextRole.CAPTION)));
        }).toList();
        return List.of(toolbar, new Nodes.ItemList(ROWS, ControlState.READY, Nodes.ListKind.DOUBLE, items,
            selectedRow == null ? Set.of() : Set.of(selectedRow.id().toString()), EDIT, List.of(), Nodes.SelectionMode.SINGLE, Optional.empty(),
            OptionalInt.of(VISIBLE_ROWS)));
    }

    private List<Nodes.Node> grantsCard() {
        var items = new ArrayList<ListItem>();
        for (int i = 0; i < grants.size(); i++) {
            String id = "grant-" + i;
            items.add(new ListItem(id, Optional.empty(), List.<Nodes.Node>of(new Nodes.Text(id + ".label", ControlState.READY, grantLabel(grants.get(i)), Nodes.TextRole.BODY)),
                List.of(), List.of()));
        }
        return List.of(new Nodes.Text("grants.title", ControlState.READY, "Saved plugin grants", Nodes.TextRole.BODY),
            new Nodes.ItemList(GRANTS, ControlState.READY, Nodes.ListKind.SINGLE, items, grantSelection < 0 ? Set.of() : Set.of("grant-" + grantSelection), "", List.of()),
            new Nodes.Stack("grants.actions", ControlState.READY, Nodes.LayoutRole.ACTIONS, List.of(
                new Nodes.Button(BACK, ControlState.READY, "Back to entries", "", Nodes.ButtonKind.SECONDARY, BACK, ""),
                new Nodes.Button(REVOKE, new ControlState(true, grantSelection >= 0, false, ""), "Revoke", "", Nodes.ButtonKind.SECONDARY, REVOKE, ""))));
    }

    private List<Nodes.Node> lockedCard() {
        if (unlock != null) return List.of(unlock.node());
        return List.of(new Nodes.Empty(LOCKED, ControlState.READY,
            new Nodes.Icon(LOCKED + ".icon", ControlState.READY, "lock", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "Vault locked"), "Vault locked", "",
            List.of(new Nodes.Button(UNLOCK, ControlState.READY, "Unlock", "unlock", Nodes.ButtonKind.SECONDARY, UNLOCK, ""))));
    }

    private Nodes.Form node() {
        List<Nodes.Node> sections = !unlocked ? lockedCard() : card == Card.GRANTS ? grantsCard() : entries();
        var actions = List.of(
            new Nodes.Button(CANCEL, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CLOSE, ""),
            // While the unlock form shows, its Unlock is the one primary of the window, as its default button was.
            new Nodes.Button(OKAY, ControlState.READY, "Okay", "", unlock != null ? Nodes.ButtonKind.SECONDARY : Nodes.ButtonKind.PRIMARY, CLOSE, ""));
        return new Nodes.Form(ROOT, ControlState.READY, Nodes.FormKind.FULL, "Credential Vault", Optional.empty(), sections,
            Optional.of(new Nodes.Text(STATUS, ControlState.READY, status, Nodes.TextRole.CAPTION)), actions, CLOSE);
    }

    /** The window closed (its surface already disposed the view and erased its storage) or is closing. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        allRows = List.of(); visible = List.of(); grants = List.of(); selection = null;
        if (unlock != null) { unlock.close(); unlock = null; }
        view.close();
    }
}
