package dev.jasper.remote.hosts;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class SshConfigTest {
    static final String CONFIG = """
        # comment
        User global
        Host prod
            HostName api.prod.example
            Port 2222
            User deploy
            User ignored
            IdentityFile ~/.ssh/id_ed25519
            ProxyJump bastion
        Host bastion staging
          HostName=bastion.example
        Host *.internal
            User ops
        Match host db
            User dba
        Host *
            Port 22
        """;

    @Test void readsEntriesWithFirstValueWinsAndGlobalDefaults() {
        SshConfig.Parsed parsed = SshConfig.parse(CONFIG, include -> List.of());
        assertThat(parsed.entries()).extracting(SshConfig.Entry::alias).containsExactly("prod", "bastion", "staging");
        SshConfig.Entry prod = parsed.entries().getFirst();
        assertThat(prod.hostname()).contains("api.prod.example");
        assertThat(prod.port()).isEqualTo(OptionalInt.of(2222));
        assertThat(prod.user()).as("first value wins").contains("deploy");
        assertThat(prod.identityFile()).contains("~/.ssh/id_ed25519");
        assertThat(prod.proxyJump()).contains("bastion");
        SshConfig.Entry bastion = parsed.entries().get(1);
        assertThat(bastion.hostname()).contains("bastion.example");
        assertThat(bastion.user()).as("global default").contains("global");
        assertThat(bastion.port()).as("Host * default").isEqualTo(OptionalInt.of(22));
        assertThat(parsed.skipped()).containsExactly("Host *.internal (pattern)", "Match host db (Match block)");
    }

    @Test void includesAreExpandedOnce() {
        SshConfig.Parsed parsed = SshConfig.parse("Include extra/*\nHost a\n HostName a.example\n", include -> include.equals("extra/*") ? List.of("Host b\n HostName b.example\n") : List.of());
        assertThat(parsed.entries()).extracting(SshConfig.Entry::alias).containsExactly("b", "a");
    }

    @Test void readsFromDiskWithGlobbedIncludes(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("conf.d"));
        Files.writeString(dir.resolve("conf.d/one"), "Host one\n HostName one.example\n");
        Files.writeString(dir.resolve("config"), "Include conf.d/*\nHost two\n");
        SshConfig.Parsed parsed = SshConfig.parse(dir.resolve("config"));
        assertThat(parsed.entries()).extracting(SshConfig.Entry::alias).containsExactly("one", "two");
        assertThat(SshConfig.parse(dir.resolve("missing")).entries()).isEmpty();
    }
}
