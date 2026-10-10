package dev.jasper.remote;
import dev.jasper.remote.hosts.*;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.vault.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.remote.testsupport.Kits.edt;
import static dev.jasper.remote.ui.UiTestAccess.*;
import static org.assertj.core.api.Assertions.*;
/**
 * The import preview is a kit view (Plan 2 Task 8a), so each body runs on the EDT: nothing here waits on a network or
 * worker thread, and the plugin's continuations (Vault futures completed by the test) run inline there.
 */
class ConfigImportControllerTest {
    @Test void replacementAndRefreshWithdrawOnlyTheirOwnPicker(@TempDir Path dir) throws Exception {
        edt(() -> {
            Files.writeString(dir.resolve("config"),"Host one\n HostName one.invalid\n User fixture\nHost two\n HostName two.invalid\n User fixture\n");
            try(var app=new FakePluginHost()) {
                var vault=new FakeVault();vault.password("login","fixture","synthetic");vault.pendingPick=new CompletableFuture<>();
                app.start(FakeVault.INFO,Set.of(),Set.of(),vault);
                var remote=new TestRemotePlugin(dev.jasper.remote.testsupport.Kits.UI,c->Optional.empty(),(d,r)->()->{},dir);
                app.start(RemotePluginTest.INFO,Set.of(),Set.of("dev.jasper.vault"),remote);
                var window=app.addTerminalWindow();app.invoke(RemotePlugin.IMPORT,window,null);RemotePluginTest.settle(app);
                var panel=remote.currentImport();chooseCredential(panel,0);var first=vault.pendingPick;
                vault.pendingPick=new CompletableFuture<>();chooseCredential(panel,1);assertThat(first).isCancelled();
                var second=vault.pendingPick;refreshImport(panel);assertThat(second).isCancelled();
                assertThat(importStatus(panel)).contains("Refreshing");
                RemotePluginTest.settle(app);assertThat(remote.currentImport()).isNotSameAs(panel);
            }
        });
    }

