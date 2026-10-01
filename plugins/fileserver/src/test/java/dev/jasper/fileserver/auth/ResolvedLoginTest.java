package dev.jasper.fileserver.auth;

import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResolvedLoginTest {
    @Test void matchesOnlyTheExactUserAndPasswordUntilClosed() {
        char[] secret = "s3cret".toCharArray();
        var login = ResolvedLogin.password("me", secret);
        secret[0] = 'X';
        assertThat(login.isAnonymous()).isFalse();
        assertThat(login.user()).contains("me");
        assertThat(login.matches("me", "s3cret".toCharArray())).as("the password was copied").isTrue();
        assertThat(login.matches("Me", "s3cret".toCharArray())).isFalse();
        assertThat(login.matches("me", "s3cret ".toCharArray())).isFalse();
        assertThat(login.matches(null, null)).isFalse();
        assertThat(login.revealPassword()).contains("s3cret");
        login.close();
        assertThat(login.matches("me", "s3cret".toCharArray())).isFalse();
        assertThat(login.revealPassword()).isEmpty();
        assertThat(ResolvedLogin.anonymous().isAnonymous()).isTrue();
        assertThat(ResolvedLogin.anonymous().matches("me", "x".toCharArray())).isFalse();
        assertThatThrownBy(() -> ResolvedLogin.password(" ", "x".toCharArray())).hasMessageContaining("user name");
    }

    @Test void sessionPasswordsAreLongAndUnambiguous() {
        String password = SessionPasswords.generate(new Random(7));
        assertThat(password).hasSize(20);
        assertThat(password.chars()).allMatch(c -> SessionPasswords.ALPHABET.indexOf(c) >= 0);
        assertThat(SessionPasswords.ALPHABET).doesNotContain("0", "O", "o", "1", "l", "I");
        assertThat(SessionPasswords.generate()).isNotEqualTo(SessionPasswords.generate());
    }
}
