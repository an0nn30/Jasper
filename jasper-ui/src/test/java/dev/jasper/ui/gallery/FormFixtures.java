package dev.jasper.ui.gallery;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import java.util.*;
import dev.jasper.ui.model.Nodes.Stack;

/** The eight supplied mock compositions, with inert local demonstration data only. */
public final class FormFixtures {
    private FormFixtures() {}
    static final ControlState READY = ControlState.READY;
    static final ControlState DISABLED = new ControlState(true, false, false, "");
    static Text text(String id, String value, TextRole role) { return new Text(id, READY, value, role); }
    static Icon icon(String id, String name, Hue hue, IconSize size) { return new Icon(id, READY, name, hue, size, name); }
    static Button button(String id, String label, ButtonKind kind) { return new Button(id, READY, label, "", kind, id, ""); }
    static Field field(String id, String value, String hint, FieldKind kind) { return new Field(id, READY, kind, value, hint, id, ""); }
    static FormRow row(String id, String label, Node control) { return new FormRow(id, READY, label, "", control, Optional.empty()); }
    static Stack stack(String id, LayoutRole role, Node... children) { return new Stack(id, READY, role, List.of(children)); }
    static Card card(String id, CardKind kind, Node... children) { return new Card(id, READY, kind, List.of(children)); }
    static Section section(String id, String title, Node body) { return new Section(id, READY, title, body); }
    static Toggle toggle(String id, boolean checked) { return new Toggle(id, READY, id, checked, id, false); }
    static Choice choice(String id, ChoiceKind kind, String selected, String... labels) {
        return new Choice(id, READY, kind, Arrays.stream(labels).map(s -> new Option(s, s, Optional.empty())).toList(), selected, id, id, kind == ChoiceKind.PICKER ? "lock" : "");
    }
    static SettingRow setting(String id, String title, String description, Node control) { return new SettingRow(id, READY, title, description, control, false); }
    static List<Button> actions(String submit) { return List.of(button("cancel", "Cancel", ButtonKind.SECONDARY), new Button("submit", DISABLED, submit, "", ButtonKind.PRIMARY, "submit", "")); }
    static Form full(String id, String title, List<Node> sections, String preview, String submit) {
        return new Form(id, READY, FormKind.FULL, title, Optional.empty(), sections, Optional.of(text("preview", preview, TextRole.MONO_SMALL)), actions(submit), "cancel");
    }
    static Stack hero(String id, String title, String description, String icon) {
        return stack(id, LayoutRole.HERO, icon(id + "-icon", icon, Hue.BLUE, IconSize.HERO), text(id + "-title", title, TextRole.HERO_TITLE), text(id + "-description", description, TextRole.DESCRIPTION));
    }
    public static ViewSpec create(String name) { return new ViewSpec(switch (name) {
        case "add-host" -> host();
        case "unlock-vault" -> new Form(name, READY, FormKind.HERO, "Unlock Vault", Optional.of(hero("hero", "Unlock Vault", "Enter your master password to use saved credentials.", "lock")), List.of(new Secret("password", READY, "Master password", "Master password", "")), Optional.empty(), actions("Unlock"), "cancel");
        case "new-file-server" -> server();
        case "create-vault" -> new Form(name, READY, FormKind.HERO, "Create Vault", Optional.of(hero("hero", "Create Vault", "Choose a master password to protect your saved credentials.", "gallery:vault")), List.of(stack("passwords", LayoutRole.VERTICAL, stack("password-meter", LayoutRole.VERTICAL, new Secret("password", READY, "Master password", "Master password", "vault-passwords"), new Strength("strength", READY, 0, "")), new Secret("confirmation", READY, "Confirm password", "Confirm password", "vault-passwords")), card("device-card", CardKind.SETTINGS, setting("device", "Bind to this device · Recommended", "The vault can only be opened on this computer", toggle("device-toggle", true))), stack("warning", LayoutRole.INLINE, icon("warning-icon", "warning", Hue.AMBER, IconSize.CONTROL), text("warning-text", "Your master password cannot be recovered if forgotten.", TextRole.CAPTION))), Optional.empty(), actions("Create Vault"), "cancel");
        case "credential-vault" -> vault();
        case "vault-add-menu" -> stack("menu-anchor", LayoutRole.ACTIONS, icon("locked", "lock", Hue.NEUTRAL, IconSize.CONTROL), addMenu());
        case "add-login-sheet" -> login();
        case "add-ssh-key-sheet" -> sshKey();
        default -> throw new IllegalArgumentException("Unknown reference fixture: " + name);
    }); }
    static Node host() {
        var keys = new ItemList("keys", READY, ListKind.ORDERED, List.of(
            new ListItem("ed", Optional.of(icon("ed-icon", "key", Hue.NEUTRAL, IconSize.CONTROL)), List.of(text("ed-title", "id_ed25519", TextRole.MONO), text("ed-type", "ED25519", TextRole.CAPTION)), List.of(), List.of()),
            new ListItem("rsa", Optional.of(icon("rsa-icon", "key", Hue.NEUTRAL, IconSize.CONTROL)), List.of(text("rsa-title", "work_rsa", TextRole.MONO), text("rsa-type", "RSA 4096", TextRole.CAPTION)), List.of(), List.of())), Set.of(), "", List.of(), SelectionMode.SINGLE, Optional.of(new Button("key-add", READY, "Add Stored Key...", "add", ButtonKind.LINK, "key-add", "")));
        return full("add-host", "Add Host", List.of(
            section("connection", "Connection", card("connection-card", CardKind.FORM,
                row("name-row", "Name", field("name", "", "e.g. homelab", FieldKind.TEXT)),
                new FormRow("host-row", READY, "Host", "", field("host", "", "hostname or IP address", FieldKind.TEXT), Optional.of(field("port", "22", "", FieldKind.PORT))),
                row("username-row", "Username", field("username", "", "root", FieldKind.TEXT)), row("jump-row", "Jump Host", choice("jump", ChoiceKind.DROPDOWN, "None", "None")))),
            section("authentication", "Authentication", card("authentication-card", CardKind.FORM,
                choice("authentication-mode", ChoiceKind.SEGMENTED, "Vault Credential", "Vault Credential", "SSH Agent"),
                row("credential-row", "Credential", choice("credential", ChoiceKind.PICKER, "", "Choose...")), new FormRow("keys-row", READY, "Keys", "Tried in order", keys, Optional.empty()))),
            section("options", "Options", card("options-card", CardKind.SETTINGS,
                setting("group-row", "Group", "", choice("group", ChoiceKind.DROPDOWN, "None", "None")), setting("favorite-row", "Add to Favorites", "", toggle("favorite", false)), setting("track-row", "Track shell folder", "SFTP follows the terminal's current directory", toggle("track", true))))), "ssh user@host", "Add Host");
    }
    static Node server() {
        var protocol = new Choice("protocol", READY, ChoiceKind.SEGMENTED, List.of(new Option("HTTP", "HTTP", Optional.of(Hue.BLUE)), new Option("FTP", "FTP", Optional.of(Hue.AMBER)), new Option("SFTP", "SFTP", Optional.of(Hue.GREEN)), new Option("RSYNC", "RSYNC", Optional.of(Hue.PURPLE))), "HTTP", "Protocol", "protocol");
        return full("new-file-server", "New File Server", List.of(
            section("server", "Server", card("server-card", CardKind.FORM,
                row("name-row", "Name", field("name", "", "e.g. Downloads", FieldKind.TEXT)), row("protocol-row", "Protocol", protocol),
                row("folder-row", "Folder", new Field("folder", READY, FieldKind.PATH, "", "No folder selected", "Folder", "choose-folder")),
                new FormRow("reachable-row", READY, "Reachable From", "", choice("reachable", ChoiceKind.DROPDOWN, "This computer only", "This computer only", "Local network"), Optional.of(field("port", "8080", "", FieldKind.PORT))))),
            section("access", "Access", card("access-card", CardKind.MIXED,
                row("login-row", "Login", choice("login", ChoiceKind.SEGMENTED, "Vault Account", "Anonymous", "Vault Account", "Session Password")), row("account-row", "Account", choice("account", ChoiceKind.PICKER, "", "Choose...")), row("account-help-row", "", text("account-help", "Clients sign in with the username and password stored in this Vault account.", TextRole.CAPTION)), setting("allow-row", "Allow changes", "Clients can upload, rename and delete files", toggle("allow", true)))),
            section("security", "Security & Startup", card("security-card", CardKind.SETTINGS,
                setting("tls-row", "Use TLS", "", toggle("tls", false)), new SettingRow("require-row", READY, "Require TLS", "Refuse logins and transfers without it", new Toggle("require", DISABLED, "Require TLS", false, "require", false), true), setting("startup-row", "Start when Jasper starts", "", toggle("startup", false))))), "http://127.0.0.1:8080", "Add Server");
    }
    static Node login() {
        return new Form("add-login-sheet", READY, FormKind.SHEET, "Add Login", Optional.of(stack("sheet-title", LayoutRole.INLINE, icon("sheet-icon", "gallery:user", Hue.BLUE, IconSize.HERO), text("sheet-label", "Add Login", TextRole.HERO_TITLE))), List.of(card("identity", CardKind.FORM, row("name-row", "Name", field("name", "", "e.g. Production server", FieldKind.TEXT)), row("username-row", "Username", field("username", "", "root", FieldKind.TEXT))), section("authentication", "Authentication", card("authentication-card", CardKind.FORM, choice("mode", ChoiceKind.SEGMENTED, "Password", "Password", "Private Key"), row("password-row", "Password", new Secret("password", READY, "", "Password", ""))))), Optional.empty(), actions("Save"), "cancel");
    }
    static Node sshKey() {
        return new Form("add-ssh-key-sheet", READY, FormKind.SHEET, "Add SSH Key", Optional.of(stack("sheet-title", LayoutRole.INLINE, icon("sheet-icon", "key", Hue.GREEN, IconSize.HERO), text("sheet-label", "Add SSH Key", TextRole.HERO_TITLE))), List.of(card("key-card", CardKind.FORM,
            row("name-row", "Name", field("name", "", "e.g. Work laptop key", FieldKind.TEXT)),
            row("private-row", "Private Key", new Field("private", READY, FieldKind.PATH, "", "~/.ssh/id_ed25519", "Private Key", "browse-private")),
            row("public-row", "Public Key", new Field("public", READY, FieldKind.PATH, "", "~/.ssh/id_ed25519.pub", "Public Key", "browse-public")), row("comment-row", "Comment", field("comment", "", "Optional", FieldKind.TEXT)))), Optional.empty(), actions("Save"), "cancel");
    }
    static MenuButton addMenu() { return new MenuButton("add-menu", READY, "", "add", ButtonKind.SECONDARY, "", List.of(new MenuItem("login", "Login", "gallery:user", "login", true, false, Hue.BLUE), new MenuItem("key", "SSH Key", "key", "key", true, false, Hue.GREEN), new MenuItem("note", "Secure Note", "note", "note", true, false, Hue.AMBER), new MenuItem("separator", "", "", "", false, true), new MenuItem("generate", "Generate SSH Key...", "sparkle", "generate", true, false, Hue.PURPLE)), "Add credential"); }
    static Node vault() {
        var categories = new ItemList("categories", READY, ListKind.SINGLE, List.of(category("all", "All Items", "gallery:menu", Hue.NEUTRAL, "5"), category("logins", "Logins", "gallery:user", Hue.BLUE, "2"), category("keys", "SSH Keys", "key", Hue.GREEN, "2"), category("notes", "Secure Notes", "note", Hue.AMBER, "1")), Set.of("all"), "category", List.of());
        var entries = new ItemList("entries", READY, ListKind.DOUBLE, List.of(entry("amp", "amp", "ubuntu · Password", "gallery:user", Hue.BLUE), entry("proxmox", "Proxmox", "root · Private key", "gallery:user", Hue.BLUE), entry("ed", "id_ed25519", "ED25519 · dustin@macbook", "key", Hue.GREEN), entry("rsa", "work_rsa", "RSA 4096 · deploy", "key", Hue.GREEN), entry("notes", "Router recovery codes", "Secure note", "note", Hue.AMBER)), Set.of("proxmox"), "entry", List.of());
        Node detail = stack("detail", LayoutRole.DETAIL, stack("identity", LayoutRole.INLINE, icon("type-icon", "gallery:user", Hue.BLUE, IconSize.HERO), stack("detail-labels", LayoutRole.VERTICAL, text("detail-title", "Proxmox", TextRole.DETAIL_TITLE), text("detail-subtitle", "Login", TextRole.DESCRIPTION)), button("delete", "Delete", ButtonKind.DESTRUCTIVE_GHOST), button("edit", "Edit", ButtonKind.SECONDARY)), card("detail-card", CardKind.MIXED, detailRow("username", "Username", "root"), detailRow("keyfile", "Key File", "~/.ssh/pve_ed25519"), new Secret("passphrase", READY, "", "Passphrase", "", SecretKind.DETAIL, "copy-passphrase")), text("modified", "Used by 1 session · Modified Sep 18, 2026", TextRole.CAPTION));
        return new Panel("credential-vault", READY, new Header("manager-header", new ControlState(false, true, false, ""), "Credential Vault", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of()), Optional.empty(), new Split("columns", READY, SplitKind.THREE_COLUMN, List.of(categories, new Panel("list-column", READY, new Header("list-header", new ControlState(false, true, false, ""), "Credentials", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of()), Optional.of(field("query", "", "Search vault", FieldKind.SEARCH)), entries, Optional.empty()), detail)), Optional.of(stack("footer", LayoutRole.NAVIGATION, text("keychain", "Keychain", TextRole.CAPTION), text("autolock", "Auto-lock 15 min", TextRole.CAPTION), text("directory", "/Users/dustin/projects/jasper-app/", TextRole.MONO_SMALL), button("done", "Done", ButtonKind.PRIMARY))));
    }
    static Node detailRow(String id, String title, String value) { return setting(id + "-row", title, value, new Button(id + "-copy", READY, "", "copy", ButtonKind.GHOST, id + "-copy", "Copy " + title)); }
    static ListItem category(String id, String label, String icon, Hue hue, String count) { return new ListItem(id, Optional.of(icon("category-" + id + "-icon", icon, hue, IconSize.RAIL)), List.of(text("category-" + id + "-label", label, TextRole.BODY)), List.of(), List.of(text("category-" + id + "-count", count, TextRole.COUNT))); }
    static ListItem entry(String id, String title, String subtitle, String icon, Hue hue) { return new ListItem(id, Optional.of(icon(id + "-icon", icon, hue, IconSize.HERO)), List.of(text(id + "-title", title, TextRole.BODY)), List.of(text(id + "-subtitle", subtitle, TextRole.DESCRIPTION)), List.of()); }
}
