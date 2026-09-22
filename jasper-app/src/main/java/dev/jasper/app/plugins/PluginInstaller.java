package dev.jasper.app.plugins;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Turns a plugin zip into a pending install. Staging unpacks only jars, under bounded sizes and with
 * names that cannot leave the staging directory, then validates the descriptor exactly as discovery
 * will. Committing happens inside the state lock together with the consent it records. Never the EDT.
 */
final class PluginInstaller {
    /** A refusal whose message is written for the user. */
    static final class InstallFailure extends Exception {
        private static final long serialVersionUID = 1L;
        InstallFailure(String message) { super(message); }
    }

    /** An unpacked, validated plugin that nothing has committed to yet. */
    record Staged(Path directory, PluginCandidate candidate) { }

    private static final int MAX_ENTRIES = 4096;
    private static final int MAX_JARS = 256;
    private static final long MAX_BYTES = 512L * 1024 * 1024;
    private static final Pattern JAR_NAME = Pattern.compile("[A-Za-z0-9 ._+-]+\\.jar");
    private static final String LAYOUT = "The zip must hold the plugin's jars at its root or in one folder";

    private PluginInstaller() { }

    static Staged stage(Path zip, Path userDirectory, Version sdk) throws InstallFailure {
        Path staging = null;
        try {
            Files.createDirectories(userDirectory);
            staging = Files.createTempDirectory(userDirectory, PluginMaintenance.STAGING_PREFIX);
            unpack(zip, staging);
            List<String> problems = new ArrayList<>();
            Optional<PluginCandidate> found = PluginDiscovery.single(staging, PluginCandidate.Origin.USER, problems);
            if (found.isEmpty()) {
                String prefix = staging + ": ";
                throw new InstallFailure("Not a Jasper plugin: " + String.join("; ", problems.stream()
                    .map(problem -> problem.startsWith(prefix) ? problem.substring(prefix.length()) : problem).toList()));
            }
            PluginDescriptor descriptor = found.get().descriptor();
            if (!descriptor.sdk().contains(sdk))
                throw new InstallFailure(descriptor.name() + " " + descriptor.version() + " needs SDK " + descriptor.sdk()
                    + "; this Jasper has SDK " + sdk);
            return new Staged(staging, found.get());
        } catch (IOException | RuntimeException failure) {
            discard(staging);
            throw new InstallFailure("The zip could not be read: " + failure.getMessage());
        } catch (InstallFailure failure) {
            discard(staging);
            throw failure;
        }
    }

    private static void unpack(Path zip, Path staging) throws IOException, InstallFailure {
        long budget = MAX_BYTES;
        int entries = 0;
        Set<String> folders = new TreeSet<>();
        Set<String> names = new HashSet<>();
        try (var file = new ZipFile(zip.toFile())) {
            for (var all = file.entries(); all.hasMoreElements();) {
                ZipEntry entry = all.nextElement();
                if (++entries > MAX_ENTRIES) throw new InstallFailure("The zip has too many entries");
                if (entry.isDirectory()) continue;
                String[] parts = entry.getName().replace('\\', '/').split("/", -1);
                String name = parts[parts.length - 1];
                if (!name.endsWith(".jar") || name.startsWith(".") || parts[0].equals("__MACOSX")) continue;
                for (String part : parts)
                    if (part.isEmpty() || part.equals(".") || part.equals(".."))
                        throw new InstallFailure("The zip has an unsafe entry name: " + entry.getName());
                if (!JAR_NAME.matcher(name).matches()) throw new InstallFailure("The zip has an unsafe entry name: " + entry.getName());
                if (parts.length > 2) throw new InstallFailure(LAYOUT);
                folders.add(parts.length == 2 ? parts[0] : "");
                if (folders.size() > 1) throw new InstallFailure(LAYOUT);
                if (!names.add(name)) throw new InstallFailure("The zip holds " + name + " twice");
                if (names.size() > MAX_JARS) throw new InstallFailure("The zip holds too many jars");
                // The name was matched against JAR_NAME, so it has no separator and stays inside staging.
                try (InputStream input = file.getInputStream(entry);
                     OutputStream output = Files.newOutputStream(staging.resolve(name), StandardOpenOption.CREATE_NEW)) {
                    budget = copy(input, output, budget);
                }
            }
        }
        if (names.isEmpty()) throw new InstallFailure("The zip holds no jar files");
    }

    /** Counts what is actually written: a zip's declared sizes are not to be trusted. */
    private static long copy(InputStream input, OutputStream output, long budget) throws IOException, InstallFailure {
        byte[] buffer = new byte[64 * 1024];
        for (int read; (read = input.read(buffer)) > 0;) {
            budget -= read;
            if (budget < 0) throw new InstallFailure("The zip is too large when unpacked");
            output.write(buffer, 0, read);
        }
        return budget;
    }

    /** One transaction: the staged plugin becomes the pending install and its capabilities become the consent. */
    static void commit(Staged staged, Path userDirectory, PluginStateStore store) throws IOException { commit(staged, userDirectory, store, true); }

    /** {@code consent}: whether the user reviewed the capabilities (a manager install) or not (a zip dropped into the directory). */
    static void commit(Staged staged, Path userDirectory, PluginStateStore store, boolean consent) throws IOException {
        String id = staged.candidate().id();
        Path pending = userDirectory.resolve(PluginMaintenance.PENDING).resolve(id);
        try {
            store.transact(state -> {
                try {
                    PluginMaintenance.deleteRecursively(pending);
                    Files.createDirectories(pending.getParent());
                    PluginMaintenance.move(staged.directory(), pending);
                } catch (IOException failure) { throw new UncheckedIOException(failure); }
                return consent ? PluginStateStore.consenting(id, staged.candidate().descriptor().capabilities()).apply(state) : state;
            });
        } catch (UncheckedIOException failure) { throw failure.getCause(); }
    }

    /** Best effort; staging directories that survive are swept at a later launch. */
    static void discard(Path stagingDirectoryOrNull) {
        if (stagingDirectoryOrNull == null) return;
        try { PluginMaintenance.deleteRecursively(stagingDirectoryOrNull); }
        catch (IOException ignored) { /* swept later */ }
    }

    /** Drops a pending install; a consent with nothing installed behind it goes too. */
    static void discardPending(String id, Path userDirectory, PluginStateStore store) throws IOException {
        try {
            store.transact(state -> {
                try { PluginMaintenance.deleteRecursively(userDirectory.resolve(PluginMaintenance.PENDING).resolve(id)); }
                catch (IOException failure) { throw new UncheckedIOException(failure); }
                if (!Files.isDirectory(userDirectory.resolve(id))) state.remove(id);
                return state;
            });
        } catch (UncheckedIOException failure) { throw failure.getCause(); }
    }
}
