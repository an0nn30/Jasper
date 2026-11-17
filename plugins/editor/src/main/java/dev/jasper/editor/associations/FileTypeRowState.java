package dev.jasper.editor.associations;

import dev.jasper.sdk.ui.FileAssociations.FileTypeStatus;
import dev.jasper.sdk.ui.FileAssociations.Handler;
import dev.jasper.sdk.ui.FileAssociations.Refusal;
import dev.jasper.sdk.ui.FileAssociations.TypeState;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** What a row's statuses read as. */
public sealed interface FileTypeRowState {
    /** The statuses have not arrived. */
    record Checking() implements FileTypeRowState { }

    /** File associations cannot be used here. */
    record Unavailable() implements FileTypeRowState { }

    /**
     * No extension of the row can be claimed.
     *
     * @param refusal the first extension's refusal
     */
    record NotClaimable(Refusal refusal) implements FileTypeRowState {
        public NotClaimable {
            Objects.requireNonNull(refusal, "refusal");
        }
    }

    /**
     * Every type of every claimable extension is Jasper's.
     *
     * @param restorable whether Restore Previous has something to hand back
     */
    record Jasper(boolean restorable) implements FileTypeRowState { }

    /**
     * Some types are Jasper's and some are not.
     *
     * @param restorable whether Restore Previous has something to hand back
     */
    record Partly(boolean restorable) implements FileTypeRowState { }

    /**
     * One other application opens the types.
     *
     * @param name its display name
     */
    record Other(String name) implements FileTypeRowState {
        public Other {
            Objects.requireNonNull(name, "name");
        }
    }

    /** Several other applications open the types. */
    record Several() implements FileTypeRowState { }

    /** No application opens the types. */
    record None() implements FileTypeRowState { }

    /** A handler could not be read, and nothing is Jasper's. */
    record Unknown() implements FileTypeRowState { }

    /**
     * Folds a row's statuses into one state. The rules apply in this order:
     * <ol>
     * <li>no claimable extension (every status has no types): {@link NotClaimable} with the first extension's refusal,
     *     {@code NO_SYSTEM_TYPE} when {@code refusals} names none;
     * <li>every type of every claimable extension is Jasper's: {@link Jasper};
     * <li>some type is Jasper's: {@link Partly};
     * <li>any type is unknown: {@link Unknown};
     * <li>every type has no application: {@link None};
     * <li>exactly one other application's name, ignoring types with no application: {@link Other};
     * <li>otherwise {@link Several}.
     * </ol>
     * Restorable means some Jasper type has a saved previous application that is not {@code None}; a saved
     * {@code None} counts when {@code noneRestorable}.
     *
     * @param statuses the row's extensions' statuses
     * @param refusals why each unclaimable extension cannot be claimed
     * @param noneRestorable whether handing back "no application" does something (Linux)
     * @return the state
     */
    static FileTypeRowState of(List<FileTypeStatus> statuses, Map<String, Refusal> refusals, boolean noneRestorable) {
        List<TypeState> types = statuses.stream().flatMap(status -> status.types().stream()).toList();
        if (types.isEmpty()) {
            Refusal refusal = statuses.stream().map(status -> refusals.get(status.extension()))
                .filter(Objects::nonNull).findFirst().orElse(Refusal.NO_SYSTEM_TYPE);
            return new NotClaimable(refusal);
        }
        List<TypeState> jasper = types.stream().filter(type -> type.handler() instanceof Handler.Jasper).toList();
        if (!jasper.isEmpty()) {
            boolean restorable = jasper.stream().anyMatch(type -> type.previous()
                .map(previous -> !(previous instanceof Handler.None) || noneRestorable).orElse(false));
            return jasper.size() == types.size() ? new Jasper(restorable) : new Partly(restorable);
        }
        if (types.stream().anyMatch(type -> type.handler() instanceof Handler.Unknown)) {
            return new Unknown();
        }
        var names = new LinkedHashSet<String>();
        for (TypeState type : types) {
            if (type.handler() instanceof Handler.Other other) {
                names.add(other.name());
            }
        }
        if (names.isEmpty()) {
            return new None();
        }
        return names.size() == 1 ? new Other(names.iterator().next()) : new Several();
    }
}
