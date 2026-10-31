package dev.jasper.app.bootstrap;

import dev.jasper.app.platform.Browser;
import dev.jasper.app.platform.FileUrls;
import dev.jasper.app.residency.LaunchRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.stream.Stream;

/**
 * Startup options parsed before any desktop initialization. An argument that does not start with {@code --} is a web
 * address to open when its scheme is {@code http} or {@code https}, in any case, read as a browser reads an address it
 * is given ({@link #webAddress}; browser plan 4c), at most {@link LaunchRequest#MAX_URLS}; otherwise it is a path to
 * open (workspace-tabs spec 5.4): a {@code file:} URL names its file ({@link FileUrls#localFile}), and anything else
 * resolves against the working directory. An http or https argument that is no web address, and a {@code file:} URL
 * that names no local file, are refused. {@code --url}, the launch a registered command makes, is a form of its own
 * ({@link #link}).
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
    /** What browsers percent-encode in an address they are given, after its host, that {@link URI} refuses. */
    private static final String ENCODED = " \"<>\\^`{|}";
    private static final HexFormat HEX = HexFormat.of().withUpperCase();

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
        if (args.length > 0 && args[0].equals("--url")) return link(args, workingDirectory);
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
                case "--url" -> throw invalid("--url must be the first argument.");
                default -> {
                    if (args[i].startsWith("--")) throw invalid("Unknown argument.");
                    if (!webScheme(args[i])) paths.add(positional(args[i], workingDirectory));
                    else if (++addresses > LaunchRequest.MAX_URLS)
                        throw invalid("Jasper opens at most " + LaunchRequest.MAX_URLS + " web addresses at once.");
                    else urls.add(webAddress(args[i]));
                }
            }
        }
        if (noWindow && background) throw invalid("--no-window and --background cannot be combined.");
        return new AppArguments(override, help, background, safeMode, pluginDir, false, noWindow, replacing,
            workingDirectory, paths.build().toList(), urls.build().toList(), List.of(args));
    }

    /**
     * The launch a registered command makes, such as Windows' {@code "Jasper.exe" --url "%1"} (browser plan 4c, ruling
     * Q13): {@code --url} first, then at most one argument and nothing after it: a web address, read as
     * {@link #webAddress} reads one, or a {@code file:} URL naming a local file, which Linux's {@code Jasper --url %u}
     * passes for Open With and a drop on the launcher and which opens as that path (ruling Q18); without one it is a
     * plain launch. A link that breaks out of its quotes therefore adds no option or path: the whole launch is refused.
     */
    private static AppArguments link(String[] args, Path workingDirectory) {
        if (args.length > 2) throw invalid("--url takes one web address or file: URL and nothing after it.");
        List<Path> paths = List.of();
        List<URI> urls = List.of();
        if (args.length == 2) {
            if (isFileUrl(args[1])) paths = List.of(positional(args[1], workingDirectory));
            else if (webScheme(args[1])) urls = List.of(webAddress(args[1]));
            else throw invalid("Not a web address or file: URL: " + args[1]);
        }
        return new AppArguments(null, false, false, false, null, false, false, OptionalLong.empty(), workingDirectory,
            paths, urls, List.of(args));
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
        Path named;
        try { named = isFileUrl(text) ? localFile(text) : Path.of(text); }
        catch (InvalidPathException ignored) { throw invalid("Invalid path: " + text); }
        return workingDirectory.resolve(named).toAbsolutePath().normalize();
    }

    private static boolean isFileUrl(String text) { return text.regionMatches(true, 0, "file:", 0, 5); }

    /**
     * The local file a {@code file:} URL names, such as {@code file:///Users/me/x.html}, given as a path or after
     * {@code --url}, as Linux's desktop entry passes it (browser plan 4c, Review Focus 2; ruling Q18).
     *
     * @throws IllegalArgumentException for one that names no local file: an opaque one, one that does not parse, and one
     *     naming another host, except on Windows, where that is the share it names
     */
    private static Path localFile(String text) {
        try {
            var file = FileUrls.localFile(new URI(text));
            if (file.isPresent()) return file.get();
        } catch (URISyntaxException notAUrl) {
            // Refused below.
        }
        throw invalid("Not a local file: " + text);
    }

    /** Whether {@code text}'s scheme is http or https, in any case: it is then a web address or refused, never a path. */
    private static boolean webScheme(String text) {
        return text.regionMatches(true, 0, "http:", 0, 5) || text.regionMatches(true, 0, "https:", 0, 6);
    }

    /**
     * {@code text}, whose scheme is http or https, as a web address, read as a browser reads an address it is given:
     * after the host, each character {@link URI} refuses and browsers percent-encode (a space and
     * {@code " < > \ ^ ` { | }}) is encoded, and so is a {@code %} that starts no escape; everything else, escapes
     * included, stays as it is.
     *
     * @throws IllegalArgumentException when that is still no web address ({@link Browser#isWebAddress}): a space in the
     *     host, no {@code //host}, nothing after the scheme
     */
    private static URI webAddress(String text) {
        try {
            URI address = new URI(encodedAsBrowsersDo(text));
            if (Browser.isWebAddress(address)) return address;
        } catch (URISyntaxException notOne) {
            // Refused below.
        }
        throw invalid("Not a web address: " + text);
    }

    /** {@code text} with what follows its scheme and {@code //host} percent-encoded as {@link #webAddress} says. */
    private static String encodedAsBrowsersDo(String text) {
        int start = text.indexOf(':') + 1;
        if (text.startsWith("//", start)) {
            start += 2;
            while (start < text.length() && "/?#".indexOf(text.charAt(start)) < 0) start++;
        }
        var encoded = new StringBuilder(text.length() + 16).append(text, 0, start);
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean escape = c == '%' && i + 2 < text.length() && HexFormat.isHexDigit(text.charAt(i + 1))
                && HexFormat.isHexDigit(text.charAt(i + 2));
            if ((c == '%' && !escape) || ENCODED.indexOf(c) >= 0) encoded.append('%').append(HEX.toHexDigits((byte) c));
            else encoded.append(c);
        }
        return encoded.toString();
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
