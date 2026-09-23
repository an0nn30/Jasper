package dev.jasper.remote;
import dev.jasper.remote.hosts.*;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.vault.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.remote.ui.UiTestAccess.*;
import static org.assertj.core.api.Assertions.*;
class ConfigImportControllerTest {
    @Test void waitsForDurableVaultResultThenRepairsExistingHost(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("config"), "Host prod\n HostName new\n IdentityFile key\n");
        try (var app = new FakePluginHost()) {
            var vault = new FakeVault(); vault.pendingImport = new CompletableFuture<>();
            app.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            var remote = new RemotePlugin(Runnable::run, c -> Optional.empty(), (d, r) -> () -> {}, dir);
            app.start(RemotePluginTest.INFO, Set.of(), Set.of("dev.jasper.vault"), remote);
            var old = RemoteHost.create("prod", "old", 22, "u", Auth.AGENT, "My group", Optional.empty()).withFavorite(true);
            remote.store().put(old); RemotePluginTest.settle(app);
            var window = app.addTerminalWindow(); app.invoke(RemotePlugin.IMPORT, window, null); RemotePluginTest.settle(app);
            var panel = remote.currentImport();
            assertThat(importCheck(panel, 0).isSelected()).isFalse(); importCheck(panel, 0).setSelected(true);
            importButton(panel).doClick(); RemotePluginTest.settle(app);
            assertThat(remote.store().hosts()).containsExactly(old);
            var source = vault.importedSources.getFirst(); UUID id = UUID.randomUUID();
            vault.pendingImport.complete(Optional.of(new SshKeyImportResult(Map.of(source.requestId(), new CredentialDescriptor(id, "key", "fp", Kind.SSH_KEY, true)), List.of())));
            RemotePluginTest.settle(app);
            assertThat(remote.store().hosts()).singleElement().satisfies(h -> {
                assertThat(h.id()).isEqualTo(old.id()); assertThat(h.group()).isEqualTo("My group"); assertThat(h.favorite()).isTrue();
                assertThat(h.auth()).isEqualTo(new Auth.VaultKeys(List.of(id))); assertThat(h.hostname()).isEqualTo("new");
            });
        }
    }
    @Test void noKeyRequiresChoiceAndCancelWithdrawsPendingImport(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("config"), "Host prod\n User me\n");
        try (var app = new FakePluginHost()) {
            var vault = new FakeVault(); vault.password("login", "me", "secret"); vault.pendingImport = new CompletableFuture<>();
            app.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            var remote = new RemotePlugin(Runnable::run, c -> Optional.empty(), (d, r) -> () -> {}, dir);
            app.start(RemotePluginTest.INFO, Set.of(), Set.of("dev.jasper.vault"), remote);
            var window = app.addTerminalWindow(); app.invoke(RemotePlugin.IMPORT, window, null); RemotePluginTest.settle(app);
            var panel = remote.currentImport(); importButton(panel).doClick();
            assertThat(importStatus(panel)).contains("Choose"); assertThat(remote.store().hosts()).isEmpty();
            chooseCredential(panel, 0).doClick(); importButton(panel).doClick(); cancelImport(panel).doClick();
            RemotePluginTest.settle(app);
            assertThat(vault.pendingImport).isCancelled(); assertThat(remote.store().hosts()).isEmpty();
        }
    }
    @Test void hostSaveFailureRetainsKeysAndRefreshReadsExternalChanges(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("config"), "Host prod\n IdentityFile key\n");
        try (var app = new FakePluginHost()) {
            var vault = new FakeVault(); vault.pendingImport = new CompletableFuture<>();
            app.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            var remote = new RemotePlugin(Runnable::run, c -> Optional.empty(), (d, r) -> () -> {}, dir);
            app.start(RemotePluginTest.INFO, Set.of(), Set.of("dev.jasper.vault"), remote);
            var old = RemoteHost.create("prod", "old", 22, "u", Auth.AGENT, "G", Optional.empty());
            remote.store().put(old); RemotePluginTest.settle(app);
            var window = app.addTerminalWindow(); app.invoke(RemotePlugin.IMPORT, window, null); RemotePluginTest.settle(app);
            var panel = remote.currentImport(); importCheck(panel, 0).setSelected(true); importButton(panel).doClick();
            var external = old.withFavorite(true); String outside = HostFile.format(List.of(external));
            Files.writeString(remote.store().file(), outside);
            var source = vault.importedSources.getFirst();
            vault.pendingImport.complete(Optional.of(new SshKeyImportResult(Map.of(source.requestId(), new CredentialDescriptor(UUID.randomUUID(), "key", "fp", Kind.SSH_KEY, true)), List.of())));
            RemotePluginTest.settle(app);
            assertThat(Files.readString(remote.store().file())).isEqualTo(outside);
            assertThat(importStatus(panel)).contains("not saved");
            refreshImport(panel).doClick(); RemotePluginTest.settle(app);
            assertThat(remote.store().hosts()).containsExactly(external);
        }
    }

}
