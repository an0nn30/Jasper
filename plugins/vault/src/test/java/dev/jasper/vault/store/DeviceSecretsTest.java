package dev.jasper.vault.store;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class DeviceSecretsTest {
    @Test void prefersTheKeychainAndCreatesOnce(@TempDir Path dir) throws Exception {
        var tool = new KeychainStoreTest.Tool();
        var keychain = new KeychainStore("Linux", tool, dir.resolve("x"));
        var secrets = new DeviceSecrets(keychain, new FileStore(dir.resolve("device.secret")));
        tool.answers.put("secret-tool store", new KeychainStore.Output(0, ""));
        assertThat(secrets.existing()).isEmpty();
        byte[] created = secrets.getOrCreate();
        assertThat(created).hasSize(32);
        String stored = tool.calls.getLast().stdin();
        tool.answers.put("secret-tool lookup", new KeychainStore.Output(0, stored));
        assertThat(secrets.getOrCreate()).isEqualTo(created);
        assertThat(secrets.source()).isEqualTo("keychain");
        assertThat(dir.resolve("device.secret")).doesNotExist();
    }

    @Test void fallsBackToTheFileWhenTheToolIsMissing(@TempDir Path dir) throws Exception {
        var keychain = new KeychainStore("Linux", command -> { throw new java.io.UncheckedIOException(new java.io.IOException("secret-tool: not found")); }, dir.resolve("x"));
        var secrets = new DeviceSecrets(keychain, new FileStore(dir.resolve("device.secret")));
        byte[] created = secrets.getOrCreate();
        assertThat(dir.resolve("device.secret")).exists();
        assertThat(secrets.existing()).contains(created);
        assertThat(secrets.source()).isEqualTo("file (no keychain found)");
    }
}
