package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.platform.FileAssociations.Claimability;
import dev.jasper.app.platform.FileAssociations.Handler;
import dev.jasper.app.platform.FileAssociations.Outcome;
import dev.jasper.app.platform.FileAssociations.Refusal;
import dev.jasper.app.platform.FileAssociations.TypeResult;
import dev.jasper.app.platform.FileAssociations.Unavailable;
import dev.jasper.app.platform.WindowsFileAssociations.Registry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The Windows backend over an in-memory registry and a recording Settings opener; nothing touches the real registry. */
class WindowsFileAssociationsTest {
    private static final String COMMAND = "Software\\Classes\\JasperFile\\shell\\open\\command";
    private static final String FILE_EXTS = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\";
    private static final String CAPABILITIES = "Software\\Jasper\\Capabilities\\FileAssociations";
    private static final URI DEEP_LINK = URI.create("ms-settings:defaultapps?registeredAppUser=Jasper");
    private static final URI DEFAULT_APPS = URI.create("ms-settings:defaultapps");
    private static final FileTypeCatalog CATALOG = FileTypeCatalog.parse(".js\tcom.netscape.javascript-source\ttext/javascript\n");

    /** An in-memory {@code HKEY_CURRENT_USER}: values keyed by {@code key + "|" + name}, with every write recorded. */
    private static final class FakeRegistry {
        final Map<String, String> values = new LinkedHashMap<>();
        final Set<String> keys = new java.util.HashSet<>();
        final Map<String, String> friendlyNames = new LinkedHashMap<>();
        final List<String> writes = new java.util.ArrayList<>();
        final List<URI> opened = new java.util.ArrayList<>();
        final Set<URI> failingLinks = new java.util.HashSet<>();
        final Set<String> failingWrites = new java.util.HashSet<>();
        int notifications;

        Registry registry() {
            return new Registry((key, name) -> Optional.ofNullable(values.get(key + "|" + name)), keys::contains,
                    (key, name, value) -> {
                        if (failingWrites.contains(key)) throw new IOException("RegSetKeyValueW failed with 5");
                        writes.add(key + "|" + name + "=" + value);
                        values.put(key + "|" + name, value);
                        keys.add(key);
                    }, () -> notifications++, extension -> Optional.ofNullable(friendlyNames.get(extension)));
        }

        Map<String, String> environment = Map.of();

        WindowsFileAssociations backend() {
            return new WindowsFileAssociations(CATALOG, environment, registry(), uri -> {
                opened.add(uri);
                if (failingLinks.contains(uri)) throw new IllegalStateException("no handler for " + uri);
            });
        }
    }

    private final FakeRegistry system = new FakeRegistry();

    @AfterEach void neverWritesTheUsersChoice() {
        assertThat(system.writes).as("Windows protects UserChoice with a hash; Jasper never writes it")
                .noneMatch(write -> write.toLowerCase(Locale.ROOT).contains("userchoice"));
    }

    @Test void availableOnlyWhenTheInstallerRegisteredTheProgIdsCommand() {
        assertThat(system.backend().unavailable()).contains(Unavailable.NOT_INSTALLED);
        system.keys.add(COMMAND);
        assertThat(system.backend().unavailable()).isEmpty();
    }

    @Test void everyExtensionIsClaimableAsItself() {
        assertThat(system.backend().claimability(".java")).isEqualTo(new Claimability.Claimable(List.of(".java"), false));
    }

