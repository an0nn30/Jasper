package dev.jasper.remote.edit;

import dev.jasper.remote.sftp.FileEndpoint;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.sftp.WriteHandle;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/** Forwards to another endpoint, records each call, and lets a test change stats or fail a replacing publish. */
final class TestEndpoint implements FileEndpoint {
    final FileEndpoint delegate;
    final List<String> calls = new CopyOnWriteArrayList<>();
    final CountDownLatch aborted = new CountDownLatch(1);
    volatile UnaryOperator<FileEntry> stats = UnaryOperator.identity();
    volatile IOException replaceFailure;
    volatile IOException createFailure;

    TestEndpoint(FileEndpoint delegate) { this.delegate = delegate; }

    boolean wasAborted() { return aborted.getCount() == 0; }

    @Override public String id() { return delegate.id(); }
    @Override public String child(String directory, String name) throws IOException { return delegate.child(directory, name); }
    @Override public String parent(String path) throws IOException { return delegate.parent(path); }
    @Override public FileEntry stat(String path) throws IOException { calls.add("stat " + path); return stats.apply(delegate.stat(path)); }
    @Override public void list(String directory, Consumer<FileEntry> visitor) throws IOException { calls.add("list " + directory); delegate.list(directory, visitor); }
    @Override public InputStream read(String path, long offset) throws IOException { calls.add("read " + path); return delegate.read(path, offset); }
    @Override public WriteHandle write(String path, long offset, boolean createExclusive) throws IOException {
        calls.add("write " + path); return delegate.write(path, offset, createExclusive);
    }
    @Override public WriteHandle create(String path, int permissions) throws IOException {
        calls.add("create " + path + " " + Integer.toOctalString(permissions));
        if (createFailure != null) throw createFailure;
        return delegate.create(path, permissions);
    }
    @Override public boolean writable(String path) throws IOException { calls.add("writable " + path); return delegate.writable(path); }
    @Override public void truncate(String path, long size) throws IOException { calls.add("truncate " + path); delegate.truncate(path, size); }
    @Override public void mkdir(String path) throws IOException { delegate.mkdir(path); }
    @Override public void symlink(String path, String target) throws IOException { delegate.symlink(path, target); }
    @Override public void remove(String path, boolean directory) throws IOException { calls.add("remove " + path); delegate.remove(path, directory); }
    @Override public void publish(String temporary, String target, boolean replace) throws IOException {
        calls.add("publish " + target);
        if (replace && replaceFailure != null) throw replaceFailure;
        delegate.publish(temporary, target, replace);
    }
    @Override public void metadata(String path, long modifiedMillis, int permissions) throws IOException {
        calls.add("metadata " + path); delegate.metadata(path, modifiedMillis, permissions);
    }
    @Override public String canonical(String path) throws IOException { return delegate.canonical(path); }
    @Override public String resolveDirectory(String path) throws IOException { return delegate.resolveDirectory(path); }
    @Override public String home() throws IOException { return delegate.home(); }
    @Override public void abort() { calls.add("abort"); aborted.countDown(); delegate.abort(); }
    @Override public void close() { abort(); }
}
