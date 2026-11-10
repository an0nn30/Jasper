package dev.jasper.app.platform;

import dev.jasper.app.platform.FileAssociations.Claimability;
import dev.jasper.app.platform.FileAssociations.Handler;
import dev.jasper.app.platform.FileAssociations.Outcome;
import dev.jasper.app.platform.FileAssociations.Refusal;
import dev.jasper.app.platform.FileAssociations.TypeResult;
import dev.jasper.app.platform.FileAssociations.Unavailable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * File associations on Linux, through the freedesktop.org specs: {@code xdg-mime} reads and sets the default
 * application of each MIME type, Jasper removes its own default from the user's {@code mimeapps.list} to hand a type
 * back to no application, and an extension the MIME database does not know becomes Jasper's own
 * {@code text/x-jasper-<ext>} type, declared in a shared-mime-info package under the user's data folder.
 */
final class LinuxFileAssociations implements FileAssociations.Backend {
    private static final System.Logger LOG = System.getLogger(LinuxFileAssociations.class.getName());
    /** The package's menu entry, as {@code linuxDesktopFileName} in {@code gradle/packaging-support.gradle} names it. */
    static final String DESKTOP_ID = "jasper-workstation-Jasper.desktop";
    private static final String PACKAGE_FILE = "jasper-workstation.xml";
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final Duration UPDATE_TIMEOUT = Duration.ofSeconds(20);

    /**
     * The tools, as functions so tests can stand in for them.
     *
     * @param run runs a command for at most a time-out: its output when it exits with 0, else empty
     * @param onPath whether a program of that name is on {@code PATH}
     */
    record Tools(BiFunction<List<String>, Duration, Optional<String>> run, Predicate<String> onPath) {
        static Tools system() {
            return new Tools(ToolProcess::run, name -> {
                if (name.isEmpty() || name.contains("/")) return false;
                String path = System.getenv("PATH");
                if (path == null) return false;
                return Stream.of(path.split(File.pathSeparator)).filter(folder -> !folder.isEmpty())
                        .map(folder -> Path.of(folder, name))
                        .anyMatch(file -> Files.isRegularFile(file) && Files.isExecutable(file));
            });
        }
    }

    private final FileTypeCatalog catalog;
    private final Tools tools;
    private final Path dataHome;
    private final List<Path> dataFolders;
    private final Path configHome;
    /** The catalogue's {@code application/*} types, which are text here. */
    private final Set<String> textApplicationTypes;

