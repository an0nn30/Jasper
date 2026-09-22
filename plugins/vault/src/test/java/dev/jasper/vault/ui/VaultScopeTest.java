package dev.jasper.vault.ui;

import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.WindowHandle;
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
import java.util.UUID;
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
    String clipboard = "";
    final List<Runnable> armed = new ArrayList<>();
    final List<String> opened = new ArrayList<>();
    LockManager lock;
    VaultScope scope;

    VaultScope scope(Path dir) {
        var keychain = new KeychainStore("Linux", command -> { throw new UncheckedIOException(new IOException("none")); }, dir.resolve("x"));
        lock = new LockManager(new VaultFile(dir.resolve("vault.jv")), new DeviceSecrets(keychain, new FileStore(dir.resolve("device.secret"))), Runnable::run, Runnable::run, state -> { });
        var service = new VaultService(lock, () -> Optional.of(window), prompt -> { }, prompt -> { }, prompt -> { }, notice -> { });
        scope = new VaultScope(lock, service, new SecretClipboard(text -> clipboard = text, () -> Optional.of(clipboard), armed::add),
            (owner, id) -> opened.add(id.map(UUID::toString).orElse("-")));
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
        assertThat(scope.spec().aliases()).containsExactly("vault", "cred");
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
        assertThat(clipboard).isEmpty();
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
        assertThat(clipboard).isEmpty();
        lock.vault().accounts().add(new Account(PROD, "prod", "deploy", new Auth.Key(Path.of("/k"), null), Instant.EPOCH, Instant.EPOCH));
        assertThat(scope.available(saved, VaultScope.COPY_PASSWORD, query)).isFalse();
        lock.lock();
        assertThat(scope.available(saved, VaultScope.COPY_USERNAME, query)).isFalse();
    }

    @Test void copyingPutsTheSecretOnTheClipboardWithAClearArmedAndOpenSelectsTheRow(@TempDir Path dir) {
        scope(dir);
        populate();
        List<PaletteRow> all = scope.search("", query).rows();
        scope.execute(row(all, "cred." + PROD), VaultScope.COPY_PASSWORD, query);
        assertThat(clipboard).isEqualTo("s3cret");
        assertThat(armed).hasSize(1);
        scope.execute(row(all, "cred." + PROD), VaultScope.COPY_USERNAME, query);
        assertThat(clipboard).isEqualTo("deploy");
        assertThat(armed).as("usernames expire too").hasSize(2);
        scope.execute(row(all, "cred." + KEY), VaultScope.OPEN, query);
        assertThat(opened).containsExactly(KEY.toString());
        int[] changes = {0};
        scope.onChanged(() -> changes[0]++);
        scope.changed();
        assertThat(changes[0]).isEqualTo(1);
    }
}
