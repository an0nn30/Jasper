package dev.jasper.app.persistence;

import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.dock.ToolWindowMode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Application-owned state that belongs to no one Jasper window: where auxiliary windows were, where the command palette
 * and the Run window were dragged, and the file chooser's folders. It is not user configuration; each window's tool
 * windows live in {@code workspace.toml} ({@link ToolWindows}). Caller-thread-confined. A bad window entry or key falls
 * back alone; an unreadable file or another version (an older one included) falls back to defaults rather than blocking
 * startup, and failed writes are only logged. Saving writes version 3.
 */
public final class UiState {
    /**
     * One panel's remembered place.
     *
     * @param slot where it docks
     * @param order its position in the slot's stripe group; {@link #LAST} when it has none yet
     * @param visible whether it showed
     * @param mode whether it was docked in {@code slot} or detached into a window of its own (which keeps the slot)
     * @param window where its own window last was, in screen coordinates, whatever the mode; empty until it has had one
     */
    public record Panel(Slot slot, int order, boolean visible, ToolWindowMode mode, Optional<Bounds> window) {
        /** The order of a panel without a remembered position: it goes after the others, in registration order. */
        public static final int LAST = 9999;

        public Panel {
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(window, "window");
            if (order < 0 || order > LAST) throw new IllegalArgumentException("Panel order must be 0 to " + LAST + ": " + order);
        }

        /** A docked panel that has never had a window of its own. */
        public Panel(Slot slot, int order, boolean visible) { this(slot, order, visible, ToolWindowMode.DOCKED, Optional.empty()); }
    }

    /** The dock's remembered sizes, in unscaled pixels, and split ratios. */
    public record Dock(int leftWidth, int rightWidth, int bottomHeight, double leftRatio, double rightRatio, double bottomRatio) {
        public static final Dock DEFAULT = new Dock(DockState.DEFAULT_SIDE, DockState.DEFAULT_SIDE, DockState.DEFAULT_BOTTOM,
            DockState.DEFAULT_RATIO, DockState.DEFAULT_RATIO, DockState.DEFAULT_RATIO);

        public Dock {
            for (int size : new int[]{leftWidth, rightWidth, bottomHeight})
                if (!size(size)) throw new IllegalArgumentException("Dock size must be " + DockState.MIN_SIZE + " to " + DockState.MAX_SIZE + ": " + size);
            for (double ratio : new double[]{leftRatio, rightRatio, bottomRatio})
                if (!ratio(ratio)) throw new IllegalArgumentException("Dock ratio must be " + DockState.MIN_RATIO + " to " + DockState.MAX_RATIO + ": " + ratio);
        }

        static boolean size(long value) { return value >= DockState.MIN_SIZE && value <= DockState.MAX_SIZE; }

        static boolean ratio(double value) { return value >= DockState.MIN_RATIO && value <= DockState.MAX_RATIO; }
    }

    /** One window's remembered bounds; the position may be negative on multi-display setups. */
    public record Bounds(int x, int y, int width, int height) {
        public Bounds {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Window bounds need a positive size");
        }
    }

    /** The file chooser's remembered folders (absolute paths; recent is newest first) and its two toggles. */
    public record Chooser(List<String> recent, List<String> pinned, boolean preview, boolean showHidden) {
        public static final Chooser DEFAULT = new Chooser(List.of(), List.of(), false, true);
        public Chooser { recent = List.copyOf(recent); pinned = List.copyOf(pinned); }
    }

    /**
     * Where the command palette was dragged, as fractions of the window: its centre across and its top down.
     *
     * @param centerX the card's horizontal centre, 0 (left edge) to 1 (right edge)
     * @param top the card's top edge, 0 (top) to 1 (bottom)
     */
    public record PalettePosition(double centerX, double top) {
        public PalettePosition {
            if (!fraction(centerX) || !fraction(top)) throw new IllegalArgumentException("Palette position must be 0 to 1: " + centerX + ", " + top);
        }

        static boolean fraction(double value) { return value >= 0 && value <= 1; }
    }

