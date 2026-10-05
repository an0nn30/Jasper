package dev.jasper.indexer.index;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Lists a local project's files: Git's view of a work tree when Git is available and the tree is not the home folder's,
 * otherwise a walk that skips excluded folder names. Git runs with {@code core.fsmonitor=false}, so a repository's own
 * configuration cannot start a program, and with {@code GIT_OPTIONAL_LOCKS=0}, so it never takes the index lock.
 * Blocking: call it off the UI thread.
 */
public final class LocalLister {
    static final Duration GIT_TIMEOUT = Duration.ofSeconds(60);
    private final List<String> git;
    private final Path home;

    /**
     * @param git the command that runs Git, such as {@code List.of("git")}
     * @param home the user's home folder
     */
    public LocalLister(List<String> git, Path home) {
        this.git = List.copyOf(git);
        this.home = home;
    }

    /**
     * @param root the project's root folder
     * @param excludes folder names never indexed
     * @param maxFiles the most files to return
     * @param cancelled polled while listing
     * @return the files, relative to the root and {@code /}-separated
     * @throws NoSuchFileException when the root is not a folder
     * @throws IOException when the walk fails, including when the root cannot be read
     * @throws CancellationException when {@code cancelled} turned true
     */
    public Listing list(Path root, Collection<String> excludes, int maxFiles, BooleanSupplier cancelled) throws IOException {
        if (!Files.isDirectory(root)) throw new NoSuchFileException(root.toString());
        Set<String> skip = Set.copyOf(excludes);
        Optional<Listing> tracked = gitListing(root, skip, maxFiles, cancelled);
        return tracked.isPresent() ? tracked.get() : walk(root, skip, maxFiles, cancelled);
    }

    private Optional<Listing> gitListing(Path root, Set<String> skip, int maxFiles, BooleanSupplier cancelled) {
        if (!workTreeBelowHome(root)) return Optional.empty();
        Optional<Process> started = start(root, "ls-files", "-z", "--cached", "--others", "--exclude-standard");
        if (started.isEmpty()) return Optional.empty();
        Process process = started.get();
        var files = new ArrayList<String>();
        Watchdog watchdog = Watchdog.of(process, GIT_TIMEOUT);
        try (InputStream in = new BufferedInputStream(process.getInputStream())) {
            var name = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1) {
                if (b != 0) {
                    name.write(b);
                    continue;
                }
                String path = name.toString(StandardCharsets.UTF_8);
                name.reset();
                if (cancelled.getAsBoolean()) {
                    process.destroyForcibly();
                    throw new CancellationException();
                }
                if (excluded(path, skip)) continue;
                if (files.size() >= maxFiles) {
                    process.destroyForcibly();
                    return Optional.of(new Listing(files, Strategy.GIT, true));
                }
                files.add(path);
            }
            return process.waitFor() == 0 ? Optional.of(new Listing(files, Strategy.GIT, false)) : Optional.empty();
        } catch (IOException failed) {
            process.destroyForcibly();
            return Optional.empty();
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new CancellationException();
        } finally {
            watchdog.close();
        }
    }

    /** Whether the root is in a Git work tree whose top level is not the home folder. */
    private boolean workTreeBelowHome(Path root) {
        Optional<Process> started = start(root, "rev-parse", "--show-toplevel");
        if (started.isEmpty()) return false;
        Process process = started.get();
        Watchdog watchdog = Watchdog.of(process, GIT_TIMEOUT);
        try {
            String top = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (process.waitFor() != 0 || top.isEmpty()) return false;
            return !same(Path.of(top), home);
        } catch (IOException | InvalidPathException failed) {
            process.destroyForcibly();
            return false;
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return false;
        } finally {
            watchdog.close();
        }
    }

    private Optional<Process> start(Path root, String... args) {
        var command = new ArrayList<>(git);
        command.addAll(List.of("-c", "core.fsmonitor=false", "-C", root.toString()));
        command.addAll(List.of(args));
        var builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("GIT_OPTIONAL_LOCKS", "0");
        try {
            Process process = builder.start();
            process.getOutputStream().close();
            return Optional.of(process);
        } catch (IOException missing) {
            return Optional.empty();
        }
    }

    /**
     * Walks the root's real folder, so a root that is a link lists what it names; links inside it are never followed.
     * A root that cannot be read fails the walk rather than listing nothing.
     */
    private static Listing walk(Path link, Set<String> skip, int maxFiles, BooleanSupplier cancelled) throws IOException {
        Path root = link.toRealPath();
        var files = new ArrayList<String>();
        var truncated = new boolean[1];
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                if (cancelled.getAsBoolean()) throw new CancellationException();
                return !dir.equals(root) && skip.contains(dir.getFileName().toString()) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (!attributes.isRegularFile()) return FileVisitResult.CONTINUE;
                if (files.size() >= maxFiles) {
                    truncated[0] = true;
                    return FileVisitResult.TERMINATE;
                }
                files.add(relative(root, file));
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFileFailed(Path file, IOException failure) throws IOException {
                if (file.equals(root)) throw failure;
                return FileVisitResult.CONTINUE;
            }
        });
        Collections.sort(files);
        return new Listing(files, Strategy.WALK, truncated[0]);
    }

    /** Whether a {@code /}-separated path has an excluded folder name. */
    static boolean excluded(String path, Set<String> skip) {
        int start = 0;
        for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', start)) {
            if (skip.contains(path.substring(start, slash))) return true;
            start = slash + 1;
        }
        return false;
    }

    private static String relative(Path root, Path file) {
        var out = new StringBuilder();
        for (Path part : root.relativize(file)) {
            if (!out.isEmpty()) out.append('/');
            out.append(part);
        }
        return out.toString();
    }

    private static boolean same(Path a, Path b) {
        try { return a.toRealPath().equals(b.toRealPath()); }
        catch (IOException unreadable) { return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize()); }
    }

    /** Kills a process that outlives its time limit; closing it means the caller finished first. */
    private record Watchdog(Thread thread) {
        static Watchdog of(Process process, Duration limit) {
            return new Watchdog(Thread.ofVirtual().start(() -> {
                try { if (!process.waitFor(limit.toMillis(), TimeUnit.MILLISECONDS)) process.destroyForcibly(); }
                catch (InterruptedException finished) { /* the caller finished first */ }
            }));
        }

        void close() { thread.interrupt(); }
    }
}
