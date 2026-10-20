package dev.jasper.app.appearance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Integrity of the consumer ledger; a row does not mean that its migration is complete. */
class ThemeMigrationCoverageTest {
    private static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot"));
    private static final Pattern CANDIDATE = Pattern.compile(
        "import (?:javax\\.swing|java\\.awt|com\\.formdev|dev\\.jasper\\.sdk\\.ui|dev\\.jasper\\.ui)|UIManager|[Aa]ppearance|paintComponent|paintBorder|new J[A-Z]|new [A-Z]\\w*(?:Panel|View|Window|Editor|Form|Field|Button|Menu|Picker|Bar|Tree|List|Table)\\b");
    private static final Set<String> CLASSIFICATIONS = Set.of("standard-ui", "custom-content", "native-platform", "nonvisual-helper");

    @Test void everyInventoriedPathExistsAndStandardControlsHaveBothOwners() throws IOException {
        List<List<String>> rows = rows();
        var paths = new HashSet<String>();
        for (var row : rows) {
            assertThat(row).as("CSV column count: %s", row).hasSize(8);
            assertThat(paths.add(row.get(0))).as("unique source: %s", row.get(0)).isTrue();
            String disposition = row.get(7);
            if ((disposition.startsWith("Removed in Plan 2 Tasks 2-3; authority=") || disposition.startsWith("Removed in Plan 2 Task 5; authority=")
                    || disposition.startsWith("Removed in Plan 2 Task 6a; authority=") || disposition.startsWith("Removed in Plan 2 Task 6b; authority=")
                    || disposition.startsWith("Removed in Plan 2 Task 6c; authority=")
                    || disposition.startsWith("Removed in Plan 2 Task 7; authority=")
                    || disposition.startsWith("Removed in Plan 2 Task 8a; authority=")
                    || disposition.startsWith("Removed in Plan 2 Task 8b; authority=")
                    || disposition.startsWith("Removed in Plan 2 Task 8c; authority=")
                    || disposition.startsWith("Removed in Plan 2 Task 9b; authority=")
                    || disposition.startsWith("Removed in Plan 2 Task 11; authority=")
                    || disposition.startsWith("Removed in Plan 2 Task 12; authority=")
                    || disposition.startsWith("Removed in Slate Steel Plan 2; authority=")
                    || disposition.startsWith("Removed in Slate Steel Plan 3; authority=")
                    || disposition.startsWith("Removed in SDK 0.16.1 clipboard plan Task 4; authority="))) {
                assertThat(Files.exists(ROOT.resolve(row.get(0)))).as("removed source: %s", row.get(0)).isFalse();
                String authority = disposition.substring(disposition.indexOf("authority=") + 10).split(";", 2)[0];
                assertThat(Files.isRegularFile(ROOT.resolve(authority))).as("replacement authority: %s", authority).isTrue();
            } else if (disposition.startsWith("Moved in Plan 2 Tasks 2-3 to ")) {
                assertThat(Files.exists(ROOT.resolve(row.get(0)))).as("moved source: %s", row.get(0)).isFalse();
                String destination = disposition.substring("Moved in Plan 2 Tasks 2-3 to ".length()).split(";", 2)[0];
                assertThat(Files.isRegularFile(ROOT.resolve(destination))).as("moved destination: %s", destination).isTrue();
                assertThat(rows).anySatisfy(moved -> assertThat(moved.get(0)).isEqualTo(destination));
            } else assertThat(Files.isRegularFile(ROOT.resolve(row.get(0)))).as("existing source: %s", row.get(0)).isTrue();
            assertThat(CLASSIFICATIONS).contains(row.get(3));
            assertThat(row.get(1)).isNotBlank();
            assertThat(row.get(7)).isNotBlank();
            if (row.get(3).equals("standard-ui")) {
                assertThat(row.get(4)).as("migration task: %s", row.get(0)).matches("[0-9]+(?:;[0-9]+)*");
                assertThat(row.get(5)).as("composition task: %s", row.get(0)).matches("[0-9]+(?:;[0-9]+)*");
            }
            for (String test : row.get(6).split(";"))
                if (!test.equals("none-direct")) assertThat(Files.isRegularFile(ROOT.resolve(test))).as("behavior evidence: %s", test).isTrue();
        }
    }

    @Test void sourceDiscoveryIncludesTestFixturesAndHelperFalsePositives() throws IOException {
        var inventoried = rows().stream().map(row -> row.get(0)).collect(java.util.stream.Collectors.toSet());
        List<Path> roots = new ArrayList<>(List.of(ROOT.resolve("jasper-app/src"), ROOT.resolve("jasper-sdk/src"), ROOT.resolve("jasper-sdk-testkit/src")));
        try (var plugins = Files.list(ROOT.resolve("plugins"))) {
            plugins.map(path -> path.resolve("src")).filter(Files::isDirectory).forEach(roots::add);
        }
        var missing = new ArrayList<String>();
        for (Path root : roots) try (var sources = Files.walk(root)) {
            for (Path path : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (CANDIDATE.matcher(Files.readString(path)).find()) {
                    String relative = ROOT.relativize(path).toString().replace('\\', '/');
                    if (!inventoried.contains(relative)) missing.add(relative);
                }
            }
        }
        assertThat(missing).as("every matching Java owner including fixture-only Swing scaffolds").isEmpty();
        assertThat(rows()).anySatisfy(row -> {
            assertThat(row.get(0)).endsWith("/testsupport/plugin/ColorWidgets.java");
            assertThat(row.get(7)).contains("Test/fixture ownership");
        });
        assertThat(rows()).anySatisfy(row -> assertThat(row.get(3)).isEqualTo("nonvisual-helper"));
    }

