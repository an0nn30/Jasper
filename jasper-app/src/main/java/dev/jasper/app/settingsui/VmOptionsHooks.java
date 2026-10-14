package dev.jasper.app.settingsui;

import dev.jasper.app.lifecycle.Subscription;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What the System Settings page's Java Virtual Machine section shows and does (spec section 5.1); the application
 * supplies it. EDT only.
 *
 * @param heap the Maximum heap size row
 * @param file the Custom VM options row's description: the file's path, or why it does not apply
 * @param edit opens the file in the user's editor, creating it first
 * @param reveal shows the file in the file manager
 * @param editable whether the file applies (Jasper's launcher started this JVM); the buttons follow it
 * @param state why the options did not apply this time, or that they apply only to the installed app
 * @param findings what the check found
 * @param restart that a restart would apply other options
 * @param restartNow restarts Jasper
 * @param onChanged runs the listener whenever any of these may have changed; subscribing also reads and checks the file
 */
public record VmOptionsHooks(SchemaPage.External heap, Supplier<String> file, Runnable edit, Runnable reveal, BooleanSupplier editable,
                             Supplier<Optional<SchemaPage.Notice>> state, Supplier<Optional<SchemaPage.Notice>> findings,
                             Supplier<Optional<SchemaPage.Notice>> restart, Runnable restartNow,
                             Function<Runnable, Subscription> onChanged) { }
