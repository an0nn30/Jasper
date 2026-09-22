package dev.jasper.history;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses each shell's history file format into entries, oldest first. Pure; no I/O. */
final class ShellHistoryParser {
    static final int MAX_LINE = 16 * 1024;
    private static final Pattern ZSH_EXTENDED = Pattern.compile("^: (\\d+):(\\d+);(.*)$", Pattern.DOTALL);
    private static final Pattern BASH_TIMESTAMP = Pattern.compile("^#(\\d{9,})$");

    /** Entries in file order and the byte count consumed: only complete lines are parsed. */
    record Parsed(List<ShellHistoryEntry> entries, int consumed) {}

    /** Line text with byte offsets: byteOffset is the start, endOffset is just past the terminating \n in raw bytes. */
    private record LineWithOffset(String text, int byteOffset, int endOffset) {}

    private ShellHistoryParser() {}

    static Parsed parse(HistoryShell shell, byte[] bytes) {
        int lineEndOffset = 0;
        for (int i = bytes.length - 1; i >= 0; i--) if (bytes[i] == '\n') { lineEndOffset = i + 1; break; }
        List<LineWithOffset> lines = lines(shell, bytes, lineEndOffset);
        return switch (shell) {
            case ZSH -> zsh(lines);
            case BASH -> bash(lines);
            case FISH -> fish(lines);
            case NUSHELL -> plain(lines, HistoryShell.NUSHELL);
            case POWERSHELL -> powershell(lines);
        };
    }

    /** zsh stores a byte {@code b} that collides with its markers as 0x83 followed by {@code b ^ 0x20}. */
    static byte[] unmetafy(byte[] bytes, int start, int end) {
        var out = new ByteArrayOutputStream(end - start);
        for (int i = start; i < end; i++) {
            int b = bytes[i] & 0xff;
            if (b == 0x83 && i + 1 < end) out.write((bytes[++i] & 0xff) ^ 0x20);
            else out.write(b);
        }
        return out.toByteArray();
    }

    private static List<LineWithOffset> lines(HistoryShell shell, byte[] bytes, int end) {
        // For zsh, compute offsets before unmetafying (newlines are never metafied)
        var lines = new ArrayList<LineWithOffset>();
        int byteOffset = 0;
        for (int i = 0; i < end; ) {
            int lineEnd = i;
            while (lineEnd < end && bytes[lineEnd] != '\n') lineEnd++;
            // lineEnd now points to '\n' or end of bytes
            int lineLength = lineEnd - i;
            int endOffset = lineLength + 1; // +1 for the \n; relative to byteOffset
            // For zsh, unmetafy only this line's bytes to get the text
            byte[] lineBytes = shell == HistoryShell.ZSH ? unmetafy(bytes, i, lineEnd) : Arrays.copyOfRange(bytes, i, lineEnd);
            String text = new String(lineBytes, StandardCharsets.UTF_8); // malformed bytes become U+FFFD
            // Strip trailing \r if present
            int stop = text.length() > 0 && text.charAt(text.length() - 1) == '\r' ? text.length() - 1 : text.length();
            String trimmed = text.substring(0, stop);
            lines.add(new LineWithOffset(trimmed, byteOffset, byteOffset + endOffset));
            byteOffset += endOffset;
            i = lineEnd + 1; // Move past the \n
        }
        return lines;
    }

    private static Parsed zsh(List<LineWithOffset> lines) {
        var entries = new ArrayList<ShellHistoryEntry>();
        StringBuilder pending = null;
        long time = 0;
        int pendingStartOffset = 0;
        int consumed = 0;
        for (LineWithOffset lineWithOffset : lines) {
            String line = lineWithOffset.text;
            if (line.length() > MAX_LINE) { pending = null; continue; }
            if (pending == null) {
                pendingStartOffset = lineWithOffset.byteOffset;
                Matcher extended = ZSH_EXTENDED.matcher(line);
                if (extended.matches()) { time = parseTime(extended.group(1)); pending = new StringBuilder(extended.group(3)); }
                else { time = 0; pending = new StringBuilder(line); }
            } else pending.append('\n').append(line);
            if (!pending.isEmpty() && pending.charAt(pending.length() - 1) == '\\') {
                pending.setLength(pending.length() - 1);
                continue;
            }
            // Line is complete (no trailing backslash)
            add(entries, pending.toString(), time, HistoryShell.ZSH);
            consumed = lineWithOffset.endOffset;
            pending = null;
        }
        // If pending is not null at end, there's an incomplete continuation: don't emit it
        // and set consumed to the start of that pending entry
        if (pending != null) {
            consumed = pendingStartOffset;
        }
        return new Parsed(entries, consumed);
    }

