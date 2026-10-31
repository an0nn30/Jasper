package dev.jasper.app.bootstrap;

import dev.jasper.app.platform.Browser;
import dev.jasper.app.residency.LaunchRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Stream;

/**
 * Startup options parsed before any desktop initialization. An argument that does not start with {@code --} is a web
 * address to open when it is an absolute {@code http} or {@code https} URL ({@link Browser#webAddress}; browser plan
 * 4c), at most {@link LaunchRequest#MAX_URLS}; otherwise it is a path to open (workspace-tabs spec 5.4): a
 * {@code file:} URL names its file, and anything else resolves against the working directory.
 *
 * @param configOverride {@code --config}, or null
 * @param help {@code --help}
 * @param background {@code --background}: the login item's windowless start
 * @param safeMode {@code --safe-mode}
 * @param pluginDir {@code --plugin-dir}, or null
 * @param homeOverride whether the home came from {@code jasper.home} or {@code JASPER_HOME}
 * @param noWindow {@code --no-window}: the {@code jasper} command's windowless start
 * @param replacing {@code --replacing}: the process a restart's replacement waits for; not in the usage
 * @param workingDirectory the directory the launch ran in
 * @param paths the paths to open, absolute and normalized, in order
 * @param urls the web addresses to open, in order, as given
 * @param given the arguments as {@code main} received them, which a restart replays
 */
record AppArguments(Path configOverride, boolean help, boolean background, boolean safeMode, Path pluginDir,
                    boolean homeOverride, boolean noWindow, OptionalLong replacing, Path workingDirectory,
                    List<Path> paths, List<URI> urls, List<String> given) {
    static final String USAGE = "Usage: jasper [--config <path>] [--background] [--no-window] [--safe-mode] "
        + "[--plugin-dir <path>] [--help] [<path>|<url>...]";

    AppArguments {
        Objects.requireNonNull(replacing, "replacing");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        paths = List.copyOf(paths);
        urls = List.copyOf(urls);
        given = List.copyOf(given);
    }

    /** Records whether the application home was overridden ({@code jasper.home} or {@code JASPER_HOME}). */
    AppArguments withHomeOverride(boolean value) {
        return new AppArguments(configOverride, help, background, safeMode, pluginDir, value, noWindow, replacing,
            workingDirectory, paths, urls, given);
    }

    /**
     * The start-up flags this launch carries: what a second launch reports to the owner as ignored (spec 5.2).
     *
     * @return {@code --safe-mode}, {@code --config} and {@code --plugin-dir}, those present, in that order
     */
    List<String> startupFlags() {
        return Stream.of(safeMode ? "--safe-mode" : null, configOverride != null ? "--config" : null,
            pluginDir != null ? "--plugin-dir" : null).filter(Objects::nonNull).toList();
    }

    /** Residency applies only to the default home launched without start-up flags (spec 5.2). */
    boolean residencyAllowed() {
        return !homeOverride && startupFlags().isEmpty();
    }

    static AppArguments parse(String[] args, Path workingDirectory) {
        Path override = null;
        Path pluginDir = null;
        boolean help = false, background = false, safeMode = false, noWindow = false;
        OptionalLong replacing = OptionalLong.empty();
        Stream.Builder<Path> paths = Stream.builder();
        Stream.Builder<URI> urls = Stream.builder();
        int addresses = 0;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help" -> {
                    if (help) throw invalid("Duplicate --help option.");
                    help = true;
                }
                case "--background" -> {
                    if (background) throw invalid("Duplicate --background option.");
                    background = true;
                }
                case "--no-window" -> {
                    if (noWindow) throw invalid("Duplicate --no-window option.");
                    noWindow = true;
                }
                case "--safe-mode" -> {
                    if (safeMode) throw invalid("Duplicate --safe-mode option.");
                    safeMode = true;
                }
                case "--config" -> {
                    if (override != null) throw invalid("Duplicate --config option.");
                    override = path(args, ++i, "--config", "a file path", workingDirectory);
                }
                case "--plugin-dir" -> {
                    if (pluginDir != null) throw invalid("Duplicate --plugin-dir option.");
                    pluginDir = path(args, ++i, "--plugin-dir", "a directory path", workingDirectory);
                }
                case "--replacing" -> {
                    if (replacing.isPresent()) throw invalid("Duplicate --replacing option.");
                    replacing = OptionalLong.of(processId(args, ++i));
                }
                default -> {
                    if (args[i].startsWith("--")) throw invalid("Unknown argument.");
                    Optional<URI> address = Browser.webAddress(args[i]);
                    if (address.isEmpty()) paths.add(positional(args[i], workingDirectory));
                    else if (++addresses > LaunchRequest.MAX_URLS)
                        throw invalid("Jasper opens at most " + LaunchRequest.MAX_URLS + " web addresses at once.");
                    else urls.add(address.get());
                }
            }
        }
        if (noWindow && background) throw invalid("--no-window and --background cannot be combined.");
        return new AppArguments(override, help, background, safeMode, pluginDir, false, noWindow, replacing,
            workingDirectory, paths.build().toList(), urls.build().toList(), List.of(args));
    }

    private static Path path(String[] args, int index, String option, String what, Path workingDirectory) {
        if (index >= args.length || args[index].isBlank() || args[index].startsWith("--"))
            throw invalid(option + " requires " + what + ".");
        try { return workingDirectory.resolve(Path.of(args[index])).toAbsolutePath().normalize(); }
        catch (InvalidPathException ignored) { throw invalid(option + " requires a valid path."); }
    }

    /** A path to open, as the launch names it (spec 5.4): a {@code file:} URL's file, else the text as a path. */
    private static Path positional(String text, Path workingDirectory) {
        if (text.isBlank()) throw invalid("A path cannot be blank.");
        Optional<Path> file = fileUrl(text);
        try { return workingDirectory.resolve(file.orElseGet(() -> Path.of(text))).toAbsolutePath().normalize(); }
        catch (InvalidPathException ignored) { throw invalid("Invalid path: " + text); }
    }

    /**
     * The local file a {@code file:} URL names, such as {@code file:///Users/me/x.html}, which a desktop entry may pass
     * (browser plan 4c, Review Focus 2); empty for any other text, or a {@code file:} URL this platform cannot read as a
     * local path (one naming another host on macOS and Linux), which then stays the text it is.
     */
    private static Optional<Path> fileUrl(String text) {
        if (!text.regionMatches(true, 0, "file:", 0, 5)) return Optional.empty();
        try {
            return Optional.of(Path.of(new URI(text)));
        } catch (URISyntaxException | IllegalArgumentException | java.nio.file.FileSystemNotFoundException notLocal) {
            return Optional.empty();
        }
    }

    private static long processId(String[] args, int index) {
        if (index < args.length) {
            try {
                long pid = Long.parseLong(args[index]);
                if (pid > 0) return pid;
            } catch (NumberFormatException ignored) {
                // Reported below.
            }
        }
        throw invalid("--replacing requires a process id.");
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message + "\n" + USAGE);
    }
}
