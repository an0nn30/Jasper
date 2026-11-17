package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Choosing Jasper as the system's application for file types, and handing each type back. Reached through
 * {@link Platform#fileAssociations()}, which needs {@code files.associate}.
 *
 * <p>Every method runs on the UI thread and throws {@code IllegalStateException} elsewhere. Every future completes on
 * the UI thread and never exceptionally: a failure arrives as {@link Outcome.Failed}, and a state that could not be read
 * as {@link Handler.Unknown}. {@link #unavailable()} never blocks.
 *
 * <p>An extension is {@code .ext}: lower case, a dot and 1 to 16 characters from {@code a-z}, {@code 0-9}, {@code _},
 * {@code +} and {@code -}. {@code .html} and {@code .htm} belong to the browser and are refused.
 *
 * <p>A claim saves each type's previous application before it takes the type, so {@link #restore} can hand it back.
 * Restore hands back only types that are still Jasper's; a type the user has since given to another application is left
 * alone. On Windows an application cannot take a type itself: a claim opens Windows Settings and answers
 * {@link Outcome.InSettings}, and the user picks Jasper there.
 *
 * <p>{@link #status} also starts background re-checks, which run until {@code status(List.of())} is called and report
 * through {@link #onChanged}.
 *
 * @since 0.31.0
 */
public interface FileAssociations {
    /** Who opens a file type. */
    sealed interface Handler {
        /** Jasper. */
        record Jasper() implements Handler { }

        /**
         * Another application.
         *
         * @param name its display name
         */
        record Other(String name) implements Handler {
            public Other {
                Objects.requireNonNull(name, "name");
            }
        }

        /** No application. */
        record None() implements Handler { }

        /** The handler could not be read. */
        record Unknown() implements Handler { }
    }

    /**
     * One type of an extension.
     *
     * @param type the system's type (a UTI, a ProgId or a MIME type)
     * @param handler who opens it now
     * @param previous the application saved by a claim, possibly {@link Handler.None}; empty when nothing is saved
     */
    record TypeState(String type, Handler handler, Optional<Handler> previous) {
        public TypeState {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(handler, "handler");
            Objects.requireNonNull(previous, "previous");
        }
    }

    /**
     * An extension's state.
     *
     * @param extension the normalised extension
     * @param types its types, copied; empty when the extension cannot be claimed here
     */
    record FileTypeStatus(String extension, List<TypeState> types) {
        public FileTypeStatus {
            Objects.requireNonNull(extension, "extension");
            types = List.copyOf(types);
        }
    }

    /** Why file associations cannot be used at all. */
    enum Unavailable {
        /** Jasper runs from a development build, not an installed one. */
        DEVELOPMENT_RUN,
        /** Jasper was not installed with its installer or package. */
        NOT_INSTALLED,
        /** Linux without {@code xdg-mime}. */
        NO_XDG_MIME,
        /** A system with no file associations Jasper can set. */
        UNSUPPORTED_SYSTEM
    }

    /** Why an extension cannot be claimed. */
    enum Refusal {
        /** Not a valid extension. */
        INVALID,
        /** The browser owns it. */
        BROWSER_OWNED,
        /** The system has no type for it. */
        NO_SYSTEM_TYPE,
        /** The system treats it as another kind of file than text. */
        NOT_TEXT
    }

    /** Whether an extension can be claimed. */
    sealed interface Claimability {
        /**
         * The extension can be claimed. An empty {@code types} throws {@code IllegalArgumentException}.
         *
         * @param types the system types a claim takes, copied, at least one
         * @param declares whether claiming first declares the extension to the system
         */
        record Claimable(List<String> types, boolean declares) implements Claimability {
            public Claimable {
                types = List.copyOf(types);
                if (types.isEmpty()) {
                    throw new IllegalArgumentException("A claimable extension has at least one type");
                }
            }
        }

        /**
         * The extension cannot be claimed.
         *
         * @param refusal why
         */
        record NotClaimable(Refusal refusal) implements Claimability {
            public NotClaimable {
                Objects.requireNonNull(refusal, "refusal");
            }
        }
    }

    /** What happened to one type. */
    sealed interface Outcome {
        /** Done. */
        record Done() implements Outcome { }

        /** The user finishes in the system's Settings (Windows). */
        record InSettings() implements Outcome { }

        /** Nothing was saved to hand back. */
        record NoPrevious() implements Outcome { }

        /** The saved application is no longer installed. */
        record PreviousGone() implements Outcome { }

        /**
         * The extension was not attempted: it cannot be claimed here.
         *
         * @param refusal why
         */
        record Refused(Refusal refusal) implements Outcome {
            public Refused {
                Objects.requireNonNull(refusal, "refusal");
            }
        }

        /**
         * The system refused or the attempt failed.
         *
         * @param detail the system's own message
         */
        record Failed(String detail) implements Outcome {
            public Failed {
                Objects.requireNonNull(detail, "detail");
            }
        }
    }

    /**
     * One type's result.
     *
     * @param extension the normalised extension
     * @param type the system type; empty when the extension was refused
     * @param outcome what happened
     */
    record TypeResult(String extension, String type, Outcome outcome) {
        public TypeResult {
            Objects.requireNonNull(extension, "extension");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    /**
     * Why file associations cannot be used here, or empty when they can. Never blocks.
     *
     * @return the reason, if any
     * @throws IllegalStateException off the UI thread
     */
    Optional<Unavailable> unavailable();

    /**
     * Reads who opens each extension's types, and starts re-checking them in the background; the re-checks report
     * through {@link #onChanged}. {@code status(List.of())} stops them.
     *
     * @param extensions the extensions to read, in the order to answer
     * @return one status per extension, in order
     * @throws IllegalStateException off the UI thread
     * @throws NullPointerException when {@code extensions} is null
     */
    CompletableFuture<List<FileTypeStatus>> status(List<String> extensions);

    /**
     * Whether an extension can be claimed here.
     *
     * @param extension the extension
     * @return the answer
     * @throws IllegalStateException off the UI thread
     * @throws NullPointerException when {@code extension} is null
     */
    CompletableFuture<Claimability> claimability(String extension);

    /**
     * Makes Jasper the application for each extension's types, saving each type's previous application first. On
     * Windows it opens Windows Settings and answers {@link Outcome.InSettings}.
     *
     * @param extensions the extensions to claim
     * @return one result per type, or one {@link Outcome.Refused} for an extension that cannot be claimed
     * @throws IllegalStateException off the UI thread
     * @throws NullPointerException when {@code extensions} is null
     */
    CompletableFuture<List<TypeResult>> claim(List<String> extensions);

    /**
     * Hands each extension's types back to the application saved by the claim. Only types that are still Jasper's are
     * handed back.
     *
     * @param extensions the extensions to restore
     * @return one result per type, or one {@link Outcome.Refused} for an extension that cannot be claimed
     * @throws IllegalStateException off the UI thread
     * @throws NullPointerException when {@code extensions} is null
     */
    CompletableFuture<List<TypeResult>> restore(List<String> extensions);

    /**
     * Drops what Jasper saved for an extension, and any declaration it made for it, without changing who opens it.
     *
     * @param extension the extension
     * @return a future that completes when done
     * @throws IllegalStateException off the UI thread
     * @throws NullPointerException when {@code extension} is null
     */
    CompletableFuture<Void> forget(String extension);

    /**
     * Runs {@code handler} on the UI thread when a background re-check finds that something changed. The registration
     * ends with the plugin.
     *
     * @param handler what to run
     * @return the registration
     * @throws IllegalStateException off the UI thread
     * @throws NullPointerException when {@code handler} is null
     */
    Subscription onChanged(Runnable handler);
}
