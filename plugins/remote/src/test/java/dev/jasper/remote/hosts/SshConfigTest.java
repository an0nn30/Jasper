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
        assertThat(prod.user()).as("earliest global value wins").contains("global");
        assertThat(prod.identityFiles()).contains("~/.ssh/id_ed25519");
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
    @Test void appliesFirstValuesInFileOrderAcrossMatchingBlocks() {
        var parsed = SshConfig.parse("User first\nHost *\n Port 2222\nHost example\n User second\n Port 22\nHost example\n HostName target\n", include -> List.of());
        assertThat(parsed.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.user()).contains("first");
            assertThat(entry.port()).isEqualTo(OptionalInt.of(2222));
            assertThat(entry.hostname()).contains("target");
        });
    }

    @Test void readsQuotedValuesAndTrailingComments() {
        var parsed = SshConfig.parse("Host \"prod\" # comment\n HostName = \"api.example\"\n Port 2222 # alternate\n IdentityFile \"~/.ssh/work key\"\n Include \"extra files/*\" # include\n", pattern -> {
            assertThat(pattern).isEqualTo("extra files/*");
            return List.of(" User deploy\n");
        });
        assertThat(parsed.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.alias()).isEqualTo("prod");
            assertThat(entry.hostname()).contains("api.example");
            assertThat(entry.port()).isEqualTo(OptionalInt.of(2222));
            assertThat(entry.identityFiles()).contains("~/.ssh/work key");
            assertThat(entry.user()).contains("deploy");
        });
    }

    @Test void accumulatesIdentitiesAcrossMatchingBlocks() {
        var parsed = SshConfig.parse("IdentityFile global\nHost prod\n IdentityFile \"work key\"\n IdentityFile global\n IdentityFile none\nHost *\n IdentityFile fallback\n", i -> List.of());
        assertThat(parsed.entries().getFirst().identityFiles()).containsExactly("global", "work key", "fallback");
    }

}
