package dev.jasper.fileserver.auth;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizedKeysTest {
    static final String ED25519 = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIAEgwWITbeAjMHfs63n4fe6AxR8buxH3Wx+IdWdotPF0 alice@laptop";
    static final String ECDSA = "ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBCKLV4ChGhVzArLw7brgwvIOQAjpwGqDaTifUtUTLxNQCWVdgGVtTjhhZSbIMKVdDUtRyBDkfjMCE3d4mznEAa8= ";

    @Test void parsesAKeyLineWithItsCommentAndFingerprint() {
        var key = AuthorizedKeys.parse(ED25519);
        assertThat(key.type()).isEqualTo("ssh-ed25519");
        assertThat(key.comment()).isEqualTo("alice@laptop");
        assertThat(key.line()).isEqualTo(ED25519);
        assertThat(key.fingerprint()).isEqualTo("SHA256:MgI+ts5vUqLauXQhbGEfXEqh28w0ZiEyBNNzcFRSBqw");
        assertThat(key.summary()).isEqualTo("ssh-ed25519 SHA256:MgI+ts5vUqLauXQhbGEfXEqh28w0ZiEyBNNzcFRSBqw  alice@laptop");
        var plain = AuthorizedKeys.parse(ECDSA);
        assertThat(plain.comment()).isEmpty();
        assertThat(plain.line()).as("trailing space dropped").isEqualTo(ECDSA.strip());
        assertThat(plain.fingerprint()).isEqualTo("SHA256:BdY9YKNPFTw7H/hoV3Qc6hCyIMvvyPYe9U1HUtCszMw");
        assertThat(plain.summary()).isEqualTo("ecdsa-sha2-nistp256 SHA256:BdY9YKNPFTw7H/hoV3Qc6hCyIMvvyPYe9U1HUtCszMw");
    }

    @Test void refusesWhatIsNotAPlainKeyLine() {
        assertThatThrownBy(() -> AuthorizedKeys.parse("command=\"echo hi\" " + ED25519))
            .hasMessage("Key options (such as command= or from=) are not supported: remove them from the line");
        assertThatThrownBy(() -> AuthorizedKeys.parse("restrict " + ED25519))
            .hasMessage("Key options (such as command= or from=) are not supported: remove them from the line");
        assertThatThrownBy(() -> AuthorizedKeys.parse("hello world")).hasMessage("Not an OpenSSH public key (for example ssh-ed25519 AAAA…)");
        assertThatThrownBy(() -> AuthorizedKeys.parse("ssh-ed25519")).hasMessage("The key data is missing");
        assertThatThrownBy(() -> AuthorizedKeys.parse("ssh-ed25519 not*base64")).hasMessage("The key data is not valid base64");
        assertThatThrownBy(() -> AuthorizedKeys.parse(ED25519.replace("ssh-ed25519 ", "ssh-rsa ")))
            .hasMessage("The key data does not match its type ssh-rsa");
        assertThatThrownBy(() -> AuthorizedKeys.parse(ED25519 + (char) 7)).hasMessage("The line contains control characters");
        assertThatThrownBy(() -> AuthorizedKeys.parse("  ")).hasMessage("Not an OpenSSH public key (for example ssh-ed25519 AAAA…)");
    }

    @Test void tabsSeparateLikeSpaces() {
        String tabbed = ED25519.replace(' ', '\t');
        var key = AuthorizedKeys.parse(tabbed + '\t');
        assertThat(key.type()).isEqualTo("ssh-ed25519");
        assertThat(key.comment()).isEqualTo("alice@laptop");
        assertThat(key.line()).as("stored with single spaces").isEqualTo(ED25519);
        assertThat(AuthorizedKeys.parseAll("# keys\n" + tabbed + "\n")).extracting(AuthorizedKeys.Key::line).containsExactly(ED25519);
        assertThatThrownBy(() -> AuthorizedKeys.parse(tabbed + (char) 0x1b)).hasMessage("The line contains control characters");
    }

    @Test void parsesAWholeFileAndNamesTheBadLine() {
        String file = "# my keys\n\n" + ED25519 + "\r\n" + ECDSA + "\n";
        assertThat(AuthorizedKeys.parseAll(file)).extracting(AuthorizedKeys.Key::type).containsExactly("ssh-ed25519", "ecdsa-sha2-nistp256");
        assertThatThrownBy(() -> AuthorizedKeys.parseAll(ED25519 + "\nnonsense here\n")).hasMessage("Line 2: Not an OpenSSH public key (for example ssh-ed25519 AAAA…)");
        assertThatThrownBy(() -> AuthorizedKeys.parseAll("# nothing\n")).hasMessage("No public keys found");
        assertThat(AuthorizedKeys.TYPES).contains("ssh-ed25519", "ssh-rsa", "ecdsa-sha2-nistp256", "sk-ssh-ed25519@openssh.com");
        assertThat(List.of(ED25519)).allSatisfy(line -> assertThat(AuthorizedKeys.parse(line).line()).isEqualTo(line));
    }
}
