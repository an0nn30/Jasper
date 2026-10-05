package dev.jasper.indexer;

import dev.jasper.indexer.detect.Markers;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import java.util.List;

/** Project Index's declared settings, stored in {@code plugins/dev.jasper.indexer/dev.jasper.indexer.toml}. */
public final class IndexerSettings {
    /** Folder names never indexed, by default. */
    public static final List<String> DEFAULT_EXCLUDES = List.of(".git", ".hg", ".svn", "node_modules", "target", "build",
        "dist", "out", ".gradle", ".idea", ".vscode", ".venv", "venv", "__pycache__", ".mypy_cache", ".pytest_cache", ".tox",
        ".next", ".nuxt", "vendor", "Pods", "DerivedData", "bin", "obj");
    private static final String SECTION = "Project Index";

    /** Whether to offer indexing when a terminal enters a project. */
    public final Setting<Boolean> prompt;
    /** Marker patterns; a leading {@code !} marks a strong one. */
    public final Setting<List<String>> markers;
    /** Folder names never indexed. */
    public final Setting<List<String>> excludes;
    /** Folders never offered: local absolute paths, and {@code <host id>:<path>} for remote ones. */
    public final Setting<List<String>> ignored;
    /** The most files one project's index holds. */
    public final Setting<Integer> maxFiles;
    /** The least time between two background refreshes of one project. */
    public final Setting<Integer> refreshMinutes;

    /**
     * Declares the settings; call it during the plugin's start.
     *
     * @param settings the plugin's settings
     */
    public IndexerSettings(PluginSettings settings) {
        prompt = settings.declare(SettingSpec.of("prompt", SettingKind.bool(), true)
            .label("Offer to index projects")
            .description("Ask when a terminal enters a folder that looks like a project.")
            .section(SECTION).keywords("index", "project", "prompt", "notification"));
        markers = settings.declare(SettingSpec.of("markers", SettingKind.stringList(entry -> !entry.isBlank()), Markers.DEFAULTS)
            .label("Project markers")
            .description("Files or folders that make a folder a project. * matches any text. A leading ! marks a strong "
                + "marker, such as !.git: the nearest folder with one wins over nearer folders with only ordinary markers.")
            .section(SECTION).keywords("marker", "project", "git", "detect"));
        excludes = settings.declare(SettingSpec.of("excludes",
                SettingKind.stringList(name -> !name.isBlank() && name.indexOf('/') < 0 && name.indexOf('\\') < 0), DEFAULT_EXCLUDES)
            .label("Excluded folders")
            .description("Folders with these names are never indexed. Git's own ignore rules also apply in a Git work tree.")
            .section(SECTION).keywords("exclude", "ignore", "node_modules", "folder"));
        ignored = settings.declare(SettingSpec.of("ignored", SettingKind.stringList(entry -> !entry.isBlank()), List.<String>of())
            .label("Ignored paths")
            .description("Never offer to index these folders or anything inside them. Ignore this path adds to this list.")
            .section(SECTION).keywords("ignore", "path", "prompt"));
        maxFiles = settings.declare(SettingSpec.of("max_files", SettingKind.integer(1_000, 1_000_000), 200_000)
            .label("Files indexed per project")
            .description("A project with more files is indexed up to this many and marked truncated.")
            .section(SECTION).keywords("limit", "files", "maximum"));
        refreshMinutes = settings.declare(SettingSpec.of("refresh_minutes", SettingKind.integer(1, 1_440), 5)
            .label("Minutes between background refreshes")
            .description("Entering a project or finishing a command in it refreshes its index at most this often.")
            .section(SECTION).keywords("refresh", "minutes", "stale"));
    }

    /** @return the markers setting, parsed */
    public Markers markers() { return Markers.parse(markers.get()); }
}
