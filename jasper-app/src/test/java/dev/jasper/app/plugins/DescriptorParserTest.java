package dev.jasper.app.plugins;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DescriptorParserTest {
    private static final String FULL = """
        id = "dev.jasper.ssh"
        name = "SSH"
        version = "0.1.0"
        entry = "dev.jasper.ssh.SshPlugin"
        sdk = ">=0.1, <0.2"
        description = "SSH sessions"
        vendor = "Jasper"
        capabilities = ["terminal.observe", "session.provide"]
        exports = ["dev.jasper.ssh.api"]

        [[requires]]
        id = "dev.jasper.vault"
        version = ">=0.1"

        [[requires]]
        id = "dev.example.extra"
        optional = true
        """;

    @Test void parsesEveryField() throws Exception {
        PluginDescriptor descriptor = DescriptorParser.parse(FULL);
        assertThat(descriptor.id()).isEqualTo("dev.jasper.ssh");
        assertThat(descriptor.version()).isEqualTo(new Version(0, 1, 0));
        assertThat(descriptor.entry()).isEqualTo("dev.jasper.ssh.SshPlugin");
        assertThat(descriptor.sdk().contains(Version.parse("0.1.5"))).isTrue();
        assertThat(descriptor.capabilities()).containsExactlyInAnyOrder("terminal.observe", "session.provide");
        assertThat(descriptor.exports()).containsExactly("dev.jasper.ssh.api");
        assertThat(descriptor.requires()).containsExactly(
            new PluginDescriptor.Requirement("dev.jasper.vault", VersionRange.parse(">=0.1"), false),
            new PluginDescriptor.Requirement("dev.example.extra", VersionRange.ANY, true));
        assertThat(descriptor.info().version()).isEqualTo("0.1.0");
        assertThat(descriptor.info().capabilities()).isEqualTo(descriptor.capabilities());
    }

    @Test void optionalFieldsDefaultToEmpty() throws Exception {
        PluginDescriptor minimal = DescriptorParser.parse("""
            id = "a.b"
            name = "AB"
            version = "1.0.0"
            entry = "a.b.Main"
            sdk = ">=0.1"
            """);
        assertThat(minimal.description()).isEmpty();
        assertThat(minimal.vendor()).isEmpty();
        assertThat(minimal.capabilities()).isEmpty();
        assertThat(minimal.exports()).isEmpty();
        assertThat(minimal.requires()).isEmpty();
    }

    @Test void rejectsEveryMalformedDescriptorWithANamedKey() {
        record Case(String replace, String with, String expected) { }
        for (Case c : List.of(
            new Case("id = \"dev.jasper.ssh\"", "id = \"jasper.core\"", "id"),
            new Case("id = \"dev.jasper.ssh\"", "id = 7", "id"),
            new Case("name = \"SSH\"", "", "name"),
            new Case("version = \"0.1.0\"", "version = \"one\"", "version"),
            new Case("entry = \"dev.jasper.ssh.SshPlugin\"", "entry = \"not a class\"", "entry"),
            new Case("sdk = \">=0.1, <0.2\"", "sdk = \"~0.1\"", "sdk"),
            new Case("\"session.provide\"", "\"root.everything\"", "capabilities"),
            new Case("\"dev.jasper.ssh.api\"", "\"dev.jasper.app.workspace\"", "exports"),
            new Case("id = \"dev.example.extra\"", "id = \"dev.jasper.vault\"", "requires"),
            new Case("id = \"dev.example.extra\"", "id = \"dev.jasper.ssh\"", "requires"),
            new Case("vendor = \"Jasper\"", "vendor = \"Jasper\"\nsurprise = true", "surprise"))) {
            assertThat(FULL).contains(c.replace());
            assertThatThrownBy(() -> DescriptorParser.parse(FULL.replace(c.replace(), c.with())))
                .as(c.with()).isInstanceOf(DescriptorParser.InvalidDescriptor.class).hasMessageContaining(c.expected());
        }
        assertThatThrownBy(() -> DescriptorParser.parse("id = ")).isInstanceOf(DescriptorParser.InvalidDescriptor.class);
        assertThat(DescriptorParser.CAPABILITIES).isEqualTo(Set.of("terminal.observe", "terminal.selection",
            "terminal.inject", "terminal.open", "session.provide"));
    }
}
