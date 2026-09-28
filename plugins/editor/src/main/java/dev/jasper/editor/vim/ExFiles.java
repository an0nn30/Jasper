package dev.jasper.editor.vim;

import dev.jasper.editor.vim.VimHost.Buffer;
import dev.jasper.editor.vim.VimHost.Documents;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The file, buffer and tab commands (spec 6.2), through the Editor ({@link VimHost.Documents}): each Editor tab is a
 * buffer and a tab page. {@code :w} and the other writes use the Editor's save, with its conflict checks and dialogs;
 * {@code :q} closes the tab; {@code :e} opens a file in a tab or selects its tab. A file name may use {@code %} (the
 * current file), {@code #} (the alternate), {@code ~} and backslash-escaped spaces; a relative name is taken from the
 * current file's folder, or the home folder for an untitled tab.
 */
final class ExFiles {
    private ExFiles() { }

    private static Documents documents(ExCommand cmd) { return cmd.editor.vim().host().documents(); }

    static List<Buffer> buffers(ExCommand cmd) { return documents(cmd).buffers().apply(cmd.editor); }

    static Buffer current(List<Buffer> buffers) {
        return buffers.stream().filter(Buffer::current).findFirst().orElseThrow(() -> new IllegalStateException("No current buffer"));
    }

    /** {@code :w [file]}: saves the tab, or saves it as the file. */
    static void write(ExCommand cmd) {
        if (partial(cmd)) throw new VimFailure("E319: Sorry, the command is not available in this version");
        write(cmd, () -> { });
    }

    /** {@code :wq [file]}: writes, then closes the tab once the write succeeded. */
    static void writeQuit(ExCommand cmd) {
        if (partial(cmd)) throw new VimFailure("E319: Sorry, the command is not available in this version");
        int number = current(buffers(cmd)).number();
        write(cmd, () -> documents(cmd).close().accept(number, false));
    }

    /** {@code :x} and {@code :exit}: as {@code :wq}, but writes only when there are changes or a file is named. */
    static void exit(ExCommand cmd) {
        if (partial(cmd)) throw new VimFailure("E319: Sorry, the command is not available in this version");
        Buffer buffer = current(buffers(cmd));
        String arg = fileArg(cmd);
        if (!buffer.modified() && arg == null) {
            cmd.editor.afterKey(() -> documents(cmd).close().accept(buffer.number(), false));
            return;
        }
        writeQuit(cmd);
    }

    /** A range that is not the whole text: Jasper writes whole files only. */
    private static boolean partial(ExCommand cmd) { return cmd.addrCount > 0 && (cmd.line1 != 1 || cmd.line2 != cmd.editor.buffer().count()); }

    private static void write(ExCommand cmd, Runnable then) {
        String arg = fileArg(cmd);
        if (arg != null && (arg.startsWith(">>") || arg.startsWith("!"))) throw new VimFailure("E319: Sorry, the command is not available in this version");
        List<Buffer> buffers = buffers(cmd);
        Buffer buffer = current(buffers);
        VimEditor editor = cmd.editor;
        String text = editor.buffer().text();
        if (arg == null || buffer.file().filter(f -> same(f, resolve(arg, buffer))).isPresent()) {
            done(editor, documents(cmd).save().apply(buffer.number()), () -> written(editor, buffer.name(), false, text), then);
            return;
        }
        Path file = resolve(arg, buffer);
        if (buffers.stream().anyMatch(other -> !other.current() && other.file().filter(f -> same(f, file)).isPresent()))
            throw new VimFailure("E139: File is loaded in another buffer");
        boolean exists = Files.exists(file);
        if (exists && !cmd.forceit) throw new VimFailure("E13: File exists (add ! to override)");
        if (Files.isDirectory(file)) throw new VimFailure("E502: \"" + arg + "\" is a directory");
        done(editor, documents(cmd).saveAs().apply(buffer.number(), file), () -> written(editor, arg, !exists, text), then);
    }

    /** When a save completes: the message, then what follows it, on the UI thread where the Editor completes it. */
    private static void done(VimEditor editor, CompletableFuture<Boolean> save, Runnable message, Runnable then) {
        save.thenAccept(saved -> {
            if (!saved) return;
            message.run();
            then.run();
        });
    }

    /** The write message: {@code "name" 3L, 12B written}, with {@code [New]} for a file that did not exist. */
    private static void written(VimEditor editor, String name, boolean created, String text) {
        editor.announce("\"" + name + "\" " + (created ? "[New] " : "") + size(text) + " written");
    }

    /** @return the text's size as Vim's messages give it: {@code 3L, 12B} (lines, UTF-8 bytes) */
    static String size(String text) {
        int lines = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') lines++;
        if (!text.isEmpty() && !text.endsWith("\n")) lines++;
        return lines + "L, " + text.getBytes(StandardCharsets.UTF_8).length + "B";
    }

    /** {@code :wa}: saves every tab with changes, one after another. */
    static void writeAll(ExCommand cmd) {
        saveAll(cmd, () -> { });
    }

    /** {@code :xa} and {@code :wqa}: saves every tab with changes, then closes them all. */
    static void exitAll(ExCommand cmd) {
        saveAll(cmd, () -> closeAll(cmd, false));
    }

    private static void saveAll(ExCommand cmd, Runnable then) {
        CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(true);
        for (Buffer buffer : buffers(cmd)) {
            if (!buffer.modified()) continue;
            chain = chain.thenCompose(ok -> ok ? documents(cmd).save().apply(buffer.number()) : CompletableFuture.completedFuture(false));
        }
        chain.thenAccept(saved -> { if (saved) then.run(); });
    }

    /** {@code :q}: closes the tab; with changes only with {@code !}, which discards them. */
    static void quit(ExCommand cmd) {
        Buffer buffer = current(buffers(cmd));
        if (buffer.modified() && !cmd.forceit) throw new VimFailure("E37: No write since last change (add ! to override)");
        cmd.editor.afterKey(() -> documents(cmd).close().accept(buffer.number(), cmd.forceit));
    }

    /** {@code :qa}: closes every tab; with changes in any only with {@code !}. */
    static void quitAll(ExCommand cmd) {
        if (!cmd.forceit) {
            List<Buffer> buffers = buffers(cmd);
            Optional<Buffer> changed = buffers.stream().filter(b -> b.current() && b.modified()).findFirst()
                .or(() -> buffers.stream().filter(Buffer::modified).findFirst());
            if (changed.isPresent()) {
                cmd.editor.message("E37: No write since last change", true);
                throw new VimFailure("E162: No write since last change for buffer \"" + changed.get().name() + "\"");
            }
        }
        cmd.editor.afterKey(() -> closeAll(cmd, cmd.forceit));
    }

    private static void closeAll(ExCommand cmd, boolean discard) {
        for (Buffer buffer : buffers(cmd)) documents(cmd).close().accept(buffer.number(), discard);
    }

    /** {@code :sav[eas] {file}}: saves the tab as the file. */
    static void saveAs(ExCommand cmd) {
        if (fileArg(cmd) == null) throw new VimFailure("E471: Argument required");
        write(cmd, () -> { });
    }

    /**
     * {@code :e [file]}: with no file, reads the tab's file again ({@code !} discards changes); with a file, opens it
     * in a tab or selects its tab.
     */
    static void edit(ExCommand cmd) {
        String arg = fileArg(cmd);
        Buffer buffer = current(buffers(cmd));
        if (arg != null) {
            Path file = resolve(arg, buffer);
            if (buffer.file().filter(f -> same(f, file)).isEmpty()) {
                documents(cmd).open().accept(cmd.editor, Optional.of(file));
                return;
            }
        }
        if (buffer.untitled() || buffer.file().isEmpty()) throw new VimFailure("E32: No file name");
        if (buffer.modified() && !cmd.forceit) throw new VimFailure("E37: No write since last change (add ! to override)");
        documents(cmd).reload().accept(buffer.number());
    }

    /** {@code :ene[w]} and {@code :tabnew}: a new untitled tab. {@code :tabnew file} and {@code :tabe file} open the file. */
    static void newTab(ExCommand cmd) {
        String arg = cmd.name().equals("enew") ? null : fileArg(cmd);
        if (arg == null) {
            documents(cmd).open().accept(cmd.editor, Optional.empty());
            return;
        }
        documents(cmd).open().accept(cmd.editor, Optional.of(resolve(arg, current(buffers(cmd)))));
    }

    /**
     * {@code :r [file]}: the file's lines below the range's line (above the first for {@code :0r}); the cursor goes to
     * the first new line's first non-blank, as readfile leaves it.
     */
    static void read(ExCommand cmd) {
        if (cmd.forceit || cmd.arg.startsWith("!")) throw new VimFailure("E319: Sorry, the command is not available in this version");
        String arg = fileArg(cmd);
        Buffer buffer = current(buffers(cmd));
        Path file;
        if (arg == null) {
            if (buffer.file().isEmpty()) throw new VimFailure("E32: No file name");
            file = buffer.file().get();
        } else file = resolve(arg, buffer);
        Optional<String> text = documents(cmd).read().apply(file);
        if (text.isEmpty()) throw new VimFailure("E484: Can't open file " + (arg == null ? buffer.name() : arg));
        String content = text.get();
        List<String> lines = new ArrayList<>(List.of(content.split("\n", -1)));
        if (lines.size() > 1 && lines.getLast().isEmpty()) lines.removeLast();
        if (content.isEmpty()) lines.clear();
        VimEditor editor = cmd.editor;
        if (!lines.isEmpty()) {
            editor.area().vimUndo().stepCursor(editor.cursor().line, editor.cursor().col);
            editor.buffer().insertLines(cmd.line2, lines);
            Marks marks = editor.marks();
            marks.set('[', new Pos(cmd.line2, 0));
            marks.set(']', new Pos(cmd.line2 + lines.size() - 1, 0));
            editor.ex().beginline(cmd.line2 + 1, false);
        }
        editor.say("\"" + (arg == null ? buffer.name() : arg) + "\" " + size(content));
    }

    /** {@code :ls}, {@code :buffers} and {@code :files}: the tabs as buffer_list shows them. */
    static void list(ExCommand cmd) {
        for (Buffer buffer : buffers(cmd)) {
            StringBuilder line = new StringBuilder(String.format("%3d %c%c %c \"%s\"", buffer.number(),
                buffer.current() ? '%' : buffer.alternate() ? '#' : ' ', 'a', buffer.modified() ? '+' : ' ', buffer.name()));
            int width = 0;
            for (int i = 0; i < line.length(); ) {
                int cp = line.codePointAt(i);
                width += Text.width(cp);
                i += Character.charCount(cp);
            }
            int pad = 40 - width;
            do line.append(' ');
            while (--pad > 0);
            cmd.editor.say(line.append("line ").append(buffer.line()).toString());
        }
    }

    /** {@code :b N} and {@code :b name}: selects a tab by its buffer number or a part of its name. */
    static void buffer(ExCommand cmd) {
        List<Buffer> buffers = buffers(cmd);
        int number;
        String name = cmd.arg.strip();
        if (!name.isEmpty()) number = match(buffers, name.replace("\\ ", " ")).number();
        else if (cmd.addrCount > 0) number = cmd.line2;
        else return;
        if (buffers.stream().noneMatch(b -> b.number() == number)) throw new VimFailure("E86: Buffer " + number + " does not exist");
        documents(cmd).select().accept(number);
    }

    /**
     * buflist_findpat: the buffer whose name is the pattern, else whose name ends with it, else starts with it, else
     * holds it; at the first of these that matches, one buffer must.
     */
    private static Buffer match(List<Buffer> buffers, String pattern) {
        List<java.util.function.Predicate<String>> levels = List.of(n -> n.equals(pattern), n -> n.endsWith(pattern),
            n -> n.startsWith(pattern), n -> n.contains(pattern));
        for (var level : levels) {
            List<Buffer> found = buffers.stream().filter(b -> level.test(b.name())
                || b.file().map(f -> level.test(String.valueOf(f.getFileName()))).orElse(false)).toList();
            if (found.size() == 1) return found.getFirst();
            if (found.size() > 1) throw new VimFailure("E93: More than one match for " + pattern);
        }
        throw new VimFailure("E94: No matching buffer for " + pattern);
    }

    /** {@code :bn} and {@code :bp} ({@code :bN}): the next or previous tab, {@code N} times, wrapping around. */
    static void step(ExCommand cmd) {
        List<Buffer> buffers = buffers(cmd);
        int count = cmd.addrCount > 0 ? cmd.line2 : 1;
        int direction = cmd.name().equals("bnext") ? 1 : -1;
        int at = buffers.indexOf(current(buffers));
        int to = Math.floorMod(at + direction * count, buffers.size());
        if (to != at) documents(cmd).select().accept(buffers.get(to).number());
    }

    /** {@code :tabn [N]}: tab N, or the next one, wrapping around. */
    static void tabNext(ExCommand cmd) {
        List<Buffer> buffers = buffers(cmd);
        int n = tabCount(cmd, -1);
        int at = buffers.indexOf(current(buffers));
        if (n < 0) goTo(cmd, buffers, Math.floorMod(at + 1, buffers.size()));
        else if (n == 0 || n > buffers.size()) throw VimFailure.bell();
        else goTo(cmd, buffers, n - 1);
    }

    /** {@code :tabp [N]} and {@code :tabN [N]}: N tabs back, wrapping around. */
    static void tabPrevious(ExCommand cmd) {
        List<Buffer> buffers = buffers(cmd);
        int n = tabCount(cmd, 1);
        int at = buffers.indexOf(current(buffers));
        goTo(cmd, buffers, Math.floorMod(at - n, buffers.size()));
    }

    /** @return the count after the command or before it, or {@code none} */
    private static int tabCount(ExCommand cmd, int none) {
        String arg = cmd.arg.strip();
        if (!arg.isEmpty()) {
            if (!arg.chars().allMatch(Character::isDigit)) throw new VimFailure("E475: Invalid argument: " + arg);
            return (int) Math.min(Integer.MAX_VALUE, Long.parseLong(arg.length() > 9 ? arg.substring(0, 9) : arg));
        }
        return cmd.addrCount > 0 ? cmd.line2 : none;
    }

    private static void goTo(ExCommand cmd, List<Buffer> buffers, int index) {
        Buffer target = buffers.get(index);
        if (!target.current()) documents(cmd).select().accept(target.number());
    }

    /** {@code gt}: the next tab, or with a count tab {count}; beyond the last it rings the bell. */
    static void gt(Context c) {
        List<Buffer> buffers = c.vim.host().documents().buffers().apply(c.editor);
        int at = buffers.indexOf(current(buffers));
        int index;
        if (c.hasCount) {
            if (c.count > buffers.size()) throw Context.fail();
            index = c.count - 1;
        } else index = Math.floorMod(at + 1, buffers.size());
        if (index != at) c.vim.host().documents().select().accept(buffers.get(index).number());
    }

    /** {@code gT}: {count} tabs back, wrapping around. */
    static void gT(Context c) {
        List<Buffer> buffers = c.vim.host().documents().buffers().apply(c.editor);
        int at = buffers.indexOf(current(buffers));
        int index = Math.floorMod(at - c.count, buffers.size());
        if (index != at) c.vim.host().documents().select().accept(buffers.get(index).number());
    }

    /**
     * The file argument as expand_filename leaves it: {@code %} is the current file and {@code #} the alternate
     * ({@code \%} and {@code \#} are themselves), and a backslash before a space keeps it; a second name fails.
     *
     * @return the name, or null when there is none
     */
    static String fileArg(ExCommand cmd) {
        String arg = cmd.arg.strip();
        if (arg.isEmpty()) return null;
        List<Buffer> buffers = null;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < arg.length(); i++) {
            char c = arg.charAt(i);
            if (c == '\\' && i + 1 < arg.length() && " %#\\".indexOf(arg.charAt(i + 1)) >= 0) {
                out.append(arg.charAt(++i));
            } else if (c == '%' || c == '#') {
                if (buffers == null) buffers = buffers(cmd);
                Optional<Buffer> which = c == '%' ? buffers.stream().filter(Buffer::current).findFirst()
                    : buffers.stream().filter(Buffer::alternate).findFirst();
                if (which.isEmpty() && c == '#') throw new VimFailure("E194: No alternate file name to substitute for '#'");
                Buffer b = which.orElseThrow();
                if (b.file().isEmpty() && b.untitled()) throw new VimFailure("E499: Empty file name for '%' or '#', only works with \":p:h\"");
                out.append(b.file().map(Path::toString).orElse(b.name()));
            } else if (Ex.isWhite(c)) throw new VimFailure("E172: Only one file name allowed");
            else out.append(c);
        }
        return out.toString();
    }

    /** @return whether two paths name the same file, once absolute and normalized */
    private static boolean same(Path a, Path b) { return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize()); }

    /** A file name as a path: {@code ~} is the home folder; a relative name is in the current file's folder. */
    static Path resolve(String name, Buffer current) {
        String home = System.getProperty("user.home");
        String expanded = name.equals("~") ? home : name.startsWith("~/") ? home + name.substring(1) : name;
        Path path = Path.of(expanded);
        if (path.isAbsolute()) return path.normalize();
        return folder(current).resolve(path).normalize();
    }

    /** @return the folder relative names start from: the current file's, or the home folder */
    static Path folder(Buffer current) {
        return current.file().map(Path::toAbsolutePath).map(Path::getParent).orElse(Path.of(System.getProperty("user.home")));
    }
}
