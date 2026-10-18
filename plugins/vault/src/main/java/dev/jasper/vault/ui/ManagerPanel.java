package dev.jasper.vault.ui;

import dev.jasper.sdk.ui.Column;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.Option;
import dev.jasper.sdk.ui.TableRow;
import dev.jasper.sdk.ui.TreeItem;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.model.Grant;
import dev.jasper.vault.model.SshKey;
import dev.jasper.vault.model.Note;
import dev.jasper.vault.model.ManagedSshKey;
import dev.jasper.vault.service.UnlockPrompt;
import dev.jasper.vault.workstation.Layout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Searchable category/list/detail manager on one host-rendered kit view. Rows are keyed by entry id; only non-secret row
 * metadata is retained. Enter in the list edits the selected entry; elsewhere it presses Done, and while an unlock prompt shows, Enter in its field unlocks. UI thread only.
 */
final class ManagerPanel implements AutoCloseable {
    static final String ROOT = "manager", SEARCH = "search", KIND = "kind", ADD = "add", EDIT = "edit", DELETE = "delete",
        COPY_PUBLIC = "copy-public", LOCK = "lock", ROWS = "rows", EMPTY = "empty", LOCKED = "locked", UNLOCK = "unlock",
        GRANTS = "grants", BACK = "back", REVOKE = "revoke", STATUS = "status", CANCEL = "cancel", OKAY = "okay", CLOSE = "close",
        ADD_GENERATE = "add.generate", UNLOCK_PREFIX = "unlock.", TOOLBAR = "toolbar";
    /** The Lock Vault action's default binding as the platform writes it (plan 10b Rulings 8). */
    static final String LOCK_SHORTCUT = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac") ? "\u21E7\u2318L" : "Ctrl+Shift+L";
    static final String ALL_TYPES = "all";
    /** The classic table's columns and footer (spec 14.8). */
    static final String COLUMN_NAME = "name", COLUMN_USERNAME = "username", COLUMN_TYPE = "type", COLUMN_MODIFIED = "modified",
        ITEMS_FOOTER = "items.footer";
    /** Full rows the former list showed in the 800×600 window (11.1 of its 42-pixel rows), measured at baseline f16ae32f. */
    static final int VISIBLE_ROWS = 11;
    private enum Card { ENTRIES, GRANTS }

    private final Function<UUID, Optional<Object>> entry;
    private final Consumer<VaultManager.Type> onAdd;
    private final Consumer<UUID> onEdit, onDelete, onCopy;
    private final Consumer<Grant> onRevoke;
    private final Runnable onGenerate, onLock, onUnlock, onClose;
    private final UiView view, titleActions;
    private final Layout layout;
    private List<VaultManager.Row> allRows = List.of(), visible = List.of();
    private List<Grant> grants = List.of();
    private boolean unlocked, closed;
    private Card card = Card.ENTRIES;
    private String query = "", kind = ALL_TYPES;
    /** The classic table's sort (a header click) and the tree items the user collapsed; both survive refreshes. */
    private String sortColumn = COLUMN_NAME;
    private boolean ascending = true;
    private final Set<String> collapsed = new HashSet<>();
    private VaultManagerWindow.Status status = new VaultManagerWindow.Status("", "", "");
    private UUID selection, shownSecret;
    private java.time.Instant shownSecretVersion;
    private int grantSelection = -1;
    private PasswordPanel unlock;
    private CopyCards copies;
    /** Where the detail pane's copy buttons copy; until set they do nothing. */
    void copies(CopyCards cards) { copies = cards; }

    ManagerPanel(UiKit kit, Function<UUID, Optional<Object>> entry, Consumer<VaultManager.Type> onAdd,
                 Consumer<UUID> onEdit, Consumer<UUID> onDelete, Consumer<UUID> onCopy,
                 Consumer<Grant> onRevoke, Runnable onGenerate,
                 Runnable onLock, Runnable onUnlock, Runnable onClose) {
        this(kit, Layout.ROWS, entry, onAdd, onEdit, onDelete, onCopy, onRevoke, onGenerate, onLock, onUnlock, onClose);
    }

