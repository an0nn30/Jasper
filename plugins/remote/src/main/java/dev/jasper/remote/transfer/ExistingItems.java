package dev.jasper.remote.transfer;

import dev.jasper.remote.sftp.FileEndpoint;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;

/** Which selected top-level names already exist in a destination directory: one stat each, never a listing. */
public final class ExistingItems {
    private ExistingItems() { }

    public static List<String> existing(FileEndpoint destination, String directory, List<String> names) throws IOException {
        var found = new ArrayList<String>();
        for (String name : names) {
            try { destination.stat(destination.child(directory, name)); found.add(name); }
            catch (NoSuchFileException absent) { /* free to copy */ }
        }
        return List.copyOf(found);
    }
}
