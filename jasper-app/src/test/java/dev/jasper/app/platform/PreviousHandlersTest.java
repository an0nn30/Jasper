package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.platform.FileAssociations.Handler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreviousHandlersTest {
    @Test void otherAndNoneRoundTripThroughTheFile(@TempDir Path home) {
        Path file = home.resolve("file-associations.toml");
        var store = new PreviousHandlers(file);
        store.put("com.apple.dt.Xcode-type", new Handler.Other("com.apple.dt.Xcode", "Xcode"));
        store.put("public.json", new Handler.None());
        var again = new PreviousHandlers(file);
        assertThat(again.get("com.apple.dt.Xcode-type")).contains(new Handler.Other("com.apple.dt.Xcode", "Xcode"));
        assertThat(again.get("public.json")).contains(new Handler.None());
        assertThat(again.get("missing")).isEmpty();
        again.remove("public.json");
        assertThat(new PreviousHandlers(file).get("public.json")).isEmpty();
        assertThat(new PreviousHandlers(file).get("com.apple.dt.Xcode-type")).isPresent();
    }

    @Test void typesWithDotsSlashesAndQuotesSurvive(@TempDir Path home) {
        Path file = home.resolve("file-associations.toml");
        var store = new PreviousHandlers(file);
        var odd = new Handler.Other("a\"b\\c", "Na\"me\twith\ttabs");
        store.put("com.sun.java-source", odd);
        store.put("text/x-c++src", new Handler.Other("org.kde.kate.desktop", "Kate"));
        store.put("we\"ird", new Handler.Other("x", "y"));
        var again = new PreviousHandlers(file);
        assertThat(again.get("com.sun.java-source")).contains(odd);
        assertThat(again.get("text/x-c++src")).contains(new Handler.Other("org.kde.kate.desktop", "Kate"));
        assertThat(again.get("we\"ird")).contains(new Handler.Other("x", "y"));
    }

    @Test void anAtomicWriteLeavesNoTemporaryFileBehind(@TempDir Path home) throws Exception {
        var store = new PreviousHandlers(home.resolve("file-associations.toml"));
        store.put("a", new Handler.None());
        store.put("b", new Handler.Other("id", "Name"));
        store.remove("a");
        try (var files = Files.list(home)) {
            assertThat(files.map(p -> p.getFileName().toString()).toList()).isEqualTo(List.of("file-associations.toml"));
        }
    }

    @Test void anUnreadableFileIsKeptAsBadAndNotWipedByAWrite(@TempDir Path home) throws Exception {
        Path file = home.resolve("file-associations.toml");
        Files.writeString(file, "[[[not toml");
        var store = new PreviousHandlers(file);
        store.put("x", new Handler.None());
        assertThat(Files.readString(home.resolve("file-associations.toml.bad"))).isEqualTo("[[[not toml");
        assertThat(new PreviousHandlers(file).get("x")).contains(new Handler.None());
        assertThat(Files.readString(file)).doesNotContain("not toml");
    }
}
