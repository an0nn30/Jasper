package dev.jasper.fileserver.share;

import java.time.Instant;

/** One file or folder in a share; a folder's size is 0. */
public record ShareEntry(String name, boolean directory, long size, Instant modified) { }
