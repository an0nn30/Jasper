package dev.jasper.vault.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class KeychainStoreTest {
    static final byte[] SECRET = new byte[32];
    static { SECRET[1] = 7; }
    static final String B64 = Base64.getEncoder().encodeToString(SECRET);

    /** A scripted tool: records commands, answers from a map keyed by the first two arguments. */
    static final class Tool implements java.util.function.Function<KeychainStore.Command, KeychainStore.Output> {
        final List<KeychainStore.Command> calls = new ArrayList<>();
        final java.util.Map<String, KeychainStore.Output> answers = new java.util.HashMap<>();
        @Override public KeychainStore.Output apply(KeychainStore.Command command) {
            calls.add(command);
            return answers.getOrDefault(command.arguments().get(0) + " " + command.arguments().get(1), new KeychainStore.Output(1, ""));
        }
    }

    @Test void macOsUsesSecurityGenericPasswords(@TempDir Path dir) throws Exception {
        var tool = new Tool();
        var store = new KeychainStore("Mac OS X", tool, dir.resolve("device.dpapi"));
        tool.answers.put("security find-generic-password", new KeychainStore.Output(44, "security: SecKeychainSearchCopyNext: The specified item could not be found in the keychain."));
        assertThat(store.read()).as("exit 44 = not found").isEmpty();
        tool.answers.put("security find-generic-password", new KeychainStore.Output(0, B64 + "\n"));
        assertThat(store.read()).contains(SECRET);
        tool.answers.put("security add-generic-password", new KeychainStore.Output(0, ""));
        store.write(SECRET);
        store.delete();
        assertThat(tool.calls.get(0).arguments()).containsExactly("security", "find-generic-password", "-s", KeychainStore.SERVICE, "-a", KeychainStore.ACCOUNT, "-w");
        assertThat(tool.calls.get(2).arguments()).containsExactly("security", "add-generic-password", "-U", "-s", KeychainStore.SERVICE, "-a", KeychainStore.ACCOUNT, "-w", B64);
        assertThat(tool.calls.get(3).arguments()).startsWith("security", "delete-generic-password");
        assertThat(store.description()).isEqualTo("keychain");
    }

    @Test void linuxUsesSecretToolWithTheSecretOnStdin(@TempDir Path dir) throws Exception {
        var tool = new Tool();
        var store = new KeychainStore("Linux", tool, dir.resolve("device.dpapi"));
        tool.answers.put("secret-tool lookup", new KeychainStore.Output(0, B64));
        tool.answers.put("secret-tool store", new KeychainStore.Output(0, ""));
        assertThat(store.read()).contains(SECRET);
        store.write(SECRET);
        assertThat(tool.calls.get(1).arguments()).startsWith("secret-tool", "store", "--label=" + KeychainStore.SERVICE);
        assertThat(tool.calls.get(1).stdin()).isEqualTo(B64);
    }

    @Test void windowsProtectsAFileWithDpapi(@TempDir Path dir) throws Exception {
        var tool = new Tool();
        Path file = dir.resolve("device.dpapi");
        var store = new KeychainStore("Windows 11", tool, file);
        assertThat(store.read()).as("no file yet: no tool call").isEmpty();
        assertThat(tool.calls).isEmpty();
        tool.answers.put("powershell -NoProfile", new KeychainStore.Output(0, B64));
        store.write(SECRET);
        assertThat(tool.calls.get(0).arguments().get(3)).contains("ProtectedData]::Protect", B64, file.toString().replace("'", "''"));
        Files.writeString(file, "x");
        assertThat(store.read()).contains(SECRET);
        store.delete();
        assertThat(file).doesNotExist();
    }

    @Test void aFailingToolIsAnIOException(@TempDir Path dir) {
        var tool = new Tool();
        tool.answers.put("security find-generic-password", new KeychainStore.Output(36, "security: SecKeychainSearchCopyNext: failed"));
        var store = new KeychainStore("Mac OS X", tool, dir.resolve("device.dpapi"));
        assertThatThrownBy(store::read).isInstanceOf(IOException.class).hasMessageContaining("security");
        assertThatThrownBy(() -> store.write(SECRET)).isInstanceOf(IOException.class);
    }

    /** Opt in with -Djasper.vault.keychainTest=true: touches the real keychain under a throwaway service name. */
    @Test void realKeychainRoundTrip(@TempDir Path dir) throws Exception {
        assumeTrue(Boolean.getBoolean("jasper.vault.keychainTest"));
        var store = new KeychainStore(System.getProperty("os.name"), KeychainStore.processTool(), dir.resolve("device.dpapi"), KeychainStore.SERVICE + " test");
        try {
            store.write(SECRET);
            assertThat(store.read()).contains(SECRET);
        } finally {
            store.delete();
        }
        assertThat(store.read()).isEmpty();
    }
}
