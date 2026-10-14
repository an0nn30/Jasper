package dev.jasper.editor.command;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@code jasper editor [-w|--wait] [FILE[:LINE[:COLUMN]]]...}, parsed. Pure, apart from {@code isFile}. */
public final class EditorArguments {
    public static final String USAGE = "Usage: jasper editor [-w|--wait] [FILE[:LINE[:COLUMN]]]...\n"
        + "Opens each FILE in Jasper's Editor, at LINE and COLUMN when given; a FILE that does not exist yet is created\n"
        + "when you save it. With --wait, returns once every tab it opened has closed, as git's core.editor needs.\n";

    /** A trailing :LINE or :LINE:COLUMN; nine digits at most, so it always fits an int. */
    private static final Pattern POSITION = Pattern.compile("(.+?):(\\d{1,9})(?::(\\d{1,9}))?");

    /** A file to open, and the line and column (1-based; 0 means none) to put the caret on. */
    public record Target(Path file, int line, int column) { }

    /** What a call asks for. */
    public sealed interface Request permits Open, Help, Invalid { }

    /** Open these files; {@code waiting} for --wait. */
    public record Open(boolean waiting, List<Target> targets) implements Request {
        public Open { targets = List.copyOf(targets); }
    }

    /** Print the usage. */
    public record Help() implements Request { }

    /** A usage error. */
    public record Invalid(String message) implements Request { }

    private EditorArguments() { }

    /**
     * @param isFile whether a path names an existing regular file
     */
    public static Request parse(List<String> arguments, Path workingDirectory, Predicate<Path> isFile) {
        boolean waiting = false, options = true;
        var targets = new ArrayList<Target>();
        for (String argument : arguments) {
            if (options && (argument.equals("-w") || argument.equals("--wait"))) {
                waiting = true;
            } else if (options && (argument.equals("-h") || argument.equals("--help"))) {
                return new Help();
            } else if (options && argument.equals("--")) {
                options = false;
            } else if (options && argument.length() > 1 && argument.startsWith("-")) {
                return new Invalid("unknown option " + argument);
            } else {
                Target target = target(argument, workingDirectory, isFile);
                if (target == null) return new Invalid("not a file path: " + argument);
                targets.add(target);
            }
        }
        if (waiting && targets.isEmpty()) return new Invalid("--wait needs a file");
        return new Open(waiting, targets);
    }

    /**
     * The argument as written when that names an existing file (even one called {@code a:1}); otherwise a trailing
     * {@code :LINE} or {@code :LINE:COLUMN} of digits is split off, so {@code C:\x} is never split. Null when it is not
     * a path at all.
     */
    static Target target(String argument, Path workingDirectory, Predicate<Path> isFile) {
        Path whole = resolve(argument, workingDirectory);
        if (whole != null && isFile.test(whole)) return new Target(whole, 0, 0);
        Matcher position = POSITION.matcher(argument);
        if (position.matches()) {
            Path file = resolve(position.group(1), workingDirectory);
            if (file != null) {
                return new Target(file, Integer.parseInt(position.group(2)),
                    position.group(3) == null ? 0 : Integer.parseInt(position.group(3)));
            }
        }
        return whole == null ? null : new Target(whole, 0, 0);
    }

    private static Path resolve(String argument, Path workingDirectory) {
        if (argument.isEmpty()) return null;
        try {
            return workingDirectory.resolve(argument).normalize();
        } catch (InvalidPathException invalid) {
            return null;
        }
    }
}
