package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.jasper.app.platform.FileAssociations.Claimability;
import dev.jasper.app.platform.FileAssociations.Handler;
import dev.jasper.app.platform.FileAssociations.Outcome;
import dev.jasper.app.platform.FileAssociations.Refusal;
import dev.jasper.app.platform.FileAssociations.TypeResult;
import dev.jasper.app.testsupport.MutableClock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileAssociationsTest {
    private static final Handler JASPER = new Handler.Jasper();
    private static final Handler XCODE = new Handler.Other("com.apple.dt.Xcode", "Xcode");

    @TempDir Path home;
    private final FakeBackend backend = new FakeBackend();
    private PreviousHandlers store;

    private FileAssociations withBackend() {
        store = new PreviousHandlers(home.resolve("file-associations.toml"));
        return new FileAssociations(Runnable::run, backend, store);
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test void extensionsAreNormalisedOrRefused() {
        assertThat(FileAssociations.normalise(".JAVA")).contains(".java");
        assertThat(FileAssociations.normalise("java")).contains(".java");
        for (String bad : List.of(".tar.gz", "../x", ".", "", ".abcdefghijklmnopq", ".a b", ".é")) {
            assertThat(FileAssociations.normalise(bad)).as(bad).isEmpty();
        }
        var associations = withBackend();
        assertThat(associations.claimability(".html").join())
                .isEqualTo(new Claimability.NotClaimable(Refusal.BROWSER_OWNED));
        assertThat(associations.claimability("../x").join())
                .isEqualTo(new Claimability.NotClaimable(Refusal.INVALID));
        assertThat(backend.calls).as("nothing odd reaches the backend").isEmpty();
    }

    @Test void aClaimSavesTheFirstPreviousHandlerOnlyOnce() {
        backend.claimable(".java", "com.sun.java-source");
        backend.claimable(".txt", "public.plain-text");
        backend.handlers.put("com.sun.java-source", XCODE);
        backend.handlers.put("public.plain-text", JASPER);
        var associations = withBackend();
        var first = associations.claim(List.of(".java", ".txt")).join();
        assertThat(first).extracting(TypeResult::outcome).containsOnly(new Outcome.Done());
        assertThat(store.get("com.sun.java-source")).contains(XCODE);
        assertThat(store.get("public.plain-text")).as("already Jasper's: nothing saved").isEmpty();
        assertThat(backend.handlers.get("com.sun.java-source")).isEqualTo(JASPER);
        associations.claim(List.of(".java")).join();
        assertThat(store.get("com.sun.java-source")).as("the earliest wins").contains(XCODE);
    }

    @Test void aPartlyRefusedClaimKeepsWhatWorkedAndSavesOnlyWhatChanged() {
        backend.claimable(".cpp", "public.c-plus-plus-source", "public.c-plus-plus-header");
        backend.handlers.put("public.c-plus-plus-source", XCODE);
        backend.handlers.put("public.c-plus-plus-header", XCODE);
        backend.failing.add("public.c-plus-plus-header");
        var associations = withBackend();
        var results = associations.claim(List.of(".cpp")).join();
        assertThat(results).containsExactly(
                new TypeResult(".cpp", "public.c-plus-plus-source", new Outcome.Done()),
                new TypeResult(".cpp", "public.c-plus-plus-header", new Outcome.Failed("boom")));
        assertThat(store.get("public.c-plus-plus-source")).contains(XCODE);
        assertThat(store.get("public.c-plus-plus-header")).isEmpty();
        assertThat(backend.calls).noneMatch(c -> c.startsWith("restore"));
    }

    @Test void restoreHandsBackAndForgetsTheEntryOnlyWhenDone() {
        backend.claimable(".a", "t.a");
        backend.claimable(".b", "t.b");
        backend.claimable(".c", "t.c");
        backend.claimable(".d", "t.d");
        var associations = withBackend();
        store.put("t.a", XCODE);
        store.put("t.b", XCODE);
        store.put("t.c", new Handler.None());
        backend.restoreOutcomes.put("t.b", new Outcome.NoPrevious());
        backend.restoreOutcomes.put("t.c", new Outcome.Failed("denied"));
        var results = associations.restore(List.of(".a", ".b", ".c", ".d")).join();
        assertThat(results).extracting(TypeResult::type).containsExactly("t.a", "t.b", "t.c");
        assertThat(store.get("t.a")).as("Done deletes").isEmpty();
        assertThat(store.get("t.b")).as("NoPrevious keeps").isPresent();
        assertThat(store.get("t.c")).as("Failed keeps").isPresent();
        assertThat(backend.restored.get(".c")).as("a saved None reaches the backend")
                .isEqualTo(Map.of("t.c", new Handler.None()));
    }

    @Test void aRestoreWhoseAppIsGoneKeepsTheEntry() {
        backend.claimable(".a", "t.a");
        var associations = withBackend();
        store.put("t.a", XCODE);
        backend.restoreOutcomes.put("t.a", new Outcome.PreviousGone());
        assertThat(associations.restore(List.of(".a")).join())
                .containsExactly(new TypeResult(".a", "t.a", new Outcome.PreviousGone()));
        assertThat(store.get("t.a")).contains(XCODE);
    }

    @Test void aCorruptOrUnwritableStoreNeverBlocksAClaim(@TempDir Path folder) throws Exception {
        Path file = Files.writeString(folder.resolve("file-associations.toml"), "[[[not toml");
        var corrupt = new PreviousHandlers(file);
        assertThat(corrupt.get("x")).isEmpty();

        Path locked = Files.createDirectory(folder.resolve("locked"));
        assumeTrue(locked.toFile().setWritable(false) && !Files.isWritable(locked), "folder permissions are not enforced");
        try {
            var unwritable = new PreviousHandlers(locked.resolve("file-associations.toml"));
            backend.claimable(".java", "com.sun.java-source");
            backend.handlers.put("com.sun.java-source", XCODE);
            var associations = new FileAssociations(Runnable::run, backend, unwritable);
            unwritable.put("x", XCODE);
            assertThat(associations.claim(List.of(".java")).join())
                    .containsExactly(new TypeResult(".java", "com.sun.java-source", new Outcome.Done()));
        } finally {
            locked.toFile().setWritable(true);
        }
    }

    @Test void thePreviousHandlerIsSavedBeforeTheBackendClaims() {
        backend.claimable(".java", "com.sun.java-source");
        backend.handlers.put("com.sun.java-source", XCODE);
        var associations = withBackend();
        backend.beforeClaim = () -> backend.seenInStore = store.get("com.sun.java-source");
        associations.claim(List.of(".java")).join();
        assertThat(backend.seenInStore).contains(XCODE);
    }

    @Test void aFailedClaimRemovesOnlyTheEntryItCreated() {
        backend.claimable(".a", "t.a");
        backend.claimable(".b", "t.b");
        backend.handlers.put("t.a", XCODE);
        backend.handlers.put("t.b", new Handler.Other("other", "Other"));
        backend.failing.addAll(List.of("t.a", "t.b"));
        var associations = withBackend();
        store.put("t.b", XCODE);
        associations.claim(List.of(".a", ".b")).join();
        assertThat(store.get("t.a")).isEmpty();
        assertThat(store.get("t.b")).as("saved earlier, so kept").contains(XCODE);
    }

    @Test void refusedExtensionsGetARefusedOutcomeInClaimAndRestore() {
        backend.claimable(".ok", "t.ok");
        var associations = withBackend();
        var expected = List.of(
                new TypeResult(".html", "", new Outcome.Refused(Refusal.BROWSER_OWNED)),
                new TypeResult("../x", "", new Outcome.Refused(Refusal.INVALID)),
                new TypeResult(".nope", "", new Outcome.Refused(Refusal.NO_SYSTEM_TYPE)));
        assertThat(associations.claim(List.of(".html", "../x", ".nope")).join()).isEqualTo(expected);
        assertThat(associations.restore(List.of(".html", "../x", ".nope")).join()).isEqualTo(expected);
    }

    @Test void claimabilityAndUnavailableTakeTheBackendLock() throws Exception {
        var inside = new java.util.concurrent.atomic.AtomicBoolean();
        var associations = withBackend();
        backend.claimable(".a", "t.a");
        Object lock = lockOf(associations);
        backend.onBackendCall = () -> inside.set(Thread.holdsLock(lock));
        associations.claimability(".a").join();
        assertThat(inside).isTrue();
        inside.set(false);
        associations.unavailable();
        assertThat(inside).isTrue();
    }

    @Test void aClaimLeavesNothingForTheNextRefreshToReport() throws Exception {
        backend.claimable(".a", "t.a");
        backend.handlers.put("t.a", XCODE);
        var clock = new MutableClock();
        store = new PreviousHandlers(home.resolve("file-associations.toml"));
        var associations = new FileAssociations(Runnable::run, backend, store, clock);
        associations.status(List.of(".a")).join();
        associations.claim(List.of(".a")).join();
        var heard = new AtomicInteger();
        flushEdt();
        associations.onChanged(heard::incrementAndGet);
        clock.advance(Duration.ofSeconds(10));
        associations.refresh();
        flushEdt();
        assertThat(heard).hasValue(0);
    }

    private static Object lockOf(FileAssociations associations) throws Exception {
        var field = FileAssociations.class.getDeclaredField("lock");
        field.setAccessible(true);
        return field.get(associations);
    }

    @Test void aBackendFailureBecomesFailedAndTheFuturesNeverFail() {
        backend.claimable(".a", "t.a");
        backend.throwOnClaim = true;
        var associations = withBackend();
        assertThat(associations.claim(List.of(".a")).join())
                .containsExactly(new TypeResult(".a", "t.a", new Outcome.Failed("kaput")));
        backend.throwOnHandler = true;
        var status = associations.status(List.of(".a")).join();
        assertThat(status.getFirst().types().getFirst().handler()).isEqualTo(new Handler.Other("", ""));
    }

    @Test void unsupportedIsAlwaysUnsupported() {
        var associations = FileAssociations.unsupported();
        assertThat(associations.unavailable()).contains(FileAssociations.Unavailable.UNSUPPORTED_SYSTEM);
        assertThat(associations.status(List.of(".java")).join().getFirst().types()).isEmpty();
    }

    @Test void refreshReportsOnlyChangesAndAtMostEveryFiveSeconds() throws Exception {
        backend.claimable(".java", "com.sun.java-source");
        backend.handlers.put("com.sun.java-source", XCODE);
        var clock = new MutableClock();
        store = new PreviousHandlers(home.resolve("file-associations.toml"));
        var associations = new FileAssociations(Runnable::run, backend, store, clock);
        var heard = new AtomicInteger();
        associations.onChanged(heard::incrementAndGet);
        associations.status(List.of(".java")).join();

        associations.refresh();
        flushEdt();
        assertThat(heard).as("same answer").hasValue(0);

        backend.handlers.put("com.sun.java-source", JASPER);
        associations.refresh();
        flushEdt();
        assertThat(heard).as("throttled").hasValue(0);

        clock.advance(Duration.ofSeconds(5));
        associations.refresh();
        flushEdt();
        assertThat(heard).as("changed").hasValue(1);

        clock.advance(Duration.ofSeconds(5));
        associations.refresh();
        flushEdt();
        assertThat(heard).as("unchanged again").hasValue(1);

        backend.handlers.put("com.sun.java-source", XCODE);
        clock.advance(Duration.ofSeconds(-60));
        associations.refresh();
        flushEdt();
        assertThat(heard).as("a clock set back holds nothing back").hasValue(2);
    }

    @Test void aClaimTellsTheListenerOnTheEdt() throws Exception {
        backend.claimable(".a", "t.a");
        var associations = withBackend();
        var onEdt = new java.util.concurrent.atomic.AtomicBoolean();
        associations.onChanged(() -> onEdt.set(SwingUtilities.isEventDispatchThread()));
        associations.claim(List.of(".a")).join();
        flushEdt();
        assertThat(onEdt).isTrue();
    }

    private static final class FakeBackend implements FileAssociations.Backend {
        final Map<String, Handler> handlers = new HashMap<>();
        final Map<String, Claimability> claimabilities = new HashMap<>();
        final Set<String> failing = new HashSet<>();
        final Map<String, Outcome> restoreOutcomes = new HashMap<>();
        final Map<String, Map<String, Handler>> restored = new LinkedHashMap<>();
        final List<String> calls = new ArrayList<>();
        Runnable beforeClaim = () -> { };
        Runnable onBackendCall = () -> { };
        Optional<Handler> seenInStore = Optional.empty();
        boolean throwOnClaim;
        boolean throwOnHandler;

        void claimable(String extension, String... types) {
            claimabilities.put(extension, new Claimability.Claimable(List.of(types), false));
        }

        @Override public Optional<FileAssociations.Unavailable> unavailable() {
            onBackendCall.run();
            return Optional.empty();
        }

        @Override public Claimability claimability(String extension) {
            calls.add("claimability " + extension);
            onBackendCall.run();
            return claimabilities.getOrDefault(extension, new Claimability.NotClaimable(Refusal.NO_SYSTEM_TYPE));
        }

        @Override public Handler handler(String type) throws IOException {
            calls.add("handler " + type);
            if (throwOnHandler) throw new IOException("unreadable");
            return handlers.getOrDefault(type, new Handler.None());
        }

        @Override public List<TypeResult> claim(Map<String, List<String>> typesByExtension) {
            calls.add("claim " + typesByExtension);
            beforeClaim.run();
            if (throwOnClaim) throw new IllegalStateException("kaput");
            List<TypeResult> out = new ArrayList<>();
            typesByExtension.forEach((extension, types) -> types.forEach(type -> {
                if (failing.contains(type)) {
                    out.add(new TypeResult(extension, type, new Outcome.Failed("boom")));
                } else {
                    handlers.put(type, JASPER);
                    out.add(new TypeResult(extension, type, new Outcome.Done()));
                }
            }));
            return out;
        }

        @Override public List<TypeResult> restore(Map<String, Map<String, Handler>> previous) {
            calls.add("restore " + previous);
            restored.putAll(previous);
            List<TypeResult> out = new ArrayList<>();
            previous.forEach((extension, types) -> types.keySet().forEach(type -> out.add(new TypeResult(
                    extension, type, restoreOutcomes.getOrDefault(type, new Outcome.Done())))));
            return out;
        }

        @Override public void forget(String extension) { calls.add("forget " + extension); }
    }
}
