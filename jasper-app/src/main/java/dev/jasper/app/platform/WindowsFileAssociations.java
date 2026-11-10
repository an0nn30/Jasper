package dev.jasper.app.platform;

import dev.jasper.app.platform.FileAssociations.Claimability;
import dev.jasper.app.platform.FileAssociations.Handler;
import dev.jasper.app.platform.FileAssociations.Outcome;
import dev.jasper.app.platform.FileAssociations.TypeResult;
import dev.jasper.app.platform.FileAssociations.Unavailable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * File associations on Windows. Windows keeps the user's choice under a hash only it may write, so Jasper never writes
 * {@code UserChoice}: claiming lists Jasper's {@code JasperFile} ProgId (which the installer registers) for each
 * extension, among the applications that may open it and among Jasper's capabilities, then opens Settings' default
 * apps page, where the user picks Jasper. The type id on Windows is the extension itself. Restoring opens Settings too.
 */
final class WindowsFileAssociations implements FileAssociations.Backend {
    private static final System.Logger LOG = System.getLogger(WindowsFileAssociations.class.getName());
    static final String PROG_ID = "JasperFile";
    private static final String COMMAND = "Software\\Classes\\JasperFile\\shell\\open\\command";
    private static final String FILE_EXTS = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\";
    private static final String CAPABILITIES = "Software\\Jasper\\Capabilities\\FileAssociations";
    private static final URI DEEP_LINK = URI.create("ms-settings:defaultapps?registeredAppUser=Jasper");
    private static final URI DEFAULT_APPS = URI.create("ms-settings:defaultapps");

