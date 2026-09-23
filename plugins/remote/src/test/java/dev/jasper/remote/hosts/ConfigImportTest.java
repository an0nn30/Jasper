package dev.jasper.remote.hosts;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConfigImportTest {
    @Test void reimportPreservesIdentityAndOrganization() {
        RemoteHost original = RemoteHost.create("PROD", "old.example", 22, "old", Auth.AGENT, "Servers", Optional.empty()).withFavorite(true);
        var parsed = SshConfig.parse("Host prod\n HostName new.example\n User deploy\n IdentityFile ~/.ssh/id_ed25519\n", include -> List.of());
        var row = ConfigImport.plan(parsed, List.of(original), Path.of("/home/me"), "me").getFirst();
        var updated = ConfigImport.bind(row, new Auth.VaultKeys(List.of(UUID.randomUUID())), Instant.ofEpochSecond(500));
        assertThat(updated.id()).isEqualTo(original.id());
        assertThat(updated.created()).isEqualTo(original.created());
        assertThat(updated.group()).isEqualTo("Servers");
        assertThat(updated.favorite()).isTrue();
        assertThat(updated.hostname()).isEqualTo("new.example");
        assertThat(updated.username()).isEqualTo("deploy");
        assertThat(row.identities()).containsExactly(Path.of("/home/me/.ssh/id_ed25519"));
    }
    @Test void resolvesSingleAliasAndBlocksUnsupportedJumps() {
        var parsed = SshConfig.parse("Host prod\n ProxyJump bastion\nHost bastion\nHost bad\n ProxyJump user@bastion:2222\nHost missing\n ProxyJump nowhere\n", i -> List.of());
        var rows = ConfigImport.plan(parsed, List.of(), Path.of("/home/me"), "me");
        assertThat(rows.get(0).jump()).contains(rows.get(1).id());
        assertThat(rows.get(2).errors()).anyMatch(s -> s.contains("Unsupported ProxyJump"));
        assertThat(rows.get(3).errors()).anyMatch(s -> s.contains("missing"));
    }
    @Test void resolvesTokensOnceAndRejectsUnsupportedSyntax() {
        var home = Path.of("/home/me");
        assertThat(IdentityPaths.resolve("%d/.ssh/%u-%r-%h-%p-%%", home, "local", "server", "remote", 2222))
            .isEqualTo(home.resolve(".ssh/local-remote-server-2222-%"));
        assertThat(IdentityPaths.resolve("relative", home, "u", "h", "r", 22)).isEqualTo(home.resolve("relative"));
        assertThat(IdentityPaths.resolve("%h", home, "u", "%r", "remote", 22)).isEqualTo(home.resolve("%r"));
        for (String invalid : List.of("%", "%x", "${HOME}/key", "~other/key", ""))
            assertThatThrownBy(() -> IdentityPaths.resolve(invalid, home, "u", "h", "r", 22)).isInstanceOf(IllegalArgumentException.class);
    }
}
