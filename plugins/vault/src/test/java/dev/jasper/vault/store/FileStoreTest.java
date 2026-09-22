package dev.jasper.vault.store;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class FileStoreTest {
    @Test void roundTripsAndDeletes(@TempDir Path dir) throws Exception {
        var store = new FileStore(dir.resolve("device.secret"));
        assertThat(store.read()).isEmpty();
        byte[] secret = new byte[32]; secret[0] = 42;
        store.write(secret);
        assertThat(store.read()).contains(secret);
        assertThat(store.description()).isEqualTo("file (no keychain found)");
        store.delete();
        assertThat(store.read()).isEmpty();
    }
}
