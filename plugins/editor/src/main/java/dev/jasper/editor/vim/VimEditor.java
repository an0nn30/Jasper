package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.fife.ui.rtextarea.CaretStyle;
import org.fife.ui.rtextarea.ConfigurableCaret;
import org.fife.ui.rtextarea.RTextArea;

/**
 * Vim in one tab (spec 1.1): the mode, the parser, the insert session, the remembered column, the tab's local
 * options and its messages. Attaching it to a {@link VimTextArea} turns Vim on for that area; {@link #close} turns
 * it off and leaves the text as it is. UI thread only.
 */
public final class VimEditor {
    private static final System.Logger LOG = System.getLogger(VimEditor.class.getName());

    private final Vim vim;
    private final VimTextArea area;
    private final Buffer buffer;
    private final Options options;
    private final Machine machine;
    private final InsertMode insert;
    private final CommandLine commandLine = new CommandLine(this);
    private final Ex ex = new Ex(this);
    private final Typeahead typeahead = new Typeahead(this);
    /** How many keys have run, so {@link #feed} knows whether one did. */
    private long steps;
    /** A question waiting for its answer key (Vim's ask_yesno and the {@code :s} confirmation): its text and what takes the key. */
    private String promptText;
    private java.util.function.Consumer<KeyInput> promptHandler;
    /** The output panel: what a command line said, shown until a key is typed (Vim's hit-enter prompt). */
    private List<OutputLine> output = List.of();
    /** While a command line runs, what it says, gathered for the output panel; null otherwise. */
    private List<OutputLine> collecting;
    /** Every message given, for the corpus's output comparison. */
    private final List<String> messageLog = new ArrayList<>();
    private final Marks marks;
    private final List<Runnable> listeners = new ArrayList<>();
    private final CaretListener caretListener = event -> caretMoved();
    private final CaretStyle originalCaret;
    private Mode mode = Mode.NORMAL;
    /** The display column vertical motions aim for; {@link Integer#MAX_VALUE} for the end of the line, -1 to take it from the cursor. */
    private int curswant = -1;
    private String message = "";
    private boolean messageError;
    private int bells;
    private boolean busy;
    private EditorDefaults defaults;
    /** In Visual mode, the other end of the area (Vim's VIsual). */
    private Pos visualAnchor;
    private final Highlights highlights;
    private final View view = new View(this);
    private final ViewportSync viewport;
    private final Folds folds;
    private final LineNumbers lineNumbers;
    /** The text area's word-wrapping, put back when Vim turns off (Vim wraps at any character). */
    private final boolean originalWrapStyleWord;
    /** The text area's fold-aware navigation, put back when Vim turns off (Vim's cursor may be inside a closed fold). */
    private final javax.swing.text.NavigationFilter originalNavigation;
    /** While a search pattern is typed with 'incsearch', the match it would find ({start, end} offsets), or null. */
    private int[] incsearch;
    /** While {@code :s} asks for confirmation, the match it asks about ({start, end} offsets), or null. */
    private int[] confirmMatch;
    /** While 'incsearch' previews a match, the view as it was when the command line opened (Vim's old_viewstate). */
    private int[] incsearchView;
    /** While the view scrolls to an 'incsearch' match, the match's start stands in for the cursor, which stays put. */
    private Pos previewCursor;
    /** Bumped by every text change, so the 'hlsearch' matches are found again only when needed. */
    private int textVersion;
    private List<Object> highlightedPattern;
    private int highlightedVersion = -1;
    private List<int[]> searchMatches = List.of();
    private boolean highlightPending;
    private java.util.function.Supplier<String> documentName = () -> "[No Name]";
    private java.util.function.BooleanSupplier documentModified = () -> false;
    /** Leaving the tab sets the {@code '"} mark (spec 3.6). */
    private final java.awt.event.FocusListener leaveListener = new java.awt.event.FocusAdapter() {
        @Override public void focusLost(java.awt.event.FocusEvent event) { marks.set('"', cursor()); }
    };
    /** The text exchange's first {@code cx} marked (the plugin's b:exchange), or null. */
    private Exchange.Region exchange;
    /** Whether the tab's 'matchpairs' has pairs its language added. */
    private boolean languagePairs;
    /** The sneak extension's matches, drawn while the cursor stays where the sneak left it. */
    private List<int[]> sneakRanges = List.of();
    private Pos sneakAt;
    private int sneakVersion;
    /** The highlightedyank extension's highlight of the last yank. */
    private final HighlightedYank highlightedYank = new HighlightedYank(this);
    /** A new language gives the tab its line comment as 'commentstring'. */
    private final java.beans.PropertyChangeListener languageListener = event -> languageChanged();
    private final DocumentListener highlightListener = new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent event) { textChanged(); }
        @Override public void removeUpdate(DocumentEvent event) { textChanged(); }
        @Override public void changedUpdate(DocumentEvent event) { }
    };

    /**
     * Turns Vim on for the area.
     *
     * @param vim the global state
     * @param area the tab's text area
     * @param defaults the Editor's option defaults for the tab
     */
    public VimEditor(Vim vim, VimTextArea area, EditorDefaults defaults) {
        this.vim = vim;
        this.area = area;
        this.buffer = new Buffer(area.getDocument());
        this.marks = new Marks(this, area);
        // Another view's editor of the document joins its Vim buffer (pane flexibility spec 6): its buffer-local options
        // are the buffer's, and its window-local ones start as that editor's window's.
        VimEditor sibling = marks.sibling();
        this.options = sibling == null ? vim.options().local() : sibling.options.window();
        this.machine = new Machine(this);
        this.insert = new InsertMode(this);
        this.highlights = new Highlights(area);
        this.folds = new Folds(area);
        this.viewport = new ViewportSync(this, area);
        this.lineNumbers = new LineNumbers(this, area);
        this.originalWrapStyleWord = area.getWrapStyleWord();
        area.setWrapStyleWord(false);
        this.originalNavigation = area.getNavigationFilter();
        area.setNavigationFilter(null);
        area.getDocument().addDocumentListener(highlightListener);
        area.addFocusListener(leaveListener);
        originalCaret = area.getCaret() instanceof ConfigurableCaret caret ? caret.getStyle() : CaretStyle.VERTICAL_LINE_STYLE;
        if (sibling != null) {
            // The buffer has its options already, which the Editor's defaults and the language set when it opened.
            this.defaults = sibling.defaults;
            languagePairs = sibling.languagePairs;
        }
        editorDefaults(defaults);
        if (sibling == null) languageChanged();
        area.addPropertyChangeListener(org.fife.ui.rsyntaxtextarea.RSyntaxTextArea.SYNTAX_STYLE_PROPERTY, languageListener);
        options.onChanged(this::syncArea);
        syncArea();
        area.attach(this);
        area.addCaretListener(caretListener);
        busy = true;
        try {
            area.select(area.getCaretPosition(), area.getCaretPosition());
            setCursor(cursor());
        } finally { busy = false; }
        look();
    }

    /** Turns Vim off: the caret and input methods return to the Editor's; the text is untouched. */
    public void close() {
        typeahead.clear();
        ex.abort();
        promptHandler = null;
        output = List.of();
        insert.abort();
        machine.abort();
        area.removeCaretListener(caretListener);
        area.removePropertyChangeListener(org.fife.ui.rsyntaxtextarea.RSyntaxTextArea.SYNTAX_STYLE_PROPERTY, languageListener);
        area.attach(null);
        area.setCaretStyle(RTextArea.INSERT_MODE, originalCaret);
        area.enableInputMethods(true);
        marks.close();
        highlightedYank.close();
        area.getDocument().removeDocumentListener(highlightListener);
        area.removeFocusListener(leaveListener);
        area.setWrapStyleWord(originalWrapStyleWord);
        area.setNavigationFilter(originalNavigation);
        viewport.close();
        lineNumbers.close();
        highlights.clear();
        options.close();
        vim.forget(this);
        buffer.close();
    }

    /**
     * This editor's view closes while {@code heir}'s shows the same file: its uppercase marks become {@code heir}'s, as
     * Vim's file marks are the file's (pane flexibility spec 6). Without it, {@link #close} gives them to another editor
     * of the file.
     *
     * @param heir the editor of another view of the same file
     */
    public void leaveMarksTo(VimEditor heir) { vim.moveGlobalMarks(this, heir); }

    /**
     * This editor's view was split from {@code source}'s (Vim's win_init): it starts with its jumplist, its {@code ''}
     * mark, its place in the change list and its window-local options.
     *
     * @param source the editor of the view of the same file this one was split from
     */
    public void splitFrom(VimEditor source) {
        if (!marks.sameBuffer(source.marks)) return;
        marks.splitFrom(source.marks);
        options.windowFrom(source.options);
    }

    /**
     * The tab's language changed: 'commentstring' becomes its line comment ({@code //%s}, {@code <!--%s-->}), as a
     * filetype plugin sets it in Vim; a language with none keeps the value it had. The pairs a language's filetype
     * plugin adds to 'matchpairs' ({@code setlocal matchpairs+=<:>} for HTML) are added to the global value.
     */
    private void languageChanged() {
        List<String> added = MatchitLanguages.of(this).addedPairs();
        if (!added.isEmpty() || languagePairs) {
            List<String> pairs = new ArrayList<>(List.of(vim.options().string("matchpairs").split(",")));
            for (String pair : added) if (!pairs.contains(pair)) pairs.add(pair);
            options.setLocal("matchpairs", String.join(",", pairs));
            languagePairs = !added.isEmpty();
        }
        if (!(area.getDocument() instanceof org.fife.ui.rsyntaxtextarea.RSyntaxDocument document)) return;
        String[] tokens = document.getLineCommentStartAndEnd(0);
        if (tokens == null || tokens[0] == null) return;
        options.setLocal("commentstring", tokens[0].strip() + "%s" + (tokens[1] == null ? "" : tokens[1].strip()));
    }

    /**
     * The Editor's defaults changed (its tab size, or Tabs/Spaces): the tab's options follow.
     *
     * @param changed the new defaults
     */
    public void editorDefaults(EditorDefaults changed) {
        if (changed.equals(defaults)) return;
        defaults = changed;
        options.setLocal("tabstop", changed.tabstop());
        options.setLocal("shiftwidth", changed.tabstop());
        options.setLocal("expandtab", changed.expandtab());
        options.setLocal("autoindent", changed.autoindent());
        options.setLocal("wrap", changed.wrap());
    }

    /** The text was replaced (a reload): any command or insert in progress is dropped. */
    public void textReplaced() {
        typeahead.clear();
        ex.abort();
        promptHandler = null;
        output = List.of();
        insert.abort();
        machine.abort();
        mode = Mode.NORMAL;
        visualAnchor = null;
        marks.clear();
        busy = true;
        try { setCursor(cursor()); }
        finally { busy = false; }
        look();
        fire();
    }

    /**
     * The handler rule (spec 2.2): gives the key to Vim when Vim takes it.
     *
     * @param key a key from the text area
     * @return whether Vim took it
     */
    public boolean offer(KeyInput key) {
        if (commandLine.active() || promptHandler != null || !output.isEmpty()) {
            // The command line takes every key but the ⌘ ones.
            if ((key.modifiers() & KeyInput.META) != 0) return false;
            feed(key);
            return true;
        }
        boolean mine = switch (vim.handlers().handler(key, handlerMode())) {
            case VIM -> true;
            case IDE -> false;
            // Keys that wait for the rest of a mapping, and a key that starts one (even a ⌘ key), are Vim's.
            case DEFAULT -> typeahead.waiting() || startsMapping(key)
                || (key.modifiers() & KeyInput.META) == 0 && (mode.inserting() ? insert.wants(key) : machine.wants(key));
        };
        if (mine) feed(key);
        return mine;
    }

    /** @return whether a mapping for the mode Vim is in starts with the key */
    private boolean startsMapping(KeyInput key) {
        int state = mapState(key);
        return state != 0 && !vim.mappings().starting(state, key).isEmpty();
    }

    private char handlerMode() {
        return switch (mode()) {
            case INSERT, REPLACE -> 'i';
            case OPERATOR_PENDING -> 'o';
            case VISUAL, VISUAL_LINE, VISUAL_BLOCK -> 'x';
            default -> 'n';
        };
    }

    /** @return whether the mode is Insert or Replace, where keys Vim declines go on to the text area */
    boolean editing() { return mode.inserting(); }

    /**
     * For a key {@link #offer} declined: whether it goes on to the text area anyway. In Insert and Replace mode every
     * declined key does; elsewhere only a ⌘ key or one a {@code sethandler} entry gives to the Editor. The text area
     * also passes on a key bound to one of the Editor's own commands (spec 2.2).
     */
    boolean passesOn(KeyInput key) {
        if (commandLine.active() || promptHandler != null || !output.isEmpty()) return (key.modifiers() & KeyInput.META) != 0;
        if (mode.inserting() || (key.modifiers() & KeyInput.META) != 0) return true;
        return vim.handlers().handler(key, handlerMode()) == KeyHandlers.Handler.IDE;
    }

    /** A key the text area held back outside Insert mode: Vim has no meaning for it, so the bell rings. */
    void declined() {
        bells++;
        fire();
    }

    /** @return the window onto the text (topline, size, 'wrap') */
    View view() { return view; }

    /**
     * The user scrolled the text area to show {@code topline} first: the view follows, and the cursor moves inside
     * the window when it was scrolled out, keeping its column, as with Vim's scroll wheel.
     */
    void viewScrolled(int topline) {
        if (mode.inserting() || commandLine.active()) {
            view.topline(topline);
            view.compBotline();
            return;
        }
        view.topline(topline);
        view.compBotline();
        int lnum = cursor().line + 1;
        int target = lnum < view.topline ? view.topline : lnum >= view.botline && view.botline <= buffer.count() ? view.botline - 1 : lnum;
        boolean was = busy;
        if (target != lnum) {
            busy = true;
            try { setCursor(new Pos(target - 1, wantedColumn(target - 1)), mode.visual()); }
            finally { busy = was; }
        }
        if (!was) fire();
    }

    /**
     * Another view of the document edited it, and the viewport goes back to {@code position}, which shows the line this
     * view showed first (pane flexibility spec 6). The cursor, which moved with its text as Vim's mark_adjust moves
     * another window's w_cursor, stays on it: when that leaves it outside the window, the window scrolls as
     * update_topline scrolls it into sight, with 'scrolloff'.
     *
     * @param position the viewport position that shows the view's first line where it was shown
     */
    void keepPlace(java.awt.Point position) {
        int top = viewport.keep(position);
        if (top < 0) return;
        view.topline(top);
        view.compBotline();
        int lnum = cursor().line + 1;
        boolean outside = lnum < view.topline || lnum >= view.botline && view.botline <= buffer.count();
        if (outside && !commandLine.active()) {
            view.updateTopline();
            view.cursColumns();
            viewport.show();
        }
        if (!busy) fire();
    }

    /** The viewport's size changed the view's: outside a command, the view scrolls to keep the cursor shown. */
    void viewResized() {
        // The constructor sizes the view before it has its viewport sync.
        if (viewport == null || busy || commandLine.active()) return;
        view.updateTopline();
        view.cursColumns();
        viewport.show();
    }

    /** @return the tab's code folds */
    Folds folds() { return folds; }

    /** @return the remembered display column, computed from the cursor when it was forgotten (Vim's w_curswant) */
    int columnWanted() { return want(false); }

    /** Rings the bell without failing the command (a key the command line has no use for). */
    void bell() { bells++; }

    /** @return the command line, active while {@code :}, {@code /} or {@code ?} reads a line */
    CommandLine commandLine() { return commandLine; }

    /** A failing Insert-mode key: the bell rings and the insert goes on, as Vim's ins_del and ins_bs beep (spec 2.3). */
    void insertKeyFailed(VimFailure failure) {
        bells++;
        if (!failure.getMessage().isEmpty()) message(failure.getMessage(), true);
        fire();
    }

    /**
     * A key typed: it goes through the typeahead, where mappings apply (spec 7.2), and then runs, unless it starts a
     * mapping and waits for the keys after it.
     *
     * @param key the key
     */
    public void feed(KeyInput key) {
        long before = steps;
        typeahead.typed(key);
        // Nothing ran (keys wait for the rest of a mapping, or a mapping did nothing): the Vim bar shows what waits.
        if (steps == before || typeahead.waiting()) fire();
    }

    /** The keys waiting for the rest of a mapping resolve now, as when 'timeoutlen' runs out. */
    void timeoutNow() {
        typeahead.timeout();
        fire();
    }

    /** @return the typeahead, where keys wait and mappings and macros insert theirs */
    Typeahead typeahead() { return typeahead; }

    /**
     * The mapping mode for the next key, as Vim's get_real_state, or 0 when the key is not mapped (no_mapping): a
     * question's answer, the key after one that reads an argument ({@code f}, {@code "}, {@code i_CTRL-V}), the second
     * key of a two-key command ({@code gx}), and {@code 0} after count digits. At the output panel's hit-enter
     * prompt Normal-mode mappings apply to any key but {@code <CR>} and {@code <Space>}.
     */
    int mapState(KeyInput next) {
        if (promptHandler != null) return 0;
        if (!output.isEmpty()) return next.is("<CR>") || next.is(" ") ? 0 : Mappings.NORMAL;
        if (commandLine.active()) return commandLine.readingArgument() ? 0 : Mappings.CMDLINE;
        if (mode.inserting()) return insert.readingArgument() ? 0 : Mappings.INSERT;
        if (!machine.mappable() || next.plainChar() == '0' && machine.counting()) return 0;
        if (mode.visual()) return Mappings.VISUAL;
        return machine.operatorPending() ? Mappings.OP_PENDING : Mappings.NORMAL;
    }

    /** Mapped keys failed (E223) or were interrupted: the bell, and the message if there is one, as for a failing command. */
    void mappingFailed(VimFailure failure) {
        bells++;
        if (!failure.getMessage().isEmpty()) message(failure.getMessage(), true);
        fire();
    }

    /**
     * Runs one key from the typeahead. A failing command rings the bell and drops the keys of the mapping or macro
     * running (spec 2.3); an unexpected exception cancels the command, closes its undo step and returns to Normal
     * mode (spec 10).
     */
    void step(KeyInput key) {
        steps++;
        busy = true;
        message = "";
        messageError = false;
        boolean vertical = key.is("<Up>") || key.is("<Down>");
        if (mode.inserting() && !vertical) curswant = -1;
        try {
            dispatch(key);
        } catch (VimFailure failure) {
            if (!failure.quiet) bells++;
            if (!failure.getMessage().isEmpty()) message(failure.getMessage(), true);
            // beep_flush and emsg drop the keys of a running mapping or macro; a beep in Insert mode does not.
            if (!mode.inserting() || !failure.getMessage().isEmpty()) typeahead.flush();
            if (!mode.inserting()) {
                machine.reset();
                machine.resumeIfInsertNormal();
            }
        } catch (RuntimeException bug) {
            LOG.log(System.Logger.Level.ERROR, "Vim command failed", bug);
            typeahead.clear();
            ex.abort();
            promptHandler = null;
            insert.abort();
            machine.abort();
            mode = Mode.NORMAL;
            message("Internal error; the command was cancelled", true);
        } finally {
            busy = false;
            if (!commandLine.active()) marks.checkpcmark(cursor());
            if (mode == Mode.NORMAL || mode == Mode.OPERATOR_PENDING) setCursor(cursor());
            // While the command line reads a pattern the cursor stays and the view shows 'incsearch''s match.
            if (!commandLine.active()) {
                view.updateTopline();
                view.cursColumns();
                viewport.show();
            }
            look();
            fire();
            List<Runnable> after = List.copyOf(afterKey);
            afterKey.clear();
            for (Runnable work : after) work.run();
        }
    }

    /** Runs one key without the failure handling, for {@code .}, a count's repeat and {@code :normal}. */
    void dispatch(KeyInput key) {
        String action = key.actionId();
        if (action != null) {
            // <Action>(id) runs the Editor's command where it is reached, in any mode (IdeaVim).
            if (!vim.host().actions().run().test(this, action)) throw new VimFailure("Not an Editor command: " + action);
            return;
        }
        if (promptHandler != null) {
            answer(key);
            return;
        }
        if (!output.isEmpty()) {
            // The hit-enter prompt: these keys only take the output away; any other key then runs as typed.
            output = List.of();
            if (key.is("<CR>") || key.is(" ") || key.is("<Esc>") || key.is("<C-j>") || key.is("<kEnter>")) return;
        }
        if (commandLine.active()) commandLine.key(key);
        else if (mode.inserting()) insert.key(key);
        else machine.key(key);
    }

    /** Text an input method committed in Insert mode, recorded for {@code .}. */
    void typedByInputMethod(String text) {
        text.codePoints().forEach(cp -> vim.macros().typed(KeyInput.of(cp)));
        insert.typedByInputMethod(text);
    }

    /** @return the mode */
    public Mode mode() {
        if (commandLine.active() || promptHandler != null) return Mode.COMMAND_LINE;
        return mode == Mode.NORMAL && machine.operatorPending() ? Mode.OPERATOR_PENDING : mode;
    }

    /** @return the partly typed command ('showcmd') */
    public String showcmd() {
        if (!options.flag("showcmd")) return "";
        String pending = machine.showcmd();
        // Keys waiting for the rest of a mapping show after the partly typed command.
        if (typeahead.waiting()) return pending + KeyInput.format(typeahead.waitingKeys());
        return pending.isEmpty() && mode.visual() && visualAnchor != null ? visualSize() : pending;
    }

    /**
     * The Visual area's size as Vim's showcmd shows it: {@code 3x5} (lines by display columns) for a block, the line
     * count for a linewise or multi-line area, else the character count, with the UTF-8 byte count after it when it
     * differs ({@code 3-4}).
     */
    private String visualSize() {
        Pos cursor = cursor();
        int lines = Math.abs(cursor.line - visualAnchor.line) + 1;
        if (mode == Mode.VISUAL_BLOCK) {
            int ts = options.number("tabstop");
            int left = Math.min(Visual.startVcol(buffer, visualAnchor, ts), Visual.startVcol(buffer, cursor, ts));
            int right = Math.max(Visual.endVcol(buffer, visualAnchor, ts), Visual.endVcol(buffer, cursor, ts));
            return lines + "x" + (right - left + 1);
        }
        if (mode == Mode.VISUAL_LINE || lines > 1) return String.valueOf(lines);
        String line = buffer.line(cursor.line);
        int from = Math.min(cursor.col, visualAnchor.col), to = Math.max(cursor.col, visualAnchor.col);
        to = to < line.length() ? Text.next(line, to) : line.length();
        String part = line.substring(Math.min(from, line.length()), to);
        int chars = part.codePointCount(0, part.length()), bytes = part.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (from >= line.length()) chars = bytes = 1;
        return bytes == chars ? String.valueOf(chars) : chars + "-" + bytes;
    }

    /** @return the last message, or empty */
    public String message() { return message; }

    /** @return whether the message is an error */
    public boolean messageIsError() { return messageError; }

    /** @return how many times the bell has rung; the Vim bar flashes when it changes */
    public int bells() { return bells; }

    /** @param listener runs after every key and every change of mode or message */
    public void addListener(Runnable listener) { listeners.add(listener); }

    /** @param listener no longer runs */
    public void removeListener(Runnable listener) { listeners.remove(listener); }

    /**
     * @return what the Vim bar shows when there is no message: the mode with 'showmode', then {@code recording @q}
     *     while a macro is recorded (showmode)
     */
    public String modeText() {
        String label = showmode() ? mode().label() : "";
        char recording = vim.macros().recording();
        return recording == 0 ? label : label + "recording @" + recording;
    }

    /** @return whether the Vim bar shows the mode ('showmode') */
    public boolean showmode() { return options.flag("showmode"); }

    /** @return the tab's options */
    public Options options() { return options; }

    Vim vim() { return vim; }

    VimTextArea area() { return area; }

    Buffer buffer() { return buffer; }

    Machine machine() { return machine; }

    Marks marks() { return marks; }

    /** @return in Visual mode, the other end of the area; null otherwise */
    Pos visualAnchor() { return visualAnchor == null ? null : visualAnchor.copy(); }

    void visualAnchor(Pos anchor) { visualAnchor = anchor == null ? null : anchor.copy(); }

    /** @return the remembered display column; {@link Integer#MAX_VALUE} after {@code $}, -1 when forgotten */
    int curswant() { return curswant; }

    void curswant(int value) { curswant = value; }

    InsertMode insert() { return insert; }

    void mode(Mode value) { mode = value; }

    void message(String text, boolean error) {
        messageLog.add(text);
        if (error && !quiet) vim.remember(text);
        if (collecting != null) {
            collecting.add(new OutputLine(text, error));
            return;
        }
        message = text;
        messageError = error;
    }

    /** An informational line, such as one line of a listing; while a command line runs it goes to the output. */
    void say(String text) { message(text, false); }

    /**
     * msgmore: after lines were added or deleted, "3 more lines" or "3 fewer lines" when more than 'report' lines
     * changed; not while {@code :global} runs, which reports once at its end.
     */
    void msgmore(int n) {
        if (ex.globalBusy()) return;
        int pn = Math.abs(n);
        if (pn <= options.number("report")) return;
        say(n > 0 ? pn + (pn == 1 ? " more line" : " more lines") : pn + (pn == 1 ? " line less" : " fewer lines"));
    }

    /** @return every message given so far, for tests */
    List<String> messageLog() { return messageLog; }

    /** @return the Ex command line */
    Ex ex() { return ex; }

    /** While a sourced file's line runs: what it says goes nowhere, and its errors do not join the history yet. */
    private boolean quiet;

    /**
     * Runs work with everything it says gathered and returned instead of shown (a line of a sourced file).
     *
     * @return what it said
     */
    List<OutputLine> quietly(Runnable work) {
        List<OutputLine> before = collecting;
        boolean wasQuiet = quiet;
        collecting = new ArrayList<>();
        quiet = true;
        try {
            work.run();
            return collecting;
        } finally {
            collecting = before;
            quiet = wasQuiet;
        }
    }

    /**
     * Starts gathering what is said for the output panel; returns the list gathered before, for {@link #stopCollecting}.
     * A command line run from inside another one ({@code :normal :s…}) adds to the outer one's output.
     */
    List<OutputLine> startCollecting() {
        List<OutputLine> before = collecting;
        if (collecting == null) collecting = new ArrayList<>();
        return before;
    }

    /**
     * Stops gathering: at the outermost level one line becomes the message and more lines the output panel, which
     * waits for a key (Vim's hit-enter prompt). After a failed command line only the last line is kept, as Vim
     * does not wait then.
     */
    void stopCollecting(List<OutputLine> before, boolean failed) {
        if (before != null) return;
        List<OutputLine> said = collecting;
        collecting = null;
        // After an error Vim does not wait for a key: the last line stays as the message.
        if (failed && !said.isEmpty()) said = List.of(said.getLast());
        if (said.size() == 1) {
            message = said.getFirst().text();
            messageError = said.getFirst().error();
        } else if (said.size() > 1) {
            output = List.copyOf(said);
            message = "";
            messageError = false;
        }
    }

    /**
     * A line of the output panel.
     *
     * @param text the line
     * @param error whether it is an error message
     */
    public record OutputLine(String text, boolean error) { }

    /** @return the output panel's lines, empty when it is not shown */
    public List<OutputLine> output() { return output; }

    /**
     * Asks a question that one key answers; {@code handler} gets the key. It may ask again. When no question is left
     * open, the command line goes on with what waited for the answer.
     */
    void ask(String question, java.util.function.Consumer<KeyInput> handler) {
        messageLog.add(question);
        promptText = question;
        promptHandler = handler;
    }

    /** @return whether a question waits for its answer */
    boolean prompting() { return promptHandler != null; }

    private void answer(KeyInput key) {
        var handler = promptHandler;
        promptHandler = null;
        promptText = null;
        ex.answered(handler, key);
    }

    /**
     * @return the question waiting for a key, or "Press ENTER or type command to continue" under the output panel;
     *     empty when neither is shown
     */
    public java.util.Optional<String> prompt() {
        if (promptText != null) return java.util.Optional.of(promptText);
        if (!output.isEmpty()) return java.util.Optional.of("Press ENTER or type command to continue");
        return java.util.Optional.empty();
    }

    /** @return the cursor as a position */
    Pos cursor() { return previewCursor != null ? previewCursor.copy() : buffer.pos(area.getCaretPosition()); }

    /** Moves the cursor, clamping it to the text; in Normal mode never past the last character. */
    void setCursor(Pos pos) { setCursor(pos, false); }

    /** @param pastEnd whether the cursor may sit after the last character (operators and Insert mode) */
    void setCursor(Pos pos, boolean pastEnd) {
        int line = Math.max(0, Math.min(pos.line, buffer.count() - 1));
        String text = buffer.line(line);
        int col = Math.max(0, Math.min(pos.col, text.length()));
        boolean normal = mode == Mode.NORMAL && !pastEnd && !machine.operatorPending();
        if (normal && col >= text.length() && !text.isEmpty()) col = Text.last(text);
        setCursorOffset(buffer.offset(line, col));
    }

    /** Moves the cursor in Insert mode, where it may sit after the last character. */
    void setCursorInInsert(Pos pos) { setCursor(pos, true); }

    void setCursorOffset(int offset) {
        boolean was = busy;
        busy = true;
        try { area.setCaretPosition(Math.max(0, Math.min(offset, buffer.length()))); }
        finally { busy = was; }
    }

    /** A motion without an operator: moves, and updates the remembered column (curswant). */
    void moveTo(Target target) {
        setCursor(target.pos());
        switch (target.column()) {
            case RESET -> curswant = -1;
            case END -> curswant = Integer.MAX_VALUE;
            case KEEP -> { }
        }
    }

    /**
     * @return the column a vertical motion lands on in {@code line}, from the remembered column; in Visual mode it
     *     may be the end of the line (coladvance's one_more)
     */
    int wantedColumn(int line) {
        String text = buffer.line(line);
        boolean visual = mode.visual();
        if (curswant == Integer.MAX_VALUE) return visual ? text.length() : Text.last(text);
        int col = Text.columnAt(text, want(false), options.number("tabstop"));
        return col >= text.length() && !text.isEmpty() && !visual ? Text.last(text) : col;
    }

    /** @return the column Insert mode's Up and Down land on: the remembered column, or the end of a shorter line */
    int insertColumn(int line) {
        String text = buffer.line(line);
        if (curswant == Integer.MAX_VALUE) return text.length();
        return Text.columnAt(text, want(true), options.number("tabstop"));
    }

    /** @return whether the remembered column is beyond the cursor, as after {@code $} */
    boolean wantsPastEnd() {
        if (curswant == Integer.MAX_VALUE) return true;
        Pos p = cursor();
        return curswant > Text.virtcol(buffer.line(p.line), p.col, options.number("tabstop"));
    }

    /** The next vertical move takes its column from the cursor (Vim's w_set_curswant). */
    void forgetColumn() { curswant = -1; }

    /** @return the remembered display column, set from the cursor if forgotten; in Normal mode a tab counts at its end */
    private int want(boolean inserting) {
        if (curswant >= 0) return curswant;
        int ts = options.number("tabstop");
        Pos p = cursor();
        String text = buffer.line(p.line);
        int vcol = Text.virtcol(text, p.col, ts);
        if (!inserting && p.col < text.length() && text.charAt(p.col) == '\t') vcol += Text.width(text, p.col, vcol, ts) - 1;
        curswant = vcol;
        return vcol;
    }

    void beginUndoStep() {
        marks.newChange();
        Pos p = cursor();
        area.vimUndo().beginStep(p.line, p.col);
    }

    void endUndoStep() { area.vimUndo().endStep(); }

    /** Opens an undo step that the commands run from a mapping or macro share ({@link #endUndoStep} closes it). */
    void beginUndoGroup() {
        Pos p = cursor();
        area.vimUndo().beginStep(p.line, p.col);
    }

    /** {@code u}: undoes {@code count} steps, placing the cursor as Vim's u_undoredo does. */
    void undo(int count) { undoRedo(count, true); }

    /** {@code CTRL-R}. */
    void redo(int count) { undoRedo(count, false); }

    /**
     * {@code :undo N}: undoes or redoes to the text after change N (0: before the first). Jasper keeps one line of
     * history, so only the changes on it can be reached.
     */
    void undoTo(int target) {
        VimUndoManager manager = area.vimUndo();
        List<Integer> done = manager.doneSeqs(), undone = manager.undoneSeqs();
        int current = done.isEmpty() ? 0 : done.getLast();
        if (target == current) return;
        if (target < current) {
            if (target != 0 && !done.contains(target)) throw new VimFailure("E830: Undo number " + target + " not found");
            int count = 0;
            for (int i = done.size() - 1; i >= 0 && done.get(i) > target; i--) count++;
            undo(count);
        } else {
            if (!undone.contains(target)) throw new VimFailure("E830: Undo number " + target + " not found");
            redo(undone.indexOf(target) + 1);
        }
    }

    private void undoRedo(int count, boolean undo) {
        VimUndoManager manager = area.vimUndo();
        for (int n = 0; n < count; n++) {
            if (undo ? !manager.canUndo() : !manager.canRedo()) {
                if (n == 0) throw new VimFailure(undo ? "Already at oldest change" : "Already at newest change");
                break;
            }
            VimUndoManager.Step step = undo ? manager.nextUndo() : manager.nextRedo();
            int[] top = {Integer.MAX_VALUE};
            DocumentListener watch = new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent event) { top[0] = Math.min(top[0], changedLine(event, true)); }
                @Override public void removeUpdate(DocumentEvent event) { top[0] = Math.min(top[0], changedLine(event, false)); }
                @Override public void changedUpdate(DocumentEvent event) { }
            };
            buffer.document().addDocumentListener(watch);
            try {
                if (undo) manager.undo();
                else manager.redo();
            } finally { buffer.document().removeDocumentListener(watch); }
            int line = Math.min(top[0] == Integer.MAX_VALUE ? cursor().line : top[0], buffer.count() - 1);
            Pos cursor;
            if (step != null && step.cursorLine >= 0) {
                if (step.cursorLine + 1 == line && line > 0) line--;
                cursor = step.cursorLine == line ? new Pos(line, step.cursorCol) : new Pos(line, Text.firstNonBlank(buffer.line(line)));
            } else cursor = new Pos(line, Text.firstNonBlank(buffer.line(line)));
            setCursor(cursor);
            curswant = -1;
        }
    }

    /**
     * @return the first line an undo event changed; text inserted at a line's end that starts with a break changes the
     *     next line. An event past the last line's break (a text without a final break passes through one while an
     *     empty last line comes back) changes the line after it.
     */
    private int changedLine(DocumentEvent event, boolean inserted) {
        int line = buffer.document().getDefaultRootElement().getElementIndex(event.getOffset());
        if (line >= buffer.count()) return line;
        if (inserted && event.getOffset() == buffer.end(line) && buffer.charAt(event.getOffset()) == '\n') return line + 1;
        return line;
    }

    /** The caret moved by the mouse or the Editor: in Normal mode it is kept off the end of the line. */
    private void caretMoved() {
        if (busy || mode != Mode.NORMAL || machine.pending()) return;
        // An outside selection (a mouse drag, the Find bar, Select All) becomes a Visual area (spec 3.1).
        if (area.getSelectionStart() != area.getSelectionEnd()) {
            javax.swing.SwingUtilities.invokeLater(this::selectionToVisual);
            return;
        }
        if (area.getSelectionStart() != area.getSelectionEnd()) return;
        Pos p = cursor();
        String text = buffer.line(p.line);
        if (!text.isEmpty() && p.col >= text.length()) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (area.vim().orElse(null) != this) return;
                if (!busy && mode == Mode.NORMAL && area.getSelectionStart() == area.getSelectionEnd()) {
                    busy = true;
                    try { setCursor(cursor()); }
                    finally { busy = false; }
                }
            });
        }
        curswant = -1;
    }

    /** The text area's own selection becomes a charwise Visual area, which Vim then draws. */
    private void selectionToVisual() {
        if (area.vim().orElse(null) != this || busy || mode != Mode.NORMAL) return;
        int start = area.getSelectionStart(), end = area.getSelectionEnd();
        if (start == end) return;
        int dot = area.getCaretPosition();
        int mark = dot == start ? end : start;
        busy = true;
        try {
            // Vim's area includes the character at each end; the text area's selection excludes the one at its end, so
            // that end moves back one character (onto the line's end when it was the line break).
            Pos anchor = selectionEnd(mark, dot);
            Pos head = selectionEnd(dot, mark);
            area.select(dot, dot);
            visualAnchor = anchor;
            mode = Mode.VISUAL;
            setCursor(head, true);
        } finally { busy = false; }
        look();
        fire();
    }

    /** @return one end of a selection as a Visual position: the end that is further in the text steps back a character */
    private Pos selectionEnd(int offset, int other) {
        if (offset <= other) return buffer.pos(offset);
        Pos p = buffer.pos(offset - 1);
        String line = buffer.line(p.line);
        if (p.col < line.length() && Character.isLowSurrogate(line.charAt(p.col)) && p.col > 0) p = new Pos(p.line, p.col - 1);
        return p;
    }

    /** The caret shape and input methods for the mode (spec 5.1 and 2.1). */
    private void look() {
        CaretStyle style = switch (mode()) {
            case INSERT -> CaretStyle.VERTICAL_LINE_STYLE;
            case REPLACE, OPERATOR_PENDING -> CaretStyle.UNDERLINE_STYLE;
            default -> CaretStyle.BLOCK_STYLE;
        };
        area.setCaretStyle(RTextArea.INSERT_MODE, style);
        area.enableInputMethods(mode.inserting());
    }

    private void syncArea() {
        area.setTabSize(options.number("tabstop"));
        area.setTabsEmulated(options.flag("expandtab"));
    }

    private void fire() {
        refreshHighlights();
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    private void textChanged() {
        textVersion++;
        if (highlightPending) return;
        highlightPending = true;
        javax.swing.SwingUtilities.invokeLater(() -> {
            highlightPending = false;
            if (area.vim().orElse(null) == this) refreshHighlights();
        });
    }

    /** @return the text exchange's first {@code cx} marked, or null */
    Exchange.Region exchange() { return exchange; }

    HighlightedYank highlightedYank() { return highlightedYank; }

    /** @param ranges the sneak's matches as {kind 3, start, end}, drawn until the cursor leaves {@code at}, Insert mode starts or the text changes */
    void sneakHighlight(List<int[]> ranges, Pos at) {
        sneakRanges = ranges;
        sneakAt = at.copy();
        sneakVersion = textVersion;
    }

    /** @return a number that changes with every change to the text (Vim's b:changedtick) */
    int textVersion() { return textVersion; }

    /** @param region the text an exchange has marked, highlighted until the second {@code cx}; null forgets it */
    void exchange(Exchange.Region region) {
        exchange = region;
        refreshHighlights();
    }

    /**
     * Draws the Visual area, the 'hlsearch' matches, the incremental-search match, the text exchange marked and the
     * last yank's highlight.
     */
    void refreshHighlights() {
        List<int[]> ranges = new ArrayList<>();
        Search search = vim.search();
        if (options.flag("hlsearch") && search.pattern() != null && !search.highlightHidden()) {
            List<Object> key = List.of(search.pattern(), options.flag("ignorecase"), options.flag("smartcase"), options.string("iskeyword"));
            if (!key.equals(highlightedPattern) || highlightedVersion != textVersion) {
                List<int[]> found = new ArrayList<>();
                for (Search.Match match : search.all(this)) {
                    int start = buffer.offset(match.start()), end = buffer.offset(match.end());
                    // An empty match is drawn one character wide, as Vim does.
                    found.add(new int[] { 1, start, end > start ? end : start + 1 });
                }
                searchMatches = found;
                highlightedPattern = key;
                highlightedVersion = textVersion;
            }
            ranges.addAll(searchMatches);
        }
        ranges.addAll(Exchange.highlight(this, exchange));
        ranges.addAll(highlightedYank.ranges());
        if (!sneakRanges.isEmpty() && (!cursor().equals(sneakAt) || mode.inserting() || sneakVersion != textVersion)) sneakRanges = List.of();
        ranges.addAll(sneakRanges);
        ranges.addAll(Highlights.visual(this));
        if (incsearch != null) ranges.add(new int[] { 0, incsearch[0], incsearch[1] });
        if (confirmMatch != null) ranges.add(new int[] { 0, confirmMatch[0], confirmMatch[1] });
        highlights.show(ranges, highlightedYank.color());
    }

    /** Shows the match {@code :s} asks about ({start, end} offsets), or none. */
    void confirmMatch(int[] match) {
        confirmMatch = match;
        refreshHighlights();
    }

    /** @return the drawn highlights as {kind, start, end}, for tests */
    List<int[]> highlights() { return highlights.shown(); }

    /**
     * 'incsearch': while {@code /} or {@code ?} reads a pattern, the match it would find is drawn and the view scrolls
     * to it from where it was when the command line opened (ex_getln.c's may_do_incsearch_highlighting); the cursor
     * stays where it is.
     */
    void incsearch(char type, String line) {
        incsearch = null;
        if (incsearchView == null) incsearchView = view.viewstate();
        view.viewstate(incsearchView);
        if (options.flag("incsearch") && !line.isEmpty()) {
            Search.Match match = vim.search().preview(this, type, line);
            if (match != null) {
                int start = buffer.offset(match.start()), end = buffer.offset(match.end());
                incsearch = new int[] { start, Math.max(end, start + 1) };
                // The caret itself does not move: a moved caret would scroll back to itself once the key is done.
                previewCursor = match.start().copy();
                try {
                    view.updateTopline();
                    view.cursColumns();
                } finally { previewCursor = null; }
            }
        }
        viewport.show();
        refreshHighlights();
    }

    /**
     * The command line closed: the incremental-search match goes and the view is put back as it was when the line
     * opened (finish_incsearch_highlighting); after the key the view scrolls to the cursor as after any command.
     */
    void incsearchDone() {
        incsearch = null;
        if (incsearchView != null) {
            view.viewstate(incsearchView);
            incsearchView = null;
        }
    }

    /** @return while a command line is typed, its text with the {@code :}, {@code /} or {@code ?} in front; else empty */
    public java.util.Optional<String> commandLineText() {
        return commandLine.active() ? java.util.Optional.of(commandLine.prompt() + commandLine.text()) : java.util.Optional.empty();
    }

    /** @return the command line's cursor, as an index into {@link #commandLineText()} */
    public int commandLineCursor() { return commandLine.cursor() + commandLine.prompt().length(); }

    /**
     * @param name the document's name for {@code <C-g>}
     * @param modified whether the document has unsaved changes
     */
    public void documentInfo(java.util.function.Supplier<String> name, java.util.function.BooleanSupplier modified) {
        documentName = name;
        documentModified = modified;
    }

    String documentName() { return documentName.get(); }

    /** @return the cursor's line, 1-based */
    int cursorLine() { return cursor().line + 1; }

    /** Work for after the key being handled ({@code :q} closes the tab once the key is done); at once outside a key. */
    void afterKey(Runnable work) {
        if (busy) afterKey.add(work);
        else work.run();
    }

    private final List<Runnable> afterKey = new ArrayList<>();

    /** A message given outside a key (a save that completed later): shown at once. */
    void announce(String text) {
        message(text, false);
        if (!busy) fire();
    }

    boolean documentModified() { return documentModified.getAsBoolean(); }
}
