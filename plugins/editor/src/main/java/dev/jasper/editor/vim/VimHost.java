package dev.jasper.editor.vim;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * What the Vim engine needs from the Editor (spec 1.1): the clipboard, the Editor's tabs as Vim's buffers, and its
 * commands for {@code <Action>(id)} and {@code :action}.
 *
 * @param readClipboard reads {@code '+'} (the clipboard) or {@code '*'} (the selection, or the clipboard where there
 *     is none); empty when it holds no text or cannot be read
 * @param writeClipboard writes {@code '+'} or {@code '*'}
 * @param documents the tabs, for the file, buffer and tab commands
 * @param actions the Editor's commands, for {@code <Action>(id)}, {@code :action} and {@code :actionlist}
 */
public record VimHost(Function<Character, Optional<String>> readClipboard, BiConsumer<Character, String> writeClipboard, Documents documents,
                      Actions actions) {
    /**
     * A host with a clipboard and tabs and no commands.
     *
     * @param readClipboard reads {@code '+'} or {@code '*'}
     * @param writeClipboard writes {@code '+'} or {@code '*'}
     * @param documents the tabs
     */
    public VimHost(Function<Character, Optional<String>> readClipboard, BiConsumer<Character, String> writeClipboard, Documents documents) {
        this(readClipboard, writeClipboard, documents, Actions.NONE);
    }

    /**
     * A host with a clipboard and no tabs.
     *
     * @param readClipboard reads {@code '+'} or {@code '*'}
     * @param writeClipboard writes {@code '+'} or {@code '*'}
     */
    public VimHost(Function<Character, Optional<String>> readClipboard, BiConsumer<Character, String> writeClipboard) {
        this(readClipboard, writeClipboard, Documents.NONE);
    }

    /** @return a host with no clipboard and no tabs: reads are empty and writes are dropped */
    public static VimHost detached() { return new VimHost(register -> Optional.empty(), (register, text) -> { }); }

    /**
     * An Editor command a mapping or {@code :action} can run (spec 7.2).
     *
     * @param id its id, as the Editor's commands name it ({@code save}, {@code find})
     * @param title what menus call it
     */
    public record Action(String id, String title) { }

    /**
     * The Editor's commands, on the UI thread.
     *
     * @param list every command, in the Editor's order
     * @param run runs a command, for the tab Vim is in; false when no command has the id
     */
    public record Actions(Supplier<List<Action>> list, BiPredicate<VimEditor, String> run) {
        /** No commands: every id is unknown. */
        public static final Actions NONE = new Actions(List::of, (editor, id) -> false);
    }

    /**
     * An Editor tab as a Vim buffer (spec 6.2).
     *
     * @param number its buffer number, which stays while the tab is open
     * @param name what {@code :ls} and the messages call it: the file's location, or the untitled tab's name
     * @param file its local file, when it has one (an untitled tab opened with {@code :e newfile} has the file it
     *     will be saved to)
     * @param current whether it is the tab the command runs in
     * @param alternate whether it is the tab that was selected before ({@code #})
     * @param modified whether it has unsaved changes
     * @param untitled whether it has never been saved
     * @param line its cursor line, 1-based
     */
    public record Buffer(int number, String name, Optional<Path> file, boolean current, boolean alternate, boolean modified,
                         boolean untitled, int line) { }

    /**
     * The Editor's tabs as Vim's buffers. Buffers are named by their numbers; the functions run on the UI thread, and
     * the futures complete there.
     *
     * @param buffers the open tabs in their order, the one {@code editor} belongs to marked current
     * @param save saves a buffer as the Editor's Save does (Save As for an untitled tab); completes with whether it saved
     * @param saveAs saves a buffer to a file, which it then edits; completes with whether it saved
     * @param close closes a buffer's tab; {@code true} discards its changes without asking
     * @param select selects a buffer's tab
     * @param reload reads a buffer's file again, discarding its changes
     * @param open opens a file in a tab (or selects its tab) next to {@code editor}'s, or with no file a new untitled
     *     tab; a file that does not exist yet gets an empty tab its first save creates
     * @param read the text of a local file, or empty when it cannot be read
     */
    public record Documents(Function<VimEditor, List<Buffer>> buffers, IntFunction<CompletableFuture<Boolean>> save,
                            BiFunction<Integer, Path, CompletableFuture<Boolean>> saveAs, BiConsumer<Integer, Boolean> close,
                            IntConsumer select, IntConsumer reload, BiConsumer<VimEditor, Optional<Path>> open,
                            Function<Path, Optional<String>> read) {
        /** No tabs: an editor alone, which is buffer 1 and cannot be saved, closed or left. */
        public static final Documents NONE = new Documents(
            editor -> List.of(new Buffer(1, editor.documentName(), Optional.empty(), true, false, editor.documentModified(), true,
                editor.cursorLine())),
            number -> CompletableFuture.completedFuture(false), (number, file) -> CompletableFuture.completedFuture(false),
            (number, discard) -> { }, number -> { }, number -> { }, (editor, file) -> { }, file -> Optional.empty());
    }
}
