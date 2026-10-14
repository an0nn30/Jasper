package dev.jasper.editor.external;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.terminal.LocalSpec;
import dev.jasper.sdk.terminal.OpenRequest;
import dev.jasper.sdk.terminal.Terminals;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.FileEditor;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * The default editor: what the Default editor settings choose, as the editor Jasper opens files with ({@link #edit},
 * the {@code Platform.editor} hook) and as the {@code EDITOR} of Jasper's shells, and their {@code VISUAL} when it
 * replaces one Jasper was started with ({@link #variables}, the {@code Terminals.environment} contributor). UI thread, except detection's search, which {@link #detect} runs on
 * the background executor before publishing its result on the UI thread.
 *
 * <p>{@link #findSaved} looks for the saved editor at start, before any shell starts, and {@link #detect} looks for all
 * of them in the background.
 */
public final class DefaultEditor {
    /**
     * What the four Default editor settings hold now.
     *
     * @param editor {@code default_editor}: jasper, system, custom or a known editor's id
     * @param customCommand {@code custom_command}
     * @param customInTerminal {@code custom_in_terminal}
     * @param keepEnvironment {@code keep_environment_editor}
     */
    public record Preference(String editor, String customCommand, boolean customInTerminal, boolean keepEnvironment) {
        /** Rejects nulls. */
        public Preference {
            Objects.requireNonNull(editor, "editor");
            Objects.requireNonNull(customCommand, "customCommand");
        }
    }

    /** Starts a program on its own: no input, its output discarded, never waited for. */
    @FunctionalInterface
    public interface Launcher {
        /**
         * @param command the program and its arguments
         * @param program what a failure names: the program's file name, or a custom command's first word; never the
         *     file or the arguments
         * @return whether it started
         */
        boolean start(List<String> command, String program);

        /**
         * The production launcher, over {@link ProcessBuilder}. A program that cannot start is logged and reported as
         * not started; one that starts and later exits with an error is logged and posted in {@code notices}, without
         * waiting for it. Both name only {@code program}.
         *
         * @param log where a failure goes
         * @param notices the Editor's notification group, for a program that exits with an error
         * @return the launcher
         */
        static Launcher detached(System.Logger log, NotificationGroup notices) {
            Objects.requireNonNull(log, "log");
            Objects.requireNonNull(notices, "notices");
            return (command, program) -> {
                Process process;
                try {
                    process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                } catch (IOException | RuntimeException failure) {
                    log.log(System.Logger.Level.WARNING, "Could not start " + program, failure);
                    return false;
                }
                try {
                    process.getOutputStream().close();
                } catch (IOException ignored) {
                    // It started; its input was never going to be used.
                }
                watch(process.onExit().thenApply(Process::exitValue), program, log, notices);
                return true;
            };
        }
    }

    /** The title of the notice a detached program that exits with an error posts. */
    static final String FAILED = "Could not open a file in your default editor";

    /**
     * Once a detached program exits, a non-zero status is logged and posted in {@code notices}, naming only
     * {@code program}; a clean exit says nothing. Returns at once: the check runs on the thread that ends {@code exit}.
     *
     * @param exit the program's exit status, when it exits
     * @param program the program's name for the notice
     * @param log where the failure is logged
     * @param notices where it is posted
     */
    static void watch(CompletionStage<Integer> exit, String program, System.Logger log, NotificationGroup notices) {
        exit.thenAccept(status -> {
            if (status == 0) return;
            String detail = program + " exited with status " + status + ".";
            log.log(System.Logger.Level.WARNING, "The default editor: " + detail);
            try {
                notices.error(FAILED, detail);
            } catch (IllegalStateException closed) {
                // The Editor stopped while the program ran; the log line stays.
            }
        });
    }

    private final Supplier<Preference> preference;
    private final Supplier<Detection.Probe> probe;
    private final Executor background, ui;
    private final Terminals terminals;
    private final FileEditor jasper;
    private final Launcher launcher;
    private final Optional<Path> client;
    private final String osName;
    private final System.Logger log;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile List<Detected> detected = List.of();

    /**
     * @param preference the settings, read at each use
     * @param probe what detection looks at, read at each detection; its home is also the one whose {@code .local/bin}
     *     a custom command adds to {@code PATH}
     * @param background where detection runs
     * @param ui the UI thread, where detection's result is published
     * @param terminals opens the tabs terminal editors run in
     * @param jasper the Jasper Editor's own file editor
     * @param launcher starts app editors and custom commands that run on their own; it reports a failure itself
     * @param client the {@code jasper} command, for the Jasper Editor's {@code EDITOR}
     * @param osName the {@code os.name} property: quoting, and how a custom command runs
     * @param log where a failed detection goes
     */
    public DefaultEditor(Supplier<Preference> preference, Supplier<Detection.Probe> probe, Executor background, Executor ui,
                         Terminals terminals, FileEditor jasper, Launcher launcher, Optional<Path> client, String osName, System.Logger log) {
        this.preference = Objects.requireNonNull(preference, "preference");
        this.probe = Objects.requireNonNull(probe, "probe");
        this.background = Objects.requireNonNull(background, "background");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.terminals = Objects.requireNonNull(terminals, "terminals");
        this.jasper = Objects.requireNonNull(jasper, "jasper");
        this.launcher = Objects.requireNonNull(launcher, "launcher");
        this.client = Objects.requireNonNull(client, "client");
        this.osName = Objects.requireNonNull(osName, "osName");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Looks for installed editors again on the background executor; {@link #onDetected} listeners hear the result on the UI thread. */
    public void detect() {
        background.execute(() -> {
            List<Detected> found;
            try {
                found = Detection.detect(probe.get());
            } catch (RuntimeException failure) {
                log.log(System.Logger.Level.WARNING, "Could not look for installed editors", failure);
                return;
            }
            ui.execute(() -> {
                detected = found;
                for (Runnable listener : listeners) listener.run();
            });
        });
    }

    /**
     * Looks now, on the calling (UI) thread, for the one installed editor the settings name, when nothing has been
     * detected yet, so the first shell of a launch already gets its EDITOR; {@link #detect} still looks for every editor.
     * A few file checks; nothing when the choice is not a known editor's id. It does not call the {@link #onDetected}
     * listeners: the page is not open yet at start.
     */
    public void findSaved() {
        if (!detected.isEmpty()) return;
        Optional<KnownEditor> editor = KnownEditors.byId(preference.get().editor());
        if (editor.isEmpty()) return;
        Optional<Detected> found;
        try {
            found = Detection.find(probe.get(), editor.get());
        } catch (RuntimeException failure) {
            log.log(System.Logger.Level.WARNING, "Could not look for the saved editor", failure);
            return;
        }
        found.ifPresent(saved -> detected = List.of(saved));
    }

    /**
     * @return what the last detection found, in the known editors' order; before the first, the saved editor alone
     *     when {@link #findSaved} found it, else empty
     */
    public List<Detected> detected() { return detected; }

    /**
     * @param listener runs on the UI thread after each detection
     * @return removes it
     */
    public Subscription onDetected(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        Runnable entry = listener::run;
        listeners.add(entry);
        return () -> listeners.remove(entry);
    }

    /**
     * @return what the settings choose now; a saved id detection did not find, and a custom choice without a command,
     *     are the system's default app
     */
    public Choice choice() {
        Preference now = preference.get();
        return switch (now.editor()) {
            case "jasper" -> new Choice.Jasper(client);
            case "system" -> new Choice.System();
            case "custom" -> now.customCommand().isBlank() ? new Choice.System() : new Choice.Custom(now.customCommand(), now.customInTerminal());
            default -> detected.stream().filter(found -> found.editor().id().equals(now.editor())).findFirst()
                .<Choice>map(Choice.Installed::new).orElseGet(Choice.System::new);
        };
    }

    /**
     * The {@code Terminals.environment} contributor. {@code VISUAL} is set only to replace one Jasper was started with
     * (with {@code keep_environment_editor} off), so an {@code EDITOR} or {@code VISUAL} exported in a shell startup
     * file still wins: programs that prefer {@code VISUAL}, such as git, fall back to {@code EDITOR}.
     *
     * @param jasperEnvironment Jasper's own environment
     * @return {@code EDITOR} for the choice, and {@code VISUAL} when Jasper's environment has one that is not blank;
     *     nothing when Jasper's environment keeps its own or the choice names none
     */
    public Map<String, String> variables(Map<String, String> jasperEnvironment) {
        if (EditorCommands.keepsEnvironment(jasperEnvironment, preference.get().keepEnvironment())) return Map.of();
        String inherited = jasperEnvironment.get("VISUAL");
        boolean replacesVisual = inherited != null && !inherited.isBlank();
        return EditorCommands.variable(choice(), osName)
            .map(value -> replacesVisual ? Map.of("EDITOR", value, "VISUAL", value) : Map.of("EDITOR", value)).orElse(Map.of());
    }

    /**
     * The {@code Platform.editor} file editor: opens a file in the chosen editor. A terminal editor, and a custom command
     * that runs in a terminal, get a new tab in {@code window} when it is a terminal window, else in the active one.
     *
     * @param file the file
     * @param line 1-based, when known
     * @param window the window Jasper opened the file from
     * @return whether it opened; false hands the file to the desktop
     */
    public boolean edit(Path file, OptionalInt line, Optional<WindowOwner> window) {
        Path target = file.toAbsolutePath().normalize();
        return switch (choice()) {
            case Choice.Jasper _ -> jasper.edit(target, line, window);
            case Choice.Installed installed -> installed.editor().editor().kind() == KnownEditor.Kind.TERMINAL
                ? inTab(EditorCommands.openAt(installed.editor(), target, line), target, window)
                : launcher.start(EditorCommands.openAt(installed.editor(), target, line), installed.editor().executable().getFileName().toString());
            case Choice.Custom custom -> {
                List<String> command = EditorCommands.custom(custom.command(), target, probe.get().home(), osName);
                yield custom.inTerminal() ? inTab(command, target, window) : launcher.start(command, EditorCommands.program(custom.command()));
            }
            case Choice.System _ -> false;
        };
    }

    /** A new tab running the command in the file's folder; false when no terminal window is open. */
    private boolean inTab(List<String> command, Path file, Optional<WindowOwner> window) {
        Optional<WindowHandle> target = window.filter(WindowHandle.class::isInstance).map(WindowHandle.class::cast)
            .filter(WindowHandle::isOpen).or(terminals::activeWindow);
        return target.isPresent() && terminals.openTab(target.get(),
            OpenRequest.local(LocalSpec.running(command, Optional.ofNullable(file.getParent())))).isPresent();
    }
}
