package dev.jasper.vault.api;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CredentialTest {
    @Test void closeZeroesAndBlocksEveryAccessor() {
        char[] password = "pw".toCharArray(), passphrase = "pp".toCharArray();
        var credential = new Credential(UUID.randomUUID(), "prod", Kind.ACCOUNT_KEY_AND_PASSWORD, "deploy", password, Path.of("/k"), passphrase);
        assertThat(credential.username()).contains("deploy");
        assertThat(credential.password()).isSameAs(password);
        assertThat(credential.keyPath()).contains(Path.of("/k"));
        credential.close();
        assertThat(password).containsOnly((char) 0);
        assertThat(passphrase).containsOnly((char) 0);
        assertThatThrownBy(credential::password).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(credential::username).isInstanceOf(IllegalStateException.class);
        credential.close();
    }

    @Test void aBareKeyHasNoUsernameOrPassword() {
        var credential = new Credential(UUID.randomUUID(), "laptop", Kind.SSH_KEY, null, null, Path.of("/k"), null);
        assertThat(credential.username()).isEmpty();
        assertThat(credential.password()).isNull();
        assertThat(credential.passphrase()).isNull();
        assertThat(credential.toString()).doesNotContain("pw").contains("laptop");
    }
}
