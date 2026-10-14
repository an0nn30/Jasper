package dev.jasper.app.plugins;

import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.ui.FileEditor;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FileEditorsTest {
    private static final Path NOTES = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().resolve("notes.txt");
    private final Containment containment = new Containment(() -> true);
    private final FileEditors editors = new FileEditors(Runnable::run, () -> true, containment);

    /** An editor that records {@code <name>|<line or ->|<window or ->} and answers {@code answer}. */
    private static FileEditor recording(String name, List<String> calls, boolean answer) {
        return (file, line, window) -> {
            calls.add(name + "|" + (line.isPresent() ? String.valueOf(line.getAsInt()) : "-") + "|" + window.map(Object::toString).orElse("-"));
            return answer;
        };
    }

    private static Optional<WindowOwner> noWindow(UUID id) { return Optional.empty(); }

    /** A window handle that prints as {@code name}. */
    private static WindowOwner handle(String name) {
        return new WindowOwner() { @Override public String toString() { return name; } };
    }

    @Test void oneEditorAtATimeAndOnlyABundledPluginReplacesAnother() {
        editors.bundled(Set.of("dev.jasper.editor", "dev.jasper.other"));
        List<String> calls = new CopyOnWriteArrayList<>();
        assertThat(editors.edit(NOTES, OptionalInt.empty(), Optional.empty())).as("nobody provides one").isFalse();
        var user = editors.register("dev.x.user", recording("user", calls, true), FileEditorsTest::noWindow);
        assertThatIllegalStateException().isThrownBy(() -> editors.register("dev.x.second", recording("second", calls, true), FileEditorsTest::noWindow))
            .withMessage("The editor is already provided by dev.x.user");
        assertThatIllegalStateException().as("the same plugin twice")
            .isThrownBy(() -> editors.register("dev.x.user", recording("user", calls, true), FileEditorsTest::noWindow))
            .withMessage("The editor is already provided by dev.x.user");
        var bundled = editors.register("dev.jasper.editor", recording("bundled", calls, true), FileEditorsTest::noWindow);
        assertThatIllegalStateException().as("a bundled plugin does not replace another bundled one")
            .isThrownBy(() -> editors.register("dev.jasper.other", recording("other", calls, true), FileEditorsTest::noWindow))
            .withMessage("The editor is already provided by dev.jasper.editor");
        user.close();
        assertThat(editors.edit(NOTES, OptionalInt.of(12), Optional.empty())).as("the replaced registration's close leaves the bundled one").isTrue();
        bundled.close();
        assertThat(editors.edit(NOTES, OptionalInt.empty(), Optional.empty())).isFalse();
        assertThat(calls).containsExactly("bundled|12|-");
    }

    @Test void aDecliningOrThrowingEditorGivesFalseAndTheThrowIsRecorded() {
        var answer = new AtomicBoolean(false);
        editors.register("dev.x.a", (file, line, window) -> {
            if (file.getFileName().toString().equals("boom.txt")) throw new IllegalStateException("editor broke");
            return answer.get();
        }, FileEditorsTest::noWindow);
        assertThat(editors.edit(NOTES, OptionalInt.empty(), Optional.empty())).isFalse();
        assertThat(containment.failures("dev.x.a")).isZero();
        assertThat(editors.edit(NOTES.resolveSibling("boom.txt"), OptionalInt.empty(), Optional.empty())).isFalse();
        assertThat(containment.failures("dev.x.a")).isEqualTo(1);
        answer.set(true);
        assertThat(editors.edit(NOTES, OptionalInt.empty(), Optional.empty())).isTrue();
    }

    @Test void aWindowIdBecomesTheRegisteringPluginsOwnHandle() {
        UUID open = UUID.randomUUID();
        List<String> calls = new CopyOnWriteArrayList<>();
        editors.register("dev.x.a", recording("a", calls, true), id -> id.equals(open) ? Optional.of(handle("window")) : Optional.empty());
        editors.edit(NOTES, OptionalInt.of(3), Optional.of(open));
        editors.edit(NOTES, OptionalInt.of(3), Optional.of(UUID.randomUUID()));
        editors.edit(NOTES, OptionalInt.empty(), Optional.empty());
        assertThat(calls).containsExactly("a|3|window", "a|3|-", "a|-|-");
    }

    @Test void aWorkerAsksOnTheUiThreadAndGivesUpAfterItsWait() throws Exception {
        assertThatIllegalStateException().as("the UI thread never waits for itself")
            .isThrownBy(() -> editors.editFromWorker(NOTES, OptionalInt.empty(), Optional::empty));

        var stuck = new FileEditors(task -> { }, () -> false, containment, Duration.ofMillis(50));
        var asked = new AtomicBoolean();
        stuck.register("dev.x.a", (file, line, window) -> { asked.set(true); return true; }, FileEditorsTest::noWindow);
        long began = System.nanoTime();
        assertThat(stuck.editFromWorker(NOTES, OptionalInt.empty(), Optional::empty)).as("no answer in time").isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - began)).isLessThan(Duration.ofSeconds(5));
        Thread.currentThread().interrupt();
        try {
            assertThat(stuck.editFromWorker(NOTES, OptionalInt.empty(), Optional::empty)).as("interrupted").isFalse();
            assertThat(Thread.currentThread().isInterrupted()).as("the interrupt is kept").isTrue();
        } finally {
            Thread.interrupted();
        }
        assertThat(asked).isFalse();

        var hopping = new FileEditors(SwingUtilities::invokeLater, SwingUtilities::isEventDispatchThread,
            new Containment(SwingUtilities::isEventDispatchThread));
        List<String> calls = new CopyOnWriteArrayList<>();
        var windowReadOnUi = new AtomicReference<Boolean>();
        UUID window = UUID.randomUUID();
        SwingUtilities.invokeAndWait(() -> hopping.register("dev.x.a", recording("a", calls, true), id -> Optional.of(handle("window"))));
        assertThat(hopping.editFromWorker(NOTES, OptionalInt.of(9), () -> {
            windowReadOnUi.set(SwingUtilities.isEventDispatchThread());
            return Optional.of(window);
        })).isTrue();
        assertThat(windowReadOnUi.get()).as("the window is read on the UI thread").isTrue();
        assertThat(calls).containsExactly("a|9|window");
    }

    /**
     * Final review M2: quitting and a plugin's drain interrupt a worker in the middle of the hop. It then opens nothing,
     * the desktop included, keeps its interrupt, and the turn it queued asks nothing when it runs late.
     */
    @Test void anInterruptedWorkerOpensNothingKeepsItsInterruptAndItsLateTurnAsksNothing() {
        List<Runnable> queued = new CopyOnWriteArrayList<>();
        var hop = new FileEditors(queued::add, () -> false, containment, Duration.ofSeconds(5));
        var asked = new AtomicBoolean();
        hop.register("dev.x.a", (file, line, window) -> { asked.set(true); return true; }, FileEditorsTest::noWindow);
        List<Path> desktop = new CopyOnWriteArrayList<>();
        Thread.currentThread().interrupt();
        try {
            hop.openFromWorker(NOTES, Optional::empty, desktop::add);
            assertThat(Thread.currentThread().isInterrupted()).as("the interrupt is kept").isTrue();
        } finally {
            Thread.interrupted();
        }
        assertThat(desktop).as("the desktop does not open it").isEmpty();
        assertThat(queued).as("the hop was queued").hasSize(1);
        queued.getFirst().run();
        assertThat(asked).as("the late turn asks nothing").isFalse();
    }

    @Test void aWorkerOpensWithTheDesktopWhenNoEditorOrADecliningOneAnswers() {
        var direct = new FileEditors(Runnable::run, () -> false, containment);
        List<Path> desktop = new CopyOnWriteArrayList<>();
        direct.openFromWorker(NOTES, Optional::empty, desktop::add);
        List<String> calls = new CopyOnWriteArrayList<>();
        var declining = direct.register("dev.x.a", recording("declining", calls, false), FileEditorsTest::noWindow);
        direct.openFromWorker(NOTES, Optional::empty, desktop::add);
        declining.close();
        direct.register("dev.x.b", recording("opening", calls, true), FileEditorsTest::noWindow);
        direct.openFromWorker(NOTES, Optional::empty, desktop::add);
        assertThat(desktop).containsExactly(NOTES, NOTES);
        assertThat(calls).containsExactly("declining|-|-", "opening|-|-");
        assertThatIllegalStateException().as("the UI thread never waits for itself")
            .isThrownBy(() -> editors.openFromWorker(NOTES, Optional::empty, desktop::add));
    }

    @Test void withoutAnEditorAWorkerDoesNotHop() {
        var never = new FileEditors(task -> { throw new AssertionError("nothing to ask"); }, () -> false, containment);
        assertThat(never.editFromWorker(NOTES, OptionalInt.empty(), Optional::empty)).isFalse();
    }
}
