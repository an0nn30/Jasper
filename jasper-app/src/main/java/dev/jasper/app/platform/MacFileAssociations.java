package dev.jasper.app.platform;

import dev.jasper.app.platform.FileAssociations.Claimability;
import dev.jasper.app.platform.FileAssociations.Handler;
import dev.jasper.app.platform.FileAssociations.Outcome;
import dev.jasper.app.platform.FileAssociations.Refusal;
import dev.jasper.app.platform.FileAssociations.TypeResult;
import dev.jasper.app.platform.FileAssociations.Unavailable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * File associations on macOS, through Launch Services ({@link MacLaunchServices}). An extension is claimable when macOS
 * gives it a declared text type, which the packaged app's {@code public.text} document type covers; claiming sets
 * Jasper's bundle as that type's default, and restoring sets the saved bundle back. macOS cannot unset a default, so a
 * type that had none keeps Jasper. Jasper declares no type at run time, so there is nothing to forget.
 */
final class MacFileAssociations implements FileAssociations.Backend {
    static final String BUNDLE_ID = "dev.jasper.app";

    /**
     * The Launch Services calls, as functions so tests can stand in for them.
     *
     * @param loaded whether the bridge is loaded (a packaged app)
     * @param handler a type's handler as {bundle id, display name}, or null for none
     * @param setHandler sets a type's handler to a bundle id: null when done, else the system's message
     * @param hasApplication whether a bundle id is installed
     * @param textType an extension's (without its dot) declared text type, or null
     */
    record MacCalls(BooleanSupplier loaded, Function<String, String[]> handler,
            BiFunction<String, String, String> setHandler, Predicate<String> hasApplication,
            Function<String, String> textType) {
        static MacCalls system() {
            return new MacCalls(MacNative::load, MacLaunchServices::contentTypeHandler,
                    MacLaunchServices::setContentTypeHandler, MacLaunchServices::hasApplication,
                    MacLaunchServices::textTypeForExtension);
        }
    }

    private final FileTypeCatalog catalog;
    private final MacCalls calls;

    MacFileAssociations(FileTypeCatalog catalog, MacCalls calls) {
        this.catalog = catalog;
        this.calls = calls;
    }

    @Override public Optional<Unavailable> unavailable() {
        return calls.loaded().getAsBoolean() ? Optional.empty() : Optional.of(Unavailable.DEVELOPMENT_RUN);
    }

    /**
     * A catalogue extension with no macOS type is not text there (macOS gives {@code .ts} a video type); any other
     * extension is claimable for the text type macOS gives it.
     */
    @Override public Claimability claimability(String extension) {
        if (catalog.extensions().contains(extension) && catalog.macTypes(extension).isEmpty()) {
            return new Claimability.NotClaimable(Refusal.NOT_TEXT);
        }
        String type = calls.textType().apply(extension.substring(1));
        return type == null
                ? new Claimability.NotClaimable(Refusal.NO_SYSTEM_TYPE)
                : new Claimability.Claimable(List.of(type), false);
    }

    @Override public Handler handler(String type) {
        String[] pair = calls.handler().apply(type);
        if (pair == null || pair.length == 0) {
            return new Handler.None();
        }
        String id = pair[0];
        if (BUNDLE_ID.equals(id)) {
            return new Handler.Jasper();
        }
        String name = pair.length > 1 && pair[1] != null && !pair[1].isBlank() ? pair[1] : id;
        return new Handler.Other(id, name);
    }

    @Override public List<TypeResult> claim(Map<String, List<String>> typesByExtension) {
        List<TypeResult> results = new java.util.ArrayList<>();
        typesByExtension.forEach((extension, types) -> {
            for (String type : types) {
                results.add(new TypeResult(extension, type, set(type, BUNDLE_ID)));
            }
        });
        return List.copyOf(results);
    }

    @Override public List<TypeResult> restore(Map<String, Map<String, Handler>> previousByTypeByExtension) {
        List<TypeResult> results = new java.util.ArrayList<>();
        previousByTypeByExtension.forEach((extension, previousByType) -> previousByType.forEach((type, previous) ->
                results.add(new TypeResult(extension, type, restore(type, previous)))));
        return List.copyOf(results);
    }

    private Outcome restore(String type, Handler previous) {
        return switch (previous) {
            case Handler.None none -> new Outcome.NoPrevious();
            case Handler.Unknown unknown -> new Outcome.NoPrevious();
            case Handler.Jasper jasper -> set(type, BUNDLE_ID);
            case Handler.Other other -> calls.hasApplication().test(other.id())
                    ? set(type, other.id())
                    : new Outcome.PreviousGone();
        };
    }

    private Outcome set(String type, String bundleId) {
        String message = calls.setHandler().apply(type, bundleId);
        return message == null ? new Outcome.Done() : new Outcome.Failed(message);
    }

    @Override public void forget(String extension) {
        // Jasper declares no type at run time on macOS: the packaged app's Info.plist declares them all.
    }
}
