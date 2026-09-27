package dev.jasper.editor.session;

import dev.jasper.editor.api.TooLargeException;
import dev.jasper.editor.text.BinaryFileException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Words for what went wrong (spec 8). */
public final class Failures {
    private Failures() { }

    /**
     * @param name the file's name
     * @param error what went wrong opening it
     * @return a sentence for the tab
     */
    public static String describe(String name, Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof TooLargeException large)
            return name + " is " + size(large.size()) + "; the limit is " + size(large.limit()) + " (max_file_mib).";
        if (cause instanceof BinaryFileException) return name + " looks like a binary file.";
        if (cause instanceof NoSuchFileException) return name + " does not exist.";
        if (cause instanceof AccessDeniedException) return "Permission denied: " + name + ".";
        return name + ": " + sentence(reason(cause));
    }

    /**
     * @param error what went wrong
     * @return a short reason, to follow "Could not save x: "
     */
    public static String reason(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof TooLargeException large) return "it is " + size(large.size()) + "; the limit is " + size(large.limit());
        if (cause instanceof NoSuchFileException) return "it does not exist";
        if (cause instanceof AccessDeniedException) return "permission denied";
        if (cause instanceof FileSystemException system && system.getReason() != null) return system.getReason();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    /**
     * @param error what went wrong opening a file
     * @return whether trying again could help
     */
    public static boolean retryable(Throwable error) { return !(unwrap(error) instanceof BinaryFileException); }

    /**
     * @param bytes a size
     * @return it in bytes, KiB or MiB
     */
    public static String size(long bytes) {
        long mib = 1024L * 1024L;
        if (bytes >= mib) return bytes % mib == 0 ? bytes / mib + " MiB" : String.format(Locale.ROOT, "%.1f MiB", bytes / (double) mib);
        if (bytes >= 1024) return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0);
        return bytes + " bytes";
    }

    static String sentence(String text) { return text.endsWith(".") ? text : text + "."; }

    static Throwable unwrap(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) cause = cause.getCause();
        return cause;
    }
}