    @Test void allBundledPluginsOccurAndHistoryAndSnippetsRemainPaletteSettingsConsumers() throws IOException {
        List<List<String>> rows = rows();
        Set<String> owners = rows.stream().map(row -> row.get(1)).collect(java.util.stream.Collectors.toSet());
        try (var plugins = Files.list(ROOT.resolve("plugins"))) {
            for (Path plugin : plugins.filter(p -> Files.isDirectory(p.resolve("src/main"))).toList())
                assertThat(owners).contains("dev.jasper." + plugin.getFileName());
        }
        for (String id : List.of("dev.jasper.history", "dev.jasper.snippets")) {
            var consumers = rows.stream().filter(row -> row.get(1).equals(id)).toList();
            assertThat(consumers).isNotEmpty().allSatisfy(row -> {
                assertThat(row.get(2)).contains("palette", "manager window");
                assertThat(row.get(4)).isEqualTo("10");
            });
            assertThat(consumers).anySatisfy(row -> assertThat(row.get(0)).endsWith("Scope.java"));
            assertThat(consumers).anySatisfy(row -> assertThat(row.get(0)).endsWith("Plugin.java"));
        }
    }

    @Test void frozenBeforeCapturesRemainByteIdentical() throws Exception {
        Path before = ROOT.resolve("docs/design/theme-engine/before-f16ae32f");
        assertThat(Files.isRegularFile(before.resolve("SHA256SUMS"))).as("immutable pre-migration checksum manifest").isTrue();
        var checksum = java.security.MessageDigest.getInstance("SHA-256");
        var names = new HashSet<String>();
        for (String line : Files.readAllLines(before.resolve("SHA256SUMS"))) {
            String[] fields = line.split("  ", 2);
            assertThat(fields).hasSize(2);
            Path path = before.resolve(fields[1]).normalize();
            assertThat(path.startsWith(before)).isTrue();
            assertThat(names.add(fields[1])).as("unique baseline asset").isTrue();
            assertThat(java.util.HexFormat.of().formatHex(checksum.digest(Files.readAllBytes(path))))
                .as("immutable before image/metadata: %s", fields[1]).isEqualTo(fields[0]);
        }
        assertThat(names).contains("manifest.json");
        for (String family : List.of("vault", "remote", "remote-sftp", "remote-tunnels", "remote-transfers", "editor", "fileserver", "history-palette", "snippets-palette", "settings", "dock", "notifications", "notification-errors", "file-chooser", "theme-import", "palette-current", "hosted-content", "title-bar", "shortcut-help"))
            assertThat(names.stream().anyMatch(name -> name.startsWith(family + "/") && name.endsWith(".png")))
                .as("pre-migration family: %s", family).isTrue();
    }

    @Test void frozenContextAndWindowTabStatesAreDistinctAndSshKeyFormIsPreserved() throws Exception {
        Path before = ROOT.resolve("docs/design/theme-engine/before-f16ae32f");
        for (String theme : List.of("jasper dark", "intellij light")) {
            Path title = before.resolve("title-bar");
            assertThat(Files.readAllBytes(title.resolve(theme + "-tmux-tabs.png")))
                .as("wide context is visibly changed, not collapsed by a zero-width root")
                .isNotEqualTo(Files.readAllBytes(title.resolve(theme + "-remote.png")));
            assertThat(Files.readAllBytes(title.resolve(theme + "-single-window-tabs.png")))
                .as("tabs have their own actual owner region, separately from the titlebar")
                .isNotEqualTo(Files.readAllBytes(title.resolve(theme + "-shell-tabs-window-tabs.png")));
        }
        for (String theme : List.of("dark", "light"))
            for (String state : List.of("ssh-key-empty", "ssh-key-invalid-path", "ssh-key-edit"))
                assertThat(Files.isRegularFile(before.resolve("vault/" + theme + "/" + state + ".png"))).as("actual EntryEditor.key state").isTrue();
    }

    private static List<List<String>> rows() throws IOException {
        List<String> lines = Files.readAllLines(ROOT.resolve("docs/design/theme-engine/migration.csv"));
        assertThat(lines.getFirst()).isEqualTo("path,owner,surface,classification,migration_task,composition_task,existing_test,disposition");
        return lines.subList(1, lines.size()).stream().map(ThemeMigrationCoverageTest::csv).toList();
    }

    private static List<String> csv(String line) {
        var result = new ArrayList<String>();
        var field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { field.append(c); i++; }
                else quoted = !quoted;
            } else if (c == ',' && !quoted) { result.add(field.toString()); field.setLength(0); }
            else field.append(c);
        }
        if (quoted) throw new IllegalArgumentException("Unclosed CSV quote");
        result.add(field.toString());
        return result;
    }
}
