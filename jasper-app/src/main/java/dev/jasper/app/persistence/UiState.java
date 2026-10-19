package dev.jasper.app.persistence;

import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Application-owned layout state: where each panel docks and the dock's sizes (the default look's, and each other look's
 * under {@code [look."<id>"]}), Zen mode, whether the stripes show, where main and auxiliary windows were, and where the
 * command palette was dragged. It is not user configuration. Caller-thread-confined. A bad panel or window entry or dock
 * key falls back alone; an unreadable file or an unknown version falls back to defaults rather than blocking
 * startup, and failed writes are only logged. Version 1 files (a region and a size per panel) are migrated on read;
 * saving writes version 2.
 */
public final class UiState {
    /**
     * One panel's remembered place.
     *
     * @param slot where it docks
     * @param order its position in the slot's stripe group; {@link #LAST} when it has none yet
     * @param visible whether it showed
     */
    public record Panel(Slot slot, int order, boolean visible) {
        /** The order of a panel without a remembered position: it goes after the others, in registration order. */
        public static final int LAST = 9999;

        public Panel {
            Objects.requireNonNull(slot, "slot");
            if (order < 0 || order > LAST) throw new IllegalArgumentException("Panel order must be 0 to " + LAST + ": " + order);
        }
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

    /** Main window's normal bounds and maximized axes (AWT bits 2 and 4); never restore minimization. */
    public record MainWindow(Bounds bounds, int extendedState) {
        public MainWindow {
            Objects.requireNonNull(bounds);
            if ((extendedState & ~6) != 0) throw new IllegalArgumentException("Invalid maximized state");
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
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_.-]{0,127}");
    private static final int MAX_BYTES = 256 * 1024;
    /** One look's dock: where each panel docks, and the dock's sizes and ratios. */
    private static final class Layout {
        final Map<String, Panel> panels = new TreeMap<>();
        Dock dock = Dock.DEFAULT;
    }

    private final Path file;
    /** The look whose dock this state reads and writes: empty for the default look (spec 10.7). */
    private final String look;
    /** Each look's dock by its key, the default look's under the empty key. */
    private final Map<String, Layout> layouts = new TreeMap<>();
    private final Map<String, Bounds> windows = new TreeMap<>();
    private boolean railVisible = true;
    private boolean zenMode;
    private MainWindow mainWindow;
    private static final int MAX_CHOOSER_PATHS = 50;
    private Chooser chooser = Chooser.DEFAULT;
    private PalettePosition palettePosition;
    private RunWindowPosition runWindowPosition;

    private UiState(Path file, String look) {
        this.file = file;
        this.look = Objects.requireNonNull(look, "look");
        if (!look.isEmpty() && !ID.matcher(look).matches()) throw new IllegalArgumentException("Not a look key: " + look);
        layout("");
    }

    /** State that lives only for this process: tests, and windows that were never connected to a file. */
    public static UiState inMemory() { return inMemory(""); }

    /** The same under the look {@code look}: empty for the default look, else the providing plugin's id. */
    public static UiState inMemory(String look) { return new UiState(null, look); }

    /** The default look's state in {@code file}. */
    public static UiState load(Path file) { return load(file, ""); }

    /**
     * The state in {@code file} under the look {@code look}: empty for the default look, whose dock is the file's
     * {@code [dock]} and {@code [panels]}, else the providing plugin's id, whose dock is {@code [look."<id>"]}'s.
     */
    public static UiState load(Path file, String look) {
        UiState state = new UiState(file, look);
        try {
            Optional<String> text = TomlStateFile.readBounded(file, MAX_BYTES, "UI state");
            if (text.isPresent()) state.parse(text.get());
        } catch (IOException | RuntimeException invalid) {
            LOG.log(System.Logger.Level.WARNING, "Ignoring unreadable UI state " + file, invalid);
            state.layouts.clear(); state.layout(""); state.windows.clear(); state.railVisible = true;
            state.chooser = Chooser.DEFAULT; state.mainWindow = null; state.zenMode = false; state.palettePosition = null; state.runWindowPosition = null;
        }
        return state;
    }

