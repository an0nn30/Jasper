package dev.jasper.remote.hosts;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HostFileTest {
    static final UUID BASTION = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID CRED = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    static RemoteHost bastion() {
        return new RemoteHost(BASTION, "bastion", "bastion.example", 22, "ops", Auth.AGENT, "Homelab", false, Optional.empty(), Instant.ofEpochSecond(1), Instant.ofEpochSecond(2));
    }

    static RemoteHost prod() {
        return new RemoteHost(UUID.fromString("00000000-0000-0000-0000-000000000002"), "api \"prod\"", "10.0.0.5", 2222, "", new Auth.Vault(CRED), "", true, Optional.of(BASTION), Instant.ofEpochSecond(3), Instant.ofEpochSecond(4));
    }

    @Test void roundTripsEveryField() throws IOException {
        String text = HostFile.format(List.of(bastion(), prod()));
        assertThat(text).startsWith(HostFile.HEADER).contains("[[host]]", "name = \"api \\\"prod\\\"\"", "auth = \"vault\"", "credential = \"" + CRED + "\"", "jump = \"" + BASTION + "\"", "favorite = true", "port = 2222", "created = 1970-01-01T00:00:03Z");
        assertThat(text).as("agent hosts carry no credential key").doesNotContain("credential = \"\"");
        HostFile.Parsed parsed = HostFile.parse(text);
        assertThat(parsed.warnings()).isEmpty();
        assertThat(parsed.hosts()).containsExactly(bastion(), prod());
    }

    @Test void skipsBadEntriesAndReportsThem() throws IOException {
        String text = HostFile.HEADER + "\n[[host]]\nname = \"a\"\nhostname = \"a.example\"\nauth = \"agent\"\nusername = \"u\"\n\n[[host]]\nname = \"b\"\nport = 70000\nauth = \"agent\"\nusername = \"u\"\nhostname = \"b\"\n\n[[host]]\nname = \"a\"\nhostname = \"dup\"\nauth = \"agent\"\nusername = \"u\"\n\n[[host]]\nname = \"c\"\nhostname = \"c\"\nauth = \"vault\"\nusername = \"\"\n";
        HostFile.Parsed parsed = HostFile.parse(text);
        assertThat(parsed.hosts()).singleElement().satisfies(host -> {
            assertThat(host.name()).isEqualTo("a");
            assertThat(host.port()).as("default port").isEqualTo(22);
            assertThat(host.id()).as("a missing id is minted").isNotNull();
            assertThat(host.created()).isNotNull();
        });
        assertThat(parsed.warnings()).hasSize(3)
            .anySatisfy(w -> assertThat(w).contains("host 2", "port"))
            .anySatisfy(w -> assertThat(w).contains("host 3", "duplicate name"))
            .anySatisfy(w -> assertThat(w).contains("host 4", "credential"));
        assertThatThrownBy(() -> HostFile.parse("[[host]\nname = ")).isInstanceOf(IOException.class).hasMessageContaining("TOML");
        assertThat(HostFile.parse("").hosts()).isEmpty();
    }

    @Test void validateRejectsCyclesDanglingJumpsAndDuplicateNames() {
        RemoteHost a = new RemoteHost(UUID.randomUUID(), "a", "a", 22, "u", Auth.AGENT, "", false, Optional.empty(), Instant.EPOCH, Instant.EPOCH);
        RemoteHost b = new RemoteHost(UUID.randomUUID(), "b", "b", 22, "u", Auth.AGENT, "", false, Optional.of(a.id()), Instant.EPOCH, Instant.EPOCH);
        HostFile.validate(List.of(a, b));
        RemoteHost aViaB = a.withEdited("a", "a", 22, "u", Auth.AGENT, "", Optional.of(b.id()));
        assertThatThrownBy(() -> HostFile.validate(List.of(aViaB, b))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cycle");
        RemoteHost dangling = b.withEdited("b", "b", 22, "u", Auth.AGENT, "", Optional.of(UUID.randomUUID()));
        assertThatThrownBy(() -> HostFile.validate(List.of(a, dangling))).hasMessageContaining("jump host missing");
        RemoteHost sameName = b.withEdited("A", "b", 22, "u", Auth.AGENT, "", Optional.empty());
        assertThatThrownBy(() -> HostFile.validate(List.of(a, sameName))).hasMessageContaining("A host named");
        assertThatThrownBy(() -> RemoteHost.create("x", "x", 0, "u", Auth.AGENT, "", Optional.empty())).hasMessageContaining("port");
        assertThatThrownBy(() -> RemoteHost.create("x", "x", 22, "", Auth.AGENT, "", Optional.empty())).hasMessageContaining("username");
        assertThat(RemoteHost.create("x", "x", 22, "", new Auth.Vault(UUID.randomUUID()), "", Optional.empty()).label()).isEqualTo("x:22");
        assertThat(a.label()).isEqualTo("u@a:22");
    }
    @Test void orderedManagedIdsRoundTripAndInvalidFormsAreRejected() throws Exception {
        UUID second = UUID.randomUUID();
        var keys = new Auth.VaultKeys(List.of(CRED, second, CRED));
        var host = bastion().withEdited("managed", "server", 22, "ops", keys, "", Optional.empty());
        String text = HostFile.format(List.of(host));
        assertThat(HostFile.parse(text).hosts()).containsExactly(host);
        assertThat(keys.credentialIds()).containsExactly(CRED, second);
        assertThatThrownBy(() -> new Auth.VaultKeys(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThat(HostFile.parse(text.replace("credentials = [", "credential = \"" + CRED + "\"\ncredentials = [")).warnings()).isNotEmpty();
        assertThat(HostFile.parse(text.replaceAll("credentials = .*", "credentials = []")).warnings()).isNotEmpty();
    }

    @Test void followDirectoryDefaultsOnAndOnlyOffIsWritten() throws IOException {
        assertThat(bastion().followDirectory()).isTrue();
        assertThat(HostFile.format(List.of(bastion()))).doesNotContain("follow_directory");
        RemoteHost off = bastion().withFollowDirectory(false);
        String text = HostFile.format(List.of(off));
        assertThat(text).contains("follow_directory = false");
        assertThat(HostFile.parse(text).hosts()).containsExactly(off);
        assertThat(off.withFavorite(true).followDirectory()).as("other edits keep it").isFalse();
        assertThat(off.withEdited("b", "b", 22, "u", Auth.AGENT, "", Optional.empty()).followDirectory()).isFalse();
        HostFile.Parsed bad = HostFile.parse(HostFile.HEADER + "\n[[host]]\nname = \"a\"\nhostname = \"a\"\nauth = \"agent\"\nusername = \"u\"\nfollow_directory = \"no\"\n");
        assertThat(bad.hosts()).isEmpty();
        assertThat(bad.warnings()).singleElement().asString().contains("follow_directory");
    }

}
