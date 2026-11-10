package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.platform.FileAssociations.Claimability;
import dev.jasper.app.platform.FileAssociations.Handler;
import dev.jasper.app.platform.FileAssociations.Outcome;
import dev.jasper.app.platform.FileAssociations.Refusal;
import dev.jasper.app.platform.FileAssociations.TypeResult;
import dev.jasper.app.platform.FileAssociations.Unavailable;
import dev.jasper.app.platform.LinuxFileAssociations.Tools;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class LinuxFileAssociationsTest {
    private static final String JASPER = "jasper-workstation-Jasper.desktop";
    private static final FileTypeCatalog CATALOG = FileTypeCatalog.parse("""
            .java\tcom.sun.java-source\ttext/x-java
            .json\tpublic.json\tapplication/json
            .ts\t\t
            .py\tpublic.python-script\ttext/x-python3,text/x-python
            """);

    @TempDir Path home;

    /** A fake xdg-mime and update-mime-database: who opens each type, and every command with its time-out. */
    private final class FakeTools {
        final Set<String> onPath = new java.util.HashSet<>(Set.of("xdg-mime", "update-mime-database"));
        final Map<String, String> defaults = new LinkedHashMap<>();
        final Set<String> failing = new java.util.HashSet<>();
        final List<String> commands = new java.util.ArrayList<>();
        final List<Duration> timeouts = new java.util.ArrayList<>();
        Function<String, String> query = type -> defaults.getOrDefault(type, "");

        Tools tools() {
            return new Tools((command, timeout) -> {
                String line = String.join(" ", command);
                commands.add(line);
                timeouts.add(timeout);
                if (failing.stream().anyMatch(line::startsWith)) return Optional.empty();
                if (command.size() == 4 && command.subList(0, 3).equals(List.of("xdg-mime", "query", "default"))) {
                    String id = query.apply(command.get(3));
                    return Optional.of(id.isEmpty() ? "" : id + "\n");
                }
                if (command.subList(0, 2).equals(List.of("xdg-mime", "default"))) {
                    command.subList(3, command.size()).forEach(type -> defaults.put(type, command.get(2)));
                }
                return Optional.of("");
            }, onPath::contains);
        }

        LinuxFileAssociations backend() {
            return new LinuxFileAssociations(CATALOG, environment(), home, tools());
        }

        List<String> queries() {
            return commands.stream().filter(line -> line.startsWith("xdg-mime query")).toList();
        }

        List<String> changes() {
            return commands.stream().filter(line -> !line.startsWith("xdg-mime query")).toList();
        }
    }

    private Map<String, String> environment() {
        return Map.of("XDG_DATA_HOME", data().toString(), "XDG_DATA_DIRS", system() + ":" + other(),
                "XDG_CONFIG_HOME", config().toString());
    }

    private Path data() { return home.resolve("data"); }

    private Path system() { return home.resolve("system"); }

    private Path other() { return home.resolve("other"); }

    private Path config() { return home.resolve("config"); }

    private Path packageFile() { return data().resolve("mime/packages/jasper-workstation.xml"); }

    private Path mimeapps() { return config().resolve("mimeapps.list"); }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    @Test void itNeedsXdgMimeAndTheInstalledDesktopEntry() throws IOException {
        FakeTools tools = new FakeTools();
        tools.onPath.clear();
        assertThat(tools.backend().unavailable()).contains(Unavailable.NO_XDG_MIME);
        tools.onPath.add("xdg-mime");
        assertThat(tools.backend().unavailable()).contains(Unavailable.NOT_INSTALLED);
        write(other().resolve("applications").resolve(JASPER), "[Desktop Entry]\nName=Jasper\n");
        assertThat(tools.backend().unavailable()).isEmpty();
        assertThat(tools.commands).as("nothing runs").isEmpty();
    }

    @Test void theFoldersDefaultToTheBaseDirectorySpec() throws IOException {
        FakeTools tools = new FakeTools();
        assertThat(new LinuxFileAssociations(CATALOG, Map.of("XDG_DATA_HOME", "relative/data"), home, tools.tools()).dataFolders())
                .as("an empty or relative value is ignored")
                .containsExactly(home.resolve(".local/share"), Path.of("/usr/local/share"), Path.of("/usr/share"));
        // The system folders stay temporary, so the test never reads the real database.
        LinuxFileAssociations backend = new LinuxFileAssociations(CATALOG,
                Map.of("XDG_DATA_HOME", "", "XDG_CONFIG_HOME", "", "XDG_DATA_DIRS", ":relative:" + system()), home, tools.tools());
        assertThat(backend.dataFolders()).containsExactly(home.resolve(".local/share"), system());
        assertThat(backend.unavailable()).contains(Unavailable.NOT_INSTALLED);
        write(home.resolve(".local/share/applications").resolve(JASPER), "[Desktop Entry]\nName=Jasper\n");
        assertThat(backend.unavailable()).isEmpty();
        write(home.resolve(".local/share/mime/globs2"), "50:text/x-protobuf:*.proto\n");
        assertThat(backend.claimability(".proto")).isEqualTo(new Claimability.Claimable(List.of("text/x-protobuf"), false));
        Map<String, List<String>> proto = Map.of(".scad", List.of("text/x-jasper-scad"));
        backend.claim(proto);
        assertThat(home.resolve(".local/share/mime/packages/jasper-workstation.xml")).exists();
        assertThat(home.resolve(".config/mimeapps.list")).content().contains("text/x-jasper-scad=" + JASPER + ";");
        assertThat(tools.commands).contains("update-mime-database " + home.resolve(".local/share/mime"));
    }

    @Test void theCatalogueDecidesItsOwnExtensions() throws IOException {
        write(system().resolve("mime/globs2"), "50:video/mp2t:*.ts\n50:text/x-java-other:*.java\n");
        LinuxFileAssociations backend = new FakeTools().backend();
        assertThat(backend.claimability(".java")).isEqualTo(new Claimability.Claimable(List.of("text/x-java"), false));
        assertThat(backend.claimability(".py"))
                .isEqualTo(new Claimability.Claimable(List.of("text/x-python3", "text/x-python"), false));
        assertThat(backend.claimability(".ts")).as("an empty Linux column")
                .isEqualTo(new Claimability.NotClaimable(Refusal.NOT_TEXT));
    }

    @Test void anotherExtensionAsksTheMimeDatabase() throws IOException {
        write(data().resolve("mime/globs2"), "50:text/x-jasper-scad:*.scad\n");
        write(system().resolve("mime/globs2"), "50:text/x-protobuf:*.proto\n50:image/png:*.png\n50:application/json:*.jsonc\n"
                + "50:application/x-sqlite3:*.sqlite\n50:text/x-other-scad:*.scad\n");
        write(other().resolve("mime/globs2"), "90:text/x-lua:*.lua\n");
        LinuxFileAssociations backend = new FakeTools().backend();
        assertThat(backend.claimability(".proto")).isEqualTo(new Claimability.Claimable(List.of("text/x-protobuf"), false));
        assertThat(backend.claimability(".lua")).isEqualTo(new Claimability.Claimable(List.of("text/x-lua"), false));
        assertThat(backend.claimability(".jsonc")).as("an application type the catalogue claims")
                .isEqualTo(new Claimability.Claimable(List.of("application/json"), false));
        assertThat(backend.claimability(".png")).isEqualTo(new Claimability.NotClaimable(Refusal.NOT_TEXT));
        assertThat(backend.claimability(".sqlite")).isEqualTo(new Claimability.NotClaimable(Refusal.NOT_TEXT));
        assertThat(backend.claimability(".scad")).as("the user's database first on a tie")
                .isEqualTo(new Claimability.Claimable(List.of("text/x-jasper-scad"), false));
        assertThat(backend.claimability(".c++-x")).as("unknown: Jasper declares it")
                .isEqualTo(new Claimability.Claimable(List.of("text/x-jasper-c++-x"), true));
    }

    @Test void theHandlerIsJasperAnotherAppOrNone() throws IOException {
        write(system().resolve("applications/org.gnome.gedit.desktop"), "[Desktop Entry]\nName[de]=Texteditor\nName=Text Editor\n");
        write(other().resolve("applications/kde4/kate.desktop"), "[Desktop Entry]\nName=Kate\n");
        FakeTools tools = new FakeTools();
        tools.defaults.put("text/x-java", JASPER);
        tools.defaults.put("text/plain", "org.gnome.gedit.desktop");
        tools.defaults.put("text/x-csrc", "kde4-kate.desktop");
        tools.defaults.put("text/x-lua", "gone.desktop");
        LinuxFileAssociations backend = tools.backend();
        assertThat(backend.handler("text/x-java")).isEqualTo(new Handler.Jasper());
        assertThat(backend.handler("text/plain")).isEqualTo(new Handler.Other("org.gnome.gedit.desktop", "Text Editor"));
        assertThat(backend.handler("text/x-csrc")).as("a desktop id naming a subfolder")
                .isEqualTo(new Handler.Other("kde4-kate.desktop", "Kate"));
        assertThat(backend.handler("text/x-lua")).isEqualTo(new Handler.Other("gone.desktop", "gone.desktop"));
        assertThat(backend.handler("text/markdown")).isEqualTo(new Handler.None());
        assertThat(tools.commands).first().isEqualTo("xdg-mime query default text/x-java");
        assertThat(tools.timeouts).containsOnly(Duration.ofSeconds(2));
    }

    @Test void aFailedQueryIsAnError() {
        FakeTools tools = new FakeTools();
        tools.failing.add("xdg-mime query");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tools.backend().handler("text/x-java"))
                .isInstanceOf(IOException.class).hasMessageContaining("xdg-mime query default text/x-java");
    }

    @Test void claimingAKnownTypeSetsTheDefaultOncePerExtension() {
        FakeTools tools = new FakeTools();
        Map<String, List<String>> types = new LinkedHashMap<>();
        types.put(".java", List.of("text/x-java"));
        types.put(".py", List.of("text/x-python3", "text/x-python"));
        assertThat(tools.backend().claim(types)).containsExactly(
                new TypeResult(".java", "text/x-java", new Outcome.Done()),
                new TypeResult(".py", "text/x-python3", new Outcome.Done()),
                new TypeResult(".py", "text/x-python", new Outcome.Done()));
        assertThat(tools.changes()).containsExactly("xdg-mime default " + JASPER + " text/x-java",
                "xdg-mime default " + JASPER + " text/x-python3 text/x-python");
        assertThat(tools.queries()).containsExactly("xdg-mime query default text/x-java",
                "xdg-mime query default text/x-python3", "xdg-mime query default text/x-python");
        assertThat(packageFile()).doesNotExist();
        assertThat(mimeapps()).doesNotExist();
    }

    @Test void claimingAnUnknownExtensionDeclaresItFirst() throws IOException {
        write(packageFile(), LinuxMime.packageXml(new java.util.TreeSet<>(Set.of("scad"))));
        write(mimeapps(), "# mine\n[Default Applications]\ntext/plain=gedit.desktop;\n");
        FakeTools tools = new FakeTools();
        assertThat(tools.backend().claim(Map.of(".proto", List.of("text/x-jasper-proto"))))
                .containsExactly(new TypeResult(".proto", "text/x-jasper-proto", new Outcome.Done()));
        assertThat(LinuxMime.declaredExtensions(Files.readString(packageFile()))).containsExactly("proto", "scad");
        assertThat(Files.readString(mimeapps())).isEqualTo("# mine\n[Default Applications]\ntext/plain=gedit.desktop;\n\n"
                + "[Added Associations]\ntext/x-jasper-proto=" + JASPER + ";\n");
        assertThat(tools.changes()).containsExactly("update-mime-database " + data().resolve("mime"),
                "xdg-mime default " + JASPER + " text/x-jasper-proto");
        assertThat(tools.timeouts.get(0)).isEqualTo(Duration.ofSeconds(20));
        assertThat(tools.timeouts.subList(1, tools.timeouts.size())).containsOnly(Duration.ofSeconds(2));
    }

    @Test void aFailedDeclarationSkipsThatExtensionsDefault() {
        FakeTools tools = new FakeTools();
        tools.failing.add("update-mime-database");
        Map<String, List<String>> types = new LinkedHashMap<>();
        types.put(".proto", List.of("text/x-jasper-proto"));
        types.put(".java", List.of("text/x-java"));
        List<TypeResult> results = tools.backend().claim(types);
        assertThat(results).hasSize(2);
        assertThat(results.get(0).outcome()).isInstanceOfSatisfying(Outcome.Failed.class,
                failed -> assertThat(failed.detail()).contains("update-mime-database"));
        assertThat(results.get(1)).isEqualTo(new TypeResult(".java", "text/x-java", new Outcome.Done()));
        assertThat(tools.changes()).containsExactly("update-mime-database " + data().resolve("mime"),
                "xdg-mime default " + JASPER + " text/x-java");
    }

    @Test void aFailedDefaultOrAnotherAnswerFails() {
        FakeTools tools = new FakeTools();
        tools.failing.add("xdg-mime default " + JASPER + " text/x-java");
        tools.query = type -> type.equals("text/x-python3") ? "other.desktop" : tools.defaults.getOrDefault(type, "");
        Map<String, List<String>> types = new LinkedHashMap<>();
        types.put(".java", List.of("text/x-java"));
        types.put(".py", List.of("text/x-python3", "text/x-python"));
        List<TypeResult> results = tools.backend().claim(types);
        assertThat(results.get(0).outcome()).isInstanceOfSatisfying(Outcome.Failed.class,
                failed -> assertThat(failed.detail()).contains("xdg-mime default"));
        assertThat(results.get(1).outcome()).isInstanceOfSatisfying(Outcome.Failed.class,
                failed -> assertThat(failed.detail()).contains("other.desktop"));
        assertThat(results.get(2)).isEqualTo(new TypeResult(".py", "text/x-python", new Outcome.Done()));
    }

    @Test void restoringAnotherAppSetsItBack() throws IOException {
        write(system().resolve("applications/code.desktop"), "[Desktop Entry]\nName=Code\n");
        FakeTools tools = new FakeTools();
        tools.defaults.put("text/x-java", JASPER);
        tools.defaults.put("text/x-python", JASPER);
        Map<String, Map<String, Handler>> previous = new LinkedHashMap<>();
        previous.put(".java", Map.of("text/x-java", new Handler.Other("code.desktop", "Code")));
        previous.put(".py", Map.of("text/x-python", new Handler.Other("gone.desktop", "Gone")));
        assertThat(tools.backend().restore(previous)).containsExactly(
                new TypeResult(".java", "text/x-java", new Outcome.Done()),
                new TypeResult(".py", "text/x-python", new Outcome.PreviousGone()));
        assertThat(tools.changes()).containsExactly("xdg-mime default code.desktop text/x-java");
        assertThat(tools.defaults).containsEntry("text/x-python", JASPER);
    }

    @Test void aRestoreThatStillNamesJasperFails() throws IOException {
        write(system().resolve("applications/code.desktop"), "[Desktop Entry]\nName=Code\n");
        FakeTools tools = new FakeTools();
        tools.query = type -> JASPER;
        List<TypeResult> results = tools.backend().restore(Map.of(".java",
                Map.of("text/x-java", new Handler.Other("code.desktop", "Code"))));
        assertThat(results).singleElement().satisfies(result -> assertThat(result.outcome()).isInstanceOf(Outcome.Failed.class));
    }

    @Test void restoringNoAppRemovesJaspersDefaultFromTheUsersListOnly() throws IOException {
        String before = "# mine\r\n[Added Associations]\r\ntext/x-java=" + JASPER + ";\r\n\r\n[Default Applications]\r\n"
                + "text/x-java=" + JASPER + ";\r\ntext/plain=gedit.desktop;\r\n";
        write(mimeapps(), before);
        Path systemList = system().resolve("applications/mimeapps.list");
        write(systemList, "[Default Applications]\ntext/x-java=" + JASPER + ";\n");
        FakeTools tools = new FakeTools();
        tools.query = type -> {
            try {
                return Files.readString(mimeapps()).contains("[Default Applications]\r\ntext/x-java=") ? JASPER : "";
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        };
        assertThat(tools.backend().restore(Map.of(".java", Map.of("text/x-java", new Handler.None()))))
                .containsExactly(new TypeResult(".java", "text/x-java", new Outcome.Done()));
        assertThat(Files.readString(mimeapps())).isEqualTo(before.replace(
                "[Default Applications]\r\ntext/x-java=" + JASPER + ";\r\n", "[Default Applications]\r\n"));
        assertThat(Files.readString(systemList)).isEqualTo("[Default Applications]\ntext/x-java=" + JASPER + ";\n");
        assertThat(tools.changes()).isEmpty();
    }

    @Test void restoringNoAppWithNoListChangesNothing() {
        FakeTools tools = new FakeTools();
        assertThat(tools.backend().restore(Map.of(".java", Map.of("text/x-java", new Handler.None()))))
                .containsExactly(new TypeResult(".java", "text/x-java", new Outcome.Done()));
        assertThat(mimeapps()).doesNotExist();
    }

    @Test void theListIsEditedThroughItsLink() throws IOException {
        Path real = home.resolve("dotfiles/mimeapps.list");
        write(real, "[Default Applications]\ntext/x-java=" + JASPER + ";\n");
        Files.createDirectories(config());
        Files.createSymbolicLink(mimeapps(), real);
        new FakeTools().backend().restore(Map.of(".java", Map.of("text/x-java", new Handler.None())));
        assertThat(Files.isSymbolicLink(mimeapps())).isTrue();
        assertThat(Files.readString(real)).isEqualTo("[Default Applications]\n");
    }

    @Test void forgettingTheLastCustomExtensionDeletesThePackage() throws IOException {
        write(packageFile(), LinuxMime.packageXml(new java.util.TreeSet<>(Set.of("proto", "scad"))));
        FakeTools tools = new FakeTools();
        LinuxFileAssociations backend = tools.backend();
        backend.forget(".scad");
        assertThat(LinuxMime.declaredExtensions(Files.readString(packageFile()))).containsExactly("proto");
        backend.forget(".proto");
        assertThat(packageFile()).doesNotExist();
        assertThat(tools.changes()).containsExactly("update-mime-database " + data().resolve("mime"),
                "update-mime-database " + data().resolve("mime"));
        assertThat(tools.timeouts).containsOnly(Duration.ofSeconds(20));
    }

    @Test void forgettingAnUndeclaredExtensionRunsNothing() throws IOException {
        FakeTools tools = new FakeTools();
        tools.backend().forget(".proto");
        write(packageFile(), LinuxMime.packageXml(new java.util.TreeSet<>(Set.of("scad"))));
        tools.backend().forget(".proto");
        assertThat(tools.commands).isEmpty();
        assertThat(packageFile()).exists();
    }

    @Test void theSystemToolsSearchThePath() {
        assertThat(Tools.system().onPath().test("sh")).isTrue();
        assertThat(Tools.system().onPath().test("jasper-no-such-tool")).isFalse();
        assertThat(Tools.system().onPath().test("../sh")).isFalse();
    }

    /** The catalogue's Linux types against the real shared-mime-info database. */
    @Test @EnabledOnOs(OS.LINUX) void theCatalogueMatchesTheSystemDatabase() throws IOException {
        Path globs2 = Path.of("/usr/share/mime/globs2");
        Assumptions.assumeTrue(Files.isRegularFile(globs2), "no shared-mime-info database");
        List<String> texts = List.of(Files.readString(globs2));
        FileTypeCatalog catalog = FileTypeCatalog.builtIn();
        List<String> mismatches = new java.util.ArrayList<>();
        for (String extension : catalog.extensions()) {
            List<String> linux = catalog.linuxTypes(extension);
            if (linux.isEmpty()) continue;
            Optional<String> type = LinuxMime.globType(texts, extension.substring(1));
            if (type.isEmpty() || !linux.contains(type.get())) {
                mismatches.add(extension + ": the database says " + type.orElse("nothing") + ", the catalogue " + linux);
            }
        }
        mismatches.forEach(System.out::println);
        assertThat(mismatches).isEmpty();
    }
}
