package dev.jasper.app.contributions;

/** A host-rendered status item; a null callback denotes plain text. */
public record ContentStatus(String text, String tooltip, Runnable run) { }
