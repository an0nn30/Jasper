package dev.jasper.app.contributions;

import java.util.UUID;
import java.util.function.Consumer;

/** App-native presentation callbacks for one main or detached workspace. EDT only. */
public record ContentHost(UUID id, boolean detached, Consumer<ContentEntry> mount,
        Consumer<ContentEntry> remove, Consumer<ContentEntry> select, Runnable toFront) { }
