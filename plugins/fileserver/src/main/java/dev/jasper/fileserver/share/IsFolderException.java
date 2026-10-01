package dev.jasper.fileserver.share;

import java.nio.file.FileSystemException;

/** A file operation on a folder. */
public final class IsFolderException extends FileSystemException {
    public IsFolderException(String clientPath) { super(clientPath, null, "Is a folder"); }
}