    /** The manager in {@code layout}'s composition (Enterprise presentation spec 14.8), kept for its life. */
    ManagerPanel(UiKit kit, Layout layout, Function<UUID, Optional<Object>> entry, Consumer<VaultManager.Type> onAdd,
                 Consumer<UUID> onEdit, Consumer<UUID> onDelete, Consumer<UUID> onCopy,
                 Consumer<Grant> onRevoke, Runnable onGenerate,
                 Runnable onLock, Runnable onUnlock, Runnable onClose) {
        this.layout = layout;
        this.entry = entry; this.onAdd = onAdd; this.onEdit = onEdit; this.onDelete = onDelete; this.onCopy = onCopy;
        this.onRevoke = onRevoke; this.onGenerate = onGenerate;
        this.onLock = onLock; this.onUnlock = onUnlock; this.onClose = onClose;
        view = kit.create(new ViewSpec(node()), this::handle);
        titleActions = kit.create(new ViewSpec(actions()), this::handle);
    }

    UiView view() { return view; }
    Layout layout() { return layout; }
    UiView titleActions() { return titleActions; }
    Optional<UUID> selected() { return Optional.ofNullable(selection); }
    List<VaultManager.Row> visibleRows() { return visible; }
    PasswordPanel unlockForm() { return unlock; }

    void refresh(List<VaultManager.Row> nextRows, List<Grant> nextGrants, VaultManagerWindow.Status text, boolean unlocked) {
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
            case UiEvent.Sorted sorted when sorted.id().equals(ROWS) -> {
                if (sortColumn.equals(sorted.columnId())) ascending = !ascending; else { sortColumn = sorted.columnId(); ascending = true; }
                filter(); render();
            }
            case UiEvent.Expanded expanded when expanded.id().equals(KIND) -> {
                if (expanded.expanded()) collapsed.remove(expanded.itemId()); else collapsed.add(expanded.itemId());
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
            case "detail.username.copy", "detail.password.copy", "detail.note.copy" -> copyDetail(id);
            case LOCK -> onLock.run();
            case UNLOCK -> onUnlock.run();
            case ADD_GENERATE -> onGenerate.run();
            case BACK -> { card = Card.ENTRIES; render(); }
            case REVOKE -> { if (grantSelection >= 0 && grantSelection < grants.size()) onRevoke.accept(grants.get(grantSelection)); }
            case CLOSE -> onClose.run();
            default -> {
                for (var type : VaultManager.Type.values()) if (id.equals("add." + type.name())) onAdd.accept(type);
            }
        }
    }

    /** Shows the saved plugin grants (the Vault's "Plugin Grants" command); inert while locked. */
    void showGrants() { if (unlocked) { card = Card.GRANTS; render(); } }

    private void copyDetail(String action) {
        if (!unlocked || selection == null || copies == null) return;
        entry.apply(selection).ifPresent(value -> {
            if (value instanceof Account account) {
                if (action.equals("detail.username.copy")) copies.secret("username", account.name(), account.username());
                else if (action.equals("detail.password.copy")) {
                    char[] secret = password(account.auth());
                    if (secret != null) copies.secret("password", account.name(), new String(secret));
                }
            } else if (value instanceof Note note && action.equals("detail.note.copy")) copies.secret("note", note.name(), new String(note.text()));
        });
    }

    private static char[] password(Auth auth) {
        return switch (auth) { case Auth.Password p -> p.password(); case Auth.KeyAndPassword p -> p.password(); default -> null; };
    }
    private String secretId() { return "detail.secret." + selection; }

    private void filter() {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        visible = allRows.stream().filter(row -> kind.equals(ALL_TYPES) || row.type().name().equals(kind))
            .filter(row -> (row.name() + " " + row.subtitle()).toLowerCase(Locale.ROOT).contains(needle))
            .sorted(layout == Layout.CLASSIC ? classicOrder()
                : Comparator.comparing(VaultManager.Row::type).thenComparing(VaultManager.Row::name, String.CASE_INSENSITIVE_ORDER)).toList();
        if (selection != null && visible.stream().noneMatch(row -> row.id().equals(selection))) selection = null;
    }

