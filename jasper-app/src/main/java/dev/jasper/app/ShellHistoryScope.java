package dev.jasper.app;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import javax.swing.Icon;

/**
 * The History scope: substring search over the shared index. Enter pastes, Cmd/Ctrl+Enter pastes and runs;
 * Shift+Enter saves the command as a snippet when a {@link SnippetStore} is attached.
 */
final class ShellHistoryScope implements PaletteScope {
    static final PaletteVerb PASTE = new PaletteVerb("paste", "Paste");
    static final PaletteVerb PASTE_RUN = new PaletteVerb("paste_run", "Paste and run");
    static final PaletteVerb SAVE = new PaletteVerb("save_snippet", "Save as snippet…");

    private final ShellHistoryIndex index;
    private final SnippetStore snippets;
    private final Icon icon;

    ShellHistoryScope(ShellHistoryIndex index) { this(index, null, AppIcons.icon("history")); }

    ShellHistoryScope(ShellHistoryIndex index, Icon icon) { this(index, null, icon); }

    ShellHistoryScope(ShellHistoryIndex index, SnippetStore snippets, Icon icon) {
        this.index = java.util.Objects.requireNonNull(index);
        this.snippets = snippets;
        this.icon = icon;
    }

    @Override public String id() { return HISTORY_ID; }
    @Override public String label() { return "History"; }
    @Override public Icon icon() { return icon; }
    @Override public String description() { return "Search shell history and paste or run a command"; }
    @Override public String placeholder() { return "Search shell history, or > to switch scope"; }
    @Override public List<String> aliases() { return List.of("hist", "shell"); }
    @Override public List<PaletteVerb> verbs() {
        return snippets == null ? List.of(PASTE, PASTE_RUN) : List.of(PASTE, PASTE_RUN, SAVE);
    }
    @Override public boolean monospaceRows() { return true; }
    @Override public void activated(PaletteContext context) { index.refresh(); }

    /** The command's first two words on its first line, cut to a valid snippet name length. */
    static String suggestedName(String command) {
        String[] words = command.strip().lines().findFirst().orElse("").strip().split("\\s+");
        String name = words.length > 1 && !words[1].isEmpty() ? words[0] + " " + words[1] : words[0];
        return name.length() > Snippet.MAX_NAME ? name.substring(0, Snippet.MAX_NAME) : name;
    }

    @Override public PaletteStep step(PaletteRow row, PaletteVerb verb, PaletteContext context) {
        if (!verb.equals(SAVE) || snippets == null || !(row.token() instanceof ShellHistoryEntry entry)) return null;
        String shown = entry.command().replace("\r", "").replace("\n", " ↵ ");
        return new PaletteStep("Save as snippet: " + shown,
            List.of(new PaletteStep.Field("name", "Name", suggestedName(entry.command()))),
            (values, done) -> snippets.append(values.get("name"), entry.command(), (saved, error) ->
                done.accept(error != null ? PaletteStep.Result.error(error)
                    : PaletteStep.Result.reopen(SNIPPETS_ID, SnippetsScope.rowId(saved.name())))));
    }

    private record Ranked(ShellHistoryEntry entry, int tier, int directory, int position) {}

    @Override public PaletteResults search(String query, PaletteContext context) {
        ShellHistorySnapshot snapshot = index.snapshot();
        boolean tagged = snapshot.shells().size() > 1;
        String q = CommandSearch.normalize(query);
        if (q.isEmpty()) {
            var rows = new ArrayList<PaletteRow>();
            for (ShellHistoryEntry entry : snapshot.entries()) {
                if (rows.size() == context.maxResults()) break;
                rows.add(row(entry, tagged));
            }
            return new PaletteResults(rows, "Most recent", null);
        }
        String[] tokens = q.split(" ");
        Path cwd = context.target().workingDirectory().get().orElse(null);
        var ranked = new ArrayList<Ranked>();
        List<ShellHistoryEntry> entries = snapshot.entries();
        for (int i = 0; i < entries.size(); i++) {
            ShellHistoryEntry entry = entries.get(i);
            int tier = tier(entry.command().toLowerCase(Locale.ROOT), q, tokens);
            if (tier < 0) continue;
            int directory = cwd != null && cwd.equals(entry.directory()) ? 0 : 1;
            ranked.add(new Ranked(entry, tier, directory, i));
        }
        ranked.sort(Comparator.comparingInt(Ranked::tier).thenComparingInt(Ranked::directory).thenComparingInt(Ranked::position));
        var rows = new ArrayList<PaletteRow>();
        for (Ranked item : ranked) {
            if (rows.size() == context.maxResults()) break;
            rows.add(row(item.entry(), tagged));
        }
        return new PaletteResults(rows, null, null);
    }

    /** -1 when a token is missing; 0 whole-query prefix; 1 every token at a word boundary; 2 plain substrings. */
    static int tier(String command, String query, String[] tokens) {
        for (String token : tokens) if (!command.contains(token)) return -1;
        if (command.startsWith(query)) return 0;
        for (String token : tokens) if (!atWordBoundary(command, token)) return 2;
        return 1;
    }

    private static boolean atWordBoundary(String command, String token) {
        for (int at = command.indexOf(token); at >= 0; at = command.indexOf(token, at + 1))
            if (at == 0 || !Character.isLetterOrDigit(command.charAt(at - 1))) return true;
        return false;
    }

    // Save as snippet needs no live target; the paste verbs still refuse a dead one.
    @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteContext context) {
        return row.token() instanceof ShellHistoryEntry
            && (verb.equals(SAVE) || context.target().live().getAsBoolean());
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) {
        if (!(row.token() instanceof ShellHistoryEntry entry)) return;
        context.target().paste().accept(entry.command());
        if (verb.equals(PASTE_RUN)) context.target().sendReturn().run();
    }

    @Override public CommandRegistry.Subscription onChanged(Runnable listener) { return index.onChanged(listener); }

    private static PaletteRow row(ShellHistoryEntry entry, boolean tagged) {
        String title = entry.command().replace("\r", "").replace("\n", " ↵ ");
        String tag = tagged ? String.join("/", new TreeSet<>(entry.shells())) : null;
        String detail = entry.directory() == null ? null : entry.directory().toString();
        return new PaletteRow(rowId(entry.command()), title, detail, tag, null, true, entry);
    }

    static String rowId(String command) {
        return "entry." + Integer.toHexString(command.hashCode()) + "." + command.length();
    }
}
