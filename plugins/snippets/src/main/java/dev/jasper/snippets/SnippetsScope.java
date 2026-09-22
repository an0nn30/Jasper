package dev.jasper.snippets;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.PaletteStep;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.palette.ScopeSpec;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.snippets.api.SnippetService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** The Snippets scope: Enter pastes, Cmd/Ctrl+Enter pastes and runs, Shift+Enter edits the file. */
final class SnippetsScope implements PaletteScope {
    static final PaletteVerb PASTE = new PaletteVerb("paste", "Paste");
    static final PaletteVerb PASTE_RUN = new PaletteVerb("paste_run", "Paste and run");
    static final PaletteVerb EDIT = new PaletteVerb("edit", "Edit file");
    static final String ERROR_ROW = "snippets.file-error";

    private final SnippetStore store;
    private final Consumer<String> onError;
    private final String openActionId;

    SnippetsScope(SnippetStore store, Consumer<String> onError, String openActionId) {
        this.store = store; this.onError = onError; this.openActionId = openActionId;
    }

    @Override public ScopeSpec spec() {
        return ScopeSpec.of(SnippetService.SCOPE_ID, "Snippets", "Search snippets, or > to switch scope", List.of(PASTE, PASTE_RUN, EDIT))
            .withDescription("Paste or run a saved command").withAliases(List.of("snip", "snippets")).withShortcutActionId(openActionId);
    }

    @Override public void activated(PaletteQuery context) { store.refresh(); }

    private record Ranked(Snippet snippet, int tier, int position) { }

    @Override public PaletteResults search(String query, PaletteQuery context) {
        SnippetStore.Snapshot snapshot = store.snapshot();
        String q = Text.normalize(query);
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
        if (snapshot.erroneous()) rows.add(PaletteRow.of(ERROR_ROW, "Snippets file has errors")
            .withDetail("Fix " + store.file() + " and Reload Config").withEnabled(false));
        for (Snippet snippet : ordered) {
            if (rows.size() >= context.maxResults()) break;
            rows.add(row(snippet));
        }
        return new PaletteResults(rows, q.isEmpty() ? Optional.of("Snippets") : Optional.empty(), Optional.empty());
    }

    /** -1 when a token matches nothing; 0 exact name, 1 name prefix, 2 name word prefix, 3 name substring, 4 keyword, 5 command text. */
    static int tier(Snippet snippet, String query, String[] tokens) {
        String name = Text.normalize(snippet.name());
        if (name.equals(query)) return 0;
        if (name.startsWith(query)) return 1;
        String[] words = name.split(" ");
        String keywords = Text.normalize(String.join(" ", snippet.keywords()));
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

    private static boolean live(PaletteQuery context) { return context.target().map(PaneHandle::isOpen).orElse(false); }

    @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (row.token() instanceof Snippet snippet)
            return store.snapshot().byName(snippet.name()).isPresent() && (verb.equals(EDIT) || live(context));
        return ERROR_ROW.equals(row.id()) && verb.equals(EDIT);
    }

    @Override public Optional<PaletteStep> step(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (verb.equals(EDIT) || !(row.token() instanceof Snippet snippet) || snippet.placeholders().isEmpty()) return Optional.empty();
        Map<String, String> remembered = store.lastValues();
        List<PaletteStep.Field> fields = snippet.placeholders().stream()
            .map(name -> new PaletteStep.Field(name, name, remembered.getOrDefault(name, ""))).toList();
        return Optional.of(new PaletteStep(snippet.name(), fields, (values, done) -> {
            store.lastValues().putAll(values);
            paste(snippet.fill(values), verb, context);
            done.accept(PaletteStep.Result.done());
        }));
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (verb.equals(EDIT)) { store.openInEditor(onError); return; }
        if (row.token() instanceof Snippet snippet) paste(snippet.fill(Map.of()), verb, context);
    }

    @Override public Subscription onChanged(Runnable listener) { return store.onChanged(listener); }

    private static void paste(String text, PaletteVerb verb, PaletteQuery context) {
        context.target().ifPresent(pane -> {
            pane.paste(text);
            if (verb.equals(PASTE_RUN)) pane.sendText("\r");
        });
    }

    private static PaletteRow row(Snippet snippet) {
        int fields = snippet.placeholders().size();
        String tag = fields == 0 ? null : fields + (fields == 1 ? " field" : " fields");
        return PaletteRow.of(SnippetService.rowId(snippet.name()), snippet.name()).withDetail(Text.oneLine(snippet.command())).withTag(tag).withToken(snippet);
    }
}
