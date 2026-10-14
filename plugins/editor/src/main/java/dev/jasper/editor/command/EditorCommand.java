package dev.jasper.editor.command;

import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.ui.EditorWindow;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.cli.CommandCall;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Answers {@code jasper editor}: opens the files in Jasper's Editor, in the call's window when it ran in a Jasper pane,
 * and with {@code --wait} completes once every tab it opened has closed. UI thread.
 */
public final class EditorCommand {
    private final Supplier<EditorWindow> editor;

    /** @param editor the Editor's window, made when first needed */
    public EditorCommand(Supplier<EditorWindow> editor) { this.editor = editor; }

    /** The handler for {@code CommandLine.handle}. */
    public CompletionStage<Integer> run(CommandCall call) {
        return switch (EditorArguments.parse(call.arguments(), call.workingDirectory(), Files::isRegularFile)) {
            case EditorArguments.Help help -> {
                call.out(EditorArguments.USAGE);
                yield CompletableFuture.completedFuture(0);
            }
            case EditorArguments.Invalid invalid -> {
                call.err("jasper editor: " + invalid.message() + "\n" + EditorArguments.USAGE);
                yield CompletableFuture.completedFuture(2);
            }
            case EditorArguments.Open open -> open(open, call);
        };
    }

    private CompletionStage<Integer> open(EditorArguments.Open request, CommandCall call) {
        EditorWindow window = editor.get();
        if (request.targets().isEmpty()) {
            window.show();
            return CompletableFuture.completedFuture(0);
        }
        WindowOwner owner = call.window().orElse(null);
        boolean[] failed = {false};
        List<CompletableFuture<DocumentSession>> opening = new ArrayList<>();
        for (EditorArguments.Target target : request.targets()) {
            Path file = target.file();
            if (Files.isDirectory(file)) {
                call.err("jasper editor: " + file + " is a folder\n");
                failed[0] = true;
            } else if (Files.exists(file)) {
                opening.add(window.openAt(file, target.line(), target.column(), owner));
            } else {
                Path folder = file.getParent();
                if (folder == null || !Files.isDirectory(folder)) {
                    call.err("jasper editor: " + (folder == null ? file : folder) + " does not exist\n");
                    failed[0] = true;
                } else {
                    opening.add(CompletableFuture.completedFuture(window.openNew(file, owner)));
                }
            }
        }
        return CompletableFuture.allOf(opening.toArray(new CompletableFuture<?>[0])).thenCompose(loaded -> {
            List<DocumentSession> open = new ArrayList<>();
            for (CompletableFuture<DocumentSession> future : opening) {
                DocumentSession session = future.join();
                if (session.state() == DocumentSession.State.FAILED) {
                    call.err("jasper editor: " + session.failure() + "\n");
                    failed[0] = true;
                } else {
                    open.add(session);
                }
            }
            int code = failed[0] ? 1 : 0;
            if (!request.waiting() || open.isEmpty()) return CompletableFuture.completedFuture(code);
            CompletableFuture<Integer> waiting = CompletableFuture.allOf(open.stream().map(window::closed)
                .toArray(CompletableFuture<?>[]::new)).thenApply(closed -> code);
            // The caller went away (Ctrl-C): stop waiting and leave the tabs open.
            call.onCancelled(() -> waiting.complete(code));
            return waiting;
        });
    }
}