    @Test void executableAndShellHandledExtensionsAreRefused() {
        WindowsFileAssociations backend = system.backend();
        for (String extension : List.of(".exe", ".com", ".bat", ".cmd", ".vbs", ".vbe", ".jse", ".wsf", ".wsh", ".msc",
                ".lnk", ".url", ".dll", ".msi", ".sys", ".scr", ".cpl", ".ps1", ".reg", ".hta", ".pif")) {
            assertThat(backend.claimability(extension)).as(extension)
                    .isEqualTo(new Claimability.NotClaimable(Refusal.NOT_TEXT));
        }
        assertThat(backend.claimability(".js")).as("a catalogue extension is never refused that way")
                .isEqualTo(new Claimability.Claimable(List.of(".js"), false));

        system.environment = Map.of("PathExt", ".EXE;.Py;;");
        backend = system.backend();
        assertThat(backend.claimability(".py")).as("PATHEXT from the environment, any case")
                .isEqualTo(new Claimability.NotClaimable(Refusal.NOT_TEXT));
        assertThat(backend.claimability(".bat")).as("the environment's PATHEXT replaces the default")
                .isEqualTo(new Claimability.Claimable(List.of(".bat"), false));
        assertThat(backend.claimability(".lnk")).isEqualTo(new Claimability.NotClaimable(Refusal.NOT_TEXT));
    }

    @Test void theUsersChoiceDecidesTheHandler() throws IOException {
        WindowsFileAssociations backend = system.backend();
        assertThat(backend.handler(".txt")).as("no choice and no application").isEqualTo(new Handler.None());
        system.friendlyNames.put(".txt", "Notepad");
        assertThat(backend.handler(".txt")).as("no choice, but Windows names an application")
                .isEqualTo(new Handler.Other("", "Notepad"));
        system.values.put(FILE_EXTS + ".java\\UserChoice|ProgId", "JasperFile");
        assertThat(backend.handler(".java")).isEqualTo(new Handler.Jasper());
        system.values.put(FILE_EXTS + ".kt\\UserChoice|ProgId", "jasperfile");
        assertThat(backend.handler(".kt")).as("ProgIds ignore case").isEqualTo(new Handler.Jasper());
        system.values.put(FILE_EXTS + ".md\\UserChoice|ProgId", "applications\\JASPER.EXE");
        assertThat(backend.handler(".md")).as("jasper.exe picked in Open with").isEqualTo(new Handler.Jasper());
        system.values.put(FILE_EXTS + ".py\\UserChoice|ProgId", "VSCode.java");
        assertThat(backend.handler(".py")).as("no friendly name").isEqualTo(new Handler.Other("VSCode.java", "VSCode.java"));
        system.friendlyNames.put(".py", "Visual Studio Code");
        assertThat(backend.handler(".py")).isEqualTo(new Handler.Other("VSCode.java", "Visual Studio Code"));
        system.values.put(FILE_EXTS + ".py\\UserChoiceLatest|ProgId", "JasperFile");
        assertThat(backend.handler(".py")).as("UserChoiceLatest decides first").isEqualTo(new Handler.Jasper());
    }

