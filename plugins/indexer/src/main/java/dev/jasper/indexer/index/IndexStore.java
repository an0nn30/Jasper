package dev.jasper.indexer.index;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UTFDataFormatException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipException;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * The plugin's files: {@code projects.toml} lists the projects, and {@code indexes/<sha256 of key>.idx} holds each
 * snapshot as a gzip stream (magic, version, key, strategy, time, truncated flag, count, then the paths). Every write
 * replaces the file atomically. A project list that could not be loaded is renamed {@code projects.toml.bad} before
 * the next save, so it is never overwritten. Blocking: call it off the UI thread.
 */
public final class IndexStore {
    static final String PROJECTS = "projects.toml";
    static final String UNREADABLE_PROJECTS = "projects.toml.bad";
    private static final int MAGIC = 0x4A50_4958;
    private static final int VERSION = 1;
    private final Path directory;
    private volatile boolean setAside;

    /** A snapshot whose contents are not a Project Index file for the project asked for. */
    private static final class Corrupt extends IOException {
        Corrupt(String message) { super(message); }
    }

    /** @param directory the plugin's data directory */
    public IndexStore(Path directory) { this.directory = directory; }

    /**
     * @return the saved projects; empty when there is no file. Entries without a valid key are skipped
     * @throws IOException when the file cannot be read or does not parse; the next save first renames it
     */
    public List<Project> loadProjects() throws IOException {
        Path file = directory.resolve(PROJECTS);
        if (!Files.exists(file)) return List.of();
        try {
            List<Project> projects = readProjects(file);
            setAside = false;
            return projects;
        } catch (IOException | RuntimeException unreadable) {
            setAside = true;
            throw unreadable;
        }
    }

    private static List<Project> readProjects(Path file) throws IOException {
        TomlParseResult toml = Toml.parse(Files.readString(file));
        if (toml.hasErrors()) throw new IOException(file + ": " + toml.errors().get(0));
        TomlArray array = toml.getArray("project");
        if (array == null) return List.of();
        var result = new ArrayList<Project>();
        for (int i = 0; i < array.size(); i++) {
            TomlTable table = array.getTable(i);
            String id = table.getString("key");
            Optional<ProjectKey> key = id == null ? Optional.empty() : ProjectKey.parse(id);
            if (key.isEmpty()) continue;
            String name = table.getString("name");
            result.add(new Project(key.get(), name == null || name.isBlank() ? key.get().name() : name,
                Boolean.TRUE.equals(table.getBoolean("manual"))));
        }
        return result;
    }

    /**
     * @param projects every project, in display order
     * @throws IOException when the file cannot be written, or a list that did not load cannot be set aside
     */
    public void saveProjects(List<Project> projects) throws IOException {
        if (setAside) {
            try { Files.move(directory.resolve(PROJECTS), directory.resolve(UNREADABLE_PROJECTS), StandardCopyOption.REPLACE_EXISTING); }
            catch (NoSuchFileException gone) { /* nothing left to keep */ }
            setAside = false;
        }
        var out = new StringBuilder("# Projects indexed by Project Index. Jasper rewrites this file; edit the lists in Settings.\n");
        for (Project project : projects) {
            out.append("\n[[project]]\n");
            out.append("key = ").append(tomlString(project.key().id())).append('\n');
            out.append("name = ").append(tomlString(project.name())).append('\n');
            out.append("manual = ").append(project.manual()).append('\n');
        }
        write(directory.resolve(PROJECTS), out.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param key a project
     * @return where its snapshot is saved
     */
    public Path indexFile(ProjectKey key) { return directory.resolve("indexes").resolve(Digests.sha256Hex(key.id()) + ".idx"); }

    /**
     * @param key a project
     * @return its saved snapshot; empty when there is none, or when it could not be read (the file stays), or when it is
     *     corrupt or names another project (the file is deleted)
     */
    public Optional<ProjectIndex> loadIndex(ProjectKey key) {
        Path file = indexFile(key);
        if (!Files.exists(file)) return Optional.empty();
        try (InputStream raw = Files.newInputStream(file); var in = new DataInputStream(new GZIPInputStream(raw))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) throw new Corrupt("Not a Project Index file");
            if (!in.readUTF().equals(key.id())) throw new Corrupt("The file belongs to another project");
            Strategy strategy = Strategy.valueOf(in.readUTF());
            Instant at = Instant.ofEpochMilli(in.readLong());
            boolean truncated = in.readBoolean();
            int count = in.readInt();
            if (count < 0) throw new Corrupt("Negative file count");
            var files = new ArrayList<String>(Math.min(count, 1_000_000));
            for (int i = 0; i < count; i++) files.add(in.readUTF());
            return Optional.of(new ProjectIndex(key, files, strategy, at, truncated));
        } catch (Corrupt | EOFException | UTFDataFormatException | ZipException | RuntimeException corrupt) {
            try { Files.deleteIfExists(file); } catch (IOException ignored) { /* the next save replaces it */ }
            return Optional.empty();
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * @param index a snapshot
     * @throws IOException when it cannot be written
     */
    public void saveIndex(ProjectIndex index) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeUTF(index.key().id());
            out.writeUTF(index.strategy().name());
            out.writeLong(index.indexedAt().toEpochMilli());
            out.writeBoolean(index.truncated());
            out.writeInt(index.size());
            for (int i = 0; i < index.size(); i++) out.writeUTF(index.file(i));
        }
        write(indexFile(index.key()), bytes.toByteArray());
    }

    /**
     * @param key a project
     * @throws IOException when its snapshot cannot be deleted
     */
    public void deleteIndex(ProjectKey key) throws IOException { Files.deleteIfExists(indexFile(key)); }

    private static void write(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            Files.write(temp, bytes);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** A TOML basic string: backslash, quote and control characters escaped. */
    static String tomlString(String text) {
        var out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x7f) out.append(String.format(Locale.ROOT, "\\u%04X", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
