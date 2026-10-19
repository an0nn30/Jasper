package dev.jasper.app.files;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A place the chooser can browse: its roots and how to connect to one. UI thread; {@link #connect}'s future may
 * complete on any thread. Two implementations: {@link LocalSource} and the plugin adapter in
 * {@code dev.jasper.app.plugins}.
 */
public interface ChooserSource {
    /**
     * One root.
     *
     * @param connected opening it needs no connection or sign-in first
     */
    record Root(String id, String label, String detail, boolean connected) { }

    String id();

    String heading();

    List<Root> roots();

    /** @return removes the listener; the listener runs on the UI thread */
    Runnable onChanged(Runnable listener);

    /** @param ownerWindow the Jasper window the chooser belongs to, for sign-in prompts; empty for other owners */
    CompletableFuture<Browser> connect(String root, Optional<UUID> ownerWindow);

    /** @return where a terminal pane is in this source, when it can tell */
    default Optional<Location> locate(UUID pane) { return Optional.empty(); }
}