    @Test void reentrantRefreshBeforeLegacyPickReturnsWithdrawsTheReturnedFuture(@TempDir Path dir)throws Exception{
        edt(()->{Files.writeString(dir.resolve("config"),"Host one\n HostName one.invalid\n User fixture\n");try(var app=new FakePluginHost()){
            var vault=new FakeVault();vault.password("login","fixture","synthetic");vault.pendingPick=new CompletableFuture<>();app.start(FakeVault.INFO,Set.of(),Set.of(),vault);
            var remote=new TestRemotePlugin(dev.jasper.remote.testsupport.Kits.UI,c->Optional.empty(),(d,r)->()->{},dir);app.start(RemotePluginTest.INFO,Set.of(),Set.of("dev.jasper.vault"),remote);
            app.invoke(RemotePlugin.IMPORT,app.addTerminalWindow(),null);RemotePluginTest.settle(app);var old=remote.currentImport();vault.duringPick=()->refreshImport(old);
            chooseCredential(old,0);assertThat(vault.pendingPick).isCancelled();RemotePluginTest.settle(app);assertThat(remote.currentImport()).isNotSameAs(old);assertThat(importStatus(remote.currentImport())).doesNotContain("could not");
        }});
    }
    @Test void failedRefreshKeepsOldRowsInertAndLatePickerCannotApply(@TempDir Path dir)throws Exception{
        edt(()->{Files.writeString(dir.resolve("config"),"Host one\n HostName one.invalid\n User fixture\n");try(var app=new FakePluginHost()){
            var vault=new FakeVault();var id=vault.password("login","fixture","synthetic");vault.pendingPick=new CompletableFuture<>(){@Override public boolean cancel(boolean ignored){return false;}};app.start(FakeVault.INFO,Set.of(),Set.of(),vault);
            var remote=new TestRemotePlugin(dev.jasper.remote.testsupport.Kits.UI,c->Optional.empty(),(d,r)->()->{},dir);app.start(RemotePluginTest.INFO,Set.of(),Set.of("dev.jasper.vault"),remote);
            app.invoke(RemotePlugin.IMPORT,app.addTerminalWindow(),null);RemotePluginTest.settle(app);var old=remote.currentImport();chooseCredential(old,0);
            Files.writeString(remote.store().file(),"[broken");refreshImport(old);RemotePluginTest.settle(app);vault.pendingPick.complete(Optional.of(vault.descriptors.get(id)));
            assertThat(importStatus(old)).contains("could not be read");assertThat(dev.jasper.remote.testsupport.Kits.node(old.view(),"import.choose.0",dev.jasper.sdk.ui.Nodes.Choice.class).state().enabled()).isFalse();
            chooseCredential(old,0);importButton(old);assertThat(remote.store().hosts()).isEmpty();
            Files.writeString(remote.store().file(),HostFile.format(List.of()));refreshImport(old);RemotePluginTest.settle(app);assertThat(remote.currentImport()).isNotSameAs(old);
        }});
    }
    @Test void waitsForDurableVaultResultThenRepairsExistingHost(@TempDir Path dir) throws Exception {
        edt(() -> {
            Files.writeString(dir.resolve("config"), "Host prod\n HostName new\n IdentityFile key\n");
            try (var app = new FakePluginHost()) {
                var vault = new FakeVault(); vault.pendingImport = new CompletableFuture<>();
                app.start(FakeVault.INFO, Set.of(), Set.of(), vault);
                var remote = new TestRemotePlugin(dev.jasper.remote.testsupport.Kits.UI, c -> Optional.empty(), (d, r) -> () -> {}, dir);
                app.start(RemotePluginTest.INFO, Set.of(), Set.of("dev.jasper.vault"), remote);
                var old = RemoteHost.create("prod", "old", 22, "u", Auth.AGENT, "My group", Optional.empty()).withFavorite(true);
                remote.store().put(old); RemotePluginTest.settle(app);
                var window = app.addTerminalWindow(); app.invoke(RemotePlugin.IMPORT, window, null); RemotePluginTest.settle(app);
                var panel = remote.currentImport();
                assertThat(importChecked(panel, 0)).as("an existing host needs an explicit Update").isFalse(); importCheck(panel, 0, true);
                importButton(panel); RemotePluginTest.settle(app);
                assertThat(remote.store().hosts()).containsExactly(old);
                refreshImport(panel);chooseCredential(panel,0);assertThat(remote.currentImport()).isSameAs(panel);assertThat(vault.pendingImport).isNotCancelled();
                var source = vault.importedSources.getFirst(); UUID id = UUID.randomUUID();
                vault.pendingImport.complete(Optional.of(new SshKeyImportResult(Map.of(source.requestId(), new CredentialDescriptor(id, "key", "fp", Kind.SSH_KEY, true)), List.of())));
                RemotePluginTest.settle(app);
                assertThat(remote.store().hosts()).singleElement().satisfies(h -> {
                    assertThat(h.id()).isEqualTo(old.id()); assertThat(h.group()).isEqualTo("My group"); assertThat(h.favorite()).isTrue();
                    assertThat(h.auth()).isEqualTo(new Auth.VaultKeys(List.of(id))); assertThat(h.hostname()).isEqualTo("new");
                });
            }
        });
    }
    @Test void noKeyRequiresChoiceAndCancelWithdrawsPendingImport(@TempDir Path dir) throws Exception {
        edt(() -> {
            Files.writeString(dir.resolve("config"), "Host prod\n User me\n");
            try (var app = new FakePluginHost()) {
                var vault = new FakeVault(); vault.password("login", "me", "secret"); vault.pendingImport = new CompletableFuture<>();
                app.start(FakeVault.INFO, Set.of(), Set.of(), vault);
                var remote = new TestRemotePlugin(dev.jasper.remote.testsupport.Kits.UI, c -> Optional.empty(), (d, r) -> () -> {}, dir);
                app.start(RemotePluginTest.INFO, Set.of(), Set.of("dev.jasper.vault"), remote);
                var window = app.addTerminalWindow(); app.invoke(RemotePlugin.IMPORT, window, null); RemotePluginTest.settle(app);
                var panel = remote.currentImport(); importButton(panel);
                assertThat(importStatus(panel)).contains("Choose"); assertThat(remote.store().hosts()).isEmpty();
                chooseCredential(panel, 0); importButton(panel); cancelImport(panel);
                RemotePluginTest.settle(app);
                assertThat(vault.pendingImport).isCancelled(); assertThat(remote.store().hosts()).isEmpty();
            }
        });
    }
    @Test void hostSaveFailureRetainsKeysAndRefreshReadsExternalChanges(@TempDir Path dir) throws Exception {
        edt(() -> {
            Files.writeString(dir.resolve("config"), "Host prod\n IdentityFile key\n");
            try (var app = new FakePluginHost()) {
                var vault = new FakeVault(); vault.pendingImport = new CompletableFuture<>();
                app.start(FakeVault.INFO, Set.of(), Set.of(), vault);
                var remote = new TestRemotePlugin(dev.jasper.remote.testsupport.Kits.UI, c -> Optional.empty(), (d, r) -> () -> {}, dir);
                app.start(RemotePluginTest.INFO, Set.of(), Set.of("dev.jasper.vault"), remote);
                var old = RemoteHost.create("prod", "old", 22, "u", Auth.AGENT, "G", Optional.empty());
                remote.store().put(old); RemotePluginTest.settle(app);
                var window = app.addTerminalWindow(); app.invoke(RemotePlugin.IMPORT, window, null); RemotePluginTest.settle(app);
                var panel = remote.currentImport(); importCheck(panel, 0, true); importButton(panel);
                var external = old.withFavorite(true); String outside = HostFile.format(List.of(external));
                Files.writeString(remote.store().file(), outside);
                var source = vault.importedSources.getFirst();
                vault.pendingImport.complete(Optional.of(new SshKeyImportResult(Map.of(source.requestId(), new CredentialDescriptor(UUID.randomUUID(), "key", "fp", Kind.SSH_KEY, true)), List.of())));
                RemotePluginTest.settle(app);
                assertThat(Files.readString(remote.store().file())).isEqualTo(outside);
                assertThat(importStatus(panel)).contains("not saved");
                refreshImport(panel); RemotePluginTest.settle(app);
                assertThat(remote.store().hosts()).containsExactly(external);
            }
        });
    }

}