    /**
     * The registry and shell calls under {@code HKEY_CURRENT_USER}, as functions so tests can stand in for them.
     *
     * @param read a string value, empty when missing; throws {@link UncheckedIOException} when the read fails
     * @param exists whether a key exists
     * @param write writes a string value, creating the key
     * @param changeNotify tells Explorer the associations changed
     * @param friendlyAppName the display name of the application that opens an extension
     */
    record Registry(BiFunction<String, String, Optional<String>> read, Predicate<String> exists, Writer write,
            Runnable changeNotify, Function<String, Optional<String>> friendlyAppName) {
        /** Writes one string value. */
        @FunctionalInterface
        interface Writer {
            void write(String key, String name, String value) throws IOException;
        }

        static Registry system() {
            return new Registry((key, name) -> {
                try {
                    return WindowsRegistry.read(key, name);
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            }, key -> {
                try {
                    return WindowsRegistry.exists(key);
                } catch (IOException failure) {
                    LOG.log(System.Logger.Level.DEBUG, "Could not look for HKEY_CURRENT_USER\\" + key, failure);
                    return false;
                }
            }, WindowsRegistry::writeString, () -> {
                try {
                    WindowsRegistry.changeNotify();
                } catch (IOException failure) {
                    LOG.log(System.Logger.Level.DEBUG, "Could not announce the changed file associations", failure);
                }
            }, extension -> {
                try {
                    return WindowsRegistry.friendlyAppName(extension);
                } catch (IOException failure) {
                    LOG.log(System.Logger.Level.DEBUG, "Could not name the application that opens " + extension, failure);
                    return Optional.empty();
                }
            });
        }
    }

    private final Registry registry;
    private final Consumer<URI> openSettings;

    /**
     * @param registry the registry calls
     * @param openSettings opens an {@code ms-settings:} link; throws when it cannot
     */
    WindowsFileAssociations(Registry registry, Consumer<URI> openSettings) {
        this.registry = registry;
        this.openSettings = openSettings;
    }

    @Override public Optional<Unavailable> unavailable() {
        return registry.exists().test(COMMAND) ? Optional.empty() : Optional.of(Unavailable.NOT_INSTALLED);
    }

    @Override public Claimability claimability(String extension) {
        return new Claimability.Claimable(List.of(extension), false);
    }

    /**
     * The user's choice for an extension. When it is Jasper, the extension is listed among Jasper's capabilities again
     * if missing: an upgrade removes and rewrites {@code Software\Jasper\Capabilities}, which drops the listings a claim
     * wrote, and Settings shows only listed extensions.
     */
    @Override public Handler handler(String extension) throws IOException {
        Optional<String> progId;
        try {
            progId = WindowsRegistry.userChoice(FILE_EXTS + extension, registry.read());
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
        if (progId.isEmpty()) return new Handler.None();
        String id = progId.get();
        if (PROG_ID.equalsIgnoreCase(id)) {
            keepListed(extension);
            return new Handler.Jasper();
        }
        return new Handler.Other(id, registry.friendlyAppName().apply(extension).filter(name -> !name.isBlank()).orElse(id));
    }

    private void keepListed(String extension) {
        try {
            if (registry.read().apply(CAPABILITIES, extension).isEmpty()) write(CAPABILITIES, extension, PROG_ID);
        } catch (IOException | UncheckedIOException failure) {
            LOG.log(System.Logger.Level.DEBUG, "Could not list " + extension + " among Jasper's capabilities again", failure);
        }
    }

    /**
     * Lists Jasper for every extension, then opens Settings once. An extension whose values cannot be written fails on
     * its own and the rest go on to Settings; when Settings cannot open, every written extension fails.
     */
    @Override public List<TypeResult> claim(Map<String, List<String>> typesByExtension) {
        Map<String, Outcome> failed = new HashMap<>();
        for (String extension : typesByExtension.keySet()) {
            try {
                // An empty REG_SZ on purpose: Explorer reads only the value names under OpenWithProgids.
                write("Software\\Classes\\" + extension + "\\OpenWithProgids", PROG_ID, "");
                write(CAPABILITIES, extension, PROG_ID);
            } catch (IOException failure) {
                LOG.log(System.Logger.Level.WARNING, "Could not list Jasper for " + extension, failure);
                failed.put(extension, new Outcome.Failed(detail(failure)));
            }
        }
        Outcome settings = new Outcome.InSettings();
        if (failed.size() < typesByExtension.size()) {
            registry.changeNotify().run();
            settings = openSettings();
        }
        List<TypeResult> results = new java.util.ArrayList<>();
        for (Map.Entry<String, List<String>> entry : typesByExtension.entrySet()) {
            Outcome outcome = failed.getOrDefault(entry.getKey(), settings);
            for (String type : entry.getValue()) {
                results.add(new TypeResult(entry.getKey(), type, outcome));
            }
        }
        return List.copyOf(results);
    }

    /** Windows cannot hand a type back by itself: Settings opens once for every type that had an application. */
    @Override public List<TypeResult> restore(Map<String, Map<String, Handler>> previousByTypeByExtension) {
        boolean needsSettings = previousByTypeByExtension.values().stream()
                .flatMap(previous -> previous.values().stream())
                .anyMatch(previous -> !(previous instanceof Handler.None));
        Outcome settings = needsSettings ? openSettings() : new Outcome.InSettings();
        List<TypeResult> results = new java.util.ArrayList<>();
        previousByTypeByExtension.forEach((extension, previousByType) -> previousByType.forEach((type, previous) ->
                results.add(new TypeResult(extension, type,
                        previous instanceof Handler.None ? new Outcome.NoPrevious() : settings))));
        return List.copyOf(results);
    }

    /** The values a claim wrote stay: they name a ProgId that uninstalling removes. */
    @Override public void forget(String extension) { }

    /** Settings' page for Jasper, else the default apps page; {@code InSettings} or the last failure. */
    private Outcome openSettings() {
        try {
            openSettings.accept(DEEP_LINK);
            return new Outcome.InSettings();
        } catch (RuntimeException deepLink) {
            LOG.log(System.Logger.Level.DEBUG, "Could not open " + DEEP_LINK, deepLink);
        }
        try {
            openSettings.accept(DEFAULT_APPS);
            return new Outcome.InSettings();
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not open " + DEFAULT_APPS, failure);
            return new Outcome.Failed(detail(failure));
        }
    }

    private void write(String key, String name, String value) throws IOException {
        if (key.toLowerCase(Locale.ROOT).contains("userchoice")) throw new IllegalArgumentException("Jasper never writes the user's choice: " + key);
        registry.write().write(key, name, value);
    }

    private static String detail(Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