    private void parse(String text) throws IOException {
        TomlParseResult toml = Toml.parse(text);
        if (toml.hasErrors()) throw new IOException("Invalid UI state format");
        Object version = toml.get(List.of("version"));
        if (Long.valueOf(1).equals(version)) migrate(toml);
        else if (Long.valueOf(2).equals(version)) readLayouts(toml);
        else throw new IOException("Unknown UI state version: " + version);
        if (toml.get(List.of("rail_visible")) instanceof Boolean visible) railVisible = visible;
        zenMode = Boolean.TRUE.equals(toml.get(List.of("zen_mode")));
        if (toml.get(List.of("windows")) instanceof TomlTable table)
            for (String id : table.keySet())
                if (ID.matcher(id).matches() && table.get(List.of(id)) instanceof TomlTable entry)
                    bounds(entry).ifPresent(bounds -> windows.put(id, bounds));
        if (toml.get(List.of("main_window")) instanceof TomlTable table)
            bounds(table).ifPresent(bounds -> {
                int extended = number(table, "extended_state").filter(value -> (value & ~6) == 0).orElse(0);
                mainWindow = new MainWindow(bounds, extended);
            });
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

    /** Version 2: the default look's dock at the top, then each look's under {@code [look."<id>"]}; a bad one falls back alone. */
    private void readLayouts(TomlParseResult toml) {
        readDock(toml, layout(""));
        if (!(toml.get(List.of("look")) instanceof TomlTable looks)) return;
        for (String key : looks.keySet())
            if (ID.matcher(key).matches() && looks.get(List.of(key)) instanceof TomlTable entry) readDock(entry, layout(key));
    }

    /** One look's {@code dock} table and one entry per panel; a bad key or entry falls back alone. */
    private static void readDock(TomlTable toml, Layout into) {
        if (toml.get(List.of("dock")) instanceof TomlTable table)
            into.dock = new Dock(size(table, "left_width", Dock.DEFAULT.leftWidth()), size(table, "right_width", Dock.DEFAULT.rightWidth()),
                size(table, "bottom_height", Dock.DEFAULT.bottomHeight()), ratio(table, "left_ratio", Dock.DEFAULT.leftRatio()),
                ratio(table, "right_ratio", Dock.DEFAULT.rightRatio()), ratio(table, "bottom_ratio", Dock.DEFAULT.bottomRatio()));
        if (!(toml.get(List.of("panels")) instanceof TomlTable table)) return;
        for (String id : table.keySet()) {
            if (!ID.matcher(id).matches() || !(table.get(List.of(id)) instanceof TomlTable entry)) continue;
            Optional<Slot> slot = slot(entry.get(List.of("slot")));
            if (slot.isEmpty()) continue;
            int order = entry.get(List.of("order")) instanceof Long value && value >= 0 && value <= Panel.LAST
                ? value.intValue() : Panel.LAST;
            into.panels.put(id, new Panel(slot.get(), order, Boolean.TRUE.equals(entry.get(List.of("visible")))));
        }
    }

    /**
     * Version 1 kept a region, a visibility and a size per panel. A region becomes its side's first slot, visibility
     * carries over (the first visible panel of a region wins), and each side's size comes from the panel that showed
     * there, else from the first panel there. Order follows registration.
     */
    private void migrate(TomlParseResult toml) {
        if (!(toml.get(List.of("panels")) instanceof TomlTable table)) return;
        var first = new EnumMap<Side, Integer>(Side.class);
        var shown = new EnumMap<Side, Integer>(Side.class);
        var taken = EnumSet.noneOf(Side.class);
        for (String id : new TreeSet<>(table.keySet())) {
            if (!ID.matcher(id).matches() || !(table.get(List.of(id)) instanceof TomlTable entry)) continue;
            Optional<Side> side = side(entry.get(List.of("region")));
            if (side.isEmpty()) continue;
            boolean visible = Boolean.TRUE.equals(entry.get(List.of("visible"))) && taken.add(side.get());
            if (entry.get(List.of("size")) instanceof Long size) {
                int clamped = Math.clamp(size, DockState.MIN_SIZE, DockState.MAX_SIZE);
                first.putIfAbsent(side.get(), clamped);
                if (visible) shown.put(side.get(), clamped);
            }
            layout("").panels.put(id, new Panel(Slot.of(side.get(), false), Panel.LAST, visible));
        }
        layout("").dock = new Dock(migrated(Side.LEFT, shown, first, DockState.DEFAULT_SIDE), migrated(Side.RIGHT, shown, first, DockState.DEFAULT_SIDE),
            migrated(Side.BOTTOM, shown, first, DockState.DEFAULT_BOTTOM), DockState.DEFAULT_RATIO, DockState.DEFAULT_RATIO, DockState.DEFAULT_RATIO);
    }

    private static int migrated(Side side, Map<Side, Integer> shown, Map<Side, Integer> first, int fallback) {
        return shown.getOrDefault(side, first.getOrDefault(side, fallback));
    }

    private static Optional<Slot> slot(Object value) {
        for (Slot slot : Slot.values()) if (slot.name().equals(value)) return Optional.of(slot);
        return Optional.empty();
    }

    private static Optional<Side> side(Object value) {
        for (Side side : Side.values()) if (side.name().equals(value)) return Optional.of(side);
        return Optional.empty();
    }

    private static int size(TomlTable table, String key, int fallback) {
        return table.get(List.of(key)) instanceof Long value && Dock.size(value) ? value.intValue() : fallback;
    }

    private static double ratio(TomlTable table, String key, double fallback) {
        return table.get(List.of(key)) instanceof Double value && Dock.ratio(value) ? value : fallback;
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

    /** A look's dock, made empty the first time it is asked for. */
    private Layout layout(String key) { return layouts.computeIfAbsent(key, ignored -> new Layout()); }

    /** The look whose dock this state reads and writes: empty for the default look. */
    public String look() { return look; }
    /** The look's remembered place of a panel. */
    public Optional<Panel> panel(String id) { return Optional.ofNullable(layout(look).panels.get(id)); }
    /** Every panel the look remembers, by id, sorted by id. */
    public Map<String, Panel> panels() { return Collections.unmodifiableMap(new TreeMap<>(layout(look).panels)); }
    public void putPanel(String id, Panel value) { if (ID.matcher(id).matches()) layout(look).panels.put(id, Objects.requireNonNull(value)); }
    /** The look's dock sizes and ratios. */
    public Dock dock() { return layout(look).dock; }
    public void setDock(Dock value) { layout(look).dock = Objects.requireNonNull(value); }
    public Optional<MainWindow> mainWindow() { return Optional.ofNullable(mainWindow); }
    public void setMainWindow(MainWindow value) { mainWindow = Objects.requireNonNull(value); }
    public Optional<Bounds> window(String id) { return Optional.ofNullable(windows.get(id)); }
    public void putWindow(String id, Bounds value) { if (ID.matcher(id).matches()) windows.put(id, value); }
    public boolean railVisible() { return railVisible; }
    public void setRailVisible(boolean value) { railVisible = value; }
    public boolean zenMode() { return zenMode; }
    public void setZenMode(boolean value) { zenMode = value; }
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

    /** Writes version 2. Ids are restricted to characters that need no TOML escaping. */
    public void save() {
        if (file == null) return;
        var text = new StringBuilder("version = 2\nrail_visible = ").append(railVisible).append('\n');
        text.append("zen_mode = ").append(zenMode).append('\n');
        appendDock(text, "", layout(""));
        layouts.forEach((key, layout) -> { if (!key.isEmpty()) appendDock(text, "look.\"" + key + "\".", layout); });
        windows.forEach((id, bounds) -> text.append("\n[windows.\"").append(id).append("\"]\nx = ").append(bounds.x())
            .append("\ny = ").append(bounds.y()).append("\nwidth = ").append(bounds.width()).append("\nheight = ").append(bounds.height()).append('\n'));
        if (mainWindow != null) {
            Bounds bounds = mainWindow.bounds();
            text.append("\n[main_window]\nx = ").append(bounds.x()).append("\ny = ").append(bounds.y())
                .append("\nwidth = ").append(bounds.width()).append("\nheight = ").append(bounds.height())
                .append("\nextended_state = ").append(mainWindow.extendedState()).append('\n');
        }
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

    /** One look's {@code dock} table and panel entries, their table names under {@code prefix}. */
    private static void appendDock(StringBuilder text, String prefix, Layout layout) {
        Dock dock = layout.dock;
        text.append("\n[").append(prefix).append("dock]\nleft_width = ").append(dock.leftWidth()).append("\nright_width = ").append(dock.rightWidth())
            .append("\nbottom_height = ").append(dock.bottomHeight()).append("\nleft_ratio = ").append(dock.leftRatio())
            .append("\nright_ratio = ").append(dock.rightRatio()).append("\nbottom_ratio = ").append(dock.bottomRatio()).append('\n');
        layout.panels.forEach((id, panel) -> text.append("\n[").append(prefix).append("panels.\"").append(id).append("\"]\nslot = \"")
            .append(panel.slot().name()).append("\"\norder = ").append(panel.order()).append("\nvisible = ").append(panel.visible()).append('\n'));
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
