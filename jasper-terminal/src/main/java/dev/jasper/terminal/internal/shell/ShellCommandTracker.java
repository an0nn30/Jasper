package dev.jasper.terminal.internal.shell;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.LongSupplier;

import java.util.function.*;

/** Reader-thread shell protocol state; metadata is published for application readers. */
public final class ShellCommandTracker {
    private static final int MAX_COMMAND_BYTES = 16 * 1024;
    private final LongSupplier clock;
    private final Supplier<CommandLocation> cursor;
    private final Function<CommandLocation,String> capture;
    private final BooleanSupplier recordPrompt;
    private final Consumer<Path> cwdChanged;
    private final Consumer<String> commandStarted;
    private final Consumer<CompletedCommand> commandFinished;
    private final Runnable integrationDetected, cursorReset;
    private final DirectoryProvenance provenance;
    private final Consumer<RemoteLocation> remoteChanged;
    private volatile Path workingDirectory;
    private volatile RemoteLocation remoteDirectory;
    private volatile boolean shellIntegrationDetected;
    private CommandLocation commandStart;
    private String pendingCommand, pendingCommandText;
    private long commandStartedAt;
    public ShellCommandTracker(LongSupplier clock, Supplier<CommandLocation> cursor,
                        Function<CommandLocation,String> capture, BooleanSupplier recordPrompt,
                        Consumer<Path> cwdChanged, Consumer<String> commandStarted,
                        Consumer<CompletedCommand> commandFinished, Runnable integrationDetected, Runnable cursorReset,
                        DirectoryProvenance provenance, Consumer<RemoteLocation> remoteChanged) {
        this.provenance = Objects.requireNonNull(provenance, "provenance"); this.remoteChanged = Objects.requireNonNull(remoteChanged, "remoteChanged");
        this.clock = Objects.requireNonNull(clock, "clock"); this.cursor = cursor; this.capture = capture;
        this.recordPrompt = recordPrompt; this.cwdChanged = cwdChanged; this.commandStarted = commandStarted;
        this.commandFinished = commandFinished; this.integrationDetected = integrationDetected; this.cursorReset = cursorReset;
    }
    public Optional<Path> workingDirectory() { return Optional.ofNullable(workingDirectory); }
    /** The directory last reported when it is not on this machine; exactly one of this and the local one is present. */
    public Optional<RemoteLocation> remoteDirectory() { return Optional.ofNullable(remoteDirectory); }
    public boolean detected() { return shellIntegrationDetected; }
    public void discardUnusedPayload() { pendingCommandText = null; }
    private void markCommandStart() { commandStart = cursor.get(); }
    private void captureCommand() {
        String text = pendingCommandText;
        pendingCommandText = null;
        if (text == null) {
            if (commandStart == null || commandStart.row() < 0) return;
            text = capture.apply(commandStart);
        }
        commandStart = null;
        pendingCommand = text.isEmpty() ? null : text;
        commandStartedAt = clock.getAsLong();
        // The capture callback has released its buffer lock before publishing arbitrary listener code.
        if (pendingCommand != null) commandStarted.accept(pendingCommand);
    }
    public void accept(List<String> args) {
        if (args.size() < 2 || !"jasper".equals(args.get(0))) {
            return;
        }
        switch (args.get(1)) {
            case "cwd" -> provenance.classify(String.join(";", args.subList(2, args.size()))).ifPresent(report -> {
                // One of the two at a time: a remote path must never be readable through the local surface.
                switch (report) {
                    case DirectoryProvenance.Report.Local local -> {
                        remoteDirectory = null;
                        workingDirectory = local.directory();
                        cwdChanged.accept(local.directory());
                    }
                    case DirectoryProvenance.Report.Remote remote -> {
                        workingDirectory = null;
                        remoteDirectory = remote.location();
                        remoteChanged.accept(remote.location());
                    }
                }
            });
            case "cmd" -> pendingCommandText = args.size() > 2
                ? decodeCommand(String.join(";", args.subList(2, args.size()))) : null;
            case "mark" -> {
                String mark = args.size() > 2 ? args.get(2) : "";
                switch (mark) {
                    case "A" -> {
                        // A shell that emits its own A (fish 4) must not flush the cycle early:
                        // the command would be reported without the status its own D carries.
                        if (!shellIntegrationDetected) {
                            // The mark draws nothing, so without this the owner only learns that
                            // integration is live when the prompt that follows happens to repaint.
                            shellIntegrationDetected = true;
                            integrationDetected.run();
                        }
                        // Only the flush is conditional. A prompt never sits inside a cycle, so a
                        // half-started capture and a payload no C consumed are dropped either way;
                        // a prompt redrawn in place (zle reset-prompt) repeats the row and would
                        // otherwise leave both behind for the next C to pick up.
                        if (recordPrompt.getAsBoolean()) flushPendingCommand(OptionalInt.empty());
                        commandStart = null;
                        pendingCommandText = null;
                    }
                    case "B" -> markCommandStart();
                    case "C" -> captureCommand();
                    case "D" -> flushPendingCommand(exitStatus(args));
                    default -> {
                        // Other FinalTerm marks carry nothing Jasper tracks.
                    }
                }
            }
            case "cursor-reset" -> cursorReset.run();
            default -> {
                // A command from a newer Jasper shell-integration script; nothing to do.
            }
        }
    }
    private void flushPendingCommand(OptionalInt exitStatus) {
        String command = pendingCommand;
        long startedAt = commandStartedAt;
        pendingCommand = null;
        pendingCommandText = null;
        if (command == null) return;
        // Only captureCommand sets pendingCommand, and it stamps the clock in the same breath, so a
        // non-null command always has a real start. nanoTime is monotonic, so this cannot go negative.
        Duration ran = Duration.ofNanos(clock.getAsLong() - startedAt);
        Optional<Path> directory = workingDirectory();
        commandFinished.accept(new CompletedCommand(command, exitStatus, directory, ran));
    }
    /**
     * Decodes the base64 UTF-8 {@code cmd} payload from Jasper's shell-integration scripts, or null if
     * malformed — the caller then falls back to reading the command off the screen. The payload is the
     * exact command line, so it is not trimmed; it is only bounded, because OSC 1341 is an open channel
     * and the history index enforces the same limit on the lines it parses from disk.
     */
    private static String decodeCommand(String encoded) {
        String trimmed = encoded.trim();
        if (trimmed.length() > (MAX_COMMAND_BYTES / 3 + 1) * 4) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(trimmed);
            if (bytes.length > MAX_COMMAND_BYTES) return null;
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            return text.isEmpty() ? null : text;
        } catch (IllegalArgumentException | CharacterCodingException malformed) {
            return null;
        }
    }
    private static OptionalInt exitStatus(List<String> args) {
        if (args.size() < 4) return OptionalInt.empty();
        try {
            return OptionalInt.of(Integer.parseInt(args.get(3).trim()));
        } catch (NumberFormatException malformed) {
            return OptionalInt.empty();
        }
    }
}
