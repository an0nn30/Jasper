package dev.jasper.editor.session;

import dev.jasper.editor.api.Content;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.api.Revision;
import dev.jasper.editor.api.TooLargeException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import static java.nio.charset.StandardCharsets.UTF_8;

/** A document whose futures complete at once unless held; the file lives in fields. */
final class FakeDocument implements EditorDocument {
    final String name, location;
    final Optional<String> host;
    byte[] bytes = new byte[0];
    long modified = 1_000;
    boolean exists = true;
    IOException readFailure, writeFailure;
    boolean holdReads, holdWrites;
    CompletableFuture<Content> heldRead;
    CompletableFuture<Revision> heldWrite;
    int reads, writes;

    FakeDocument(String name, String host) {
        this.name = name;
        this.host = Optional.ofNullable(host);
        this.location = host == null ? "/srv/app/" + name : "dustin@" + host + ":/srv/app/" + name;
    }

    FakeDocument text(String value) { bytes = value.getBytes(UTF_8); return this; }
    String text() { return new String(bytes, UTF_8); }
    Revision now() { return new Revision(modified, bytes.length); }
    void change(String value) { bytes = value.getBytes(UTF_8); modified += 1_000; exists = true; }

    @Override public String key() { return "fake:" + location; }
    @Override public String name() { return name; }
    @Override public String location() { return location; }
    @Override public Optional<String> host() { return host; }

    @Override public CompletableFuture<Content> read(long limitBytes) {
        reads++;
        if (holdReads) return heldRead = new CompletableFuture<>();
        if (readFailure != null) return CompletableFuture.failedFuture(readFailure);
        if (!exists) return CompletableFuture.failedFuture(new NoSuchFileException(location));
        if (bytes.length > limitBytes) return CompletableFuture.failedFuture(new TooLargeException(bytes.length, limitBytes));
        return CompletableFuture.completedFuture(new Content(bytes.clone(), now()));
    }

    @Override public CompletableFuture<Optional<Revision>> revision() {
        return CompletableFuture.completedFuture(exists ? Optional.of(now()) : Optional.empty());
    }

    @Override public CompletableFuture<Revision> write(byte[] content) {
        writes++;
        if (holdWrites) return heldWrite = new CompletableFuture<>();
        if (writeFailure != null) return CompletableFuture.failedFuture(writeFailure);
        bytes = content.clone();
        modified += 1_000;
        exists = true;
        return CompletableFuture.completedFuture(now());
    }
}
