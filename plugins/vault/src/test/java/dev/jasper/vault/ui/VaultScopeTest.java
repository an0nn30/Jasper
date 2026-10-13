package dev.jasper.vault.ui;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.testing.FakeNotifications;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.vault.lock.LockManager;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.model.SshKey;
import dev.jasper.vault.service.VaultService;
import dev.jasper.vault.store.DeviceSecrets;
import dev.jasper.vault.store.FileStore;
import dev.jasper.vault.store.KeychainStore;
import dev.jasper.vault.store.VaultFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class VaultScopeTest {
    static final UUID PROD = UUID.randomUUID(), BASTION = UUID.randomUUID(), KEY = UUID.randomUUID();

    static WindowHandle window() {
        return new WindowHandle() {
            final UUID id = UUID.randomUUID();
            @Override public UUID id() { return id; }
            @Override public List<TabHandle> tabs() { return List.of(); }
            @Override public Optional<TabHandle> activeTab() { return Optional.empty(); }
            @Override public boolean isActive() { return true; }
            @Override public boolean isOpen() { return true; }
            @Override public void toFront() { }
        };
    }

    final WindowHandle window = window();
    final PaletteQuery query = new PaletteQuery(window, Optional.empty(), 5, true);
    final FakePluginHost host = new FakePluginHost();
    final dev.jasper.sdk.ui.Platform platform = host.start(new PluginInfo("dev.jasper.vault", "Vault", "0.2.0", Set.of()),
        Set.of(), Set.of(), context -> { }).platform();
    final List<String> opened = new ArrayList<>();
    final FakeNotifications notifications = new FakeNotifications();
    LockManager lock;
    VaultScope scope;

    @AfterEach void closeHost() { host.close(); }

    VaultScope scope(Path dir) {
        var keychain = new KeychainStore("Linux", command -> { throw new UncheckedIOException(new IOException("none")); }, dir.resolve("x"));
        lock = new LockManager(new VaultFile(dir.resolve("vault.jv")), new DeviceSecrets(keychain, new FileStore(dir.resolve("device.secret"))), Runnable::run, Runnable::run, state -> { });
        var service = new VaultService(lock, () -> Optional.of(window), prompt -> { }, prompt -> { }, prompt -> { },
            notifications.group("dev.jasper.vault"));
        scope = new VaultScope(lock, service, platform, (owner, id) -> opened.add(id.map(UUID::toString).orElse("-")));
        return scope;
    }

    void populate() {
        lock.create("pw".toCharArray(), false).join();
        lock.vault().accounts().add(new Account(PROD, "prod", "deploy", new Auth.Password("s3cret".toCharArray()), Instant.EPOCH, Instant.EPOCH));
        lock.vault().accounts().add(new Account(BASTION, "bastion", "ops", new Auth.Key(Path.of("/k"), null), Instant.EPOCH, Instant.EPOCH));
        lock.vault().keys().add(new SshKey(KEY, "laptop", "ed25519", "SHA256:abc", "", Path.of("/k"), Path.of("/k.pub"), Instant.EPOCH));
    }

    static PaletteRow row(List<PaletteRow> rows, String id) { return rows.stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow(); }

    @Test void specNamesTheOpenActionAndThreeVerbs(@TempDir Path dir) {
        scope(dir);
        assertThat(scope.spec().id()).isEqualTo(VaultScope.ID);
        assertThat(scope.spec().inAll()).isFalse();
        assertThat(scope.spec().verbs()).containsExactly(VaultScope.COPY_PASSWORD, VaultScope.COPY_USERNAME, VaultScope.OPEN);
        assertThat(scope.spec().shortcutActionId()).contains("dev.jasper.vault.open");
    }

    @Test void lockedAndMissingVaultsShowOneRowWhoseEnterOpensTheVault(@TempDir Path dir) {
        scope(dir);
        List<PaletteRow> rows = scope.search("anything", query).rows();
        assertThat(rows).singleElement().satisfies(row -> { assertThat(row.id()).isEqualTo(VaultScope.LOCKED_ROW); assertThat(row.title()).contains("No vault yet"); });
        assertThat(scope.available(rows.getFirst(), VaultScope.COPY_PASSWORD, query)).isTrue();
        scope.execute(rows.getFirst(), VaultScope.COPY_PASSWORD, query);
        assertThat(opened).containsExactly("-");
        populate();
        lock.lock();
        rows = scope.search("", query).rows();
        assertThat(rows.getFirst().title()).contains("locked");
        assertThat(host.clipboard().copies()).isEmpty();
    }

    @Test void rowsFilterByNameAndUsernameAndVerbsFollowTheKind(@TempDir Path dir) {
        scope(dir);
        populate();
        List<PaletteRow> all = scope.search("", query).rows();
        assertThat(all).extracting(PaletteRow::title).containsExactly("prod", "bastion", "laptop");
        assertThat(all.get(0).detail()).contains("deploy");
        assertThat(all.get(0).tag()).contains("password");
        assertThat(all.get(1).tag()).contains("key");
        assertThat(all.get(2).tag()).contains("ssh key");
        assertThat(scope.search("ops", query).rows()).extracting(PaletteRow::title).containsExactly("bastion");
        assertThat(scope.search("LAP", query).rows()).extracting(PaletteRow::title).containsExactly("laptop");
        assertThat(scope.search("nothing", query).rows()).isEmpty();
        assertThat(scope.search("", new PaletteQuery(window, Optional.empty(), 2, true)).rows()).hasSize(2);
        PaletteRow prod = row(all, "cred." + PROD), bastion = row(all, "cred." + BASTION), laptop = row(all, "cred." + KEY);
        assertThat(scope.available(prod, VaultScope.COPY_PASSWORD, query)).isTrue();
        assertThat(scope.available(bastion, VaultScope.COPY_PASSWORD, query)).as("a key account has no password").isFalse();
        assertThat(scope.available(bastion, VaultScope.COPY_USERNAME, query)).isTrue();
        assertThat(scope.available(laptop, VaultScope.COPY_USERNAME, query)).as("a bare key has no username").isFalse();
        assertThat(scope.available(laptop, VaultScope.OPEN, query)).isTrue();
    }

    @Test void staleRowsCannotCopyAfterAuthChangesDeletionOrLock(@TempDir Path dir) {
        scope(dir);
        populate();
        PaletteRow saved = scope.search("prod", query).rows().getFirst();
        lock.vault().remove(PROD);
        assertThat(scope.available(saved, VaultScope.COPY_PASSWORD, query)).isFalse();
        scope.execute(saved, VaultScope.COPY_PASSWORD, query);
        assertThat(host.clipboard().copies()).isEmpty();
        lock.vault().accounts().add(new Account(PROD, "prod", "deploy", new Auth.Key(Path.of("/k"), null), Instant.EPOCH, Instant.EPOCH));
        assertThat(scope.available(saved, VaultScope.COPY_PASSWORD, query)).isFalse();
        lock.lock();
        assertThat(scope.available(saved, VaultScope.COPY_USERNAME, query)).isFalse();
    }

    @Test void copyingPutsASecretOnTheClipboardThatExpiresAndOpenSelectsTheRow(@TempDir Path dir) {
        scope(dir);
        populate();
        List<PaletteRow> all = scope.search("", query).rows();
        scope.execute(row(all, "cred." + PROD), VaultScope.COPY_PASSWORD, query);
        assertThat(host.clipboard().text()).contains("s3cret");
        scope.execute(row(all, "cred." + PROD), VaultScope.COPY_USERNAME, query);
        assertThat(host.clipboard().text()).contains("deploy");
        assertThat(host.clipboard().copies()).as("usernames expire too")
            .containsExactly("secret|dev.jasper.vault|s3cret", "secret|dev.jasper.vault|deploy");
        host.clipboard().expireSecrets();
        assertThat(host.clipboard().text()).contains("");
        scope.execute(row(all, "cred." + KEY), VaultScope.OPEN, query);
        assertThat(opened).containsExactly(KEY.toString());
        int[] changes = {0};
        scope.onChanged(() -> changes[0]++);
        scope.changed();
        assertThat(changes[0]).isEqualTo(1);
    }

    @Test void aCopyThatFailsIsReportedByJasper(@TempDir Path dir) {
        scope(dir);
        populate();
        host.clipboard().failWrites(true);
        scope.execute(row(scope.search("", query).rows(), "cred." + PROD), VaultScope.COPY_USERNAME, query);
        assertThat(host.notifications().lines()).singleElement().asString()
            .startsWith("dev.jasper.vault|ERROR|Could not copy|").endsWith("|IllegalStateException");
        assertThat(host.clipboard().text()).isEmpty();
    }
}