    @Test void aFailedReadIsAnIOException() {
        var backend = new WindowsFileAssociations(CATALOG, Map.of(), new Registry((key, name) -> {
            throw new UncheckedIOException(new IOException("RegGetValueW failed with 5"));
        }, key -> true, (key, name, value) -> { }, () -> { }, extension -> Optional.empty()), uri -> { });
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> backend.handler(".java"))
                .isInstanceOf(IOException.class).hasMessageContaining("failed with 5");
    }

    @Test void jasperAsTheChoiceRelistsTheExtensionAnUpgradeRemoved() throws IOException {
        WindowsFileAssociations backend = system.backend();
        system.values.put(FILE_EXTS + ".java\\UserChoice|ProgId", "JasperFile");
        assertThat(backend.handler(".java")).isEqualTo(new Handler.Jasper());
        assertThat(system.writes).containsExactly(CAPABILITIES + "|.java=JasperFile");
        backend.handler(".java");
        assertThat(system.writes).as("written only when missing").hasSize(1);

        system.values.put(FILE_EXTS + ".py\\UserChoice|ProgId", "VSCode.java");
        backend.handler(".py");
        assertThat(system.writes).as("another app's choice lists nothing").hasSize(1);

        system.values.put(FILE_EXTS + ".kt\\UserChoice|ProgId", "JasperFile");
        system.failingWrites.add(CAPABILITIES);
        assertThat(backend.handler(".kt")).as("a failed re-listing is only logged").isEqualTo(new Handler.Jasper());
    }

    @Test void aClaimListsJasperForEachExtensionThenOpensSettingsOnce() {
        WindowsFileAssociations backend = system.backend();
        Map<String, List<String>> types = new LinkedHashMap<>();
        types.put(".java", List.of(".java"));
        types.put(".py", List.of(".py"));
        assertThat(backend.claim(types)).containsExactly(new TypeResult(".java", ".java", new Outcome.InSettings()),
                new TypeResult(".py", ".py", new Outcome.InSettings()));
        assertThat(system.writes).containsExactly(
                "Software\\Classes\\.java\\OpenWithProgids|JasperFile=",
                CAPABILITIES + "|.java=JasperFile",
                "Software\\Classes\\.py\\OpenWithProgids|JasperFile=",
                CAPABILITIES + "|.py=JasperFile");
        assertThat(system.notifications).isEqualTo(1);
        assertThat(system.opened).containsExactly(DEEP_LINK);

        backend.claim(types);
        assertThat(system.writes).as("a repeated claim writes the same values again").hasSize(8);
        assertThat(system.values).hasSize(4);
    }

    @Test void aDeepLinkThatFailsFallsBackToDefaultApps() {
        system.failingLinks.add(DEEP_LINK);
        assertThat(system.backend().claim(Map.of(".java", List.of(".java"))))
                .containsExactly(new TypeResult(".java", ".java", new Outcome.InSettings()));
        assertThat(system.opened).containsExactly(DEEP_LINK, DEFAULT_APPS);
    }

    @Test void settingsThatCannotOpenFailEveryType() {
        system.failingLinks.add(DEEP_LINK);
        system.failingLinks.add(DEFAULT_APPS);
        Map<String, List<String>> types = new LinkedHashMap<>();
        types.put(".java", List.of(".java"));
        types.put(".py", List.of(".py"));
        List<TypeResult> results = system.backend().claim(types);
        assertThat(results).hasSize(2).allSatisfy(result -> assertThat(result.outcome()).isInstanceOf(Outcome.Failed.class));
        assertThat(((Outcome.Failed) results.getFirst().outcome()).detail()).contains("ms-settings:defaultapps");
    }

    @Test void anExtensionThatCannotBeWrittenFailsAlone() {
        system.failingWrites.add("Software\\Classes\\.py\\OpenWithProgids");
        Map<String, List<String>> types = new LinkedHashMap<>();
        types.put(".java", List.of(".java"));
        types.put(".py", List.of(".py"));
        assertThat(system.backend().claim(types)).containsExactly(
                new TypeResult(".java", ".java", new Outcome.InSettings()),
                new TypeResult(".py", ".py", new Outcome.Failed("RegSetKeyValueW failed with 5")));
        assertThat(system.opened).containsExactly(DEEP_LINK);
    }

    @Test void restoringHandsTheChoiceToSettings() {
        WindowsFileAssociations backend = system.backend();
        assertThat(backend.restore(Map.of(".java", Map.of(".java", new Handler.None()))))
                .containsExactly(new TypeResult(".java", ".java", new Outcome.NoPrevious()));
        assertThat(system.opened).as("nothing to hand back").isEmpty();

        Map<String, Map<String, Handler>> previous = new LinkedHashMap<>();
        previous.put(".py", Map.of(".py", new Handler.Other("VSCode.py", "Visual Studio Code")));
        previous.put(".kt", Map.of(".kt", new Handler.Other("IntelliJ.kt", "IntelliJ IDEA")));
        assertThat(backend.restore(previous)).containsExactly(new TypeResult(".py", ".py", new Outcome.InSettings()),
                new TypeResult(".kt", ".kt", new Outcome.InSettings()));
        assertThat(system.opened).containsExactly(DEEP_LINK);
        assertThat(system.writes).isEmpty();
    }

    @Test void forgettingWritesNothing() {
        system.backend().forget(".java");
        assertThat(system.writes).isEmpty();
        assertThat(system.opened).isEmpty();
    }
}