    private static Parsed bash(List<LineWithOffset> lines) {
        var entries = new ArrayList<ShellHistoryEntry>();
        long time = 0;
        int consumed = 0;
        for (LineWithOffset lineWithOffset : lines) {
            String line = lineWithOffset.text;
            consumed = lineWithOffset.endOffset;
            Matcher stamp = BASH_TIMESTAMP.matcher(line);
            if (stamp.matches()) { time = parseTime(stamp.group(1)); continue; }
            boolean added = add(entries, line, time, HistoryShell.BASH);
            if (added) time = 0; // Only reset time when entry is actually added
        }
        return new Parsed(entries, consumed);
    }

    private static Parsed fish(List<LineWithOffset> lines) {
        var entries = new ArrayList<ShellHistoryEntry>();
        String command = null;
        long time = 0;
        int commandStartOffset = 0;
        int consumed = 0;
        for (int i = 0; i < lines.size(); i++) {
            LineWithOffset lineWithOffset = lines.get(i);
            String line = lineWithOffset.text;
            if (line.startsWith("- cmd: ")) {
                if (command != null) add(entries, command, time, HistoryShell.FISH);
                command = unescapeFish(line.substring(7));
                commandStartOffset = lineWithOffset.byteOffset;
                time = 0;
            } else if (command != null && line.startsWith("  when: ")) {
                time = parseTime(line.substring(8).trim());
            }
        }
        // Emit the trailing command if any
        if (command != null) {
            add(entries, command, time, HistoryShell.FISH);
            // If this is the last block and no following "- cmd:" exists, set consumed to its start
            // so a tail read re-parses it once "when:" is added
            consumed = commandStartOffset;
        } else {
            // No trailing command; consumed is set normally
            if (!lines.isEmpty()) {
                LineWithOffset last = lines.get(lines.size() - 1);
                consumed = last.endOffset;
            }
        }
        return new Parsed(entries, consumed);
    }

    private static String unescapeFish(String text) {
        var out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                out.append(next == 'n' ? '\n' : next);
            } else out.append(c);
        }
        return out.toString();
    }

    private static Parsed powershell(List<LineWithOffset> lines) {
        var entries = new ArrayList<ShellHistoryEntry>();
        StringBuilder pending = null;
        int pendingStartOffset = 0;
        int consumed = 0;
        for (LineWithOffset lineWithOffset : lines) {
            String line = lineWithOffset.text;
            if (pending == null) {
                pendingStartOffset = lineWithOffset.byteOffset;
                pending = new StringBuilder(line);
            } else pending.append('\n').append(line);
            if (!pending.isEmpty() && pending.charAt(pending.length() - 1) == '`') {
                pending.setLength(pending.length() - 1);
                continue;
            }
            // Line is complete (no trailing backtick)
            add(entries, pending.toString(), 0, HistoryShell.POWERSHELL);
            consumed = lineWithOffset.endOffset;
            pending = null;
        }
        // If pending is not null at end, there's an incomplete continuation: don't emit it
        // and set consumed to the start of that pending entry
        if (pending != null) {
            consumed = pendingStartOffset;
        }
        return new Parsed(entries, consumed);
    }

    private static Parsed plain(List<LineWithOffset> lines, HistoryShell shell) {
        var entries = new ArrayList<ShellHistoryEntry>();
        int consumed = 0;
        for (LineWithOffset lineWithOffset : lines) {
            add(entries, lineWithOffset.text, 0, shell);
            consumed = lineWithOffset.endOffset;
        }
        return new Parsed(entries, consumed);
    }

    private static boolean add(List<ShellHistoryEntry> entries, String command, long time, HistoryShell shell) {
        String trimmed = command.stripTrailing();
        if (trimmed.isBlank() || trimmed.length() > MAX_LINE) return false;
        entries.add(ShellHistoryEntry.of(trimmed, time, shell.label()));
        return true;
    }

    private static long parseTime(String digits) {
        try { return Long.parseLong(digits); } catch (NumberFormatException overflow) { return 0; }
    }
}
