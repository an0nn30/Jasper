package dev.jasper.app.commandline;

import dev.jasper.app.platform.WindowsUserPath;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Putting the {@code jasper} command on {@code PATH} and taking it off again, the way this OS does it. Blocking: never on the EDT. */
public final class CommandInstall {
    /** Where the command stands. */
    public enum Kind { INSTALLED, NOT_INSTALLED, MISSING_TARGET, OTHER_PROGRAM, UNAVAILABLE }

    /** Where the command stands, and the sentence Settings shows for it. */
    public record State(Kind kind, String text) {
        static State unavailable() { return new State(Kind.UNAVAILABLE, "Needs an installed Jasper"); }
        static State notInstalled() { return new State(Kind.NOT_INSTALLED, "Not installed"); }
    }

    /** The user cancelled the system's administrator prompt; nothing changed. */
    public static final class Declined extends IOException {
        private static final long serialVersionUID = 1L;
        Declined() { super("The administrator prompt was cancelled"); }
    }

    /** One OS's way of putting the command on PATH. */
    interface Placement {
        /** Whether there is a command to install: only an installed app image has one. */
        boolean available();
        State state();
        /** {@code replace} allows replacing another program of the same name. */
        void install(boolean replace) throws IOException;
        /** Removes only what Jasper put there. */
        void uninstall() throws IOException;
        /** What the user still has to do after installing, given the {@code PATH} Jasper runs with. */
        Optional<String> hint(String pathVariable);
    }

    private final Placement placement;

    CommandInstall(Placement placement) { this.placement = Objects.requireNonNull(placement, "placement"); }

    /**
     * This platform's: a link at {@code /usr/local/bin/jasper} on macOS (through the administrator prompt when that folder
     * needs it), at {@code ~/.local/bin/jasper} on Linux, or the image's {@code bin} folder in the user's {@code Path} on
     * Windows.
     *
     * @param appPath the launcher's path ({@code jpackage.app-path}); null in a development run, which cannot install
     */
    public static CommandInstall forThisPlatform(String osName, String appPath, Path home) {
        Path client = clientIn(osName, appPath).orElse(null);
        if (osName.startsWith("Windows")) {
            return new CommandInstall(new WindowsPathPlacement(client == null ? null : client.getParent(), WindowsUserPath::read,
                value -> {
                    try { WindowsUserPath.write(value); }
                    catch (IOException failure) { throw new UncheckedIOException(failure); }
                }));
        }
        if (osName.startsWith("Mac")) {
            return new CommandInstall(new LinkPlacement(Path.of("/usr/local/bin/jasper"), client, command -> {
                try { MacAdministrator.run(command); }
                catch (IOException failure) { throw new UncheckedIOException(failure); }
            }, false));
        }
        return new CommandInstall(new LinkPlacement(home.resolve(".local").resolve("bin").resolve("jasper"), client, null));
    }

    /** The command inside an installed app image, from its launcher's path; empty for a development run. */
    public static Optional<Path> clientIn(String osName, String appPath) {
        if (appPath == null || appPath.isBlank()) return Optional.empty();
        Path folder = Path.of(appPath).getParent();
        if (folder == null) return Optional.empty();
        if (osName.startsWith("Mac")) return Optional.of(folder.resolveSibling("Resources").resolve("bin").resolve("jasper"));
        if (osName.startsWith("Windows")) return Optional.of(folder.resolve("bin").resolve("jasper.exe"));
        return Optional.ofNullable(folder.getParent()).map(root -> root.resolve("lib").resolve("bin").resolve("jasper"));
    }

    /**
     * The {@code jasper} command this Jasper names for other programs, such as in {@code EDITOR}: the one inside the
     * installed app image, else in a development run {@code developmentClient} (the {@code jasper.commandClient}
     * property the build sets), else none.
     *
     * @param osName the {@code os.name} property, which says where the app image keeps the command
     * @param appPath the launcher's path ({@code jpackage.app-path}); null or blank in a development run
     * @param developmentClient the built client's path, or null or blank
     * @return the command's path; empty when there is no app image and no usable development client
     */
    public static Optional<Path> client(String osName, String appPath, String developmentClient) {
        Optional<Path> installed = clientIn(osName, appPath);
        if (installed.isPresent()) return installed;
        if (developmentClient == null || developmentClient.isBlank()) return Optional.empty();
        try {
            return Optional.of(Path.of(developmentClient));
        } catch (java.nio.file.InvalidPathException unusable) {
            return Optional.empty();
        }
    }

    public boolean available() { return placement.available(); }

    public State state() { return placement.state(); }

    public void install(boolean replace) throws IOException { placement.install(replace); }

    public void uninstall() throws IOException { placement.uninstall(); }

    public Optional<String> hint(String pathVariable) { return placement.hint(pathVariable); }
}
