package dev.jasper.app.commandline;

import dev.jasper.app.commandline.CommandInstall.Kind;
import dev.jasper.app.commandline.CommandInstall.State;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;

/** The command as a symbolic link in a folder on PATH: {@code /usr/local/bin} on macOS, {@code ~/.local/bin} on Linux. */
final class LinkPlacement implements CommandInstall.Placement {
    private final Path link;
    private final Path client;
    private final Consumer<String> elevated;
    private final boolean hintsAtPath;

    /** A link that hints when its folder is not on {@code PATH}. */
    LinkPlacement(Path link, Path client, Consumer<String> elevated) {
        this(link, client, elevated, true);
    }

    /**
     * @param client the command in the installed image; null when there is none
     * @param elevated runs a shell command as an administrator, throwing {@link UncheckedIOException}; null where the
     *     folder is the user's own
     * @param hintsAtPath whether {@link #hint} may tell the user the folder is missing from {@code PATH}; false on macOS,
     *     where a Jasper started from Finder or the Dock has launchd's {@code PATH} and not the one terminals have
     */
    LinkPlacement(Path link, Path client, Consumer<String> elevated, boolean hintsAtPath) {
        this.link = link;
        this.client = client;
        this.elevated = elevated;
        this.hintsAtPath = hintsAtPath;
    }

    @Override public boolean available() { return client != null; }

    @Override public State state() {
        if (client == null) return State.unavailable();
        try {
            if (Files.isSymbolicLink(link)) {
                Path target = link.resolveSibling(Files.readSymbolicLink(link)).normalize();
                if (target.equals(client.normalize()) && Files.exists(client)) return new State(Kind.INSTALLED, "Installed at " + link);
                if (!Files.exists(link) && namesJasperCommand(target)) return new State(Kind.MISSING_TARGET, link + " points to a missing file");
                return new State(Kind.OTHER_PROGRAM, link + " is another program (" + target + ")");
            }
            if (Files.exists(link, LinkOption.NOFOLLOW_LINKS)) return new State(Kind.OTHER_PROGRAM, link + " is another program");
            return State.notInstalled();
        } catch (IOException | RuntimeException unreadable) {
            return new State(Kind.OTHER_PROGRAM, link + " is another program");
        }
    }

    @Override public void install(boolean replace) throws IOException {
        State now = state();
        if (now.kind() == Kind.UNAVAILABLE) throw new IOException("Needs an installed Jasper");
        if (now.kind() == Kind.INSTALLED) return;
        if (now.kind() == Kind.OTHER_PROGRAM && !replace) throw new IllegalStateException(now.text());
        Path temporary = link.resolveSibling(".jasper-" + ProcessHandle.current().pid());
        try {
            Files.createDirectories(link.getParent());
            Files.deleteIfExists(temporary);
            Files.createSymbolicLink(temporary, client);
            Files.move(temporary, link, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileSystemException denied) {
            if (elevated == null) throw denied;
            elevate("mkdir -p " + quote(link.getParent()) + " && ln -sfn " + quote(client) + " " + quote(link));
        } finally {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    @Override public void uninstall() throws IOException {
        if (state().kind() != Kind.INSTALLED) return;
        try {
            Files.delete(link);
        } catch (FileSystemException denied) {
            if (elevated == null) throw denied;
            elevate("rm -f " + quote(link));
        }
    }

    @Override public Optional<String> hint(String pathVariable) {
        if (!hintsAtPath) return Optional.empty();
        Path folder = link.getParent();
        boolean onPath = Arrays.stream(pathVariable.split(File.pathSeparator))
            .anyMatch(entry -> !entry.isBlank() && Path.of(entry).normalize().equals(folder.normalize()));
        return onPath ? Optional.empty() : Optional.of(folder + " is not on your PATH; add it to use jasper in other terminals");
    }

    /**
     * Whether a link's target ends with the last three names of this image's command ({@code Resources/bin/jasper} or
     * {@code lib/bin/jasper}), which is how a link to a Jasper that has since moved looks; any other dangling link is
     * someone else's.
     */
    private boolean namesJasperCommand(Path target) {
        int names = Math.min(client.getNameCount(), 3);
        return names > 0 && target.endsWith(client.subpath(client.getNameCount() - names, client.getNameCount()));
    }

    private void elevate(String command) throws IOException {
        try {
            elevated.accept(command);
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
    }

    private static String quote(Path path) { return "'" + path.toString().replace("'", "'\\''") + "'"; }
}
