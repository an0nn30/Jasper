package dev.jasper.indexer.detect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static java.util.Map.entry;

/**
 * The configured project markers: names, or globs with {@code *}, of files or folders that make a folder a project.
 * A strong marker (written with a leading {@code !} in the setting) outranks every weak one. Matching is
 * case-sensitive.
 *
 * @param strong strong patterns, in configured order
 * @param weak weak patterns, in configured order
 */
public record Markers(List<String> strong, List<String> weak) {
    /** The setting's default. */
    public static final List<String> DEFAULTS = List.of("!.git", "!.hg", "!.svn", "!*.sln",
        "Cargo.toml", "go.mod", "pyproject.toml", "setup.py", "requirements.txt", "Pipfile", "package.json", "deno.json",
        "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "CMakeLists.txt",
        "Makefile", "meson.build", "composer.json", "Gemfile", "mix.exs", "pubspec.yaml", "Package.swift", "*.csproj",
        "*.xcodeproj", "flake.nix");

    private static final Map<String, String> KINDS = Map.ofEntries(
        entry(".git", "Git repository"), entry(".hg", "Mercurial repository"), entry(".svn", "Subversion working copy"),
        entry("*.sln", "Visual Studio solution"), entry("Cargo.toml", "Cargo project"), entry("go.mod", "Go module"),
        entry("pyproject.toml", "Python project"), entry("setup.py", "Python project"),
        entry("requirements.txt", "Python project"), entry("Pipfile", "Python project"),
        entry("package.json", "Node.js project"), entry("deno.json", "Deno project"), entry("pom.xml", "Maven project"),
        entry("build.gradle", "Gradle project"), entry("build.gradle.kts", "Gradle project"),
        entry("settings.gradle", "Gradle project"), entry("settings.gradle.kts", "Gradle project"),
        entry("CMakeLists.txt", "CMake project"), entry("Makefile", "Make project"), entry("meson.build", "Meson project"),
        entry("composer.json", "PHP project"), entry("Gemfile", "Ruby project"), entry("mix.exs", "Elixir project"),
        entry("pubspec.yaml", "Dart project"), entry("Package.swift", "Swift package"), entry("*.csproj", ".NET project"),
        entry("*.xcodeproj", "Xcode project"), entry("flake.nix", "Nix flake"));

    /**
     * One marker found in a folder.
     *
     * @param pattern the configured pattern
     * @param name the entry that matched it
     */
    public record Match(String pattern, String name) {
        /** @return how a card names the project's kind, such as "Git repository" */
        public String kind() { return KINDS.getOrDefault(pattern, "Contains " + name); }
    }

    /** Copies the lists. */
    public Markers {
        strong = List.copyOf(strong);
        weak = List.copyOf(weak);
    }

    /**
     * Reads the setting: blank entries are skipped and a leading {@code !} makes an entry strong.
     *
     * @param entries the setting's values
     * @return the markers
     */
    public static Markers parse(List<String> entries) {
        var strong = new ArrayList<String>();
        var weak = new ArrayList<String>();
        for (String entry : entries) {
            String text = entry.strip();
            if (text.startsWith("!")) {
                text = text.substring(1).strip();
                if (!text.isEmpty()) strong.add(text);
            } else if (!text.isEmpty()) weak.add(text);
        }
        return new Markers(strong, weak);
    }

    /**
     * @param names the entries of one folder
     * @return the first strong pattern, in configured order, that one of them matches
     */
    public Optional<Match> strongMatch(Collection<String> names) { return first(strong, names); }

    /**
     * @param names the entries of one folder
     * @return the first weak pattern, in configured order, that one of them matches
     */
    public Optional<Match> weakMatch(Collection<String> names) { return first(weak, names); }

    /** @return every pattern without {@code *}: names a probe can test directly */
    public Set<String> literals() {
        return Stream.concat(strong.stream(), weak.stream()).filter(pattern -> pattern.indexOf('*') < 0)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    /** @return whether a probe must list folders to match globs */
    public boolean hasGlobs() { return Stream.concat(strong.stream(), weak.stream()).anyMatch(pattern -> pattern.indexOf('*') >= 0); }

    /**
     * @param name an entry name
     * @return whether any pattern matches it
     */
    public boolean matchesAny(String name) {
        return Stream.concat(strong.stream(), weak.stream()).anyMatch(pattern -> glob(pattern, name));
    }

    /** Whether a name matches a pattern in which {@code *} matches any text, possibly empty. */
    static boolean glob(String pattern, String name) {
        if (pattern.indexOf('*') < 0) return pattern.equals(name);
        String[] parts = pattern.split("\\*", -1);
        if (!name.startsWith(parts[0])) return false;
        int at = parts[0].length();
        for (int i = 1; i < parts.length - 1; i++) {
            int found = name.indexOf(parts[i], at);
            if (found < 0) return false;
            at = found + parts[i].length();
        }
        String last = parts[parts.length - 1];
        return name.length() - last.length() >= at && name.endsWith(last);
    }

    private static Optional<Match> first(List<String> patterns, Collection<String> names) {
        var sorted = new TreeSet<>(names);
        for (String pattern : patterns)
            for (String name : sorted)
                if (glob(pattern, name)) return Optional.of(new Match(pattern, name));
        return Optional.empty();
    }
}
