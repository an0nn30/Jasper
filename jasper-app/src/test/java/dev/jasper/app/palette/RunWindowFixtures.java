package dev.jasper.app.palette;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.ui.model.Nodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The 06 Run Dialog mocks' data as palette entries (the mocks' names, hosts and paths are fixtures): the sessions,
 * commands, snippets and files scopes with their nouns, hues and verbs (3d's bundled values), "Just opened" and
 * "Typing auth".
 */
final class RunWindowFixtures {
    private RunWindowFixtures() { }

    static PaletteScope scope(String id, String label, String noun, Nodes.Hue hue, String description, List<PaletteVerb> verbs) {
        return new PaletteScope() {
            @Override public String id() { return id; }
            @Override public String label() { return label; }
            @Override public String description() { return description; }
            @Override public String placeholder() { return "Search " + label; }
            @Override public List<PaletteVerb> verbs() { return verbs; }
            @Override public Nodes.Hue hue() { return hue; }
            @Override public String itemNoun() { return noun; }
            @Override public PaletteResults search(String query, PaletteContext context) { return PaletteResults.none(); }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) { }
            @Override public Subscription onChanged(Runnable listener) { return new Subscription(() -> { }); }
        };
    }

    static final PaletteScope SESSIONS = scope("dev.jasper.remote.scope", "SSH", "session", Nodes.Hue.GREEN, "Connect to a saved SSH host",
        List.of(new PaletteVerb("connect", "Connect"), new PaletteVerb("split", "Connect in split", PaletteVerb.Role.SPLIT), new PaletteVerb("edit", "Edit")));
    static final PaletteScope COMMANDS = scope("jasper.commands", "Commands", "command", Nodes.Hue.BLUE, "Run a command",
        List.of(new PaletteVerb("run", "Run")));
    static final PaletteScope SNIPPETS = scope("dev.jasper.snippets.scope", "Snippets", "snippet", Nodes.Hue.PURPLE, "Insert or run a saved command",
        List.of(new PaletteVerb("paste", "Insert"), new PaletteVerb("paste_run", "Insert and run"), new PaletteVerb("edit", "Edit")));
    static final PaletteScope FILES = scope("dev.jasper.indexer.files", "Files", "file", Nodes.Hue.AMBER, "Open a file of an indexed project",
        List.of(new PaletteVerb("open", "Open"), new PaletteVerb("copy-path", "Copy path"), new PaletteVerb("terminal", "Open terminal here")));

    static List<PaletteScope> scopes() { return List.of(COMMANDS, SESSIONS, SNIPPETS, FILES); }

    static PaletteEntry.Item item(PaletteScope scope, String id, String title, String detail, String tag) {
        return new PaletteEntry.Item(scope, new PaletteRow(id, title, detail, tag, null, true, null));
    }

    /** "Run Dialog - Just opened (recent + most used)": three recent rows, then five most used; Mac selected. */
    static List<PaletteEntry> justOpened() {
        return List.of(new PaletteEntry.Header(PaletteEntry.Header.Kind.RECENT, "Recently used"),
            item(SESSIONS, "mac", "Mac", "dustin@192.168.1.119:22", null),
            item(COMMANDS, "install_cli", "Install 'jasper' Command in PATH", "/usr/local/bin/jasper", null),
            item(SNIPPETS, "wiremock", "wiremock run", "./gradlew run --args=\"regression --config regression-suite.yml\"", null),
            new PaletteEntry.Header(PaletteEntry.Header.Kind.USED, "Most used"),
            item(COMMANDS, "new_editor_file", "New Editor File", "Editor", null),
            item(FILES, "settings", "settings.gradle.kts", "moray/settings.gradle.kts", null),
            item(SNIPPETS, "gcloud", "gcloud auth", "gcloud auth login", null),
            item(FILES, "vendor", "VendorDetailsService.java", "src/main/java/com/homedepot/sourcing/orders/VendorDetailsService.java", null),
            item(COMMANDS, "thd_crypto", "THD Crypto: Encrypt / Decrypt…", "THD Sourcing Crypto", "⌃⌥⇧C"));
    }

    /** "Run Dialog - Typing auth (filtered)": one match, a snippet, selected. */
    static List<PaletteEntry> typingAuth() {
        return List.of(new PaletteEntry.Header(PaletteEntry.Header.Kind.MATCHES, "Snippets"), item(SNIPPETS, "gcloud", "gcloud auth", "gcloud auth login", null));
    }

    /** A host that records what the view calls back. */
    static PaletteView.Host host(List<String> events) {
        return new PaletteView.Host(query -> events.add("query:" + query),
            (entry, verb) -> events.add("run:" + PaletteTestSupport.describe(entry) + ":" + verb),
            text -> Optional.empty(), scope -> events.add("scope:" + (scope == null ? "all" : scope.id())), RunWindowFixtures::scopes,
            () -> "⇧⌘P", () -> events.add("cancel"), () -> events.add("browse"));
    }

    static RunWindowView view(dev.jasper.ui.swing.KitContext context, List<String> events) {
        var view = new RunWindowView(context, true, host(events));
        view.setVisibleRows(8);
        return view;
    }

    /** Every visible label's text under {@code root}, in component order. */
    static List<String> texts(java.awt.Component root) {
        var texts = new ArrayList<String>();
        collect(root, texts);
        return texts;
    }

    private static void collect(java.awt.Component component, List<String> texts) {
        if (!component.isVisible()) return;
        if (component instanceof javax.swing.JLabel label && label.getText() != null && !label.getText().isEmpty()) texts.add(label.getText());
        else if (component instanceof javax.swing.AbstractButton button && button.getText() != null && !button.getText().isEmpty()) texts.add(button.getText());
        if (component instanceof java.awt.Container container) for (var child : container.getComponents()) collect(child, texts);
    }
}
