package dev.jasper.remote.hosts;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConfigImportTest {
    static final String PUB = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGb3mJ8dR0JZQm3W3n9oQjzN1tYw0N3HkL5wS2hXlY1X me@laptop";
    static final UUID KEY = UUID.randomUUID();

    @Test void mapsEntriesToHostsWithVaultKeysAgentFallbackAndJumps() {
        String fingerprint = Fingerprints.ofPublicKeyLine(PUB).orElseThrow();
        var parsed = SshConfig.parse("""
            Host prod
              HostName api.example
              User deploy
              IdentityFile ~/.ssh/id_ed25519
              ProxyJump bastion
            Host bastion
              HostName bastion.example
              IdentityFile ~/.ssh/other
            Host lonely
              ProxyJump nowhere
            """, include -> List.of());
        RemoteHost existing = RemoteHost.create("Bastion", "old.example", 22, "u", Auth.AGENT, "", Optional.empty());
        List<ConfigImport.Candidate> plan = ConfigImport.plan(parsed, List.of(existing), Map.of(fingerprint, KEY),
            path -> path.equals(Path.of("/home/me/.ssh/id_ed25519.pub")) ? Optional.of(PUB) : Optional.empty(), Path.of("/home/me"), "me");
        assertThat(plan).hasSize(3);
        ConfigImport.Candidate prod = plan.get(0), bastion = plan.get(1), lonely = plan.get(2);
        assertThat(prod.host().name()).isEqualTo("prod");
        assertThat(prod.host().hostname()).isEqualTo("api.example");
        assertThat(prod.host().username()).isEqualTo("deploy");
        assertThat(prod.host().auth()).isEqualTo(new Auth.Vault(KEY));
        assertThat(prod.host().jump()).as("jumps resolve to the existing host of that name").contains(existing.id());
        assertThat(prod.exists()).isFalse();
        assertThat(prod.notes()).isEmpty();
        assertThat(bastion.exists()).as("name matches an existing host, case-insensitively").isTrue();
        assertThat(bastion.host().auth()).isEqualTo(Auth.AGENT);
        assertThat(bastion.host().username()).as("no User: the local user").isEqualTo("me");
        assertThat(bastion.notes()).containsExactly("key not in the vault: uses the agent", "no User: uses your local username");
        assertThat(lonely.host().hostname()).as("no HostName: the alias").isEqualTo("lonely");
        assertThat(lonely.host().jump()).isEmpty();
        assertThat(lonely.notes()).contains("ProxyJump nowhere dropped: no such host");
    }

    @Test void fingerprintsMatchOpenSsh() {
        assertThat(Fingerprints.ofPublicKeyLine(PUB)).contains("SHA256:" + Fingerprints.sha256(java.util.Base64.getDecoder().decode(PUB.split(" ")[1])).substring(7));
        assertThat(Fingerprints.ofPublicKeyLine("not a key")).isEmpty();
        assertThat(Fingerprints.sha256(new byte[] {1, 2, 3})).startsWith("SHA256:").doesNotEndWith("=");
    }
}
