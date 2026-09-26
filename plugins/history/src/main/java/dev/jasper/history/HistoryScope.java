package dev.jasper.history;

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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * The History scope: Enter pastes, Cmd/Ctrl+Enter pastes and runs, and, when the Snippets plugin is
 * present, Shift+Enter saves the command as a snippet through a name step.
 */
final class HistoryScope implements PaletteScope {
    static final String ID = "dev.jasper.history.scope";
    static final PaletteVerb PASTE = new PaletteVerb("paste", "Paste");
    static final PaletteVerb PASTE_RUN = new PaletteVerb("paste_run", "Paste and run");
    static final PaletteVerb SAVE = new PaletteVerb("save_snippet", "Save as snippet…");
    static final int MAX_NAME = 128;

    private final ShellHistoryIndex index;
    private final Optional<SnippetService> snippets;
    private final Supplier<Set<String>> trivial;
    private final String openActionId;

    HistoryScope(ShellHistoryIndex index, Optional<SnippetService> snippets, Supplier<Set<String>> trivial, String openActionId) {
        this.index = index; this.snippets = snippets; this.trivial = trivial; this.openActionId = openActionId;
    }

    @Override public ScopeSpec spec() {
        return ScopeSpec.of(ID, "History", "Search shell history",
                snippets.isPresent() ? List.of(PASTE, PASTE_RUN, SAVE) : List.of(PASTE, PASTE_RUN))
            .withDescription("Search shell history and paste or run a command")
            .withMonospaceRows(true).withShortcutActionId(openActionId);
    }

    @Override public void activated(PaletteQuery context) { index.refresh(); }

    /** The command's first two words on its first line, cut to a valid snippet name length. */
    static String suggestedName(String command) {
        String[] words = command.strip().lines().findFirst().orElse("").strip().split("\\s+");
        String name = words.length > 1 && !words[1].isEmpty() ? words[0] + " " + words[1] : words[0];
        return name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
    }

    @Override public Optional<PaletteStep> step(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (!verb.equals(SAVE) || snippets.isEmpty() || !(row.token() instanceof ShellHistoryEntry entry)) return Optional.empty();
        SnippetService service = snippets.get();
        return Optional.of(new PaletteStep("Save as snippet: " + Text.oneLine(entry.command()),
            List.of(new PaletteStep.Field("name", "Name", suggestedName(entry.command()))),
            (values, done) -> service.append(values.get("name"), entry.command(), (saved, error) ->
                done.accept(error.isPresent() || saved.isEmpty() ? PaletteStep.Result.error(error.orElse("Could not save the snippet"))
                    : PaletteStep.Result.reopen(SnippetService.SCOPE_ID, Optional.of(SnippetService.rowId(saved.get().name())), Optional.of(saved.get().name()))))));
    }

    /**
     * True for a short command whose first word is in {@code trivial}; "cd deep/path && build" is real
     * work, and "clearcache" is not "clear". Entries are compared lower case, so the list is not case-sensitive.
     */
    static boolean trivial(String command, Collection<String> trivial) {
        if (trivial.isEmpty()) return false;
        String[] words = command.strip().split("\\s+");
        return words.length <= 2 && trivial.contains(words[0].toLowerCase(Locale.ROOT));
    }

    private record Ranked(ShellHistoryEntry entry, int tier, int directory, int position) { }

    @Override public PaletteResults search(String query, PaletteQuery context) {
        ShellHistorySnapshot snapshot = index.snapshot();
        boolean tagged = snapshot.shells().size() > 1;
        String q = Text.normalize(query);
        if (q.isEmpty()) {
            List<ShellHistoryEntry> ordered = snapshot.entries();
            Set<String> set = trivial.get();
            if (!set.isEmpty()) {
                // A partition, not a score tweak: trivial commands keep their order among themselves
                // and simply follow everything else, so the rule stays predictable.
                var work = new ArrayList<ShellHistoryEntry>();
                var noise = new ArrayList<ShellHistoryEntry>();
                for (ShellHistoryEntry entry : ordered) (trivial(entry.command(), set) ? noise : work).add(entry);
                work.addAll(noise);
                ordered = work;
            }
            var rows = new ArrayList<PaletteRow>();
            for (ShellHistoryEntry entry : ordered) {
                if (rows.size() == context.maxResults()) break;
                rows.add(row(entry, tagged));
            }
            return new PaletteResults(rows, Optional.of("Most recent"), Optional.empty());
        }
        String[] tokens = q.split(" ");
        Path cwd = context.target().flatMap(pane -> pane.info().workingDirectory()).orElse(null);
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
        return PaletteResults.of(rows);
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

    private static boolean live(PaletteQuery context) { return context.target().map(PaneHandle::isOpen).orElse(false); }

    // Save as snippet needs no live target; the paste verbs still refuse a dead one.
    @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        return row.token() instanceof ShellHistoryEntry && (verb.equals(SAVE) || live(context));
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (!(row.token() instanceof ShellHistoryEntry entry)) return;
        context.target().ifPresent(pane -> {
            pane.paste(entry.command());
            if (verb.equals(PASTE_RUN)) pane.sendText("\r");
        });
    }

    @Override public Subscription onChanged(Runnable listener) { return index.onChanged(listener); }

    private static PaletteRow row(ShellHistoryEntry entry, boolean tagged) {
        String tag = tagged ? String.join("/", new TreeSet<>(entry.shells())) : null;
        String detail = entry.directory() == null ? null : entry.directory().toString();
        return PaletteRow.of(rowId(entry.command()), Text.oneLine(entry.command())).withDetail(detail).withTag(tag).withToken(entry);
    }

    static String rowId(String command) {
        return "entry." + Integer.toHexString(command.hashCode()) + "." + command.length();
    }
}
