package dev.jasper.fileserver.definition;

import java.util.Objects;
import java.util.UUID;

/** How clients log in: not at all, with a password generated for each run, or with a Vault account. */
public sealed interface Login {
    record Anonymous() implements Login { }
    record Session() implements Login { }
    record Vault(UUID credentialId) implements Login {
        public Vault { Objects.requireNonNull(credentialId, "credentialId"); }
    }

    Login ANONYMOUS = new Anonymous();
    Login SESSION = new Session();

    default String token() {
        return switch (this) {
            case Anonymous anonymous -> "anonymous";
            case Session session -> "session";
            case Vault vault -> "vault:" + vault.credentialId();
        };
    }

    static Login parse(String token) {
        String text = token.strip();
        if (text.equals("anonymous")) return ANONYMOUS;
        if (text.equals("session")) return SESSION;
        if (text.startsWith("vault:")) {
            try { return new Vault(UUID.fromString(text.substring("vault:".length()))); }
            catch (IllegalArgumentException bad) { throw new IllegalArgumentException("'login' names no Vault account id"); }
        }
        throw new IllegalArgumentException("'login' must be \"anonymous\", \"session\" or \"vault:<id>\"");
    }
}
