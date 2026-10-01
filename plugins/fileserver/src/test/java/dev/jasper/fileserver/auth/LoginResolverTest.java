package dev.jasper.fileserver.auth;

import dev.jasper.fileserver.FakeVault;
import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.vault.api.LockState;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginResolverTest {
    static final Path FOLDER = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath();
    final FakeVault vault = new FakeVault();

    static ServerDefinition with(Login login) {
        var base = ServerDefinition.create("docs", Protocol.HTTP, FOLDER);
        return new ServerDefinition(base.id(), base.name(), base.protocol(), base.folder(), base.bind(), base.port(),
            base.passiveRange(), base.writable(), login, List.of(), false, false, false);
    }

    LoginResolver resolver(Optional<FakeVault> withVault) {
        return new LoginResolver(withVault.map(FakeVault::api), () -> "dustin", () -> "generated-password-1");
    }

    static String failure(CompletableFuture<ResolvedLogin> future) {
        assertThat(future).isCompletedExceptionally();
        try { future.join(); throw new AssertionError("expected a failure"); }
        catch (CompletionException failed) {
            assertThat(failed.getCause()).isInstanceOf(LoginUnavailableException.class);
            return failed.getCause().getMessage();
        }
    }

    @Test void anonymousAndSessionLoginsNeedNoVault() {
        var resolver = resolver(Optional.empty());
        assertThat(resolver.resolve(with(Login.ANONYMOUS), null).join().isAnonymous()).isTrue();
        ResolvedLogin session = resolver.resolve(with(Login.SESSION), null).join();
        assertThat(session.user()).contains("dustin");
        assertThat(session.revealPassword()).contains("generated-password-1");
        assertThat(resolver.vaultPresent()).isFalse();
        assertThat(failure(resolver.resolve(with(new Login.Vault(UUID.randomUUID())), null))).isEqualTo(LoginResolver.NO_VAULT);
    }

    @Test void aVaultAccountSuppliesTheUserAndPassword() {
        UUID id = vault.password("Share login", "guest", "pw");
        var resolver = resolver(Optional.of(vault));
        ResolvedLogin login = resolver.resolve(with(new Login.Vault(id)), null).join();
        assertThat(login.matches("guest", "pw".toCharArray())).isTrue();
        assertThat(resolver.needsUnlock(with(new Login.Vault(id)))).isFalse();
    }

    @Test void explainsWhyAVaultAccountIsUnavailable() {
        var resolver = resolver(Optional.of(vault));
        assertThat(failure(resolver.resolve(with(new Login.Vault(UUID.randomUUID())), null))).isEqualTo(LoginResolver.MISSING);
        UUID key = vault.key("Just a key");
        assertThat(failure(resolver.resolve(with(new Login.Vault(key)), null))).contains("Just a key", "no user name and password");
        UUID id = vault.password("Share login", "guest", "pw");
        vault.state = LockState.LOCKED;
        assertThat(resolver.needsUnlock(with(new Login.Vault(id)))).isTrue();
        assertThat(resolver.needsUnlock(with(Login.SESSION))).isFalse();
        assertThat(failure(resolver.resolve(with(new Login.Vault(id)), null))).isEqualTo(LoginResolver.NOT_PROVIDED);
        vault.unlockOnRequest = true;
        assertThat(resolver.resolve(with(new Login.Vault(id)), null).join().user()).contains("guest");
    }
}
