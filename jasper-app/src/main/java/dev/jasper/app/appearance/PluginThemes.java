package dev.jasper.app.appearance;

import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.ThemeFileException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The themes plugins provide (spec 7.4 and 7.5): the themes of loadable plugins, ordered by name, after the built-ins and
 * before the folder's; the theme ids of every discovered plugin, reserved like built-in ids, with the plugin that declares
 * each; the ids only plugins that are not loadable declare (dormant); and the files that could not be used. Immutable.
 *
 * @param provided the plugin themes, by name then id
 * @param reserved every discovered plugin's theme ids, each with its plugin's name
 * @param dormant the ids declared only by plugins that are not loadable
 * @param problems the files that could not be used
 */
public record PluginThemes(List<Provided> provided, Map<String, String> reserved, Set<String> dormant, List<ThemeLibrary.Problem> problems) {
    /** No plugin themes. */
    public static final PluginThemes NONE = new PluginThemes(List.of(), Map.of(), Set.of(), List.of());

    /**
     * A plugin theme.
     *
     * @param theme the theme
     * @param pluginName the name of the plugin that provides it
     */
    public record Provided(JasperTheme theme, String pluginName) { }

    /**
     * A theme file a discovered plugin declares.
     *
     * @param pluginId the plugin's id
     * @param pluginName its name
     * @param rank its origin's precedence: 0 for development, 1 for bundled, 2 for installed
     * @param loadable whether the plugin will load at this start
     * @param themeId the file's theme id (its name without {@code .json})
     * @param path the file's path in the plugin's jars, as its descriptor names it
     * @param directory the plugin's directory, which problems name
     * @param bytes its first bytes, read only for a loadable plugin; empty when absent
     */
    public record Declared(String pluginId, String pluginName, int rank, boolean loadable, String themeId, String path, Path directory,
                           Optional<byte[]> bytes) { }

    /** Copies the parts. */
    public PluginThemes {
        provided = List.copyOf(provided); reserved = Map.copyOf(reserved); dormant = Set.copyOf(dormant); problems = List.copyOf(problems);
    }

    /** The tier from every declared file: DEV before BUNDLED before USER, then the lowest plugin id; no built-in id is taken. */
    public static PluginThemes of(List<Declared> declared) {
        var reserved = new LinkedHashMap<String, String>();
        var problems = new ArrayList<ThemeLibrary.Problem>();
        var winners = new LinkedHashMap<String, Provided>();
        var winnerIds = new LinkedHashMap<String, String>();
        var sorted = new ArrayList<>(declared);
        sorted.sort(Comparator.comparingInt(Declared::rank).thenComparing(Declared::pluginId));
        for (Declared file : sorted) {
            if (ThemeLibrary.desktopId(file.themeId())) {
                problems.add(new ThemeLibrary.Problem(file.directory(), 0, ThemeLibrary.bundledOnly().reservedLabel(file.themeId()).orElseThrow()
                    + " is reserved; the " + file.pluginName() + " plugin's file is ignored."));
                continue;
            }
            var builtIn = JasperTheme.builtIn(file.themeId());
            if (builtIn.isEmpty()) reserved.putIfAbsent(file.themeId(), file.pluginName());
            if (!file.loadable()) continue;
            if (builtIn.isPresent()) {
                problems.add(new ThemeLibrary.Problem(file.directory(), 0, builtIn.get().name() + " is built in; the " + file.pluginName() + " plugin's file is ignored."));
                continue;
            }
            var winner = winners.get(file.themeId());
            if (winner != null) {
                problems.add(new ThemeLibrary.Problem(file.directory(), 0, winner.theme().name() + " is provided by the " + winner.pluginName()
                    + " plugin; the " + file.pluginName() + " plugin's file is ignored."));
                continue;
            }
            String where = "The " + file.pluginName() + " plugin's theme file " + file.path();
            if (file.bytes().isEmpty()) {
                problems.add(new ThemeLibrary.Problem(file.directory(), 0, where + " is not in its jars."));
                continue;
            }
            try {
                var theme = JasperTheme.parse(text(file.bytes().get()), file.themeId());
                winners.put(file.themeId(), new Provided(theme, file.pluginName()));
                winnerIds.put(file.themeId(), file.pluginId());
            } catch (ThemeFileException failure) {
                problems.add(new ThemeLibrary.Problem(file.directory(), 0, where + (failure.line() > 0 ? ", line " + failure.line() : "") + ": "
                    + failure.detail()));
            } catch (CharacterCodingException | RuntimeException failure) {
                problems.add(new ThemeLibrary.Problem(file.directory(), 0, where + " could not be used: "
                    + Objects.requireNonNullElse(failure.getMessage(), failure.getClass().getSimpleName())));
            }
        }
        // A provided id is reserved for the plugin that provides it, not a higher-ranked one that does not load.
        winners.forEach((id, winner) -> reserved.put(id, winner.pluginName()));
        var dormant = new LinkedHashSet<String>();
        for (Declared file : declared) if (!file.loadable() && !winners.containsKey(file.themeId()) && ThemeLibrary.bundledOnly().reservedLabel(file.themeId()).isEmpty())
            dormant.add(file.themeId());
        var provided = new ArrayList<>(winners.values());
        provided.sort(Comparator.comparing((Provided item) -> item.theme().name().toLowerCase(Locale.ROOT)).thenComparing(item -> item.theme().id()));
        return new PluginThemes(provided, reserved, dormant, problems);
    }

    private static String text(byte[] bytes) throws CharacterCodingException {
        if (bytes.length > ThemeLibrary.MAX_BYTES) throw new CharacterCodingException() {
            @Override public String getMessage() { return "Theme files are at most 1 MiB."; }
        };
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    /** The provided theme with this id. */
    Optional<Provided> provided(String id) { return provided.stream().filter(item -> item.theme().id().equals(id)).findFirst(); }
}