    /**
     * Where the Run window was dragged (Enterprise presentation spec 11.5), as fractions of the workspace: its left edge
     * across and its bottom edge down. The card's place is {@link PalettePosition}.
     *
     * @param left the window's left edge, 0 (left) to 1 (right)
     * @param bottom the window's bottom edge, 0 (top) to 1 (bottom)
     */
    public record RunWindowPosition(double left, double bottom) {
        public RunWindowPosition {
            if (!PalettePosition.fraction(left) || !PalettePosition.fraction(bottom))
                throw new IllegalArgumentException("Run window position must be 0 to 1: " + left + ", " + bottom);
        }
    }

    private static final System.Logger LOG = System.getLogger(UiState.class.getName());
    /** The longest window key kept. */
    public static final int WINDOW_KEY_LENGTH = 255;
    /**
     * A window's key: an id, or a plugin dialog's plugin id and title ({@code dev.jasper.vault|Credential Vault}), quoted;
     * no control characters, nor half a surrogate pair, which TOML cannot hold.
     */
    private static final Pattern WINDOW_ID = Pattern.compile("[^\\p{Cntrl}\\p{Cs}]{1," + WINDOW_KEY_LENGTH + "}");
    private static final int MAX_BYTES = 256 * 1024;
    /** The version {@link #save()} writes and {@link #load} reads; any other is read as defaults. */
    private static final long VERSION = 3;

    private final Path file;
    private final Map<String, Bounds> windows = new TreeMap<>();
    private static final int MAX_CHOOSER_PATHS = 50;
    private Chooser chooser = Chooser.DEFAULT;
    private PalettePosition palettePosition;
    private RunWindowPosition runWindowPosition;

    private UiState(Path file) { this.file = file; }

    /** State that lives only for this process: tests, and windows that were never connected to a file. */
    public static UiState inMemory() { return new UiState(null); }

    /** The state in {@code file}. */
    public static UiState load(Path file) {
        UiState state = new UiState(file);
        try {
            Optional<String> text = TomlStateFile.readBounded(file, MAX_BYTES, "UI state");
            if (text.isPresent()) state.parse(text.get());
        } catch (IOException | RuntimeException invalid) {
            LOG.log(System.Logger.Level.WARNING, "Ignoring unreadable UI state " + file, invalid);
            state.windows.clear();
            state.chooser = Chooser.DEFAULT; state.palettePosition = null; state.runWindowPosition = null;
        }
        return state;
    }

    private void parse(String text) throws IOException {
        TomlParseResult toml = Toml.parse(text);
        if (toml.hasErrors()) throw new IOException("Invalid UI state format");
        Object version = toml.get(List.of("version"));
        if (!Long.valueOf(VERSION).equals(version)) throw new IOException("Unknown UI state version: " + version);
        if (toml.get(List.of("windows")) instanceof TomlTable table)
            for (String id : table.keySet())
                if (WINDOW_ID.matcher(id).matches() && table.get(List.of(id)) instanceof TomlTable entry)
                    bounds(entry).ifPresent(bounds -> windows.put(id, bounds));
        if (toml.get(List.of("palette")) instanceof TomlTable table
                && table.get(List.of("center_x")) instanceof Number x && PalettePosition.fraction(x.doubleValue())
                && table.get(List.of("top")) instanceof Number top && PalettePosition.fraction(top.doubleValue()))
            palettePosition = new PalettePosition(x.doubleValue(), top.doubleValue());
        if (toml.get(List.of("palette", "run_window")) instanceof TomlTable table
                && table.get(List.of("left")) instanceof Number left && PalettePosition.fraction(left.doubleValue())
                && table.get(List.of("bottom")) instanceof Number bottom && PalettePosition.fraction(bottom.doubleValue()))
            runWindowPosition = new RunWindowPosition(left.doubleValue(), bottom.doubleValue());
        if (toml.get(List.of("chooser")) instanceof TomlTable table)
            chooser = new Chooser(strings(table, "recent"), strings(table, "pinned"),
                Boolean.TRUE.equals(table.get(List.of("preview"))), !Boolean.FALSE.equals(table.get(List.of("show_hidden"))));
    }