    private void render() {
        if (closed) return;
        if (!unlocked || card != Card.ENTRIES || selection == null || !selection.equals(shownSecret)) { shownSecret=null; shownSecretVersion=null; }
        view.update(new ViewSpec(node())); titleActions.update(new ViewSpec(actions()));
        if (unlocked && card == Card.ENTRIES && selection != null) entry.apply(selection).ifPresent(value -> {
            char[] secret = value instanceof Account account ? password(account.auth()) : value instanceof Note note ? note.text() : null;
            java.time.Instant version = value instanceof Account account ? account.updated() : value instanceof Note note ? note.updated() : null;
            if (secret != null && (!selection.equals(shownSecret) || !java.util.Objects.equals(version, shownSecretVersion))) view.replaceSecret(secretId(), secret);
            shownSecret = secret == null ? null : selection; shownSecretVersion = version;
        });
    }

    private String subtitle(VaultManager.Row row) {
        return entry.apply(row.id()).map(value -> switch (value) {
            case Account a -> a.username() + " · " + (a.auth() instanceof Auth.Password ? "Password" : a.auth() instanceof Auth.Key ? "Private key" : "Private key and password");
            case dev.jasper.vault.model.ManagedSshKey k -> "Stored in Vault · " + k.fingerprint();
            case SshKey k -> k.algorithm() + " · " + (k.comment().isBlank() ? k.fingerprint() : k.comment());
            default -> row.subtitle();
        }).orElse(row.subtitle());
    }

    private static Nodes.Button tool(String id, String icon, String tooltip, boolean enabled) {
        return new Nodes.Button(id, new ControlState(true, enabled, false, ""), "", icon, Nodes.ButtonKind.GHOST, id, tooltip);
    }


