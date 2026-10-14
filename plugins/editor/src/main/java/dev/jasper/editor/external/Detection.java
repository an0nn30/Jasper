package dev.jasper.editor.external;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Finds the known editors installed on this machine. Each editor's first program wins, looked for in the folders of
 * {@code PATH} in order, then in {@code /opt/homebrew/bin}, {@code /usr/local/bin} and {@code ~/.local/bin} (not on
 * Windows; a macOS app started from the Dock has a short {@code PATH}), then, on macOS, as the command-line tool inside
 * its app in {@code /Applications} and {@code ~/Applications}, then, on Windows, in Notepad++'s and Notepad's own
 * folders. On Windows a program name is tried with {@code .exe}, {@code .cmd} and {@code .bat}. Pure apart from
 * {@link Probe#executable()}, which touches the file system: run {@link #detect} off the UI thread; {@link #find} is one
 * editor's bounded search, a few checks in each folder, which the default editor runs on the UI thread at start.
 */
public final class Detection {
    private static final List<String> WINDOWS_SUFFIXES = List.of(".exe", ".cmd", ".bat");

    /**
     * What detection looks at.
     *
     * @param osName the {@code os.name} property
     * @param path the folders of {@code PATH}, in order
     * @param home the user's home folder
     * @param environment Jasper's environment, for the Windows program folders
     * @param executable whether a path is a program that can run
     */
    public record Probe(String osName, List<Path> path, Path home, Map<String, String> environment, Predicate<Path> executable) {
        /** Copies the folders and the environment; rejects nulls. */
        public Probe {
            Objects.requireNonNull(osName, "osName");
            path = List.copyOf(path);
            Objects.requireNonNull(home, "home");
            environment = Map.copyOf(environment);
            Objects.requireNonNull(executable, "executable");
        }

        /**
         * This machine: {@code os.name}, the absolute folders of {@code PATH} (blank, relative and malformed entries are
         * skipped), {@code user.home}, {@code System.getenv()}, and regular files that may be executed.
         *
         * @return the probe
         */
        public static Probe system() {
            var folders = new ArrayList<Path>();
            for (String entry : Objects.requireNonNullElse(System.getenv("PATH"), "").split(Pattern.quote(File.pathSeparator))) {
                if (entry.isBlank()) continue;
                try {
                    Path folder = Path.of(entry);
                    if (folder.isAbsolute()) folders.add(folder);
                } catch (InvalidPathException malformed) {
                    // An entry that is not a path names no program.
                }
            }
            return new Probe(System.getProperty("os.name", ""), folders, Path.of(System.getProperty("user.home")), System.getenv(),
                file -> Files.isRegularFile(file) && Files.isExecutable(file));
        }
    }

    /** Where to look on this system: the folders and applications every editor is searched in, and the system's kind. */
    private record Places(Set<Path> folders, List<Path> applications, boolean windows, Probe probe) {
        static Places of(Probe probe) {
            String os = probe.osName().toLowerCase(Locale.ROOT);
            boolean windows = os.startsWith("windows"), mac = os.startsWith("mac");
            Set<Path> folders = new LinkedHashSet<>(probe.path());
            if (!windows) folders.addAll(extraFolders(probe.home()));
            List<Path> applications = mac ? List.of(Path.of("/Applications"), probe.home().resolve("Applications")) : List.of();
            return new Places(folders, applications, windows, probe);
        }
    }

    private Detection() { }

    /**
     * The folders searched after {@code PATH}, except on Windows, so that a macOS app started from the Dock, whose
     * {@code PATH} is short, still finds what the user's shell finds. Custom commands add them to {@code PATH} too.
     *
     * @param home the user's home folder
     * @return {@code /opt/homebrew/bin}, {@code /usr/local/bin} and {@code <home>/.local/bin}, in that order
     */
    public static List<Path> extraFolders(Path home) {
        return List.of(Path.of("/opt/homebrew/bin"), Path.of("/usr/local/bin"), home.resolve(".local").resolve("bin"));
    }

    /**
     * @param probe what to look at
     * @return each known editor found, once, with the first program found for it, in {@link KnownEditors#ALL}'s order
     */
    public static List<Detected> detect(Probe probe) {
        Places places = Places.of(probe);
        var found = new ArrayList<Detected>();
        for (KnownEditor editor : KnownEditors.ALL) first(editor, places).ifPresent(program -> found.add(new Detected(editor, program)));
        return List.copyOf(found);
    }

    /**
     * The same search as {@link #detect} for one editor, for a caller that needs only that one now.
     *
     * @param probe what to look at
     * @param editor the editor to look for
     * @return it, with the first program found for it, or empty when it is not installed
     */
    public static Optional<Detected> find(Probe probe, KnownEditor editor) {
        Objects.requireNonNull(editor, "editor");
        return first(editor, Places.of(probe)).map(program -> new Detected(editor, program));
    }

    /** The editor's first program: in the folders, then inside its apps, then in its Windows folders. */
    private static Optional<Path> first(KnownEditor editor, Places places) {
        for (Path folder : places.folders()) {
            Optional<Path> program = program(editor, folder, places.windows(), places.probe());
            if (program.isPresent()) return program;
        }
        for (Path folder : places.applications()) {
            for (String tool : editor.macApps()) {
                Optional<Path> program = runnable(folder.resolve(tool), places.probe());
                if (program.isPresent()) return program;
            }
        }
        if (places.windows()) {
            for (Path folder : windowsFolders(editor, places.probe())) {
                Optional<Path> program = program(editor, folder, true, places.probe());
                if (program.isPresent()) return program;
            }
        }
        return Optional.empty();
    }

    /** The editor's first program name in the folder; on Windows each name with .exe, .cmd or .bat. */
    private static Optional<Path> program(KnownEditor editor, Path folder, boolean windows, Probe probe) {
        for (String name : editor.executables()) {
            for (String file : windows ? WINDOWS_SUFFIXES.stream().map(suffix -> name + suffix).toList() : List.of(name)) {
                Optional<Path> program = runnable(folder.resolve(file), probe);
                if (program.isPresent()) return program;
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> runnable(Path candidate, Probe probe) {
        Path absolute = candidate.toAbsolutePath().normalize();
        return probe.executable().test(absolute) ? Optional.of(absolute) : Optional.empty();
    }

    /** Where Notepad++ and Notepad install on Windows, from Jasper's environment. */
    private static List<Path> windowsFolders(KnownEditor editor, Probe probe) {
        var folders = new ArrayList<Path>();
        switch (editor.id()) {
            case "notepadpp" -> {
                folder(probe, "ProgramFiles").ifPresent(folder -> folders.add(folder.resolve("Notepad++")));
                folder(probe, "ProgramFiles(x86)").ifPresent(folder -> folders.add(folder.resolve("Notepad++")));
            }
            case "notepad" -> folder(probe, "SystemRoot").ifPresent(folder -> folders.add(folder.resolve("System32")));
            default -> { }
        }
        return folders;
    }

    /** The folder an environment variable names; Windows variable names ignore case. */
    private static Optional<Path> folder(Probe probe, String variable) {
        return probe.environment().entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(variable))
            .map(Map.Entry::getValue).filter(value -> !value.isBlank()).findFirst().flatMap(value -> {
                try {
                    return Optional.of(Path.of(value));
                } catch (InvalidPathException malformed) {
                    return Optional.empty();
                }
            });
    }
}
