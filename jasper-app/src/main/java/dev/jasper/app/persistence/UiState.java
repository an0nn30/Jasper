package dev.jasper.app.persistence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Application-owned layout state: where each panel sits, whether the rail shows, and where auxiliary
 * windows were. It is not user configuration. Caller-thread-confined. Reads are strict; anything
 * unreadable falls back to defaults rather than blocking startup, and failed writes are only logged.
 */
public final class UiState {
    /** One panel's remembered place. */
    public record Panel(String region, boolean visible, int size) {
        public Panel {
            if (!REGIONS.contains(region)) throw new IllegalArgumentException("Unknown panel region: " + region);
            if (size < 80 || size > 4000) throw new IllegalArgumentException("Panel size must be 80 to 4000: " + size);
        }
    }

    /** One auxiliary window's remembered bounds; the position may be negative on multi-display setups. */
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

    private static final System.Logger LOG = System.getLogger(UiState.class.getName());
    private static final Set<String> REGIONS = Set.of("LEFT", "RIGHT", "BOTTOM");
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_.-]{0,127}");
    private static final int MAX_BYTES = 256 * 1024;
    private final Path file;
    private final Map<String, Panel> panels = new TreeMap<>();
    private final Map<String, Bounds> windows = new TreeMap<>();
    private boolean railVisible = true;
    private static final int MAX_CHOOSER_PATHS = 50;
    private Chooser chooser = Chooser.DEFAULT;

    private UiState(Path file) { this.file = file; }

    /** State that lives only for this process: tests, and windows that were never connected to a file. */
    public static UiState inMemory() { return new UiState(null); }

    public static UiState load(Path file) {
        UiState state = new UiState(file);
        try {
            Optional<String> text = TomlStateFile.readBounded(file, MAX_BYTES, "UI state");
            if (text.isPresent()) state.parse(text.get());
        } catch (IOException | RuntimeException invalid) {
            LOG.log(System.Logger.Level.WARNING, "Ignoring unreadable UI state " + file, invalid);
            state.panels.clear(); state.windows.clear(); state.railVisible = true; state.chooser = Chooser.DEFAULT;
        }
        return state;
    }

    private void parse(String text) throws IOException {
        TomlParseResult toml = Toml.parse(text);
        if (toml.hasErrors() || !Long.valueOf(1).equals(toml.get(List.of("version")))) throw new IOException("Invalid UI state version or format");
        if (toml.get(List.of("rail_visible")) instanceof Boolean visible) railVisible = visible;
        if (toml.get(List.of("panels")) instanceof TomlTable table)
            for (String id : table.keySet())
                if (ID.matcher(id).matches() && table.get(List.of(id)) instanceof TomlTable entry)
                    panels.put(id, new Panel(String.valueOf(entry.get(List.of("region"))),
                        Boolean.TRUE.equals(entry.get(List.of("visible"))), number(entry, "size")));
        if (toml.get(List.of("windows")) instanceof TomlTable table)
            for (String id : table.keySet())
                if (ID.matcher(id).matches() && table.get(List.of(id)) instanceof TomlTable entry)
                    windows.put(id, new Bounds(number(entry, "x"), number(entry, "y"), number(entry, "width"), number(entry, "height")));
        if (toml.get(List.of("chooser")) instanceof TomlTable table)
            chooser = new Chooser(strings(table, "recent"), strings(table, "pinned"),
                Boolean.TRUE.equals(table.get(List.of("preview"))), !Boolean.FALSE.equals(table.get(List.of("show_hidden"))));
    }

    private static int number(TomlTable table, String key) throws IOException {
        if (table.get(List.of(key)) instanceof Long value && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) return value.intValue();
        throw new IOException("UI state " + key + " must be an integer");
    }

    private static List<String> strings(TomlTable table, String key) {
        if (!(table.get(List.of(key)) instanceof TomlArray array)) return List.of();
        return array.toList().stream().filter(String.class::isInstance).map(String.class::cast).limit(MAX_CHOOSER_PATHS).toList();
    }

    public Optional<Panel> panel(String id) { return Optional.ofNullable(panels.get(id)); }
    public void putPanel(String id, Panel value) { if (ID.matcher(id).matches()) panels.put(id, value); }
    public Optional<Bounds> window(String id) { return Optional.ofNullable(windows.get(id)); }
    public void putWindow(String id, Bounds value) { if (ID.matcher(id).matches()) windows.put(id, value); }
    public boolean railVisible() { return railVisible; }
    public void setRailVisible(boolean value) { railVisible = value; }
    public Chooser chooser() { return chooser; }
    public void setChooser(Chooser value) { chooser = Objects.requireNonNull(value); }

    /** Ids are restricted to characters that need no TOML escaping. */
    public void save() {
        if (file == null) return;
        var text = new StringBuilder("version = 1\nrail_visible = ").append(railVisible).append('\n');
        panels.forEach((id, panel) -> text.append("\n[panels.\"").append(id).append("\"]\nregion = \"").append(panel.region())
            .append("\"\nvisible = ").append(panel.visible()).append("\nsize = ").append(panel.size()).append('\n'));
        windows.forEach((id, bounds) -> text.append("\n[windows.\"").append(id).append("\"]\nx = ").append(bounds.x())
            .append("\ny = ").append(bounds.y()).append("\nwidth = ").append(bounds.width()).append("\nheight = ").append(bounds.height()).append('\n'));
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