    /**
     * @param environment where the XDG Base Directory variables are read
     * @param home the user's home folder, for the variables' defaults
     */
    LinuxFileAssociations(FileTypeCatalog catalog, Map<String, String> environment, Path home, Tools tools) {
        this.catalog = catalog;
        this.tools = tools;
        this.dataHome = folder(environment.get("XDG_DATA_HOME")).orElse(home.resolve(".local/share"));
        List<Path> system = folders(environment.get("XDG_DATA_DIRS"));
        List<Path> folders = new java.util.ArrayList<>(List.of(dataHome));
        folders.addAll(system.isEmpty() ? List.of(Path.of("/usr/local/share"), Path.of("/usr/share")) : system);
        this.dataFolders = List.copyOf(folders);
        this.configHome = folder(environment.get("XDG_CONFIG_HOME")).orElse(home.resolve(".config"));
        this.textApplicationTypes = catalog.extensions().stream().flatMap(extension -> catalog.linuxTypes(extension).stream())
                .filter(type -> type.startsWith("application/")).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** The data folders, the user's first: the spec ignores an empty or relative value. */
    List<Path> dataFolders() { return dataFolders; }

    private static Optional<Path> folder(String value) {
        if (value == null || value.isEmpty()) return Optional.empty();
        Path path = Path.of(value);
        return path.isAbsolute() ? Optional.of(path) : Optional.empty();
    }

    private static List<Path> folders(String value) {
        if (value == null) return List.of();
        return Stream.of(value.split(":")).map(LinuxFileAssociations::folder).flatMap(Optional::stream).toList();
    }

    @Override public Optional<Unavailable> unavailable() {
        if (!tools.onPath().test("xdg-mime")) return Optional.of(Unavailable.NO_XDG_MIME);
        if (desktopEntry(DESKTOP_ID).isEmpty()) return Optional.of(Unavailable.NOT_INSTALLED);
        return Optional.empty();
    }

    @Override public Claimability claimability(String extension) {
        if (catalog.extensions().contains(extension)) {
            List<String> types = catalog.linuxTypes(extension);
            return types.isEmpty() ? new Claimability.NotClaimable(Refusal.NOT_TEXT) : new Claimability.Claimable(types, false);
        }
        String body = extension.substring(1);
        List<String> globs = new java.util.ArrayList<>();
        for (Path folder : dataFolders) {
            Path file = folder.resolve("mime/globs2");
            try {
                if (Files.isRegularFile(file)) globs.add(Files.readString(file, StandardCharsets.UTF_8));
            } catch (IOException | UncheckedIOException failure) {
                LOG.log(System.Logger.Level.DEBUG, "Could not read " + file, failure);
            }
        }
        Optional<String> type = LinuxMime.globType(globs, body);
        if (type.isEmpty()) return new Claimability.Claimable(List.of(LinuxMime.OWN_TYPE_PREFIX + body), true);
        String found = type.get();
        return found.startsWith("text/") || textApplicationTypes.contains(found)
                ? new Claimability.Claimable(List.of(found), false)
                : new Claimability.NotClaimable(Refusal.NOT_TEXT);
    }

    @Override public Handler handler(String type) throws IOException {
        List<String> command = List.of("xdg-mime", "query", "default", type);
        Optional<String> output = tools.run().apply(command, TIMEOUT);
        if (output.isEmpty()) throw new IOException(String.join(" ", command) + " failed");
        String id = output.get().lines().map(String::strip).filter(line -> !line.isEmpty()).findFirst().orElse("");
        if (id.isEmpty()) return new Handler.None();
        if (id.equals(DESKTOP_ID)) return new Handler.Jasper();
        String name = desktopEntry(id).flatMap(LinuxFileAssociations::read).flatMap(LinuxMime::desktopName).orElse(id);
        return new Handler.Other(id, name);
    }

    /**
     * Declares Jasper's own types first (the package, the MIME database, and Jasper among the type's applications),
     * then sets Jasper as the default of each extension's types with one {@code xdg-mime} call. A type is done when
     * {@code xdg-mime} names Jasper afterwards.
     */
    @Override public List<TypeResult> claim(Map<String, List<String>> typesByExtension) {
        Map<String, String> failed = new java.util.HashMap<>();
        Map<String, List<String>> declared = new java.util.LinkedHashMap<>();
        typesByExtension.forEach((extension, types) -> {
            List<String> own = types.stream().filter(type -> type.equals(LinuxMime.OWN_TYPE_PREFIX + extension.substring(1))).toList();
            if (!own.isEmpty()) declared.put(extension, own);
        });
        if (!declared.isEmpty()) {
            Optional<String> failure = declare(declared);
            failure.ifPresent(detail -> declared.keySet().forEach(extension -> failed.put(extension, detail)));
        }
        List<TypeResult> results = new java.util.ArrayList<>();
        typesByExtension.forEach((extension, types) -> {
            String failure = failed.get(extension);
            if (failure == null) {
                List<String> command = new java.util.ArrayList<>(List.of("xdg-mime", "default", DESKTOP_ID));
                command.addAll(types);
                if (tools.run().apply(List.copyOf(command), TIMEOUT).isEmpty()) {
                    failure = String.join(" ", command) + " failed";
                    LOG.log(System.Logger.Level.WARNING, failure);
                }
            }
            for (String type : types) {
                results.add(new TypeResult(extension, type, failure != null ? new Outcome.Failed(failure) : named(type, true)));
            }
        });
        return List.copyOf(results);
    }

    /** Writes the package with {@code declared}'s extensions added, updates the database and adds the associations. */
    private Optional<String> declare(Map<String, List<String>> declared) {
        try {
            SortedSet<String> extensions = packageExtensions();
            declared.keySet().forEach(extension -> extensions.add(extension.substring(1)));
            writeAtomically(packageFile(), LinuxMime.packageXml(extensions));
        } catch (IOException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not write " + packageFile(), failure);
            return Optional.of("Could not write " + packageFile() + ": " + failure.getMessage());
        }
        Optional<String> updated = updateDatabase();
        if (updated.isPresent()) return updated;
        try {
            editMimeapps(text -> {
                String edited = text;
                for (List<String> types : declared.values()) {
                    for (String type : types) edited = LinuxMime.withAddedAssociation(edited, type, DESKTOP_ID);
                }
                return edited;
            }, true);
        } catch (IOException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not edit " + mimeapps(), failure);
            return Optional.of("Could not edit " + mimeapps() + ": " + failure.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Sets each saved application back with {@code xdg-mime}; for no application, removes Jasper's default from the
     * user's {@code mimeapps.list}. A type is done when {@code xdg-mime} no longer names Jasper.
     */
    @Override public List<TypeResult> restore(Map<String, Map<String, Handler>> previousByTypeByExtension) {
        List<TypeResult> results = new java.util.ArrayList<>();
        previousByTypeByExtension.forEach((extension, previousByType) -> previousByType.forEach((type, previous) ->
                results.add(new TypeResult(extension, type, restore(type, previous)))));
        return List.copyOf(results);
    }

    private Outcome restore(String type, Handler previous) {
        switch (previous) {
            case Handler.Other other -> {
                if (desktopEntry(other.id()).isEmpty()) return new Outcome.PreviousGone();
                List<String> command = List.of("xdg-mime", "default", other.id(), type);
                if (tools.run().apply(command, TIMEOUT).isEmpty()) {
                    String failure = String.join(" ", command) + " failed";
                    LOG.log(System.Logger.Level.WARNING, failure);
                    return new Outcome.Failed(failure);
                }
            }
            case Handler.None none -> {
                try {
                    editMimeapps(text -> LinuxMime.withoutDefault(text, type, DESKTOP_ID), false);
                } catch (IOException | RuntimeException failure) {
                    LOG.log(System.Logger.Level.WARNING, "Could not edit " + mimeapps(), failure);
                    return new Outcome.Failed("Could not edit " + mimeapps() + ": " + failure.getMessage());
                }
            }
            case Handler.Jasper jasper -> {
                return new Outcome.NoPrevious();
            }
        }
        return named(type, false);
    }

    /** Removes the extension from Jasper's package, deleting the package once empty, and updates the database. */
    @Override public void forget(String extension) {
        Path file = packageFile();
        SortedSet<String> extensions;
        try {
            extensions = packageExtensions();
            if (!extensions.remove(extension.substring(1))) return;
            if (extensions.isEmpty()) {
                Files.delete(file);
            } else {
                writeAtomically(file, LinuxMime.packageXml(extensions));
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not drop " + extension + " from " + file, failure);
        }
        updateDatabase().ifPresent(failure -> LOG.log(System.Logger.Level.WARNING, failure));
    }

    /** Whether {@code xdg-mime} now names Jasper ({@code jasper}) or not: {@code Done}, else what it names. */
    private Outcome named(String type, boolean jasper) {
        Handler now;
        try {
            now = handler(type);
        } catch (IOException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not read the handler of " + type, failure);
            return new Outcome.Failed(failure.getMessage());
        }
        if ((now instanceof Handler.Jasper) == jasper) return new Outcome.Done();
        String names = switch (now) {
            case Handler.Other other -> other.id();
            case Handler.Jasper j -> DESKTOP_ID;
            case Handler.None n -> "no application";
        };
        String detail = "xdg-mime query default " + type + " names " + names;
        LOG.log(System.Logger.Level.WARNING, detail);
        return new Outcome.Failed(detail);
    }

    private Optional<String> updateDatabase() {
        List<String> command = List.of("update-mime-database", dataHome.resolve("mime").toString());
        if (tools.run().apply(command, UPDATE_TIMEOUT).isPresent()) return Optional.empty();
        String failure = String.join(" ", command) + " failed";
        LOG.log(System.Logger.Level.WARNING, failure);
        return Optional.of(failure);
    }

    private Path packageFile() { return dataHome.resolve("mime/packages").resolve(PACKAGE_FILE); }

    private Path mimeapps() { return configHome.resolve("mimeapps.list"); }

    /** The extensions Jasper's package declares now; a new set when there is no package. */
    private SortedSet<String> packageExtensions() throws IOException {
        Path file = packageFile();
        if (!Files.exists(file)) return new TreeSet<>();
        return new TreeSet<>(LinuxMime.declaredExtensions(Files.readString(file, StandardCharsets.UTF_8)));
    }

    /**
     * Rewrites the user's {@code mimeapps.list} with {@code edit}, through a link to the real file, and only when the
     * text changes. A missing file is created only when {@code create}.
     */
    private void editMimeapps(java.util.function.UnaryOperator<String> edit, boolean create) throws IOException {
        Path file = mimeapps();
        boolean exists = Files.exists(file);
        if (!exists && !create) return;
        Path target = exists ? file.toRealPath() : file;
        String before = exists ? Files.readString(target, StandardCharsets.UTF_8) : "";
        String after = edit.apply(before);
        if (!after.equals(before)) writeAtomically(target, after);
    }

    /** Writes beside {@code file} and moves the copy over it, so a reader never sees half a file. */
    private static void writeAtomically(Path file, String text) throws IOException {
        Path folder = file.toAbsolutePath().getParent();
        Files.createDirectories(folder);
        Path temporary = Files.createTempFile(folder, "." + file.getFileName(), ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * The desktop entry with this desktop file id in the first data folder that has it. The id of
     * {@code applications/kde4/kate.desktop} is {@code kde4-kate.desktop}.
     */
    private Optional<Path> desktopEntry(String id) {
        if (id.isEmpty() || id.contains("/") || id.equals(".") || id.equals("..")) return Optional.empty();
        for (Path folder : dataFolders) {
            Path applications = folder.resolve("applications");
            Path direct = applications.resolve(id);
            if (Files.isRegularFile(direct)) return Optional.of(direct);
            if (id.indexOf('-') > 0 && Files.isDirectory(applications)) {
                try (Stream<Path> files = Files.walk(applications, 4)) {
                    Optional<Path> found = files.filter(Files::isRegularFile)
                            .filter(path -> applications.relativize(path).toString().replace(File.separatorChar, '-').equals(id))
                            .findFirst();
                    if (found.isPresent()) return found;
                } catch (IOException | UncheckedIOException failure) {
                    LOG.log(System.Logger.Level.DEBUG, "Could not search " + applications, failure);
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> read(Path file) {
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | UncheckedIOException failure) {
            LOG.log(System.Logger.Level.DEBUG, "Could not read " + file, failure);
            return Optional.empty();
        }
    }
}
