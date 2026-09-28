package dev.jasper.editor.vim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes corpus files (spec 11.1). Lines starting with {@code #} outside a text block are comments. Text
 * lines are indented two spaces; one trailing {@code $} is dropped, so a line ending in white space or {@code $} is
 * written with a {@code $} after it. Everything from a case's {@code ---} line on is its expectation; writing
 * replaces only that part.
 */
final class CorpusFile {
    private CorpusFile() { }

    static List<CorpusCase> read(Path file) throws IOException {
        return parse(file.getFileName().toString(), Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    static List<CorpusCase> parse(String file, List<String> lines) {
        List<CorpusCase> cases = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (line.isBlank() || line.startsWith("#")) {
                i++;
                continue;
            }
            if (!line.startsWith("=== ")) throw error(file, i, "expected \"=== name\"");
            String name = line.substring(4).trim();
            String divergent = null, set = "", keys = null;
            Map<Character, CorpusCase.Register> given = new LinkedHashMap<>();
            List<String> text = null;
            int cursorLine = 1, cursorCol = 1;
            List<Character> registers = new ArrayList<>();
            CorpusCase.Expected expected = null;
            i++;
            while (i < lines.size() && !lines.get(i).startsWith("=== ")) {
                String field = lines.get(i);
                if (field.isBlank() || field.startsWith("#")) {
                    i++;
                    continue;
                }
                if (field.startsWith("---")) {
                    String by = field.substring(3).trim();
                    Parsed parsed = expectation(file, lines, i + 1, by);
                    expected = parsed.expected;
                    i = parsed.next;
                    break;
                }
                if (field.equals("text:")) {
                    List<String> block = new ArrayList<>();
                    i = block(lines, i + 1, block);
                    text = block;
                    continue;
                }
                int colon = field.indexOf(':');
                if (colon < 0) throw error(file, i, "expected \"field: value\"");
                String key = field.substring(0, colon), value = field.substring(colon + 1).trim();
                switch (key) {
                    case "divergent" -> divergent = value;
                    case "set" -> set = value;
                    case "cursor" -> {
                        String[] parts = value.split(",");
                        cursorLine = Integer.parseInt(parts[0].trim());
                        cursorCol = Integer.parseInt(parts[1].trim());
                    }
                    case "keys" -> keys = field.substring(colon + 2);
                    case "registers" -> {
                        for (String name1 : value.split("\\s+")) if (!name1.isEmpty()) registers.add(name1.charAt(0));
                    }
                    default -> {
                        if (key.startsWith("register ")) given.put(key.charAt(9), register(value));
                        else throw error(file, i, "unknown field " + key);
                    }
                }
                i++;
            }
            if (text == null || keys == null) throw error(file, i, "case \"" + name + "\" needs text: and keys:");
            cases.add(new CorpusCase(file, name, divergent, set, given, text, cursorLine, cursorCol, keys, registers, expected));
        }
        return cases;
    }

    private record Parsed(CorpusCase.Expected expected, int next) { }

    private static Parsed expectation(String file, List<String> lines, int i, String by) {
        List<String> text = List.of();
        int line = 1, col = 1;
        String mode = "normal", visual = null;
        Map<Character, CorpusCase.Register> registers = new LinkedHashMap<>();
        while (i < lines.size() && !lines.get(i).startsWith("=== ")) {
            String field = lines.get(i);
            if (field.isBlank() || field.startsWith("#")) {
                i++;
                continue;
            }
            if (field.equals("expect-text:")) {
                List<String> block = new ArrayList<>();
                i = block(lines, i + 1, block);
                text = block;
                continue;
            }
            int colon = field.indexOf(':', field.startsWith("expect-register ") ? 17 : 0);
            String key = field.substring(0, colon), value = field.substring(colon + 1).trim();
            switch (key) {
                case "expect-cursor" -> {
                    String[] parts = value.split(",");
                    line = Integer.parseInt(parts[0].trim());
                    col = Integer.parseInt(parts[1].trim());
                }
                case "expect-mode" -> mode = value;
                case "expect-visual" -> visual = value;
                default -> {
                    if (!key.startsWith("expect-register ")) throw error(file, i, "unknown field " + key);
                    if (!value.equals("none")) registers.put(key.charAt(16), register(value));
                }
            }
            i++;
        }
        return new Parsed(new CorpusCase.Expected(by, text, line, col, mode, visual, registers), i);
    }

    private static CorpusCase.Register register(String value) {
        int space = value.indexOf(' ');
        return new CorpusCase.Register(value.substring(0, space), Json.unquote(value.substring(space + 1).trim()));
    }

    /** Reads indented text lines until a line that is not indented; returns the index after them. */
    private static int block(List<String> lines, int i, List<String> out) {
        while (i < lines.size() && lines.get(i).startsWith("  ")) {
            String content = lines.get(i).substring(2);
            if (content.endsWith("$")) content = content.substring(0, content.length() - 1);
            out.add(content);
            i++;
        }
        return i;
    }

    private static IllegalArgumentException error(String file, int line, String message) {
        return new IllegalArgumentException(file + ":" + (line + 1) + ": " + message);
    }

    /** Rewrites the file with each named case's expectation replaced by the lines given, keeping everything above it. */
    static void writeExpectations(Path file, Map<String, List<String>> byName) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<String> out = new ArrayList<>();
        String current = null;
        boolean skipping = false;
        for (int i = 0; i <= lines.size(); i++) {
            String line = i < lines.size() ? lines.get(i) : null;
            boolean boundary = line == null || line.startsWith("=== ");
            if (boundary && current != null && byName.containsKey(current)) {
                while (!out.isEmpty() && out.getLast().isBlank()) out.removeLast();
                out.addAll(byName.get(current));
                out.add("");
            }
            if (line == null) break;
            if (boundary) {
                current = line.substring(4).trim();
                skipping = false;
                out.add(line);
                continue;
            }
            if (line.startsWith("---") && current != null && byName.containsKey(current)) skipping = true;
            if (!skipping) out.add(line);
        }
        while (!out.isEmpty() && out.getLast().isBlank()) out.removeLast();
        Files.write(file, out, StandardCharsets.UTF_8);
    }

    /** @return a case's expectation lines: its {@code ---} line, the text, cursor, mode and each compared register */
    static List<String> format(CorpusCase c, CorpusCase.Expected expected) {
        List<String> out = new ArrayList<>();
        out.add("--- " + expected.recordedBy());
        out.add("expect-text:");
        for (String line : expected.text()) out.add(textLine(line));
        out.add("expect-cursor: " + expected.line() + "," + expected.col());
        out.add("expect-mode: " + expected.mode());
        if (expected.visual() != null) out.add("expect-visual: " + expected.visual());
        for (char name : c.registers()) {
            CorpusCase.Register register = expected.registers().get(name);
            out.add("expect-register " + name + ": " + (register == null ? "none" : register.type() + " " + Json.quote(register.text())));
        }
        return out;
    }

    static String textLine(String content) {
        boolean mark = content.isEmpty() || content.endsWith(" ") || content.endsWith("\t") || content.endsWith("$");
        return "  " + content + (mark ? "$" : "");
    }
}
