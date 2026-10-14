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

    /**
     * @param client the command in the installed image; null when there is none
     * @param elevated runs a shell command as an administrator, throwing {@link UncheckedIOException}; null where the
     *     folder is the user's own
     */
    LinkPlacement(Path link, Path client, Consumer<String> elevated) {
        this.link = link;
        this.client = client;
        this.elevated = elevated;
    }

    @Override public boolean available() { return client != null; }

    @Override public State state() {
        if (client == null) return State.unavailable();
        try {
            if (Files.isSymbolicLink(link)) {
                Path target = link.resolveSibling(Files.readSymbolicLink(link)).normalize();
                if (target.equals(client.normalize()) && Files.exists(client)) return new State(Kind.INSTALLED, "Installed at " + link);
                if (!Files.exists(link)) return new State(Kind.MISSING_TARGET, link + " points to a missing file");
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
        Path folder = link.getParent();
        boolean onPath = Arrays.stream(pathVariable.split(File.pathSeparator))
            .anyMatch(entry -> !entry.isBlank() && Path.of(entry).normalize().equals(folder.normalize()));
        return onPath ? Optional.empty() : Optional.of(folder + " is not on your PATH; add it to use jasper in other terminals");
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
