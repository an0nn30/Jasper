package dev.jasper.app.platform;

import dev.jasper.app.platform.FileAssociations.Claimability;
import dev.jasper.app.platform.FileAssociations.Handler;
import dev.jasper.app.platform.FileAssociations.Outcome;
import dev.jasper.app.platform.FileAssociations.Refusal;
import dev.jasper.app.platform.FileAssociations.TypeResult;
import dev.jasper.app.platform.FileAssociations.Unavailable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The platform layer's facade over a scripted in-memory system, for tests outside this package, which cannot reach the
 * facade's constructor. Types and handlers are set up front and changed later; the system's availability changes by
 * making a new facade over the same system and saved handlers, so the answer {@link FileAssociations#unavailable()} keeps
 * is asked again. Work runs on the caller's thread. {@link #close()} deletes the saved handlers' folder.
 */
public final class FileAssociationsBridge implements AutoCloseable {
    private final Path folder;
    private final PreviousHandlers store;
    private final ScriptedBackend backend;
    private FileAssociations facade;

    private FileAssociationsBridge(Map<String, List<String>> types, Map<String, Handler> handlers,
                                   Optional<Unavailable> unavailable) {
        try {
            folder = Files.createTempDirectory("jasper-file-associations-bridge");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        store = new PreviousHandlers(folder.resolve("file-associations.toml"));
        backend = new ScriptedBackend(types, handlers, unavailable);
        facade = new FileAssociations(Runnable::run, backend, store);
    }

    /** A bridge over a system with these extensions' types and these types' handlers; a type with no handler has none. */
    public static FileAssociationsBridge open(Map<String, List<String>> types, Map<String, Handler> handlers,
                                              Optional<Unavailable> unavailable) {
        return new FileAssociationsBridge(types, handlers, unavailable);
    }

    /** The current facade; {@link #unavailable} replaces it. */
    public synchronized FileAssociations facade() {
        return facade;
    }

    /** Makes an extension claimable with these types. */
    public void type(String extension, List<String> types) {
        synchronized (backend) { backend.types.put(extension, List.copyOf(types)); }
    }

    /** Sets who opens a type now. */
    public void handler(String type, Handler handler) {
        synchronized (backend) { backend.handlers.put(type, handler); }
    }

    /** Changes why file associations cannot be used, by making a new facade; the old one's listener goes with it. */
    public synchronized FileAssociations unavailable(Optional<Unavailable> reason) {
        synchronized (backend) { backend.unavailable = reason; }
        facade = new FileAssociations(Runnable::run, backend, store);
        return facade;
    }

    @Override public void close() {
        dev.jasper.app.testsupport.TempFolders.delete(folder);
    }

    private static final class ScriptedBackend implements FileAssociations.Backend {
        final Map<String, List<String>> types = new LinkedHashMap<>();
        final Map<String, Handler> handlers = new LinkedHashMap<>();
        Optional<Unavailable> unavailable;

        ScriptedBackend(Map<String, List<String>> types, Map<String, Handler> handlers, Optional<Unavailable> unavailable) {
            this.types.putAll(types);
            this.handlers.putAll(handlers);
            this.unavailable = unavailable;
        }

        @Override public synchronized Optional<Unavailable> unavailable() { return unavailable; }

        @Override public synchronized Claimability claimability(String extension) {
            List<String> known = types.get(extension);
            return known == null || known.isEmpty() ? new Claimability.NotClaimable(Refusal.NO_SYSTEM_TYPE)
                : new Claimability.Claimable(known, false);
        }

        @Override public synchronized Handler handler(String type) {
            return handlers.getOrDefault(type, new Handler.None());
        }

        @Override public synchronized List<TypeResult> claim(Map<String, List<String>> typesByExtension) {
            List<TypeResult> out = new ArrayList<>();
            typesByExtension.forEach((extension, claimed) -> claimed.forEach(type -> {
                handlers.put(type, new Handler.Jasper());
                out.add(new TypeResult(extension, type, new Outcome.Done()));
            }));
            return out;
        }

        @Override public synchronized List<TypeResult> restore(Map<String, Map<String, Handler>> previous) {
            List<TypeResult> out = new ArrayList<>();
            previous.forEach((extension, byType) -> byType.forEach((type, handler) -> {
                handlers.put(type, handler);
                out.add(new TypeResult(extension, type, new Outcome.Done()));
            }));
            return out;
        }

        @Override public void forget(String extension) { }
    }
}
