package dev.jasper.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.Icon;

/** The Snippets scope: named commands from snippets.toml. Enter pastes, Cmd/Ctrl+Enter pastes and runs, Shift+Enter edits the file. */
final class SnippetsScope implements PaletteScope {
    static final PaletteVerb PASTE = new PaletteVerb("paste", "Paste");
    static final PaletteVerb PASTE_RUN = new PaletteVerb("paste_run", "Paste and run");
    static final PaletteVerb EDIT = new PaletteVerb("edit", "Edit file");
    private static final String ERROR_ROW = "snippets.file-error";

    private final SnippetStore store;
    private final Consumer<String> onError;
    private final Icon icon;

    SnippetsScope(SnippetStore store, Consumer<String> onError) { this(store, onError, AppIcons.icon("bookmark")); }

    SnippetsScope(SnippetStore store, Consumer<String> onError, Icon icon) {
        this.store = Objects.requireNonNull(store);
        this.onError = Objects.requireNonNull(onError);
        this.icon = icon;
    }

    @Override public String id() { return SNIPPETS_ID; }
    @Override public String label() { return "Snippets"; }
    @Override public Icon icon() { return icon; }
    @Override public String description() { return "Paste or run a saved command"; }
    @Override public String placeholder() { return "Search snippets, or > to switch scope"; }
    @Override public List<String> aliases() { return List.of("snip", "snippets"); }
    @Override public List<PaletteVerb> verbs() { return List.of(PASTE, PASTE_RUN, EDIT); }
    @Override public void activated(PaletteContext context) { store.refresh(); }

    private record Ranked(Snippet snippet, int tier, int position) {}

    @Override public PaletteResults search(String query, PaletteContext context) {
        SnippetStore.Snapshot snapshot = store.snapshot();
        String q = CommandSearch.normalize(query);
        List<Snippet> ordered;
        if (q.isEmpty()) ordered = snapshot.snippets();
        else {
            String[] tokens = q.split(" ");
            var ranked = new ArrayList<Ranked>();
            for (int i = 0; i < snapshot.snippets().size(); i++) {
                int tier = tier(snapshot.snippets().get(i), q, tokens);
                if (tier >= 0) ranked.add(new Ranked(snapshot.snippets().get(i), tier, i));
            }
            ranked.sort(Comparator.comparingInt(Ranked::tier).thenComparingInt(Ranked::position));
            ordered = ranked.stream().map(Ranked::snippet).toList();
        }
        var rows = new ArrayList<PaletteRow>();
        if (snapshot.erroneous()) rows.add(new PaletteRow(ERROR_ROW, "Snippets file has errors",
            "Fix " + store.file() + " and Reload Config", null, null, false, null));
        for (Snippet snippet : ordered) {
            if (rows.size() >= context.maxResults()) break;
            rows.add(row(snippet));
        }
        return new PaletteResults(rows, q.isEmpty() ? "Snippets" : null, null);
    }

    /** -1 when a token matches nothing; 0 exact name, 1 name prefix, 2 name word prefix, 3 name substring, 4 keyword, 5 command text. */
    static int tier(Snippet snippet, String query, String[] tokens) {
        String name = CommandSearch.normalize(snippet.name());
        if (name.equals(query)) return 0;
        if (name.startsWith(query)) return 1;
        String[] words = name.split(" ");
        String keywords = CommandSearch.normalize(String.join(" ", snippet.keywords()));
        String command = snippet.command().toLowerCase(Locale.ROOT);
        int tier = 0;
        for (String token : tokens) {
            int current;
            if (Arrays.stream(words).anyMatch(word -> word.startsWith(token))) current = 2;
            else if (name.contains(token)) current = 3;
            else if (keywords.contains(token)) current = 4;
            else if (command.contains(token)) current = 5;
            else return -1;
            tier = Math.max(tier, current);
        }
        return tier;
    }

    @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteContext context) {
        if (row.token() instanceof Snippet snippet)
            return store.snapshot().byName(snippet.name()).isPresent() && (verb.equals(EDIT) || context.target().live().getAsBoolean());
        return ERROR_ROW.equals(row.id()) && verb.equals(EDIT);
    }

    @Override public PaletteStep step(PaletteRow row, PaletteVerb verb, PaletteContext context) {
        if (verb.equals(EDIT) || !(row.token() instanceof Snippet snippet) || snippet.placeholders().isEmpty()) return null;
        Map<String, String> remembered = store.lastValues();
        List<PaletteStep.Field> fields = snippet.placeholders().stream()
            .map(name -> new PaletteStep.Field(name, name, remembered.getOrDefault(name, ""))).toList();
        return new PaletteStep(snippet.name(), fields, (values, done) -> {
            store.lastValues().putAll(values);
            paste(snippet.fill(values), verb, context);
            done.accept(PaletteStep.Result.done());
        });
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) {
        if (verb.equals(EDIT)) { store.openInEditor(onError); return; }
        if (row.token() instanceof Snippet snippet) paste(snippet.fill(Map.of()), verb, context);
    }

    @Override public CommandRegistry.Subscription onChanged(Runnable listener) { return store.onChanged(listener); }

    private static void paste(String text, PaletteVerb verb, PaletteContext context) {
        context.target().paste().accept(text);
        if (verb.equals(PASTE_RUN)) context.target().sendReturn().run();
    }

    private static PaletteRow row(Snippet snippet) {
        int fields = snippet.placeholders().size();
        String tag = fields == 0 ? null : fields + (fields == 1 ? " field" : " fields");
        String detail = snippet.command().replace("\r", "").replace("\n", " ↵ ");
        return new PaletteRow(rowId(snippet.name()), snippet.name(), detail, tag, null, true, snippet);
    }

    static String rowId(String name) {
        String key = name.strip().toLowerCase(Locale.ROOT);
        return "snippet." + Integer.toHexString(key.hashCode()) + "." + key.length();
    }
}
