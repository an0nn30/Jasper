package dev.jasper.editor.vim;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The vimrc and the files it sources (spec 7.1), ported from Vim's do_source and getsourceline: lines run in order as
 * command lines; {@code "} starts a comment line; a line starting with {@code \} continues the one before;
 * {@code if has('ide')} and {@code has('jasper')} are true (so IdeaVim's guarded blocks apply) and any other condition
 * false, with {@code elseif}, {@code else} and {@code endif}; {@code finish} ends the file. A line that fails is
 * skipped and reported as Vim reports it ({@code Error detected while processing …}, {@code line    3:}, the error);
 * the rest of the file still runs.
 */
public final class Vimrc {
    private Vimrc() { }

    /** How deep {@code source} may nest (spec 7.1). */
    static final int MAX_DEPTH = 10;

    /**
     * What reading a vimrc found.
     *
     * @param found whether the file exists (a missing vimrc is not an error)
     * @param error why it could not be read, or null
     * @param skipped how many lines failed and were skipped, in it and the files it sourced
     */
    public record Result(boolean found, String error, int skipped) { }

    /** A reading under way: the report so far, the file being read, and how deep sourcing is. */
    static final class Reading {
        final List<String> report = new ArrayList<>();
        int skipped;
        int depth;
        Path file;
        /** The file the last "Error detected while processing" line named. */
        private Path reported;

        void failed(int line, String error) {
            skipped++;
            if (!file.equals(reported)) {
                report.add("Error detected while processing " + file + ":");
                reported = file;
            }
            report.add(String.format("line %4d:", line));
            report.add(error);
        }
    }

    /**
     * Reads the vimrc into Vim's global state, running its lines in {@code editor}; each skipped line joins the
     * message history, for {@code :messages}.
     *
     * @param editor the tab whose editor runs the lines
     * @param file the vimrc
     * @return whether it was there, why it could not be read, and how many lines were skipped
     */
    public static Result read(VimEditor editor, Path file) {
        Reading reading = new Reading();
        try {
            source(editor, file, reading);
        } catch (NoSuchFileException missing) {
            return new Result(false, null, 0);
        } catch (IOException unreadable) {
            String reason = unreadable.getMessage() == null ? unreadable.getClass().getSimpleName() : unreadable.getMessage();
            return new Result(true, reason, 0);
        }
        for (String line : reading.report) editor.vim().remember(line);
        return new Result(true, null, reading.skipped);
    }

    /**
     * do_source: runs a file's lines, reporting the ones that fail into {@code reading}.
     *
     * @throws IOException when the file cannot be read
     */
    static void source(VimEditor editor, Path file, Reading reading) throws IOException {
        if (reading.depth >= MAX_DEPTH) throw new VimFailure("E169: Command too recursive");
        List<String> physical = lines(file);
        Vim vim = editor.vim();
        Reading outer = vim.reading();
        Path outerFile = reading.file;
        reading.depth++;
        reading.file = file;
        vim.reading(reading);
        try {
            run(editor, logical(physical), reading);
        } finally {
            reading.depth--;
            reading.file = outerFile;
            vim.reading(outer);
        }
    }

    /** The file's lines as UTF-8, with any malformed bytes replaced. */
    private static List<String> lines(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE).decode(ByteBuffer.wrap(bytes)).toString();
        if (text.startsWith("﻿")) text = text.substring(1);
        List<String> lines = new ArrayList<>(List.of(text.split("\r?\n", -1)));
        if (!lines.isEmpty() && lines.getLast().isEmpty()) lines.removeLast();
        return lines;
    }

    /** A line to run and the number of its first physical line. */
    private record Line(int number, String text) { }

    /**
     * getsourceline's line continuation: a line whose first non-blank is {@code \} joins the one before, without the
     * backslash; {@code "\ } starts a comment inside a continued line.
     */
    private static List<Line> logical(List<String> physical) {
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < physical.size(); i++) {
            String text = physical.get(i);
            String trimmed = text.stripLeading();
            if (!lines.isEmpty() && trimmed.startsWith("\\")) {
                Line last = lines.removeLast();
                lines.add(new Line(last.number(), last.text() + trimmed.substring(1)));
            } else if (!lines.isEmpty() && trimmed.startsWith("\"\\ ")) {
                continue;
            } else lines.add(new Line(i + 1, text));
        }
        return lines;
    }

    /** The conditional commands, as Vim abbreviates them. */
    private static final Pattern CONDITIONAL = Pattern.compile("(if|elsei(?:f)?|el(?:se?)?|en(?:d(?:if?)?)?|fini(?:sh?)?)(?![A-Za-z])\\s*(.*)");

    /** One {@code if} being read: whether the lines around it run, whether a branch was taken, and whether this one runs. */
    private record Conditional(boolean outer, boolean taken, boolean active) { }

    private static void run(VimEditor editor, List<Line> lines, Reading reading) {
        Deque<Conditional> stack = new ArrayDeque<>();
        for (Line line : lines) {
            String text = stripColons(line.text());
            if (text.isEmpty() || text.startsWith("\"")) continue;
            boolean active = stack.isEmpty() || stack.peek().active();
            Matcher m = CONDITIONAL.matcher(text);
            if (m.matches()) {
                String word = m.group(1), condition = m.group(2);
                if (word.equals("if")) {
                    boolean truth = active && condition(condition);
                    stack.push(new Conditional(active, truth, truth));
                } else if (word.startsWith("elsei")) {
                    if (stack.isEmpty()) reading.failed(line.number(), "E582: :elseif without :if");
                    else {
                        Conditional c = stack.pop();
                        boolean truth = c.outer() && !c.taken() && condition(condition);
                        stack.push(new Conditional(c.outer(), c.taken() || truth, truth));
                    }
                } else if (word.startsWith("el")) {
                    if (stack.isEmpty()) reading.failed(line.number(), "E581: :else without :if");
                    else {
                        Conditional c = stack.pop();
                        stack.push(new Conditional(c.outer(), true, c.outer() && !c.taken()));
                    }
                } else if (word.startsWith("en")) {
                    if (stack.isEmpty()) reading.failed(line.number(), "E580: :endif without :if");
                    else stack.pop();
                } else if (active) return;
                continue;
            }
            if (!active) continue;
            String error = editor.ex().sourceLine(text);
            if (error != null) reading.failed(line.number(), error);
        }
        if (!stack.isEmpty()) reading.failed(lines.getLast().number(), "E171: Missing :endif");
    }

    private static String stripColons(String text) {
        int i = 0;
        while (i < text.length() && (Ex.isWhite(text.charAt(i)) || text.charAt(i) == ':')) i++;
        return text.substring(i);
    }

    /** A condition Jasper knows: {@code has('ide')} and {@code has('jasper')} are true, a number is itself; anything else is false. */
    static boolean condition(String text) {
        String c = text.strip();
        int comment = c.indexOf(" \"");
        if (comment >= 0) c = c.substring(0, comment).strip();
        if (c.matches("-?\\d+")) return Long.parseLong(c) != 0;
        Matcher has = Pattern.compile("has\\(\\s*(['\"])(ide|jasper)\\1\\s*\\)").matcher(c);
        return has.matches();
    }
}
