package dev.jasper.editor.ui;

import dev.jasper.editor.associations.FileTypeRowState;
import dev.jasper.editor.associations.FileTypeRows;
import dev.jasper.editor.associations.FileTypeRows.Row;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.FileAssociations;
import dev.jasper.sdk.ui.FileAssociations.Claimability;
import dev.jasper.sdk.ui.FileAssociations.FileTypeStatus;
import dev.jasper.sdk.ui.FileAssociations.Handler;
import dev.jasper.sdk.ui.FileAssociations.Outcome;
import dev.jasper.sdk.ui.FileAssociations.Refusal;
import dev.jasper.sdk.ui.FileAssociations.TypeResult;
import dev.jasper.sdk.ui.FileAssociations.Unavailable;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The Editor settings page's File associations section: the file-type rows with a tick each, Make Jasper the Default
 * (Choose in Windows Settings… on Windows), Restore Previous, and a field that adds an extension. Ticks last while the page
 * is open; the user's extensions are staged as {@code file_association_extensions}, and a user row can be claimed only
 * once its extension is saved. Every status and claimability answer carries its opening and its request's round, and only
 * the newest round of the current opening is shown; after {@link #close()} answers go nowhere. UI thread.
 */
final class FileAssociationsSection {
    static final String INTRO = "editor.associations.intro", UNAVAILABLE = "editor.associations.unavailable", LIST = "editor.associations.list",
        TICK = "editor.associations.tick:", REMOVE = "editor.associations.remove:", BUTTONS = "editor.associations.buttons",
        CLAIM = "editor.associations.claim", RESTORE = "editor.associations.restore", NOTE = "editor.associations.note",
        ADD_ROW = "editor.associations.add.row", FIELD = "editor.associations.add.field", ADD = "editor.associations.add";

    private enum Os {
        MAC("macOS"), WINDOWS("Windows"), LINUX("Linux");
        final String system;
        Os(String system) { this.system = system; }
        static Os of(String name) {
            String os = name.toLowerCase(Locale.ROOT);
            return os.startsWith("windows") ? WINDOWS : os.startsWith("mac") ? MAC : LINUX;
        }
    }

    /** A row as one render shows it: its state is folded once. */
    private record Line(Row row, FileTypeRowState state, boolean unsaved) {
        boolean tickable() {
            return !unsaved && !(state instanceof FileTypeRowState.NotClaimable || state instanceof FileTypeRowState.Checking
                || state instanceof FileTypeRowState.Unavailable);
        }
    }

    private final FileAssociations associations;
    private final Os os;
    private final Supplier<List<String>> userExtensions, savedExtensions;
    private final Consumer<List<String>> stageUserExtensions;
    private final Runnable render;
    private final Map<String, FileTypeStatus> statuses = new HashMap<>();
    private final Map<String, Boolean> touched = new HashMap<>();
    private Map<String, Refusal> refusals = Map.of();
    /** Each row's result text, and the row's state when the call's own status answer came, which a change ends. */
    private final Map<String, String> results = new HashMap<>();
    private final Map<String, FileTypeRowState> resultStates = new HashMap<>();
    private Subscription changes = () -> { };
    private boolean open, busy;
    /** Moves on each open and close, so an answer from an earlier opening is dropped. */
    private long session;
    /** The newest status request and the newest round of claimability requests; older answers are dropped. */
    private long statusRound, claimRound;
    /** The status request a claim or restore made after its answer; that answer and later ones settle its results. */
    private long resultsRound = Long.MAX_VALUE;
    private String typed = "";

    /**
     * @param associations the plugin's file associations
     * @param osName the {@code os.name} the copy follows
     * @param userExtensions the pending user extensions
     * @param savedExtensions the saved user extensions; a user row is claimable only once its extension is saved, so
     *     removing it later is applied and drops any declaration Jasper made for it
     * @param stageUserExtensions stages a new list of user extensions
     * @param render re-renders the page
     */
    FileAssociationsSection(FileAssociations associations, String osName, Supplier<List<String>> userExtensions,
                            Supplier<List<String>> savedExtensions, Consumer<List<String>> stageUserExtensions, Runnable render) {
        this.associations = Objects.requireNonNull(associations, "associations");
        this.os = Os.of(Objects.requireNonNull(osName, "osName"));
        this.userExtensions = Objects.requireNonNull(userExtensions, "userExtensions");
        this.savedExtensions = Objects.requireNonNull(savedExtensions, "savedExtensions");
        this.stageUserExtensions = Objects.requireNonNull(stageUserExtensions, "stageUserExtensions");
        this.render = Objects.requireNonNull(render, "render");
    }

    /** Asks for every row's status and claimability, and follows the system's changes until {@link #close()}. */
    void open() {
        if (open) return;
        open = true;
        session++;
        changes = associations.onChanged(this::changed);
        changed();
    }

    /**
     * Ends the subscription and the background re-checks and forgets the opening's ticks, typed text, results and
     * answers, so the next opening starts from Checking…; answers that arrive later are dropped.
     */
    void close() {
        if (!open) return;
        open = false;
        session++;
        changes.close();
        changes = () -> { };
        touched.clear();
        statuses.clear();
        refusals = Map.of();
        results.clear();
        resultStates.clear();
        typed = "";
        busy = false;
        try {
            associations.status(List.of());
        } catch (IllegalStateException stopped) {
            // The plugin stopped first, which ended its re-checks with it.
        }
    }

    /** Asks for every listed extension's claimability, then its status. */
    private void changed() {
        if (!open || associations.unavailable().isPresent()) return;
        long mine = session, round = ++claimRound;
        List<String> extensions = extensions();
        List<CompletableFuture<Claimability>> answers = extensions.stream().map(associations::claimability).toList();
        CompletableFuture.allOf(answers.toArray(CompletableFuture[]::new)).thenRun(() -> {
            if (mine != session || round != claimRound) return;
            var refused = new HashMap<String, Refusal>();
            for (int i = 0; i < extensions.size(); i++)
                if (answers.get(i).join() instanceof Claimability.NotClaimable not) refused.put(extensions.get(i), not.refusal());
            refusals = Map.copyOf(refused);
            render.run();
        });
        refresh();
    }

    private List<String> extensions() { return rows().stream().flatMap(row -> row.extensions().stream()).toList(); }

    private List<Row> rows() { return FileTypeRows.withUser(userExtensions.get()); }

    /** Asks for every listed extension's status; only the newest request's answer is kept. */
    private void refresh() {
        long mine = session, asked = ++statusRound;
        associations.status(extensions()).thenAccept(answer -> {
            if (mine != session || asked != statusRound) return;
            answer.forEach(status -> statuses.put(status.extension(), status));
            if (asked >= resultsRound) settleResults();
            render.run();
        });
    }

    /** Notes each result's row state at the first answer after its call, and drops a result whose row state changed since. */
    private void settleResults() {
        boolean unavailable = associations.unavailable().isPresent();
        for (Row row : rows()) {
            if (!results.containsKey(row.id())) continue;
            FileTypeRowState now = state(row, unavailable);
            FileTypeRowState then = resultStates.putIfAbsent(row.id(), now);
            if (then != null && !then.equals(now)) {
                results.remove(row.id());
                resultStates.remove(row.id());
            }
        }
    }

    // ---- events

    /**
     * @param event a page event
     * @return whether it was the section's
     */
    boolean handle(UiEvent event) {
        switch (event) {
            case UiEvent.Toggled toggled when toggled.id().startsWith(TICK) -> {
                touched.put(toggled.id().substring(TICK.length()), toggled.checked());
                render.run();
            }
            case UiEvent.Action action when action.id().equals(CLAIM) -> perform(claimable(lines()), false);
            case UiEvent.Action action when action.id().equals(RESTORE) -> perform(restorable(lines()), true);
            case UiEvent.Edited edited when edited.id().equals(FIELD) -> {
                typed = edited.text();
                render.run();
            }
            case UiEvent.Action action when action.id().equals(ADD) -> add();
            case UiEvent.Action action when action.id().startsWith(REMOVE) -> remove(action.id().substring(REMOVE.length()));
            default -> { return false; }
        }
        return true;
    }

    /**
     * Claims or restores, then shows each row's first outcome that is not Done until the next claim or restore, or until
     * a later status answer changes the row's state. A claim that was done for every type of a row drops its tick.
     */
    private void perform(List<String> extensions, boolean restore) {
        if (!open || busy || extensions.isEmpty()) return;
        long mine = session;
        // Who each type had before, as the claim saved it: a restore in Windows Settings names that app.
        Map<String, String> previous = new HashMap<>();
        for (String extension : extensions)
            Optional.ofNullable(statuses.get(extension)).ifPresent(status -> status.types().forEach(type ->
                type.previous().ifPresent(handler -> { if (handler instanceof Handler.Other other) previous.put(type.type(), other.name()); })));
        busy = true;
        results.clear();
        resultStates.clear();
        render.run();
        (restore ? associations.restore(extensions) : associations.claim(extensions)).thenAccept(answer -> {
            if (mine != session) return;
            busy = false;
            for (Row row : rows()) {
                List<TypeResult> own = answer.stream().filter(result -> row.extensions().contains(result.extension())).toList();
                own.stream().map(result -> restore && result.outcome() instanceof Outcome.InSettings && previous.containsKey(result.type())
                        ? "Pick " + previous.get(result.type()) + " for " + result.extension() + " in Windows Settings"
                        : outcomeText(result.outcome(), result.extension(), os))
                    .filter(text -> !text.isEmpty()).findFirst().ifPresent(text -> results.put(row.id(), text));
                if (!restore && !own.isEmpty() && own.stream().allMatch(result -> result.outcome() instanceof Outcome.Done))
                    touched.remove(row.id());
            }
            resultsRound = statusRound + 1;
            refresh();
            render.run();
        });
    }

    private void add() {
        List<String> current = userExtensions.get();
        if (typed.isBlank() || FileTypeRows.addError(typed, current).isPresent() || associations.unavailable().isPresent()) return;
        String extension = FileTypeRows.normalised(typed);
        typed = "";
        stageUserExtensions.accept(Stream.concat(current.stream(), Stream.of(extension)).toList());
        changed();
    }

    private void remove(String rowId) {
        List<String> current = userExtensions.get();
        rows().stream().filter(row -> row.user() && row.id().equals(rowId)).findFirst().ifPresent(row ->
            stageUserExtensions.accept(current.stream().filter(extension -> !row.extensions().contains(extension)).toList()));
    }

    // ---- state

    private List<Line> lines() {
        boolean unavailable = associations.unavailable().isPresent();
        List<String> saved = savedExtensions.get();
        return rows().stream().map(row -> new Line(row, state(row, unavailable),
            row.user() && !saved.containsAll(row.extensions()))).toList();
    }

    private FileTypeRowState state(Row row, boolean unavailable) {
        if (unavailable) return new FileTypeRowState.Unavailable();
        List<FileTypeStatus> answered = row.extensions().stream().map(statuses::get).filter(Objects::nonNull).toList();
        if (answered.size() < row.extensions().size()) return new FileTypeRowState.Checking();
        return FileTypeRowState.of(answered, refusals, os == Os.LINUX);
    }

    private boolean ticked(Line line) {
        return line.tickable() && touched.getOrDefault(line.row().id(), !(line.state() instanceof FileTypeRowState.Jasper));
    }

    /** The ticked rows that are not wholly Jasper's: their extensions that have system types. */
    private List<String> claimable(List<Line> lines) {
        return lines.stream().filter(line -> ticked(line) && !(line.state() instanceof FileTypeRowState.Jasper))
            .flatMap(line -> line.row().extensions().stream())
            .filter(extension -> statuses.containsKey(extension) && !statuses.get(extension).types().isEmpty()).toList();
    }

    private List<String> restorable(List<Line> lines) {
        return lines.stream().filter(line -> ticked(line) && (line.state() instanceof FileTypeRowState.Jasper(boolean back) && back
                || line.state() instanceof FileTypeRowState.Partly(boolean partly) && partly))
            .flatMap(line -> line.row().extensions().stream()).toList();
    }

    // ---- nodes

    /**
     * @param query the Settings search, which highlights the add row
     * @return the section's rows
     */
    List<Nodes.Node> nodes(String query) {
        var intro = new Nodes.Text(INTRO, ControlState.READY, "Choose the file types " + switch (os) {
            case MAC -> "Finder";
            case WINDOWS -> "File Explorer";
            case LINUX -> "your file manager";
        } + " opens with Jasper.", Nodes.TextRole.DESCRIPTION);
        Optional<Unavailable> unavailable = associations.unavailable();
        if (unavailable.isPresent())
            return List.of(intro, new Nodes.Text(UNAVAILABLE, ControlState.READY, unavailableText(unavailable.get()), Nodes.TextRole.BODY));
        List<Line> lines = lines();
        var items = lines.stream().map(this::item).toList();
        var list = new Nodes.ItemList(LIST, ControlState.READY, Nodes.ListKind.DOUBLE, items, Set.of(), "", List.of(), Nodes.SelectionMode.NONE,
            Optional.empty(), OptionalInt.empty(), "", "");
        Stream<Nodes.Node> note = os == Os.WINDOWS ? Stream.of(new Nodes.Text(NOTE, ControlState.READY,
            "Windows asks you to pick Jasper for each type in Settings › Default apps.", Nodes.TextRole.SUPPORTING)) : Stream.empty();
        List<Nodes.Node> buttons = Stream.concat(Stream.<Nodes.Node>of(
            new Nodes.Button(CLAIM, enabled(!busy && !claimable(lines).isEmpty()), os == Os.WINDOWS ? "Choose in Windows Settings…" : "Make Jasper the Default", "",
                Nodes.ButtonKind.PRIMARY, CLAIM, ""),
            new Nodes.Button(RESTORE, enabled(!busy && !restorable(lines).isEmpty()), "Restore Previous", "", Nodes.ButtonKind.SECONDARY, RESTORE, "")),
            note).toList();
        String error = typed.isBlank() ? "" : FileTypeRows.addError(typed, userExtensions.get()).orElse("");
        var field = new Nodes.Field(FIELD, new ControlState(true, true, false, error), Nodes.FieldKind.TEXT, typed, ".ext", "Extension", ADD);
        var addButton = new Nodes.Button(ADD, enabled(!typed.isBlank() && error.isEmpty()), "Add Extension", "", Nodes.ButtonKind.SECONDARY, ADD, "");
        var addRow = new Nodes.FormRow(ADD_ROW, ControlState.READY, "", "", field, Optional.of(addButton), matches(query));
        return List.of(intro, list, new Nodes.Stack(BUTTONS, ControlState.READY, Nodes.LayoutRole.INLINE, buttons), addRow);
    }

    private ListItem item(Line line) {
        Row row = line.row();
        String prefix = "editor.associations.row:" + row.id();
        String extension = row.extensions().getFirst();
        String text = line.unsaved() && !(line.state() instanceof FileTypeRowState.NotClaimable)
            ? "Apply Settings to choose Jasper for " + extension
            : Optional.ofNullable(results.get(row.id())).orElseGet(() -> stateText(line.state(), extension)
                + (noPrevious(line) ? " · " + outcomeText(new Outcome.NoPrevious(), extension, os) : ""));
        var title = new Nodes.Text(prefix + ".title", ControlState.READY, row.name(), Nodes.TextRole.BODY);
        var subtitle = new Nodes.Text(prefix + ".subtitle", ControlState.READY, String.join(", ", row.extensions()) + " · " + text,
            Nodes.TextRole.DESCRIPTION);
        var tick = new Nodes.Toggle(TICK + row.id(), enabled(line.tickable()), "Choose Jasper for " + row.name(), ticked(line),
            TICK + row.id(), true);
        List<MenuItem> menu = row.user()
            ? List.of(new MenuItem("editor.associations.remove.item:" + row.id(), "Remove", "", REMOVE + row.id(), true, false))
            : List.of();
        return new ListItem(row.id(), Optional.empty(), List.of(title), List.of(subtitle), List.of(tick), false, Optional.of(menu));
    }

    /**
     * Whether a Jasper row has nothing to hand back because no app had one of its types before Jasper, which only Linux
     * can hand back: Restore is off for it, and its row says why.
     */
    private boolean noPrevious(Line line) {
        boolean restorable = switch (line.state()) {
            case FileTypeRowState.Jasper jasper -> jasper.restorable();
            case FileTypeRowState.Partly partly -> partly.restorable();
            default -> true;
        };
        return os != Os.LINUX && !restorable && line.row().extensions().stream().map(statuses::get).filter(Objects::nonNull)
            .flatMap(status -> status.types().stream())
            .anyMatch(type -> type.handler() instanceof Handler.Jasper && type.previous().filter(Handler.None.class::isInstance).isPresent());
    }

    private String stateText(FileTypeRowState state, String extension) {
        return switch (state) {
            case FileTypeRowState.Checking checking -> "Checking…";
            case FileTypeRowState.Unavailable unavailable -> "";
            case FileTypeRowState.NotClaimable not -> refusalText(not.refusal(), extension, os);
            case FileTypeRowState.Jasper jasper -> "Jasper";
            case FileTypeRowState.Partly partly -> "Partly Jasper's";
            case FileTypeRowState.Other other -> other.name();
            case FileTypeRowState.Several several -> "Several apps";
            case FileTypeRowState.None none -> "No default app";
            case FileTypeRowState.Unknown unknown -> "Unknown";
        };
    }

    private static String refusalText(Refusal refusal, String extension, Os os) {
        return switch (refusal) {
            case NO_SYSTEM_TYPE -> os.system + " has no file type for " + extension;
            case NOT_TEXT -> os.system + " treats " + extension + " as another kind of file";
            case INVALID -> "Use a dot and up to 16 letters, digits, _, + or -";
            case BROWSER_OWNED -> extension + " opens in Jasper's browser";
        };
    }

    /**
     * What a claim's or restore's outcome reads as; empty for {@code Done}.
     *
     * @param outcome the outcome
     * @param extension its extension
     * @param osName the {@code os.name} the copy follows
     * @return the text
     */
    static String outcomeText(Outcome outcome, String extension, String osName) { return outcomeText(outcome, extension, Os.of(osName)); }

    private static String outcomeText(Outcome outcome, String extension, Os os) {
        return switch (outcome) {
            case Outcome.Done done -> "";
            case Outcome.InSettings settings -> "Pick Jasper for " + extension + " in Windows Settings";
            case Outcome.NoPrevious none -> "No app had this type before Jasper; choose one in " + switch (os) {
                case MAC -> "Finder's Get Info";
                case WINDOWS -> "Windows Settings › Default apps";
                case LINUX -> "your file manager";
            };
            case Outcome.PreviousGone gone -> "The app it had before is no longer installed";
            case Outcome.Refused refused -> refusalText(refused.refusal(), extension, os);
            case Outcome.Failed failed -> os.system + " refused: " + failed.detail();
        };
    }

    private String unavailableText(Unavailable reason) {
        return switch (reason) {
            case DEVELOPMENT_RUN -> "File associations work in an installed Jasper.";
            case NOT_INSTALLED -> os == Os.LINUX ? "Install Jasper's .deb or .rpm package to choose it for file types."
                : "Install Jasper with its installer to choose it for file types.";
            case NO_XDG_MIME -> "Install xdg-utils to choose Jasper for file types.";
            case UNSUPPORTED_SYSTEM -> "This system has no file associations Jasper can set.";
        };
    }

    private static ControlState enabled(boolean enabled) { return new ControlState(true, enabled, false, ""); }

    private static boolean matches(String query) {
        var words = Stream.of(query.toLowerCase(Locale.ROOT).trim().split("\\s+")).filter(word -> !word.isEmpty()).toList();
        return !words.isEmpty() && words.stream().allMatch("add extension file associations"::contains);
    }
}
