package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.platform.FileAssociations.Claimability;
import dev.jasper.app.platform.FileAssociations.Handler;
import dev.jasper.app.platform.FileAssociations.Outcome;
import dev.jasper.app.platform.FileAssociations.Refusal;
import dev.jasper.app.platform.FileAssociations.TypeResult;
import dev.jasper.app.platform.FileAssociations.Unavailable;
import dev.jasper.app.platform.MacFileAssociations.MacCalls;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

class MacFileAssociationsTest {
    private static final FileTypeCatalog CATALOG = FileTypeCatalog.parse("""
            .java\tcom.sun.java-source\ttext/x-java
            .kt\tdev.jasper.kotlin-source\ttext/x-kotlin
            .ts\t\tapplication/x-typescript
            import\tdev.jasper.kotlin-source\tpublic.source-code\tkt\tKotlin source code
            """);

    /** A fake Launch Services: who opens each type, the installed apps, and every set call it was given. */
    private static final class FakeSystem {
        boolean loaded = true;
        final Map<String, String[]> handlers = new LinkedHashMap<>();
        final Map<String, String> textTypes = new LinkedHashMap<>();
        final Map<String, String> failures = new LinkedHashMap<>();
        final Set<String> installed = new java.util.HashSet<>(Set.of("dev.jasper.app"));
        final List<String> sets = new java.util.ArrayList<>();
        final List<String> askedExtensions = new java.util.ArrayList<>();

        MacCalls calls() {
            return new MacCalls(() -> loaded, handlers::get, (type, bundleId) -> {
                sets.add(type + "=" + bundleId);
                return failures.get(type);
            }, installed::contains, extension -> {
                askedExtensions.add(extension);
                return textTypes.get(extension);
            });
        }

        MacFileAssociations backend() {
            return new MacFileAssociations(CATALOG, calls());
        }
    }

    @Test void aDevelopmentRunHasNoBridge() {
        FakeSystem system = new FakeSystem();
        assertThat(system.backend().unavailable()).isEmpty();
        system.loaded = false;
        assertThat(system.backend().unavailable()).contains(Unavailable.DEVELOPMENT_RUN);
    }

    @Test void claimabilityAsksTheSystemForTheExtensionsTextType() {
        FakeSystem system = new FakeSystem();
        system.textTypes.put("java", "com.sun.java-source");
        system.textTypes.put("swift", "public.swift-source");
        MacFileAssociations backend = system.backend();
        assertThat(backend.claimability(".java")).isEqualTo(new Claimability.Claimable(List.of("com.sun.java-source"), false));
        // A custom extension the catalogue does not list.
        assertThat(backend.claimability(".swift")).isEqualTo(new Claimability.Claimable(List.of("public.swift-source"), false));
        // No declared text type: dynamic, or not text.
        assertThat(backend.claimability(".kt")).isEqualTo(new Claimability.NotClaimable(Refusal.NO_SYSTEM_TYPE));
        assertThat(backend.claimability(".xyz")).isEqualTo(new Claimability.NotClaimable(Refusal.NO_SYSTEM_TYPE));
        assertThat(system.askedExtensions).containsExactly("java", "swift", "kt", "xyz");
    }

    @Test void anEmptyMacColumnIsNotText() {
        FakeSystem system = new FakeSystem();
        system.textTypes.put("ts", "public.mpeg-2-transport-stream");
        assertThat(system.backend().claimability(".ts")).isEqualTo(new Claimability.NotClaimable(Refusal.NOT_TEXT));
        assertThat(system.askedExtensions).isEmpty();
    }

    @Test void theHandlerIsJasperAnotherAppOrNone() {
        FakeSystem system = new FakeSystem();
        system.handlers.put("com.sun.java-source", new String[] {"dev.jasper.app", "Jasper"});
        system.handlers.put("public.json", new String[] {"com.apple.TextEdit", "TextEdit"});
        system.handlers.put("public.yaml", new String[] {"com.example.unnamed", ""});
        MacFileAssociations backend = system.backend();
        assertThat(backend.handler("com.sun.java-source")).isEqualTo(new Handler.Jasper());
        assertThat(backend.handler("public.json")).isEqualTo(new Handler.Other("com.apple.TextEdit", "TextEdit"));
        assertThat(backend.handler("public.yaml")).isEqualTo(new Handler.Other("com.example.unnamed", "com.example.unnamed"));
        assertThat(backend.handler("public.xml")).isEqualTo(new Handler.None());
    }