    private Nodes.Stack actions() {
        var adds = new ArrayList<MenuItem>();
        for (var type : VaultManager.Type.values()) adds.add(new MenuItem("add." + type.name(), type.toString(), icon(type), "add." + type.name(), true, false, hue(type)));
        adds.add(new MenuItem("add.separator", "", "", "", true, true));
        adds.add(new MenuItem(ADD_GENERATE, "Generate SSH Key...", "sparkle", ADD_GENERATE, true, false, Nodes.Hue.PURPLE));
        var state = new ControlState(true, unlocked, false, "");
        return new Nodes.Stack("title-actions", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Button(LOCK, state, "", "lock", Nodes.ButtonKind.GHOST, LOCK, "Lock vault"),
            new Nodes.MenuButton(ADD, state, "", "add", Nodes.ButtonKind.GHOST, "", adds, "Add entry")));
    }

    private static String icon(VaultManager.Type type) { return switch (type) { case LOGIN -> "user"; case SSH_KEY -> "key"; case NOTE -> "note"; }; }
    private static Nodes.Hue hue(VaultManager.Type type) { return switch (type) { case LOGIN -> Nodes.Hue.BLUE; case SSH_KEY -> Nodes.Hue.GREEN; case NOTE -> Nodes.Hue.AMBER; }; }
    private static Nodes.Text text(String id, String value, Nodes.TextRole role) { return new Nodes.Text(id, ControlState.READY, value, role); }
    private static Nodes.Header hiddenHeader(String id, String title) {
        return new Nodes.Header(id, new ControlState(false, true, false, ""), title, OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of());
    }
    private Nodes.Node entries() {
        var categories = new ArrayList<ListItem>();
        categories.add(category(ALL_TYPES, "All Items", "menu", Nodes.Hue.NEUTRAL, allRows.size()));
        for (var type : VaultManager.Type.values()) categories.add(category(type.name(), switch(type) {
            case LOGIN -> "Logins"; case SSH_KEY -> "SSH Keys"; case NOTE -> "Secure Notes";
        }, icon(type), hue(type), (int)allRows.stream().filter(row -> row.type() == type).count()));
        var rail = new Nodes.Panel("categories", ControlState.READY, hiddenHeader("categories.header", "Categories"), Optional.empty(),
            new Nodes.ItemList(KIND, ControlState.READY, Nodes.ListKind.SINGLE, categories, Set.of(kind), "", List.of()), Optional.empty());
        Nodes.Node list;
        if (visible.isEmpty()) {
            String title = allRows.isEmpty() ? "Nothing to show" : "No matching entries";
            list = new Nodes.Empty(EMPTY, ControlState.READY,
                new Nodes.Icon(EMPTY + ".icon", ControlState.READY, "search", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, title), title, "", List.of());
        } else {
            var items = visible.stream().map(row -> {
                String id = row.id().toString();
                return new ListItem(id, Optional.of(new Nodes.Icon("row." + id + ".icon", ControlState.READY, icon(row.type()), hue(row.type()), Nodes.IconSize.RAIL, row.type().toString())),
                    List.<Nodes.Node>of(text("row." + id + ".name", row.name(), Nodes.TextRole.BODY)),
                    List.<Nodes.Node>of(text("row." + id + ".detail", subtitle(row), Nodes.TextRole.CAPTION)), List.of());
            }).toList();
            list = new Nodes.ItemList(ROWS, ControlState.READY, Nodes.ListKind.DOUBLE, items,
                selected().map(id -> Set.of(id.toString())).orElse(Set.of()), EDIT, List.of(), Nodes.SelectionMode.SINGLE, Optional.empty(), OptionalInt.of(VISIBLE_ROWS));
        }
        var browser = new Nodes.Panel("browser", ControlState.READY, hiddenHeader("browser.header", "Items"), Optional.of(
            new Nodes.Field(SEARCH, ControlState.READY, Nodes.FieldKind.SEARCH, query, "Search vault", "Search vault", "")), list, Optional.empty());
        return new Nodes.Split("columns", ControlState.READY, Nodes.SplitKind.THREE_COLUMN, List.of(rail, browser, detail()));
    }

    private ListItem category(String id, String label, String icon, Nodes.Hue hue, int count) {
        return new ListItem(id, Optional.of(new Nodes.Icon("category." + id + ".icon", ControlState.READY, icon, hue, Nodes.IconSize.RAIL, label)),
            List.of(text("category." + id + ".label", label, Nodes.TextRole.BODY)), List.of(), List.of(text("category." + id + ".count", Integer.toString(count), Nodes.TextRole.COUNT)));
    }

    private Nodes.Node detail() {
        var row = visible.stream().filter(item -> item.id().equals(selection)).findFirst().orElse(null);
        var content = new ArrayList<Nodes.Node>();
        if (row == null) {
            content.add(new Nodes.Empty("detail.empty", ControlState.READY, new Nodes.Icon("detail.empty.icon", ControlState.READY, "shield", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "Select an item"), "Select an item", "", List.of()));
            // Retained actions remain disabled for keyboard/event callers while there is no selection.
            content.add(new Nodes.Stack("detail.actions", new ControlState(false, false, false, ""), Nodes.LayoutRole.INLINE,
                List.of(tool(EDIT, "edit", "Edit entry", false), tool(DELETE, "delete", "Delete entry", false), tool(COPY_PUBLIC, "copy", "Copy public key", false))));
        } else {
            content.add(new Nodes.Stack("detail.heading", ControlState.READY, Nodes.LayoutRole.NAVIGATION, List.of(
                new Nodes.Icon("detail.icon", ControlState.READY, icon(row.type()), hue(row.type()), Nodes.IconSize.PROMPT, row.type().toString()),
                new Nodes.Stack("detail.identity", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(text("detail.name", row.name(), Nodes.TextRole.DETAIL_TITLE), text("detail.type", row.type().toString(), Nodes.TextRole.DESCRIPTION))),
                tool(DELETE, "delete", "Delete entry", true), new Nodes.Button(EDIT, ControlState.READY, "Edit", "", Nodes.ButtonKind.SECONDARY, EDIT, "Edit entry"))));
            entry.apply(row.id()).ifPresent(value -> {
                var fields = new ArrayList<Nodes.Node>();
                switch (value) {
                    case Account account -> {
                        fields.add(new Nodes.SettingRow("detail.username", ControlState.READY, "Username", account.username(), tool("detail.username.copy", "copy", "Copy username", true), false));
                        if (password(account.auth()) != null) fields.add(new Nodes.SettingRow("detail.password", ControlState.READY, "Password", "",
                            new Nodes.Secret(secretId(), ControlState.READY, "", "Password", "", Nodes.SecretKind.DETAIL, "detail.password.copy"), false));
                        if (account.auth() instanceof Auth.Key key) fields.add(detailValue("private", "Private Key", key.keyPath().toString()));
                        if (account.auth() instanceof Auth.KeyAndPassword key) fields.add(detailValue("private", "Private Key", key.keyPath().toString()));
                        content.add(new Nodes.Card("detail.fields", ControlState.READY, Nodes.CardKind.SETTINGS, fields));
                        content.add(text("detail.modified", "Modified " + date(account.updated()), Nodes.TextRole.CAPTION));
                    }
                    case SshKey key -> {
                        fields.add(detailValue("algorithm", "Algorithm", key.algorithm())); fields.add(detailValue("fingerprint", "Fingerprint", key.fingerprint()));
                        fields.add(detailValue("private", "Private Key", key.privatePath().toString())); fields.add(detailValue("public", "Public Key", key.publicPath().toString()));
                        if (!key.comment().isBlank()) fields.add(detailValue("comment", "Comment", key.comment()));
                        content.add(new Nodes.Card("detail.fields", ControlState.READY, Nodes.CardKind.FORM, fields));
                        content.add(text("detail.created", "Created " + date(key.created()), Nodes.TextRole.CAPTION));
                    }
                    case ManagedSshKey key -> {
                        fields.add(detailValue("storage", "Storage", "Stored in Vault")); fields.add(detailValue("fingerprint", "Fingerprint", key.fingerprint()));
                        content.add(new Nodes.Card("detail.fields", ControlState.READY, Nodes.CardKind.FORM, fields));
                        content.add(text("detail.created", "Created " + date(key.created()), Nodes.TextRole.CAPTION));
                    }
                    case Note note -> {
                        content.add(new Nodes.Card("detail.fields", ControlState.READY, Nodes.CardKind.FORM, List.of(
                            new Nodes.Secret(secretId(), ControlState.READY, "", "Secure note", "", Nodes.SecretKind.DETAIL, "detail.note.copy"))));
                        content.add(text("detail.modified", "Modified " + date(note.updated()), Nodes.TextRole.CAPTION));
                    }
                    default -> { }
                }
            });
            if (row.type() == VaultManager.Type.SSH_KEY) content.add(new Nodes.Button(COPY_PUBLIC, ControlState.READY, "Copy Public Key", "copy", Nodes.ButtonKind.SECONDARY, COPY_PUBLIC, "Copy public key"));
        }
        return new Nodes.Panel("detail", ControlState.READY, hiddenHeader("detail.header", "Details"), Optional.empty(),
            new Nodes.Stack("detail.content", ControlState.READY, Nodes.LayoutRole.DETAIL, content), Optional.empty());
    }

    private Nodes.Node detailValue(String id, String label, String value) {
        return new Nodes.Stack("detail." + id + ".row", ControlState.READY, Nodes.LayoutRole.VERTICAL,
            List.of(text("detail." + id + ".label", label, Nodes.TextRole.CAPTION), text("detail." + id, value, Nodes.TextRole.MONO_SMALL)));
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
        if (unlock != null) return List.of(unlock.embedded());
        return List.of(new Nodes.Empty(LOCKED, ControlState.READY,
            new Nodes.Icon(LOCKED + ".icon", ControlState.READY, "lock", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "Vault locked"), "Vault locked", "",
            List.of(new Nodes.Button(UNLOCK, ControlState.READY, "Unlock", "unlock", Nodes.ButtonKind.SECONDARY, UNLOCK, ""))));
    }

    private Nodes.Form node() {
        Nodes.Node body = !unlocked ? new Nodes.Stack("locked.body", ControlState.READY, Nodes.LayoutRole.DETAIL, lockedCard())
            : card == Card.GRANTS ? new Nodes.Stack("grants.body", ControlState.READY, Nodes.LayoutRole.DETAIL, grantsCard())
            : layout == Layout.CLASSIC ? classicEntries() : entries();
        if (layout == Layout.CLASSIC) {
            // One section: the toolbar row over the body, which fills (plan 10b Rulings 3); the passive locked state centres.
            if (!unlocked && unlock == null) body = lockedCard().getFirst();
            body = new Nodes.Split("classic", ControlState.READY, Nodes.SplitKind.DOCUMENT, List.of(toolbar(), body));
        }
        boolean prompting = unlock != null && !unlocked;
        var actions = new ArrayList<Nodes.Button>(List.of(new Nodes.Button(OKAY, ControlState.READY, "Done", "", prompting ? Nodes.ButtonKind.SECONDARY : Nodes.ButtonKind.PRIMARY, CLOSE, "")));
        if (prompting) actions.add(unlock.submitButton());
        return new Nodes.Form(ROOT, ControlState.READY, Nodes.FormKind.WINDOW, "Credential Vault", Optional.empty(), List.of(body),
            Optional.of(layout == Layout.CLASSIC ? classicStatus() : status()), actions, prompting ? unlock.cancel : CLOSE);
    }

    private static String date(java.time.Instant timestamp) {
        return java.time.format.DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH).withZone(java.time.ZoneId.systemDefault()).format(timestamp);
    }
    private Nodes.Node status() {
        return new Nodes.Stack(STATUS, ControlState.READY, Nodes.LayoutRole.FOOTER, List.of(
            statusGroup("provider", "key", sentence(status.deviceSecret()), Nodes.TextRole.CAPTION),
            statusGroup("autolock", "clock", "Auto-lock " + status.autoLock(), Nodes.TextRole.CAPTION),
            statusGroup("path", "folder", status.path(), Nodes.TextRole.MONO_CAPTION)));
    }
    /** A store description ("keychain") as a footer label ("Keychain"). */
    private static String sentence(String value) { return value.isEmpty() ? value : value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1); }
    private Nodes.Node statusGroup(String id,String icon,String value,Nodes.TextRole role) {
        return new Nodes.Stack("status."+id+".group",ControlState.READY,Nodes.LayoutRole.NAVIGATION,List.of(
            new Nodes.Icon("status."+id+".icon",ControlState.READY,icon,Nodes.Hue.NEUTRAL,Nodes.IconSize.CAPTION,id),
            text("status."+id,value,role)));
    }

    /** The classic columns (spec 14.8): the Categories tree with counts, the item table over its footer, and the details. */
    private Nodes.Node classicEntries() {
        var kinds = new ArrayList<TreeItem>();
        for (var type : VaultManager.Type.values())
            kinds.add(new TreeItem(type.name(), plural(type), Optional.of(icon(type)), true, List.of(), OptionalInt.of(count(type)), false));
        var all = new TreeItem(ALL_TYPES, "All Items", Optional.of("menu"), false, kinds, OptionalInt.of(allRows.size()), false);
        var tree = new Nodes.Tree(KIND, ControlState.READY, List.of(all), collapsed.contains(ALL_TYPES) ? Set.of() : Set.of(ALL_TYPES), Set.of(kind), "");
        var rail = new Nodes.Panel("categories", ControlState.READY, header("categories.header", "Categories"), Optional.empty(), tree, Optional.empty());
        int scoped = kind.equals(ALL_TYPES) ? allRows.size() : count(VaultManager.Type.valueOf(kind));
        var browser = new Nodes.Panel("browser", ControlState.READY, hiddenHeader("browser.header", "Items"), Optional.empty(), classicTable(),
            Optional.of(text(ITEMS_FOOTER, visible.size() + " of " + scoped + (scoped == 1 ? " item" : " items"), Nodes.TextRole.CAPTION)));
        return new Nodes.Split("columns", ControlState.READY, Nodes.SplitKind.THREE_COLUMN, List.of(rail, browser, detail()));
    }

    /** The item table: the kind's swatch and the name, then the username, the kind and the date; a header click sorts it. */
    private Nodes.Table classicTable() {
        var columns = List.of(column(COLUMN_NAME, "Name", Nodes.TextRole.BODY, 111), column(COLUMN_USERNAME, "Username", Nodes.TextRole.MONO, 80),
            column(COLUMN_TYPE, "Type", Nodes.TextRole.DESCRIPTION, 72), column(COLUMN_MODIFIED, "Modified", Nodes.TextRole.DESCRIPTION, 85));
        Optional<Nodes.Empty> none = allRows.isEmpty() ? Optional.of(new Nodes.Empty(EMPTY, ControlState.READY,
            new Nodes.Icon(EMPTY + ".icon", ControlState.READY, "search", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "Nothing to show"),
            "Nothing to show", "", List.of())) : Optional.empty();
        return new Nodes.Table(ROWS, ControlState.READY, columns, visible.stream().map(this::tableRow).toList(),
            selected().map(id -> Set.of(id.toString())).orElse(Set.of()), EDIT, List.of(), Nodes.SelectionMode.SINGLE,
            Nodes.ScrollPolicy.STANDARD, none, allRows.isEmpty() ? "" : "No matching entries");
    }

    private Column column(String id, String title, Nodes.TextRole role, double weight) {
        return new Column(id, title, role, false, weight, sortColumn.equals(id) ? Optional.of(ascending) : Optional.empty());
    }

    private TableRow tableRow(VaultManager.Row row) {
        String id = row.id().toString();
        var name = new Nodes.Stack("row." + id + ".name", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Icon("row." + id + ".icon", ControlState.READY, icon(row.type()), hue(row.type()), Nodes.IconSize.SWATCH, row.type().toString()),
            text("row." + id + ".title", row.name(), Nodes.TextRole.BODY)));
        return new TableRow(id, Map.of(COLUMN_USERNAME, username(row), COLUMN_TYPE, row.type().toString(),
                COLUMN_MODIFIED, modified(row).map(ManagerPanel::date).orElse("")),
            Optional.empty(), false, Optional.empty(), row.name() + ", " + row.type(), Map.of(COLUMN_NAME, name), Optional.empty());
    }

    /** The classic order: the sorted column, then the name and the id, so equal keys keep a stable order. */
    private Comparator<VaultManager.Row> classicOrder() {
        Comparator<VaultManager.Row> order = switch (sortColumn) {
            case COLUMN_USERNAME -> Comparator.comparing(this::username, String.CASE_INSENSITIVE_ORDER);
            case COLUMN_TYPE -> Comparator.comparing(VaultManager.Row::type);
            case COLUMN_MODIFIED -> Comparator.comparing(row -> modified(row).orElse(java.time.Instant.EPOCH));
            default -> Comparator.comparing(VaultManager.Row::name, String.CASE_INSENSITIVE_ORDER);
        };
        order = order.thenComparing(VaultManager.Row::name, String.CASE_INSENSITIVE_ORDER).thenComparing(row -> row.id().toString());
        return ascending ? order : order.reversed();
    }

    private String username(VaultManager.Row row) {
        return entry.apply(row.id()).map(value -> value instanceof Account account ? account.username() : "—").orElse("—");
    }

    private Optional<java.time.Instant> modified(VaultManager.Row row) {
        return entry.apply(row.id()).map(value -> switch (value) {
            case Account account -> account.updated();
            case SshKey key -> key.created();
            case ManagedSshKey key -> key.created();
            case Note note -> note.updated();
            default -> null;
        });
    }

    private int count(VaultManager.Type type) { return (int) allRows.stream().filter(row -> row.type() == type).count(); }

    private static String plural(VaultManager.Type type) {
        return switch (type) { case LOGIN -> "Logins"; case SSH_KEY -> "SSH Keys"; case NOTE -> "Secure Notes"; };
    }

    private static Nodes.Header header(String id, String title) {
        return new Nodes.Header(id, ControlState.READY, title, OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of());
    }

    /**
     * The classic toolbar row (spec 14.8, plan 10b Rulings 3): + New, the selection's verbs, Lock Vault and its shortcut,
     * then the search, which the bar puts at its trailing edge. The verbs run the same actions as the rows composition's.
     */
    private Nodes.Stack toolbar() {
        var row = visible.stream().filter(item -> item.id().equals(selection)).findFirst().orElse(null);
        Object value = row == null ? null : entry.apply(row.id()).orElse(null);
        boolean chosen = unlocked && card == Card.ENTRIES && row != null;
        boolean account = chosen && value instanceof Account;
        boolean secret = account && password(((Account) value).auth()) != null;
        var adds = new ArrayList<MenuItem>();
        for (var type : VaultManager.Type.values()) adds.add(new MenuItem("add." + type.name(), type.toString(), icon(type), "add." + type.name(), true, false, hue(type)));
        adds.add(new MenuItem("add.separator", "", "", "", true, true));
        adds.add(new MenuItem(ADD_GENERATE, "Generate SSH Key...", "sparkle", ADD_GENERATE, true, false, Nodes.Hue.PURPLE));
        return new Nodes.Stack(TOOLBAR, ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.MenuButton(TOOLBAR + ".new", new ControlState(true, unlocked, false, ""), "New", "add", Nodes.ButtonKind.GHOST, "", adds, "Add entry"),
            new Nodes.Separator(TOOLBAR + ".rule.1", ControlState.READY),
            verb("edit", "Edit…", EDIT, chosen), verb("delete", "Delete", DELETE, chosen),
            verb("copy-username", "Copy Username", "detail.username.copy", account),
            verb("copy-password", "Copy Password", "detail.password.copy", secret),
            new Nodes.Separator(TOOLBAR + ".rule.2", ControlState.READY),
            verb("lock", "Lock Vault", LOCK, unlocked),
            text(TOOLBAR + ".shortcut", LOCK_SHORTCUT, Nodes.TextRole.CAPTION),
            new Nodes.Field(SEARCH, new ControlState(true, unlocked && card == Card.ENTRIES, false, ""), Nodes.FieldKind.SEARCH, query,
                "Search vault", "Search vault", "")));
    }

    private static Nodes.Button verb(String id, String label, String action, boolean enabled) {
        return new Nodes.Button(TOOLBAR + "." + id, new ControlState(true, enabled, false, ""), label, "", Nodes.ButtonKind.GHOST, action, "");
    }

    /**
     * The classic status row (spec 14.8): the lock state with its dot and store, a rule, the auto-lock and the vault file.
     * As a FOOTER stack the leading cells stay whole and a long path elides; a Separator stands only between two items of
     * an INLINE stack, so the cells before the path are one and no rule precedes the path.
     */
    private Nodes.Node classicStatus() {
        String state = !unlocked ? "Locked" : status.deviceSecret().isEmpty() ? "Unlocked" : "Unlocked · " + sentence(status.deviceSecret());
        return new Nodes.Stack(STATUS, ControlState.READY, Nodes.LayoutRole.FOOTER, List.of(
            new Nodes.Stack("status.cells", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
                new Nodes.Stack("status.state.group", ControlState.READY, Nodes.LayoutRole.NAVIGATION, List.of(
                    new Nodes.StatusDot("status.state", ControlState.READY, unlocked ? Nodes.StatusKind.SUCCESS : Nodes.StatusKind.IDLE),
                    text("status.provider", state, Nodes.TextRole.CAPTION))),
                new Nodes.Separator("status.rule.1", ControlState.READY),
                text("status.autolock", "Auto-lock: " + status.autoLock(), Nodes.TextRole.CAPTION))),
            text("status.path", status.path(), Nodes.TextRole.MONO_CAPTION)));
    }

    /** The window closed (its surface already disposed the view and erased its storage) or is closing. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        allRows = List.of(); visible = List.of(); grants = List.of(); selection = null;
        if (unlock != null) { unlock.close(); unlock = null; }
        view.close(); titleActions.close();
    }
}
