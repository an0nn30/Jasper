package dev.jasper.editor.session;

import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * What a session needs from the plugin.
 *
 * @param background where decoding and encoding run
 * @param ui the UI thread; results come back here
 * @param ask asks the user a question on the UI thread and returns the chosen answer
 * @param limitBytes the largest file the editor opens
 */
public record SessionEnvironment(Executor background, Executor ui, Function<Question, String> ask, LongSupplier limitBytes) { }