    @Test void aClaimSetsJasperForEachTypeAndReportsTheSystemsMessage() {
        FakeSystem system = new FakeSystem();
        system.failures.put("public.json", "The operation couldn't be completed.");
        Map<String, List<String>> types = new LinkedHashMap<>();
        types.put(".java", List.of("com.sun.java-source"));
        types.put(".json", List.of("public.json"));
        assertThat(system.backend().claim(types)).containsExactly(
                new TypeResult(".java", "com.sun.java-source", new Outcome.Done()),
                new TypeResult(".json", "public.json", new Outcome.Failed("The operation couldn't be completed.")));
        assertThat(system.sets).containsExactly("com.sun.java-source=dev.jasper.app", "public.json=dev.jasper.app");
    }

    @Test void aRestoreSetsThePreviousAppBack() {
        FakeSystem system = new FakeSystem();
        system.installed.add("com.apple.TextEdit");
        system.failures.put("public.yaml", "refused");
        Map<String, Map<String, Handler>> previous = new LinkedHashMap<>();
        previous.put(".json", Map.of("public.json", new Handler.Other("com.apple.TextEdit", "TextEdit")));
        previous.put(".yaml", Map.of("public.yaml", new Handler.Other("com.apple.TextEdit", "TextEdit")));
        assertThat(system.backend().restore(previous)).containsExactly(
                new TypeResult(".json", "public.json", new Outcome.Done()),
                new TypeResult(".yaml", "public.yaml", new Outcome.Failed("refused")));
        assertThat(system.sets).containsExactly("public.json=com.apple.TextEdit", "public.yaml=com.apple.TextEdit");
    }

    @Test void aRestoreWithNoPreviousAppSetsNothing() {
        FakeSystem system = new FakeSystem();
        assertThat(system.backend().restore(Map.of(".xml", Map.of("public.xml", new Handler.None()))))
                .containsExactly(new TypeResult(".xml", "public.xml", new Outcome.NoPrevious()));
        assertThat(system.sets).isEmpty();
    }

    @Test void aRestoreWhoseAppIsGoneKeepsTheEntry() {
        FakeSystem system = new FakeSystem();
        assertThat(system.backend().restore(Map.of(".json",
                Map.of("public.json", new Handler.Other("com.example.uninstalled", "Gone")))))
                .containsExactly(new TypeResult(".json", "public.json", new Outcome.PreviousGone()));
        assertThat(system.sets).as("the type stays Jasper's").isEmpty();
    }

    @Test void forgettingDeclaresNothingSoChangesNothing() {
        FakeSystem system = new FakeSystem();
        system.backend().forget(".xyz");
        assertThat(system.sets).isEmpty();
        assertThat(system.askedExtensions).isEmpty();
    }

    @Test void outsideAPackagedAppTheBridgeIsNotAsked() {
        // A test JVM is not a packaged macOS app, so MacNative.load() is false and no native method is reached.
        assertThat(MacLaunchServices.contentTypeHandler("public.plain-text")).isNull();
        assertThat(MacLaunchServices.textTypeForExtension("txt")).isNull();
        assertThat(MacLaunchServices.hasApplication("dev.jasper.app")).isFalse();
        assertThat(MacLaunchServices.setContentTypeHandler("public.plain-text", "dev.jasper.app")).isNotNull();
        assertThat(new MacFileAssociations(CATALOG, MacCalls.system()).unavailable())
                .isEqualTo(Optional.of(Unavailable.DEVELOPMENT_RUN));
    }

    @Test
    @EnabledIfSystemProperty(named = "jasper.testNativeLibrary", matches = ".+")
    @SuppressWarnings("restricted")
    void theRealBridgeReadsLaunchServices() {
        // Read-only: no default is set.
        System.load(System.getProperty("jasper.testNativeLibrary"));
        assertThat(MacLaunchServices.contentTypeHandlerOf("dev.jasper.test-no-such-type")).as("no such type").isNull();
        assertThat(MacLaunchServices.textTypeForExtensionOf("swift")).isEqualTo("public.swift-source");
        assertThat(MacLaunchServices.textTypeForExtensionOf("jasper-no-such-ext")).isNull();
        assertThat(MacLaunchServices.hasApplicationOf("dev.jasper.test-no-such-app")).isFalse();
    }
}
