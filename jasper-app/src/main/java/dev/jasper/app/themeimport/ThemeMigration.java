package dev.jasper.app.themeimport;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeCatalog;
import dev.jasper.app.appearance.ThemeDocument;
import dev.jasper.app.appearance.ThemeResolver;
import dev.jasper.app.appearance.ThemeToml;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Resumable, data-only legacy upgrade. Config publication is a separate step before {@link #complete}. */
public final class ThemeMigration {
    public record MigrationReport(String generation, boolean completed, Map<String, String> chromeRenames,
                                  Map<String, String> workspaceRenames, List<Path> outputs, List<Path> originals,
                                  List<ThemeCatalog.Problem> problems) {
        public MigrationReport {
            chromeRenames = Map.copyOf(chromeRenames); workspaceRenames = Map.copyOf(workspaceRenames);
            outputs = List.copyOf(outputs); originals = List.copyOf(originals); problems = List.copyOf(problems);
        }
    }
    public record ConfigIntent(String configFileName, String expectedSha256, String desiredSha256,
                               Map<String, String> before, Map<String, String> after) {
        public ConfigIntent { before = Map.copyOf(before); after = Map.copyOf(after); }
    }
    public java.util.Optional<ConfigIntent> configIntent() {
        try { return files.locked(() -> { var state = journal().read(); return state == null ? java.util.Optional.empty() : java.util.Optional.ofNullable(state.configIntent()); }); }
        catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
    public void recordConfigIntent(String generation, ConfigIntent intent) throws IOException {
        files.locked(() -> {
            MigrationJournal.validateIntent(intent);
            var journal = journal(); var state = journal.read();
            if (state == null || !state.generation().equals(generation) || Files.exists(journal.resolve("themes/legacy/.migrated"), LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Migration is no longer pending");
            var previous = state.configIntent();
            if (previous != null && (!previous.configFileName().equals(intent.configFileName()) || !previous.before().equals(intent.before()) || !previous.after().equals(intent.after())))
                throw new IOException("Pending configuration intent conflicts with this edit");
            journal.write(new MigrationJournal.State(state.generation(), state.sources(), state.workspaceAliases(), intent, state.nativeEdits()));
            return null;
        });
    }
    private record Input(String relative, String hash, String key, ThemeConverter.Batch batch) {}
    private record Candidate(ThemeDocument document, String source, String provider, boolean nativeFile) {}
    private record Owner(String source, String provider) {}
    private record Planned(MigrationJournal.State state, Map<String, Map<String, byte[]>> bytes) {}
    private final Path home;
    private final ThemeFiles files;
    private final Consumer<String> fault;

    public ThemeMigration(Path home) { this(home, stage -> {}); }
    ThemeMigration(Path home, Consumer<String> fault) {
        this.home = home.toAbsolutePath().normalize(); this.files = new ThemeFiles(home); this.fault = fault;
    }

    public MigrationReport run() {
        try { return files.locked(this::runLocked); }
        catch (IOException failure) { return failed(failure); }
    }
    public void complete(MigrationReport report) throws IOException {
        files.locked(() -> {
            var journal = journal(); var state = journal.read();
            if (state == null || !state.generation().equals(report.generation())) throw new IOException("Migration generation changed before completion");
            Path marker = journal.resolve("themes/legacy/.migrated");
            if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) { validateMarker(marker, state); return null; }
            fault.accept("before-complete");
            // Re-read after the hook so an observed external journal edit cannot be marked complete.
            var current = new MigrationJournal(home).read();
            if (!state.equals(current)) throw new IOException("Migration journal changed before completion");
            MigrationJournal.atomic(marker, ("version = 1\ngeneration = " + MigrationJournal.quoted(state.generation()) + "\n").getBytes(StandardCharsets.UTF_8), null);
            fault.accept("complete");
            cleanupStages(journal, state);
            return null;
        });
    }

    /** Refreshes persisted warnings only; never discovers or converts new inputs. */
    public List<ThemeCatalog.Problem> problems() {
        try {
            return files.locked(() -> {
                Path path = home.resolve("themes/legacy/migration.toml");
                if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return List.of();
                var journal = journal(); var state = journal.read();
                Path marker = journal.resolve("themes/legacy/.migrated");
                if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) { validateMarker(marker, state); state = refresh(journal, state); }
                return report(journal, state, true).problems();
            });
        } catch (IOException failure) { return failed(failure).problems(); }
    }

    private MigrationReport runLocked() throws IOException {
        var journal = journal(); var state = journal.read();
        Path marker = journal.resolve("themes/legacy/.migrated");
        if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            if (state == null) throw new IOException("Migration marker has no valid journal");
            validateMarker(marker, state); state = refresh(journal, state); cleanupStages(journal, state);
            return report(journal, state, true);
        }
        Map<String, Map<String, byte[]>> plannedBytes = Map.of();
        if (state == null) {
            Planned planned = plan(journal); state = planned.state(); plannedBytes = planned.bytes();
            journal.write(state); fault.accept("intent");
        }
        var context = new Context(journal, state);
        // Every successful conversion is staged before any source moves, including files used as local parents.
        for (int i = 0; i < context.state.sources().size(); i++) stage(context, i, plannedBytes);
        for (int i = 0; i < context.state.sources().size(); i++) publish(context, i);
        for (int i = 0; i < context.state.nativeEdits().size(); i++) applyNativeEdit(context, i);
        Path terminal = home.resolve("terminal-themes");
        if (Files.isDirectory(terminal, LinkOption.NOFOLLOW_LINKS)) {
            try (var children = Files.list(terminal)) { if (children.findAny().isEmpty()) Files.delete(terminal); }
        }
        return report(journal, context.state, false);
    }

    private MigrationJournal journal() throws IOException {
        Path themes = files.directory(); Path legacy = themes.resolve("legacy");
        if (!Files.exists(legacy, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(legacy);
        if (!Files.isDirectory(legacy, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsafe legacy migration directory");
        return new MigrationJournal(home);
    }

    private Planned plan(MigrationJournal journal) throws IOException {
        var inputs = new ArrayList<Input>(); var candidates = new ArrayList<Candidate>();
        var nativeNames = new HashSet<String>();
        var nativeBytes = new HashMap<String, byte[]>();
        for (String folder : List.of("themes", "terminal-themes")) for (Path file : regularFiles(folder)) {
            String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            String relative = folder + "/" + file.getFileName();
            if (folder.equals("themes") && name.endsWith(".toml")) {
                try {
                    byte[] content = MigrationJournal.readBounded(file, ThemeCatalog.MAX_THEME_FILE);
                    var document = ThemeToml.parse(new String(content, StandardCharsets.UTF_8), relative);
                    nativeBytes.put(relative, content);
                    candidates.add(new Candidate(document, relative, "", true));
                } catch (IOException | RuntimeException ignored) { /* Existing broken native files remain untouched. */ }
                continue;
            }
            boolean archive = name.endsWith(".jar") || name.endsWith(".zip");
            if (!(folder.equals("themes") ? archive || name.endsWith(".theme.json") : name.endsWith(".toml"))) continue;
            String digest; String identity; ThemeConverter.Batch batch;
            try { digest = sourceHash(file); identity = fileKey(file); batch = ThemeConverter.convertAll(file); }
            catch (IOException failure) {
                digest = "0".repeat(64); identity = "unreadable";
                batch = new ThemeConverter.Batch(List.of(), List.of(new ThemeConverter.Failure(relative, failure.getMessage())));
            }
            inputs.add(new Input(relative, digest, identity, batch));
            if (folder.equals("themes") && archive)
                batch.results().forEach(result -> candidates.add(new Candidate(result.document(), relative, result.sourceEntry(), false)));
        }
        // Restore native/archive filename order, then the retained original built-ins.
        candidates.sort(Comparator.comparing(Candidate::source, String.CASE_INSENSITIVE_ORDER).thenComparing(Candidate::source));
        List<Theme> classic = List.of(Theme.JASPER_LIGHT, Theme.DARK);
        var documents = new ArrayList<>(candidates.stream().map(Candidate::document).toList());
        classic.forEach(theme -> documents.add(theme.source().document()));
        var chrome = new LinkedHashMap<String, Owner>();
        for (Candidate candidate : candidates) {
            try {
                ThemeResolver.resolve(candidate.document(), name -> documents.stream().filter(doc -> key(doc.name()).equals(key(name))).toList());
                String normalized = key(candidate.document().name());
                chrome.putIfAbsent(normalized, new Owner(candidate.source(), candidate.provider()));
                if (candidate.nativeFile()) nativeNames.add(normalized);
            } catch (RuntimeException ignored) { /* Same invalid-parent behavior as the old catalog. */ }
        }
        classic.forEach(theme -> chrome.putIfAbsent(key(theme.name()), new Owner("builtin", theme.id())));
        var workspace = new LinkedHashMap<>(chrome);
        for (Input input : inputs) if (input.relative().startsWith("terminal-themes/") && Files.size(journal.resolve(input.relative())) <= 64 * 1024)
            input.batch().results().forEach(result -> workspace.putIfAbsent(key(result.document().name()), new Owner(input.relative(), result.sourceEntry())));
        var aliases = new LinkedHashMap<String, String>();
        for (var entry : legacyNames().entrySet()) if (!workspace.containsKey(entry.getKey()) && !entry.getKey().equals(key(entry.getValue())))
            aliases.put(entry.getKey(), entry.getValue());
        var names = new HashSet<>(nativeNames); var outputFiles = occupied(files.directory());
        var legacyFiles = occupied(journal.file().getParent());
        var sources = new ArrayList<MigrationJournal.Source>(); var staged = new LinkedHashMap<String, Map<String, byte[]>>();
        String generation = UUID.randomUUID().toString();
        for (Input input : inputs) {
            var outputs = new ArrayList<MigrationJournal.Output>(); var bytes = new LinkedHashMap<String, byte[]>();
            var problems = new ArrayList<String>();
            input.batch().failures().forEach(failure -> problems.add(failure.sourceEntry() + ": " + failure.message()));
            boolean complete = input.batch().failures().isEmpty() && !input.batch().results().isEmpty();
            for (ConversionResult result : input.batch().results()) {
                String originalName = result.document().name(), name = originalName;
                boolean archive = input.relative().endsWith(".jar") || input.relative().endsWith(".zip")
                    || input.relative().toLowerCase(java.util.Locale.ROOT).endsWith(".jar") || input.relative().toLowerCase(java.util.Locale.ROOT).endsWith(".zip");
                for (int suffix = 2; names.contains(key(name)) || (!archive && chrome.containsKey(key(name))); suffix++) name = originalName + " " + suffix;
                var document = ThemeImporter.rename(result.document(), name);
                byte[] content;
                try { content = ThemeFiles.serialized(document); }
                catch (IOException | RuntimeException failure) {
                    complete = false; problems.add(result.sourceEntry() + ": " + failure.getMessage()); continue;
                }
                String filename = reserve(ThemeFiles.fileId(name), ".toml", outputFiles);
                String normalized = key(originalName);
                Owner owner = new Owner(input.relative(), result.sourceEntry());
                outputs.add(new MigrationJournal.Output(result.sourceEntry(), name, "themes/" + filename, ThemeSources.hash(content), false,
                    owner.equals(chrome.get(normalized)) ? normalized : "", owner.equals(workspace.get(normalized)) ? normalized : ""));
                bytes.put(result.sourceEntry(), content); names.add(key(name)); problems.addAll(result.warnings());
            }
            String leaf = Path.of(input.relative()).getFileName().toString(); String extension = extension(leaf);
            String stem = ThemeFiles.fileId(input.relative().substring(0, input.relative().indexOf('/')) + "-" + leaf.substring(0, leaf.length() - extension.length()));
            String destination = "themes/legacy/" + reserve(stem, extension, legacyFiles);
            sources.add(new MigrationJournal.Source(input.relative(), input.hash(), input.key(), destination, complete, false, false, outputs, problems));
            staged.put(input.relative(), bytes);
        }
        var edits = new ArrayList<MigrationJournal.NativeEdit>();
        for (Candidate candidate : candidates) {
            if (!candidate.nativeFile() || candidate.document().parent().isEmpty()) continue;
            try { ThemeResolver.resolve(candidate.document(), name -> named(documents, name)); }
            catch (RuntimeException invalid) { continue; }
            String parentName = candidate.document().parent().orElseThrow();
            var layers = named(documents, parentName);
            int first = key(candidate.document().name()).equals(key(parentName)) ? layers.indexOf(candidate.document()) + 1 : 0;
            ThemeDocument parent = null;
            for (int i = first; i < layers.size(); i++) {
                try { ThemeResolver.resolve(layers.get(i), name -> named(documents, name)); parent = layers.get(i); break; }
                catch (RuntimeException invalid) { /* Same first-resolvable-layer rule as the catalog. */ }
            }
            ThemeDocument selected = parent;
            var owner = candidates.stream().filter(value -> !value.nativeFile() && value.document() == selected).findFirst();
            if (owner.isEmpty()) continue;
            var output = sources.stream().filter(value -> value.path().equals(owner.get().source()))
                .flatMap(value -> value.outputs().stream()).filter(value -> value.provider().equals(owner.get().provider())).findFirst();
            if (output.isEmpty() || key(output.get().name()).equals(key(parentName))) continue;
            byte[] original = nativeBytes.get(candidate.source());
            String edited = dev.jasper.app.settings.TomlDocument.parse(new String(original, StandardCharsets.UTF_8))
                .set(List.of("extends"), MigrationJournal.quoted(output.get().name())).text();
            byte[] content = edited.getBytes(StandardCharsets.UTF_8);
            if (content.length > ThemeCatalog.MAX_THEME_FILE) throw new IOException("Native parent edit exceeds theme size limit");
            String backup = "themes/legacy/" + reserve("native-" + ThemeFiles.fileId(candidate.source()), ".toml", legacyFiles);
            edits.add(new MigrationJournal.NativeEdit(candidate.source(), ThemeSources.hash(original), fileKey(journal.resolve(candidate.source())),
                ThemeSources.hash(content), edited, backup, output.get().path(), output.get().hash(), false, ""));
        }
        return new Planned(new MigrationJournal.State(generation, sources, aliases, null, edits), staged);
    }

    private static List<ThemeDocument> named(List<ThemeDocument> documents, String name) {
        return documents.stream().filter(document -> key(document.name()).equals(key(name))).toList();
    }

    private void applyNativeEdit(Context context, int index) throws IOException {
        var edit = context.state.nativeEdits().get(index);
        if (edit.applied() || !edit.problem().isEmpty()) return;
        try {
            Path target = context.journal.resolve(edit.path()), backup = context.journal.resolve(edit.backup());
            String current = ThemeSources.hash(MigrationJournal.readBounded(target, ThemeCatalog.MAX_THEME_FILE));
            if (current.equals(edit.desiredHash())) {
                // A crash may occur after replacement but before recording progress.
                if (!edit.hash().equals(ThemeSources.hash(MigrationJournal.readBounded(backup, ThemeCatalog.MAX_THEME_FILE)))
                        || !edit.fileKey().equals(fileKey(backup))) throw new IOException("Native theme backup changed; original edit not acknowledged");
            } else {
                if (!current.equals(edit.hash()) || !edit.fileKey().equals(fileKey(target)))
                    throw new IOException("Native theme changed after migration planning; parent reference retained; use Import to repair");
                Path parent = context.journal.resolve(edit.parentPath());
                if (!edit.parentHash().equals(ThemeSources.hash(MigrationJournal.readBounded(parent, ThemeCatalog.MAX_THEME_FILE))))
                    throw new IOException("Migrated native parent is unavailable or changed; parent reference retained");
                if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
                    if (!edit.fileKey().equals(fileKey(backup)) || !edit.hash().equals(ThemeSources.hash(MigrationJournal.readBounded(backup, ThemeCatalog.MAX_THEME_FILE))))
                        throw new IOException("Native backup destination is occupied; original retained");
                } else Files.createLink(backup, target);
                fault.accept("native-backup");
                MigrationJournal.atomic(target, edit.text().getBytes(StandardCharsets.UTF_8), edit.hash());
                fault.accept("native-edit");
            }
            context.state = context.state.replacingEdit(index, edit.progress(true, ""));
        } catch (IOException failure) {
            context.state = context.state.replacingEdit(index, edit.progress(false, failure.getMessage()));
        }
        context.journal.write(context.state);
    }

    private void stage(Context context, int index, Map<String, Map<String, byte[]>> plannedBytes) throws IOException {
        var source = context.state.sources().get(index);
        if (source.moved() || source.blocked() || !ready(context, index)) return;
        source = context.state.sources().get(index);
        try {
            Map<String, byte[]> bytes = plannedBytes.get(source.path());
            for (var output : source.outputs()) {
                Path stage = stagePath(context.journal, context.state, output);
                if (Files.exists(stage, LinkOption.NOFOLLOW_LINKS)) {
                    if (!ThemeSources.hash(MigrationJournal.readBounded(stage, ThemeCatalog.MAX_THEME_FILE)).equals(output.hash())) throw new IOException("Staged output changed; use Import to retry");
                    continue;
                }
                Path existing = context.journal.resolve(output.path());
                byte[] data;
                if (Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) data = MigrationJournal.readBounded(existing, ThemeCatalog.MAX_THEME_FILE);
                else {
                    if (output.published()) throw new IOException("Published output is missing; use Import to retry");
                    if (bytes == null) {
                        bytes = new HashMap<>();
                        var batch = ThemeConverter.convertAll(context.journal.resolve(source.path()));
                        for (var result : batch.results()) {
                            var planned = source.outputs().stream().filter(value -> value.provider().equals(result.sourceEntry())).findFirst();
                            if (planned.isPresent()) bytes.put(result.sourceEntry(), ThemeFiles.serialized(ThemeImporter.rename(result.document(), planned.get().name())));
                        }
                    }
                    data = bytes.get(output.provider());
                }
                if (data == null || !ThemeSources.hash(data).equals(output.hash())) throw new IOException("Planned output changed; use Import to retry");
                MigrationJournal.atomic(stage, data, null);
            }
        } catch (IOException failure) { context.block(index, failure.getMessage()); }
    }

    private void publish(Context context, int index) throws IOException {
        var source = context.state.sources().get(index);
        if (source.moved() || source.blocked() || !ready(context, index)) return;
        try {
            for (int j = 0; j < source.outputs().size(); j++) {
                source = context.state.sources().get(index);
                var output = source.outputs().get(j); Path target = context.journal.resolve(output.path());
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    if (!ThemeSources.hash(MigrationJournal.readBounded(target, ThemeCatalog.MAX_THEME_FILE)).equals(output.hash())) throw new IOException("Published output changed; use Import to retry");
                } else {
                    if (output.published()) throw new IOException("Published output is missing; use Import to retry");
                    byte[] data = MigrationJournal.readBounded(stagePath(context.journal, context.state, output), ThemeCatalog.MAX_THEME_FILE);
                    if (!ThemeSources.hash(data).equals(output.hash())) throw new IOException("Staged output changed; use Import to retry");
                    files.createAt(target, ThemeToml.parse(new String(data, StandardCharsets.UTF_8), output.path()));
                    fault.accept("output");
                }
                if (!output.published()) {
                    var outputs = new ArrayList<>(source.outputs()); outputs.set(j, output.publishedOutput());
                    context.set(index, source.progress(outputs, false, false, source.problems()));
                }
            }
            source = context.state.sources().get(index);
            if (!source.convertible() || !ready(context, index)) return;
            Path input = context.journal.resolve(source.path()), destination = context.journal.resolve(source.legacy());
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                // Equal bytes alone cannot prove this is our original. The recorded file identity must match too.
                if (!source.fileKey().equals(fileKey(destination)) || !source.hash().equals(sourceHash(destination))) throw new IOException("Legacy destination is occupied; original retained");
            } else Files.createLink(destination, input);
            if (!source.fileKey().equals(fileKey(input)) || !source.hash().equals(sourceHash(input))) throw new IOException("Legacy source changed before relocation");
            Files.delete(input); fault.accept("original");
            context.set(index, source.progress(source.outputs(), true, false, source.problems()));
        } catch (IOException failure) { context.block(index, failure.getMessage()); }
    }

    private boolean ready(Context context, int index) throws IOException {
        var source = context.state.sources().get(index);
        try {
            Path input = context.journal.resolve(source.path());
            if (!Files.exists(input, LinkOption.NOFOLLOW_LINKS)) {
                Path saved = context.journal.resolve(source.legacy());
                if (source.convertible() && source.outputs().stream().allMatch(MigrationJournal.Output::published)
                        && Files.isRegularFile(saved, LinkOption.NOFOLLOW_LINKS)
                        && source.fileKey().equals(fileKey(saved)) && source.hash().equals(sourceHash(saved))) {
                    context.set(index, source.progress(source.outputs(), true, false, source.problems())); return false;
                }
                throw new IOException("Legacy source is missing; use Import to retry");
            }
            if (!source.hash().equals(sourceHash(input)) || !source.fileKey().equals(fileKey(input))) throw new IOException("Legacy source changed; use Import to retry");
            return true;
        } catch (IOException failure) { context.block(index, failure.getMessage()); return false; }
    }

    private MigrationJournal.State refresh(MigrationJournal journal, MigrationJournal.State state) throws IOException {
        var next = state;
        for (int i = 0; i < state.sources().size(); i++) {
            var source = state.sources().get(i);
            if (source.moved()) continue;
            var problems = new ArrayList<>(source.problems());
            Path input = journal.resolve(source.path());
            if (!Files.exists(input, LinkOption.NOFOLLOW_LINKS)) problems.clear();
            else {
                try { if (!source.hash().equals(sourceHash(input))) add(problems, "Legacy input changed after migration; use Import to retry."); }
                catch (IOException failure) { add(problems, failure.getMessage()); }
            }
            if (!problems.equals(source.problems())) next = next.replacing(i, source.progress(source.outputs(), false, source.blocked(), problems));
        }
        if (!next.equals(state)) journal.write(next);
        return next;
    }

    private MigrationReport report(MigrationJournal journal, MigrationJournal.State state, boolean completed) throws IOException {
        var chrome = new LinkedHashMap<String, String>(); var workspace = new LinkedHashMap<String, String>();
        if (!completed) workspace.putAll(state.workspaceAliases());
        var outputs = new ArrayList<Path>(); var originals = new ArrayList<Path>(); var problems = new ArrayList<ThemeCatalog.Problem>();
        for (var source : state.sources()) {
            Path location = home.resolve(source.moved() ? source.legacy() : source.path());
            for (String message : source.problems()) problems.add(new ThemeCatalog.Problem(location, message));
            if (completed) continue;
            if (source.moved()) originals.add(journal.resolve(source.legacy()));
            for (var output : source.outputs()) if (output.published()) {
                Path path = journal.resolve(output.path());
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !ThemeSources.hash(MigrationJournal.readBounded(path, ThemeCatalog.MAX_THEME_FILE)).equals(output.hash())) continue;
                outputs.add(path);
                if (!output.chromeKey().isEmpty() && !output.chromeKey().equals(key(output.name()))) chrome.put(output.chromeKey(), output.name());
                if (!output.workspaceKey().isEmpty() && !output.workspaceKey().equals(key(output.name()))) workspace.put(output.workspaceKey(), output.name());
            }
        }
        for (var edit : state.nativeEdits()) {
            if (!edit.problem().isEmpty()) problems.add(new ThemeCatalog.Problem(home.resolve(edit.path()), edit.problem()));
            if (!completed && edit.applied()) originals.add(journal.resolve(edit.backup()));
        }
        return new MigrationReport(state.generation(), completed, chrome, workspace, outputs, originals, problems);
    }

    private List<Path> regularFiles(String folder) throws IOException {
        Path directory = home.resolve(folder);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return List.of();
        try (var files = Files.list(directory)) {
            return files.filter(path -> !path.getFileName().toString().startsWith(".") && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .sorted(Comparator.comparing((Path path) -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER).thenComparing(path -> path.getFileName().toString())).toList();
        }
    }
    private static String sourceHash(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Not a regular legacy source");
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        long limit = name.endsWith(".jar") || name.endsWith(".zip") ? ThemeArchive.MAX_ARCHIVE : ThemeArchive.MAX_FILE;
        String oversized = path.getFileName() + " is larger than " + limit + " bytes; use Import after reducing its size";
        if (Files.size(path) > limit) throw new IOException(oversized);
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192]; int count;
            long read = 0;
            while ((count = input.read(buffer, 0, (int) Math.min(buffer.length, limit - read + 1))) != -1) {
                read += count;
                if (read > limit) throw new IOException(oversized);
                digest.update(buffer, 0, count);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static String fileKey(Path path) throws IOException {
        var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.fileKey() == null) throw new IOException("Legacy input has no stable regular-file identity");
        return attributes.fileKey().toString();
    }
    private static String reserve(String stem, String extension, HashSet<String> occupied) {
        String name = stem + extension;
        for (int suffix = 2; occupied.contains(key(name)); suffix++) name = stem + "-" + suffix + extension;
        occupied.add(key(name)); return name;
    }
    private static HashSet<String> occupied(Path directory) throws IOException {
        var names = new HashSet<String>(); try (var files = Files.list(directory)) { files.forEach(path -> names.add(key(path.getFileName().toString()))); } return names;
    }
    private static String extension(String file) {
        String lower = file.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".theme.json") ? ".theme.json" : lower.substring(lower.lastIndexOf('.'));
    }
    private static Path stagePath(MigrationJournal journal, MigrationJournal.State state, MigrationJournal.Output output) throws IOException {
        return journal.resolve("themes/legacy/.stage-" + state.generation() + "-" + Path.of(output.path()).getFileName());
    }
    private static void cleanupStages(MigrationJournal journal, MigrationJournal.State state) {
        for (var source : state.sources()) for (var output : source.outputs()) {
            try {
                Path stage = stagePath(journal, state, output);
                if (Files.isRegularFile(stage, LinkOption.NOFOLLOW_LINKS)
                        && ThemeSources.hash(MigrationJournal.readBounded(stage, ThemeCatalog.MAX_THEME_FILE)).equals(output.hash())) Files.delete(stage);
            } catch (IOException failure) {
                System.getLogger(ThemeMigration.class.getName()).log(System.Logger.Level.WARNING, "Could not clean migration stage", failure);
            }
        }
    }
    private static void validateMarker(Path path, MigrationJournal.State state) throws IOException {
        if (state == null) throw new IOException("Marker has no migration journal");
        try {
            var parsed = org.tomlj.Toml.parse(new String(MigrationJournal.readBounded(path, 4096), StandardCharsets.UTF_8));
            if (parsed.hasErrors() || !Long.valueOf(1).equals(parsed.getLong("version")) || !state.generation().equals(parsed.getString("generation")))
                throw new IOException("Invalid migration completion marker");
        } catch (RuntimeException invalid) { throw new IOException("Invalid migration completion marker", invalid); }
    }
    private static Map<String, String> legacyNames() throws IOException {
        try (var input = ThemeMigration.class.getResourceAsStream("/dev/jasper/app/themes/legacy-aliases.json")) {
            if (input == null) throw new IOException("Missing bundled legacy theme aliases");
            Object parsed = com.formdev.flatlaf.json.Json.parse(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?> map)) throw new IOException("Invalid bundled aliases");
            var values = new LinkedHashMap<String, String>();
            for (var entry : map.entrySet()) values.put((String) entry.getKey(), (String) entry.getValue());
            return values;
        }
    }
    private MigrationReport failed(IOException failure) {
        return new MigrationReport("", false, Map.of(), Map.of(), List.of(), List.of(),
            List.of(new ThemeCatalog.Problem(home.resolve("themes/legacy/migration.toml"), failure.getMessage())));
    }
    private static String key(String name) { return ThemeImporter.key(name); }
    private static void add(List<String> messages, String message) { if (!messages.contains(message)) messages.add(message); }
    private static final class Context {
        final MigrationJournal journal; MigrationJournal.State state;
        Context(MigrationJournal journal, MigrationJournal.State state) { this.journal = journal; this.state = state; }
        void set(int index, MigrationJournal.Source source) throws IOException { state = state.replacing(index, source); journal.write(state); }
        void block(int index, String message) throws IOException {
            var source = state.sources().get(index); var problems = new ArrayList<>(source.problems()); add(problems, message);
            set(index, source.progress(source.outputs(), source.moved(), true, problems));
        }
    }
}
