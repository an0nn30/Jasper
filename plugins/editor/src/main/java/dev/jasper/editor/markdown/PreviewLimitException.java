package dev.jasper.editor.markdown;

/** A bounded preview could not be produced; source editing remains available. */
public final class PreviewLimitException extends RuntimeException {
    PreviewLimitException() { super("This document is too large to preview"); }
}