    /** One window's bounds, or empty when a key is missing, not an integer or the size is not positive: it falls back alone. */
    static Optional<Bounds> bounds(TomlTable entry) {
        Optional<Integer> x = number(entry, "x"), y = number(entry, "y"), width = number(entry, "width"), height = number(entry, "height");
        if (x.isEmpty() || y.isEmpty() || width.isEmpty() || height.isEmpty() || width.get() <= 0 || height.get() <= 0) return Optional.empty();
        return Optional.of(new Bounds(x.get(), y.get(), width.get(), height.get()));
    }

    static Optional<Integer> number(TomlTable table, String key) {
        return table.get(List.of(key)) instanceof Long value && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
            ? Optional.of(value.intValue()) : Optional.empty();
    }

    private static List<String> strings(TomlTable table, String key) {
        if (!(table.get(List.of(key)) instanceof TomlArray array)) return List.of();
        return array.toList().stream().filter(String.class::isInstance).map(String.class::cast).limit(MAX_CHOOSER_PATHS).toList();
    }

    public Optional<Bounds> window(String id) { return Optional.ofNullable(windows.get(id)); }
    public void putWindow(String id, Bounds value) { if (WINDOW_ID.matcher(id).matches()) windows.put(id, value); }
    public Chooser chooser() { return chooser; }
    /** Where the command palette was dragged; empty while it keeps its default place. */
    public Optional<PalettePosition> palettePosition() { return Optional.ofNullable(palettePosition); }
    /** Null forgets the dragged place. */
    public void setPalettePosition(PalettePosition value) { palettePosition = value; }
    /** Where the Run window was dragged; empty while it keeps its home. */
    public Optional<RunWindowPosition> runWindowPosition() { return Optional.ofNullable(runWindowPosition); }
    /** Null forgets the Run window's dragged place. */
    public void setRunWindowPosition(RunWindowPosition value) { runWindowPosition = value; }
    public void setChooser(Chooser value) { chooser = Objects.requireNonNull(value); }

    /** Writes version 3. Window keys are quoted. */
    public void save() {
        if (file == null) return;
        var text = new StringBuilder("version = ").append(VERSION).append('\n');
        windows.forEach((id, bounds) -> text.append("\n[windows.").append(quote(id)).append("]\nx = ").append(bounds.x())
            .append("\ny = ").append(bounds.y()).append("\nwidth = ").append(bounds.width()).append("\nheight = ").append(bounds.height()).append('\n'));
        if (palettePosition != null)
            text.append("\n[palette]\ncenter_x = ").append(palettePosition.centerX()).append("\ntop = ").append(palettePosition.top()).append('\n');
        if (runWindowPosition != null)
            text.append("\n[palette.run_window]\nleft = ").append(runWindowPosition.left()).append("\nbottom = ").append(runWindowPosition.bottom()).append('\n');
        if (!chooser.equals(Chooser.DEFAULT))
            text.append("\n[chooser]\nrecent = ").append(array(chooser.recent())).append("\npinned = ").append(array(chooser.pinned()))
                .append("\npreview = ").append(chooser.preview()).append("\nshow_hidden = ").append(chooser.showHidden()).append('\n');
        try { TomlStateFile.writeAtomically(file, ".ui-state-", text.toString()); }
        catch (IOException failure) { LOG.log(System.Logger.Level.WARNING, "Could not save UI state to " + file, failure); }
    }

    private static String array(List<String> values) {
        return values.stream().map(UiState::quote).collect(Collectors.joining(", ", "[", "]"));
    }

    /** A TOML basic string: quotes and backslashes escaped, control characters as \\uXXXX escapes, a lone surrogate as U+FFFD. */
    static String quote(String value) {
        var out = new StringBuilder("\"");
        value.codePoints().forEach(c -> {
            if (c == '"' || c == '\\') out.append('\\').appendCodePoint(c);
            else if (c < 0x20 || c == 0x7f) out.append(String.format("\\u%04X", c));
            else if (c >= 0xD800 && c <= 0xDFFF) out.append('\uFFFD');
            else out.appendCodePoint(c);
        });
        return out.append('"').toString();
    }
}
