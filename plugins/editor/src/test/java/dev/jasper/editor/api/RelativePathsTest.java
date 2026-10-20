package dev.jasper.editor.api;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RelativePathsTest {
    @Test void acceptsDecodedRelativePathsWithoutDecodingThemAgain() {
        for (String path : List.of("a+b c.md", "a%20b.md", "../caf\u00e9/\u65e5\u672c.md", ".", "../../a", "dir/a:b", "dir/line\u2028.md", "dir/line\u2029.md", "\uD83C\uDF32.md"))
            assertThat(RelativePaths.requireValid(path)).isSameAs(path);
    }

    @Test void defaultProviderValidatesBeforeReportingUnsupported() {
        var document = new EditorDocument() {
            public String key() { return "custom:1"; }
            public String name() { return "readme.md"; }
            public String location() { return "custom"; }
            public Optional<String> host() { return Optional.empty(); }
            public CompletableFuture<Content> read(long limit) { throw new AssertionError("No I/O"); }
            public CompletableFuture<Optional<Revision>> revision() { throw new AssertionError("No I/O"); }
            public CompletableFuture<Revision> write(byte[] bytes) { throw new AssertionError("No I/O"); }
        };
        assertThat(document.resolveRelative("a+b%20c.md")).isEmpty();
        for (String path : List.of("", "/root", "//host/file", "C:/file", "C:file", "file:a",
                "C:/outside/line\u2028.txt", "C:/outside/line\u2029.txt", "C:line\u2028.txt", "C:line\u2029.txt",
                "javascript:line\u2028.txt", "javascript:line\u2029.txt", "a\\b", "a?b", "a#b",
                "a\u0000b", "a\u001Fb", "a\u007Fb", "a\u0085b", "a\uD800b")) {
            assertThatIllegalArgumentException().as(path).isThrownBy(() -> RelativePaths.requireValid(path));
            assertThatIllegalArgumentException().as(path).isThrownBy(() -> document.resolveRelative(path));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> document.resolveRelative(null));
    }
}
