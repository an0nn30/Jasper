package dev.jasper.app.persistence;

import dev.jasper.app.persistence.WorkspaceFile.WindowRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * The saved layouts, {@code layouts.toml} beside {@code workspace.toml} (full windows plan 4): {@code version = 1}, an
 * optional {@code [default]} layout and a {@code [[layouts]]} array of a {@code name} and a layout, which have the window
 * keys {@code workspace.toml} has, without bounds. A layout is a window's state without its bounds: its record has no
 * bounds and extended state 0. {@link #read} never throws: it drops a bad entry alone, logging it, and sets an unreadable
 * file aside as {@code layouts.toml.bad} and reads it as empty. Stateless; the values are immutable.
 */
public final class LayoutsFile {
    /** One named layout. */
    public record Named(String name, WindowRecord layout) {
        /** Checks the values are present and keeps the layout without bounds. */
        public Named {
            Objects.requireNonNull(name, "name");
            layout = asLayout(layout);
        }
    }

    /**
     * The saved layouts.
     *
     * @param defaultLayout the layout a new window opens with, if one is saved
     * @param named the named layouts in order
     */
    public record Layouts(Optional<WindowRecord> defaultLayout, List<Named> named) {
        /** No saved layouts. */
        public static final Layouts EMPTY = new Layouts(Optional.empty(), List.of());

        /** Checks the values are present and keeps the default without bounds. */
        public Layouts {
            Objects.requireNonNull(defaultLayout, "defaultLayout");
            defaultLayout = defaultLayout.map(LayoutsFile::asLayout);
            named = List.copyOf(named);
        }
    }

    /** The version {@link #write} writes. */
    static final long VERSION = 1;
    /** The largest file read, in bytes. */
    public static final int MAX_BYTES = 256 * 1024;
    /** The longest a layout name may be. */
    public static final int MAX_NAME = 64;
    private static final System.Logger LOG = System.getLogger(LayoutsFile.class.getName());

    private LayoutsFile() { }

    /**
     * {@code window} as a layout: without bounds and with extended state 0.
     *
     * @param window a window's state
     * @return its layout
     */
    public static WindowRecord asLayout(WindowRecord window) {
        if (window.bounds().isEmpty() && window.extendedState() == 0) return window;
        return new WindowRecord(Optional.empty(), 0, window.layout(), window.tabs(), window.tools());
    }

    /**
     * Reads the layouts; never throws.
     *
     * @param file {@code layouts.toml}
     * @return the layouts; empty when the file is absent or unreadable (kept as {@code layouts.toml.bad})
     */
    public static Layouts read(Path file) {
        try {
            Optional<String> text = TomlStateFile.readBounded(file, MAX_BYTES, "Layouts");
            if (text.isEmpty()) return Layouts.EMPTY;
            return parse(text.get());
        } catch (IOException | RuntimeException | StackOverflowError unreadable) {
            // tomlj's parser has no nesting guard: deeply nested arrays overflow the stack inside Toml.parse.
            setAside(file, unreadable);
            return Layouts.EMPTY;
        }
    }

    private static Layouts parse(String text) throws IOException {
        TomlParseResult toml = Toml.parse(text);
        if (toml.hasErrors()) throw new IOException("Invalid layouts: " + toml.errors().getFirst());
        Object version = toml.get(List.of("version"));
        if (!Long.valueOf(VERSION).equals(version)) throw new IOException("Unknown layouts version: " + version);
        Optional<WindowRecord> defaultLayout = Optional.empty();
        if (toml.get(List.of("default")) instanceof TomlTable entry) defaultLayout = layout(entry, "the default layout");
        var named = new ArrayList<Named>();
        Set<String> names = new HashSet<>();
        if (toml.get(List.of("layouts")) instanceof TomlArray entries) {
            for (int i = 0; i < entries.size(); i++) {
                if (!(entries.get(i) instanceof TomlTable entry) || !(entry.get(List.of("name")) instanceof String name)
                        || name.isBlank() || name.length() > MAX_NAME || !name.equals(name.strip())) {
                    LOG.log(System.Logger.Level.WARNING, "Dropping layout entry " + (i + 1) + " from the layouts: it has no valid name");
                    continue;
                }
                if (!names.add(name)) {
                    LOG.log(System.Logger.Level.WARNING, "Dropping the layout " + name + " from the layouts: its name is used twice");
                    continue;
                }
                Optional<WindowRecord> layout = layout(entry, "the layout " + name);
                if (layout.isPresent()) named.add(new Named(name, layout.get()));
                else names.remove(name);
            }
        }
        return new Layouts(defaultLayout, named);
    }

    private static Optional<WindowRecord> layout(TomlTable entry, String what) {
        var dropped = new ArrayList<String>();
        Optional<WindowRecord> window = WorkspaceFile.window(entry, dropped);
        // A layout that lost an entry is kept without it.
        if (window.isPresent() && !dropped.isEmpty()) LOG.log(System.Logger.Level.WARNING, "Kept " + what + " without its bad entries");
        return window.map(LayoutsFile::asLayout);
    }

    private static void setAside(Path file, Throwable cause) {
        Path bad = file.resolveSibling(file.getFileName() + ".bad");
        try {
            Files.move(file, bad, StandardCopyOption.REPLACE_EXISTING);
            LOG.log(System.Logger.Level.WARNING, "Set aside the unreadable layouts " + file + " as " + bad, cause);
        } catch (IOException failed) {
            failed.addSuppressed(cause);
            LOG.log(System.Logger.Level.WARNING, "Could not set aside the unreadable layouts " + file, failed);
        }
    }

    /**
     * Writes the layouts atomically.
     *
     * @param file {@code layouts.toml}
     * @param layouts what to write
     * @throws IOException when the file cannot be written, or the layouts would exceed {@link #MAX_BYTES}
     */
    public static void write(Path file, Layouts layouts) throws IOException {
        String text = encode(layouts);
        if (!fits(text)) throw new IOException("The layouts exceed " + MAX_BYTES + " bytes");
        TomlStateFile.writeAtomically(file, ".layouts-", text);
    }

    /**
     * Whether {@link #write} would write {@code layouts}: they fit in {@link #MAX_BYTES}.
     *
     * @param layouts the layouts
     * @return false when they are too large to save
     */
    public static boolean fits(Layouts layouts) { return fits(encode(layouts)); }

    private static boolean fits(String text) { return text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= MAX_BYTES; }

    private static String encode(Layouts layouts) {
        var text = new StringBuilder("version = ").append(VERSION).append('\n');
        layouts.defaultLayout().ifPresent(layout -> text.append(WorkspaceFile.encodeWindow("\n[default]\n", "default", layout)));
        for (Named named : layouts.named())
            text.append(WorkspaceFile.encodeWindow("\n[[layouts]]\nname = " + UiState.quote(named.name()) + "\n", "layouts", named.layout()));
        return text.toString();
    }
}
