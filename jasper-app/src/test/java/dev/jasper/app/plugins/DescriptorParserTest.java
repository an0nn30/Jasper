package dev.jasper.app.plugins;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DescriptorParserTest {
    @Test void acceptsDeclaredIdentityHueAsOptionalDecoration() {
        for(String hue : List.of("neutral", "blue", "green", "amber", "purple")) {
            assertThatCode(() -> DescriptorParser.parse("icon_hue = \"" + hue + "\"\n" + FULL))
                .as("declared decoration %s", hue).doesNotThrowAnyException();
        }
    }

    @Test void hueTokensDefaultsAndStructuralErrorsHaveDistinctPolicies() throws Exception {
        for(var hue:dev.jasper.ui.model.Nodes.Hue.values())if(hue!=dev.jasper.ui.model.Nodes.Hue.NONE) {
            assertThat(DescriptorParser.parse("icon_hue = \""+hue.name().toLowerCase(java.util.Locale.ROOT)+"\"\n"+FULL).iconHue()).isEqualTo(hue);
        }
        assertThat(DescriptorParser.parse(FULL).iconHue()).isEqualTo(dev.jasper.ui.model.Nodes.Hue.NEUTRAL);
        var logger = java.util.logging.Logger.getLogger(DescriptorParser.class.getName());
        var previousLevel = logger.getLevel();
        boolean previousParents = logger.getUseParentHandlers();
        var records = new java.util.ArrayList<java.util.logging.LogRecord>();
        var handler = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        handler.setLevel(java.util.logging.Level.ALL);
        logger.addHandler(handler);
        logger.setLevel(java.util.logging.Level.ALL);
        logger.setUseParentHandlers(false);
        try {
            var invalid = List.of("none", "RGB", "auto", "Blue", "");
            for (String bad : invalid) {
                assertThat(DescriptorParser.parse("icon_hue = \"" + bad + "\"\n" + FULL).iconHue())
                    .isEqualTo(dev.jasper.ui.model.Nodes.Hue.NEUTRAL);
            }
            assertThat(records).extracting(java.util.logging.LogRecord::getMessage)
                .containsExactlyElementsOf(invalid.stream()
                    .map(value -> "Plugin dev.jasper.ssh: invalid icon_hue; using neutral: " + value).toList());
            assertThat(records).allSatisfy(record -> {
                assertThat(record.getLevel()).isEqualTo(java.util.logging.Level.WARNING);
                assertThat(record.getLoggerName()).isEqualTo(DescriptorParser.class.getName());
            });
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousParents);
        }
        assertThatThrownBy(()->DescriptorParser.parse("icon_hue = 12\n"+FULL)).hasMessageContaining("icon_hue must be a string");
        assertThatThrownBy(()->DescriptorParser.parse("arbitrary_hue = \"blue\"\n"+FULL)).hasMessageContaining("Unknown key");
    }
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
            "terminal.inject", "terminal.open", "session.provide", "palette.contribute", "files.source", "network.listen"));
    }

    @Test void aNameThatShowsNothingIsRejected() {
        for (int[] points : new int[][] {{0x200B}, {0xA0, 0xA0}, {0x202E, 0x2066}, {0xFEFF}}) {
            String invisible = new String(points, 0, points.length);
            assertThatThrownBy(() -> DescriptorParser.parse(FULL.replace("name = \"SSH\"", "name = \"" + invisible + "\"")))
                .as("%s", java.util.Arrays.toString(points)).isInstanceOf(DescriptorParser.InvalidDescriptor.class)
                .hasMessageContaining("name");
        }
    }

    @Test void acceptsTheNetworkListenCapability() throws Exception {
        PluginDescriptor descriptor = DescriptorParser.parse(FULL.replace("\"session.provide\"", "\"network.listen\""));
        assertThat(descriptor.capabilities()).contains("network.listen");
    }

    @Test void anIconAndAHomepageAreOptionalAndABadOneIsDropped() throws Exception {
        String base = "id = \"a.b\"\nname = \"AB\"\nversion = \"1.0.0\"\nentry = \"a.b.Main\"\nsdk = \">=0.1\"\n";
        PluginDescriptor full = DescriptorParser.parse(base + "icon = \"icons/ab.svg\"\nhomepage = \"https://example.dev/ab\"\n");
        assertThat(full.icon()).isEqualTo("icons/ab.svg");
        assertThat(full.homepage()).isEqualTo("https://example.dev/ab");
        assertThat(DescriptorParser.parse(base + "icon = \"icons/ab.PNG\"\nhomepage = \"http://example.dev\"\n"))
            .satisfies(descriptor -> {
                assertThat(descriptor.icon()).isEqualTo("icons/ab.PNG");
                assertThat(descriptor.homepage()).isEqualTo("http://example.dev");
            });
        PluginDescriptor bad = DescriptorParser.parse(base + "icon = \"icons/ab.gif\"\nhomepage = \"javascript:alert(1)\"\n");
        assertThat(bad.icon()).as("not an .svg or .png").isEmpty();
        assertThat(bad.homepage()).as("not http or https").isEmpty();
        assertThat(DescriptorParser.parse(base + "homepage = \"example.dev\"\n").homepage()).as("not absolute").isEmpty();
        PluginDescriptor minimal = DescriptorParser.parse(base);
        assertThat(minimal.icon()).isEmpty();
        assertThat(minimal.homepage()).isEmpty();
    }
}
