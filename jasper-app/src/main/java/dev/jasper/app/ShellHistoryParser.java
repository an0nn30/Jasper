package dev.jasper.app;

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

    private ShellHistoryParser() {}

    static Parsed parse(HistoryShell shell, byte[] bytes) {
        int consumed = 0;
        for (int i = bytes.length - 1; i >= 0; i--) if (bytes[i] == '\n') { consumed = i + 1; break; }
        List<String> lines = lines(shell, bytes, consumed);
        List<ShellHistoryEntry> entries = switch (shell) {
            case ZSH -> zsh(lines);
            case BASH -> bash(lines);
            case FISH -> fish(lines);
            case NUSHELL -> plain(lines, HistoryShell.NUSHELL);
            case POWERSHELL -> powershell(lines);
        };
        return new Parsed(entries, consumed);
    }

    /** zsh stores a byte {@code b} that collides with its markers as 0x83 followed by {@code b ^ 0x20}. */
    static byte[] unmetafy(byte[] bytes, int length) {
        var out = new ByteArrayOutputStream(length);
        for (int i = 0; i < length; i++) {
            int b = bytes[i] & 0xff;
            if (b == 0x83 && i + 1 < length) out.write((bytes[++i] & 0xff) ^ 0x20);
            else out.write(b);
        }
        return out.toByteArray();
    }

    private static List<String> lines(HistoryShell shell, byte[] bytes, int end) {
        byte[] data = shell == HistoryShell.ZSH ? unmetafy(bytes, end) : Arrays.copyOf(bytes, end);
        String text = new String(data, StandardCharsets.UTF_8); // malformed bytes become U+FFFD
        var lines = new ArrayList<String>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != '\n') continue;
            int stop = i > start && text.charAt(i - 1) == '\r' ? i - 1 : i;
            lines.add(text.substring(start, stop));
            start = i + 1;
        }
        return lines;
    }

    private static List<ShellHistoryEntry> zsh(List<String> lines) {
        var entries = new ArrayList<ShellHistoryEntry>();
        StringBuilder pending = null;
        long time = 0;
        for (String line : lines) {
            if (line.length() > MAX_LINE) { pending = null; continue; }
            if (pending == null) {
                Matcher extended = ZSH_EXTENDED.matcher(line);
                if (extended.matches()) { time = parseTime(extended.group(1)); pending = new StringBuilder(extended.group(3)); }
                else { time = 0; pending = new StringBuilder(line); }
            } else pending.append('\n').append(line);
            if (!pending.isEmpty() && pending.charAt(pending.length() - 1) == '\\') {
                pending.setLength(pending.length() - 1);
                continue;
            }
            add(entries, pending.toString(), time, HistoryShell.ZSH);
            pending = null;
        }
        return entries;
    }

    private static List<ShellHistoryEntry> bash(List<String> lines) {
        var entries = new ArrayList<ShellHistoryEntry>();
        long time = 0;
        for (String line : lines) {
            Matcher stamp = BASH_TIMESTAMP.matcher(line);
            if (stamp.matches()) { time = parseTime(stamp.group(1)); continue; }
            add(entries, line, time, HistoryShell.BASH);
            time = 0;
        }
        return entries;
    }

    private static List<ShellHistoryEntry> fish(List<String> lines) {
        var entries = new ArrayList<ShellHistoryEntry>();
        String command = null;
        long time = 0;
        for (String line : lines) {
            if (line.startsWith("- cmd: ")) {
                if (command != null) add(entries, command, time, HistoryShell.FISH);
                command = unescapeFish(line.substring(7));
                time = 0;
            } else if (command != null && line.startsWith("  when: ")) {
                time = parseTime(line.substring(8).trim());
            }
        }
        if (command != null) add(entries, command, time, HistoryShell.FISH);
        return entries;
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

    private static List<ShellHistoryEntry> powershell(List<String> lines) {
        var entries = new ArrayList<ShellHistoryEntry>();
        StringBuilder pending = null;
        for (String line : lines) {
            if (pending == null) pending = new StringBuilder(line); else pending.append('\n').append(line);
            if (!pending.isEmpty() && pending.charAt(pending.length() - 1) == '`') {
                pending.setLength(pending.length() - 1);
                continue;
            }
            add(entries, pending.toString(), 0, HistoryShell.POWERSHELL);
            pending = null;
        }
        return entries;
    }

    private static List<ShellHistoryEntry> plain(List<String> lines, HistoryShell shell) {
        var entries = new ArrayList<ShellHistoryEntry>();
        for (String line : lines) add(entries, line, 0, shell);
        return entries;
    }

    private static void add(List<ShellHistoryEntry> entries, String command, long time, HistoryShell shell) {
        String trimmed = command.stripTrailing();
        if (trimmed.isBlank() || trimmed.length() > MAX_LINE) return;
        entries.add(ShellHistoryEntry.of(trimmed, time, shell.label()));
    }

    private static long parseTime(String digits) {
        try { return Long.parseLong(digits); } catch (NumberFormatException overflow) { return 0; }
    }
}
