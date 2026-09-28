package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Vim's registers (spec 3.5), with Vim's rules for which registers a yank or delete writes (ops.c: op_yank and
 * op_delete) and what the unnamed register points at. {@code "+} and {@code "*} are the host's clipboard.
 */
public final class Registers {
    /** How a register's text is put: within a line, as whole lines (or, from plan V1b, as a block). */
    public enum Type { CHAR, LINE }

    /**
     * A register's text as Vim holds it: lines, and how they are put.
     *
     * @param lines at least one line; a character register's lines are joined by line breaks
     * @param type charwise or linewise
     */
    public record Content(List<String> lines, Type type) {
        /** Copies the lines. */
        public Content {
            lines = List.copyOf(lines);
            if (lines.isEmpty()) throw new IllegalArgumentException("A register holds at least one line");
        }

        /**
         * @param text register text; a linewise register's text ends with a line break, which is dropped
         * @param type the type
         * @return the content
         */
        public static Content of(String text, Type type) {
            String body = type == Type.LINE && text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
            return new Content(List.of(body.split("\n", -1)), type);
        }

        /** @return the text as {@code getreg()} gives it: linewise text ends with a line break */
        public String text() {
            String joined = String.join("\n", lines);
            return type == Type.LINE ? joined + "\n" : joined;
        }
    }

    private final Map<Character, Content> values = new HashMap<>();
    private final VimHost host;
    private char previous;
    private String lastInserted = "";
    private Content clipboardWritten;

    /** @param host reaches the clipboard */
    public Registers(VimHost host) { this.host = host; }

    /**
     * @param name a register name, or {@code "} for the unnamed register
     * @return its content, or empty for an empty or unknown register
     */
    public Optional<Content> get(char name) {
        char register = Character.toLowerCase(name);
        return switch (register) {
            case '"' -> previous == 0 ? Optional.empty() : get(previous);
            case '*', '+' -> clipboard(register);
            case '.' -> lastInserted.isEmpty() ? Optional.empty() : Optional.of(Content.of(lastInserted, Type.CHAR));
            case '_' -> Optional.empty();
            default -> Optional.ofNullable(values.get(register));
        };
    }

    /** Sets a register directly, as {@code setreg()} or {@code :let @a = …}; the unnamed register then points at it. */
    public void set(char name, Content content) {
        write(name, content);
        previous = Character.toLowerCase(name) == '"' ? '0' : Character.toLowerCase(name);
    }

    /** @return whether the name is a register a command may name */
    static boolean valid(char name, boolean writing) {
        if (Character.isLetterOrDigit(name) && name < 128 || "\"-_*+".indexOf(name) >= 0) return true;
        return !writing && name == '.';
    }

    /**
     * A yank (op_yank): into the named register, or into {@code "0} when none is named.
     *
     * @param requested the register typed ({@code 0} for none)
     * @param content what was yanked
     * @param clipboard the 'clipboard' option
     */
    void yank(char requested, Content content, String clipboard) {
        char register = effective(requested, clipboard);
        if (register == '_') return;
        if (register == 0 || register == '"') {
            values.put('0', content);
            previous = '0';
            return;
        }
        write(register, content);
        previous = Character.toLowerCase(register);
    }

    /**
     * A delete or change (op_delete): the named register if any; register 1 (shifting 1 to 9) for a delete of a
     * line or more or with {@code regOne}; the small delete register {@code "-} for a delete within one line with no
     * register named.
     *
     * @param requested the register typed ({@code 0} for none)
     * @param content what was deleted
     * @param lineCount how many lines the deleted text touched
     * @param regOne the motion always uses register 1 ({@code % ( ) ` / ? n N { }})
     * @param clipboard the 'clipboard' option
     */
    void delete(char requested, Content content, int lineCount, boolean regOne, String clipboard) {
        char register = effective(requested, clipboard);
        if (register == '_') return;
        boolean named = register != 0 && register != '"';
        if (named) {
            write(register, content);
            previous = Character.toLowerCase(register);
        }
        // Vim 9 also puts a delete into a named register in register 1 (recorded by the corpus).
        if (content.type() == Type.LINE || lineCount > 1 || regOne || named && !clipboardRegister(register)) {
            for (char r = '9'; r > '1'; r--) {
                Content shifted = values.get((char) (r - 1));
                if (shifted == null) values.remove(r);
                else values.put(r, shifted);
            }
            values.put('1', content);
            previous = '1';
        }
        List<String> items = List.of(clipboard.split(","));
        boolean clipboardDefault = register == '*' && items.contains("unnamed") || register == '+' && items.contains("unnamedplus");
        if ((!named || clipboardDefault) && content.type() != Type.LINE && lineCount == 1) {
            values.put('-', content);
            previous = '-';
        }
    }

    private static boolean clipboardRegister(char register) { return register == '*' || register == '+'; }

    /** @param text the text of the last insert, for {@code ".} and {@code i_CTRL-A} */
    void lastInserted(String text) { lastInserted = text; }

    String lastInserted() { return lastInserted; }

    /** With 'clipboard' holding unnamed or unnamedplus, a command naming no register uses the clipboard register. */
    static char effective(char requested, String clipboard) {
        if (requested != 0 && requested != '"') return requested;
        List<String> items = List.of(clipboard.split(","));
        if (items.contains("unnamedplus")) return '+';
        if (items.contains("unnamed")) return '*';
        return requested;
    }

    private void write(char name, Content content) {
        char register = Character.toLowerCase(name);
        if (register == '*' || register == '+') {
            clipboardWritten = content;
            host.writeClipboard().accept(register, content.text());
            return;
        }
        if (register == '"') register = '0';
        Content old = values.get(register);
        if (!Character.isUpperCase(name) || old == null) {
            values.put(register, content);
            return;
        }
        // Appending (ops.c): linewise wins; two charwise texts join their touching lines.
        Type type = content.type() == Type.LINE || old.type() == Type.LINE ? Type.LINE : Type.CHAR;
        List<String> lines = new ArrayList<>(old.lines());
        if (type == Type.CHAR) {
            lines.set(lines.size() - 1, lines.getLast() + content.lines().getFirst());
            lines.addAll(content.lines().subList(1, content.lines().size()));
        } else lines.addAll(content.lines());
        values.put(register, new Content(lines, type));
    }

    private Optional<Content> clipboard(char register) {
        Optional<String> read = host.readClipboard().apply(register);
        if (read.isEmpty()) return Optional.empty();
        // Windows (and old Mac) text arrives with \r\n or \r; Vim's registers hold \n only.
        String text = read.get().replace("\r\n", "\n").replace('\r', '\n');
        if (clipboardWritten != null && clipboardWritten.text().equals(text)) return Optional.of(clipboardWritten);
        return Optional.of(Content.of(text, text.endsWith("\n") ? Type.LINE : Type.CHAR));
    }
}
