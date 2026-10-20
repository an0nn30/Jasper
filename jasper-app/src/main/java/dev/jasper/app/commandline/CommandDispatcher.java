package dev.jasper.app.commandline;

import java.util.concurrent.CompletionStage;

/** Runs the {@code jasper} command's calls. */
@FunctionalInterface
public interface CommandDispatcher {
    /**
     * Starts a call. Called on a listener thread; the stage may complete on any thread.
     *
     * @param invocation the call
     * @return its exit code
     */
    CompletionStage<Integer> dispatch(Invocation invocation);
}
