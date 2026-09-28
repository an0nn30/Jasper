package dev.jasper.editor.vim;

import dev.jasper.editor.vim.VimHost.Buffer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * What {@code <Tab>} completes on the {@code :} command line (spec 6.1), as Vim's set_one_cmd_context decides: a
 * command name (among the commands Jasper runs), an option name or its value after {@code :set}, a path after the
 * file commands, and a tab's name after {@code :b}. Matches are sorted; the command line cycles through them.
 */
final class Completion {
    private Completion() { }

    /**
     * @param start where the completed word starts in the text
     * @param matches the words that may replace it, sorted; empty when nothing matches
     * @param completes false where nothing completes (Vim's EXPAND_NOTHING), where {@code <Tab>} types a tab
     */
    record Result(int start, List<String> matches, boolean completes) {
        Result(int start, List<String> matches) { this(start, matches, true); }

        /** Nothing completes here: {@code <Tab>} types a tab. */
        static final Result NOTHING = new Result(0, List.of(), false);
        /** Something completes here, but nothing matches: the bell rings. */
        static final Result NO_MATCH = new Result(0, List.of(), true);
    }

    private static final List<String> FILE_COMMANDS = List.of("edit", "write", "read", "saveas", "tabnew", "tabedit", "wq", "xit", "exit");

    /**
     * @param editor the editor the command line is in
     * @param before the command line's text before the cursor
     * @return what may replace the word before the cursor
     */
    static Result complete(VimEditor editor, String before) {
        int i = 0, n = before.length();
        while (i < n && (Ex.isWhite(before.charAt(i)) || before.charAt(i) == ':')) i++;
        i = Ex.skipRange(before, i);
        int nameStart = i;
        while (i < n && Character.isLetter(before.charAt(i)) && before.charAt(i) < 128) i++;
        if (i == n) return commands(before.substring(nameStart), nameStart);
        String typed = before.substring(nameStart, i);
        String name = typed.isEmpty() ? null : ExTable.resolve(typed);
        if (name == null) return Result.NO_MATCH;
        if (i < n && before.charAt(i) == '!') i++;
        if (i >= n || !Ex.isWhite(before.charAt(i))) return Result.NOTHING;
        int argStart = Ex.skipWhite(before, i);
        if (name.equals("set") || name.equals("setlocal") || name.equals("setglobal")) return options(editor, before, argStart);
        if (FILE_COMMANDS.contains(name)) return paths(editor, before, argStart);
        if (name.equals("buffer")) return buffers(editor, before, argStart);
        return Result.NOTHING;
    }

    /** Command names: the commands Jasper runs whose full name starts with the typed text. */
    private static Result commands(String prefix, int start) {
        List<String> names = Ex.implemented().stream().map(Ex.Def::name)
            .filter(name -> Character.isLetter(name.charAt(0)) && name.startsWith(prefix)).sorted().toList();
        return new Result(start, names);
    }

    /** After {@code :set}: an option name (with {@code no} or {@code inv} for a boolean), or after {@code =} its value. */
    private static Result options(VimEditor editor, String before, int argStart) {
        int start = before.length();
        while (start > argStart && !Ex.isWhite(before.charAt(start - 1))) start--;
        String word = before.substring(start);
        int equals = firstOf(word, "=:");
        if (equals > 0) {
            String option = word.substring(0, equals);
            if (option.endsWith("+") || option.endsWith("-") || option.endsWith("^")) option = option.substring(0, option.length() - 1);
            if (equals + 1 < word.length()) return Result.NO_MATCH;
            var def = Options.def(option);
            if (def.isEmpty() || def.get().type() == Options.Type.BOOLEAN) return Result.NO_MATCH;
            String value = String.valueOf(editor.options().get(def.get().name())).replace(" ", "\\ ");
            return new Result(start + equals + 1, List.of(value));
        }
        List<String> matches = new ArrayList<>();
        for (Options.Def def : Options.DEFS) {
            if (def.name().startsWith(word)) matches.add(def.name());
            if (def.type() != Options.Type.BOOLEAN) continue;
            for (String prefix : List.of("no", "inv"))
                if (word.startsWith(prefix) && def.name().startsWith(word.substring(prefix.length()))) matches.add(prefix + def.name());
        }
        if ("all".startsWith(word) && !word.isEmpty()) matches.add("all");
        return new Result(start, matches.stream().distinct().sorted().toList());
    }

    private static int firstOf(String text, String characters) {
        for (int i = 0; i < text.length(); i++) if (characters.indexOf(text.charAt(i)) >= 0) return i;
        return -1;
    }

    /**
     * A path: the entries of the typed folder (the current file's folder for a relative one) whose names start with
     * the typed name, hidden ones only when the name starts with a dot; folders end with a slash, spaces are escaped.
     */
    private static Result paths(VimEditor editor, String before, int argStart) {
        String word = before.substring(argStart);
        String unescaped = word.replace("\\ ", " ");
        int slash = unescaped.lastIndexOf('/');
        String folderPart = slash < 0 ? "" : unescaped.substring(0, slash + 1), namePart = unescaped.substring(slash + 1);
        Buffer current = current(editor);
        Path folder = folderPart.isEmpty() ? ExFiles.folder(current) : ExFiles.resolve(folderPart, current);
        List<String> matches = new ArrayList<>();
        try (Stream<Path> entries = Files.list(folder)) {
            for (Path entry : entries.toList()) {
                String name = String.valueOf(entry.getFileName());
                if (!name.startsWith(namePart) || name.startsWith(".") && !namePart.startsWith(".")) continue;
                matches.add((folderPart + name + (Files.isDirectory(entry) ? "/" : "")).replace(" ", "\\ "));
            }
        } catch (IOException | RuntimeException unreadable) {
            return Result.NO_MATCH;
        }
        matches.sort(String::compareTo);
        return new Result(argStart, matches);
    }

    /** After {@code :b}: the names of the tabs that hold the typed text. */
    private static Result buffers(VimEditor editor, String before, int argStart) {
        String word = before.substring(argStart).replace("\\ ", " ");
        List<String> matches = editor.vim().host().documents().buffers().apply(editor).stream().map(Buffer::name)
            .filter(name -> name.toLowerCase(Locale.ROOT).contains(word.toLowerCase(Locale.ROOT))).map(name -> name.replace(" ", "\\ "))
            .sorted().toList();
        return new Result(argStart, matches);
    }

    private static Buffer current(VimEditor editor) {
        return editor.vim().host().documents().buffers().apply(editor).stream().filter(Buffer::current).findFirst().orElseThrow();
    }
}
