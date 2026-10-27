package dev.jasper.app.plugins;

import java.util.List;
import java.util.Map;
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
    @Test void selectedInkDefaultsWarningsAndStructuralErrorsHaveDistinctPolicies() throws Exception {
        for(var hue:dev.jasper.ui.icons.IdentityArtwork.SelectedInk.values()) {
            assertThat(DescriptorParser.parse("icon_selected_ink = \""+hue.name().toLowerCase(java.util.Locale.ROOT)+"\"\n"+FULL).iconSelectedInk()).isEqualTo(hue);
        }
        assertThat(DescriptorParser.parse(FULL).iconSelectedInk()).isEqualTo(dev.jasper.ui.icons.IdentityArtwork.SelectedInk.AUTHORED);
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
            var invalid = List.of("none", "RGB", "auto", "Foreground", "");
            for (String bad : invalid) {
                assertThat(DescriptorParser.parse("icon_selected_ink = \"" + bad + "\"\n" + FULL).iconSelectedInk())
                    .isEqualTo(dev.jasper.ui.icons.IdentityArtwork.SelectedInk.AUTHORED);
            }
            assertThat(records).extracting(java.util.logging.LogRecord::getMessage)
                .containsExactlyElementsOf(invalid.stream()
                    .map(value -> "Plugin dev.jasper.ssh: invalid icon_selected_ink; using authored: " + value).toList());
            assertThat(records).allSatisfy(record -> {
                assertThat(record.getLevel()).isEqualTo(java.util.logging.Level.WARNING);
                assertThat(record.getLoggerName()).isEqualTo(DescriptorParser.class.getName());
            });
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousParents);
        }
        assertThatThrownBy(()->DescriptorParser.parse("icon_selected_ink = 12\n"+FULL)).hasMessageContaining("icon_selected_ink must be a string");
        assertThatThrownBy(()->DescriptorParser.parse("arbitrary_hue = \"blue\"\n"+FULL)).hasMessageContaining("Unknown key");
    }
    @Test void selectedInkTokensAreAcceptedWithoutWeakeningStrictUnknownKeyValidation() {
        for (String token : List.of("authored", "foreground"))
            assertThatCode(() -> DescriptorParser.parse("icon_selected_ink = \"" + token + "\"\n" + FULL)).doesNotThrowAnyException();
        assertThatThrownBy(() -> DescriptorParser.parse("icon_selected_ink = 12\n" + FULL))
            .hasMessageContaining("icon_selected_ink must be a string");
        assertThatThrownBy(() -> DescriptorParser.parse("icon_arbitrary_state = \"foreground\"\n" + FULL))
            .hasMessageContaining("Unknown key");
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
        assertThat(DescriptorParser.CAPABILITIES).as("every SDK capability, and nothing else")
            .isEqualTo(Set.copyOf(dev.jasper.sdk.Capabilities.ALL));
    }

    private static final String LOOK = """
        id = "dev.example.look"
        name = "Look"
        version = "0.1.0"
        entry = "dev.example.look.LookPlugin"
        sdk = ">=0.18.0, <0.19"
        """;

    @Test void aPresentationProviderDeclaresItsKitItsThemesAndThatItIsOffByDefault() throws Exception {
        var descriptor = DescriptorParser.parse(LOOK + """
            capabilities = ["presentation.provide"]
            presentation = "presentation/look.kit.toml"
            themes = ["themes/look.json", "themes/look-contrast.json"]
            enabled_by_default = false
            """);
        assertThat(descriptor.presentation()).isEqualTo("presentation/look.kit.toml");
        assertThat(descriptor.declaresPresentation()).isTrue();
        assertThat(descriptor.themes()).containsExactly("themes/look.json", "themes/look-contrast.json");
        assertThat(descriptor.themeIds()).containsExactly("look", "look-contrast");
        assertThat(descriptor.enabledByDefault()).isFalse();
    }

    @Test void withoutTheKeysAPluginIsOnByDefaultAndProvidesNothing() throws Exception {
        var descriptor = DescriptorParser.parse(LOOK);
        assertThat(descriptor.presentation()).isEmpty();
        assertThat(descriptor.declaresPresentation()).isFalse();
        assertThat(descriptor.themes()).isEmpty();
        assertThat(descriptor.enabledByDefault()).isTrue();
        assertThat(DescriptorParser.parse(LOOK + "themes = [\"themes/extra.json\"]\n").themeIds())
            .as("any plugin may ship themes, with no capability").containsExactly("extra");
    }

    @Test void thePresentationKeyAndItsCapabilityNeedEachOther() {
        assertThatThrownBy(() -> DescriptorParser.parse(LOOK + "presentation = \"look.kit.toml\"\n"))
            .isInstanceOf(DescriptorParser.InvalidDescriptor.class)
            .hasMessage("presentation needs the presentation.provide capability");
        assertThatThrownBy(() -> DescriptorParser.parse(LOOK + "capabilities = [\"presentation.provide\"]\n"))
            .isInstanceOf(DescriptorParser.InvalidDescriptor.class)
            .hasMessage("capabilities: presentation.provide needs the presentation key");
    }

    @Test void pathsAreLowercaseJarPathsWithoutDotSegmentsOrALeadingSlash() {
        for (String bad : List.of("/look.kit.toml", "a/../look.kit.toml", "Look.kit.toml", "a//look.kit.toml", "a/./b.toml",
                "presentation/", "a b.toml", "..", "a\\b.toml"))
            assertThatThrownBy(() -> DescriptorParser.parse(LOOK + "capabilities = [\"presentation.provide\"]\npresentation = \""
                    + bad.replace("\\", "\\\\") + "\"\n"))
                .as(bad).isInstanceOf(DescriptorParser.InvalidDescriptor.class).hasMessageStartingWith("presentation: ");
        for (String bad : List.of("themes/Look.json", "themes/look.txt", "../look.json", "themes/look_.json", "themes/.json"))
            assertThatThrownBy(() -> DescriptorParser.parse(LOOK + "themes = [\"" + bad + "\"]\n"))
                .as(bad).isInstanceOf(DescriptorParser.InvalidDescriptor.class).hasMessageStartingWith("themes");
        assertThatThrownBy(() -> DescriptorParser.parse(LOOK + "themes = [\"a/look.json\", \"b/look.json\"]\n"))
            .isInstanceOf(DescriptorParser.InvalidDescriptor.class).hasMessage("themes names the theme look twice");
        assertThatThrownBy(() -> DescriptorParser.parse(LOOK + "themes = \"themes/look.json\"\n"))
            .isInstanceOf(DescriptorParser.InvalidDescriptor.class).hasMessage("themes must be an array of strings");
        assertThatThrownBy(() -> DescriptorParser.parse(LOOK + "enabled_by_default = \"no\"\n"))
            .isInstanceOf(DescriptorParser.InvalidDescriptor.class).hasMessage("enabled_by_default must be a boolean");
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

    @Test void acceptsTheClipboardReadCapability() throws Exception {
        PluginDescriptor descriptor = DescriptorParser.parse(FULL.replace("\"session.provide\"", "\"clipboard.read\""));
        assertThat(descriptor.capabilities()).contains("clipboard.read");
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

    private static final String HEAD = "id = \"a.b\"\nname = \"AB\"\nversion = \"1.0.0\"\nentry = \"a.b.Main\"\nsdk = \">=0.17\"\n";

    @Test void subcommandsAreReadInOrder() throws Exception {
        PluginDescriptor descriptor = DescriptorParser.parse(HEAD + """

            [[subcommands]]
            name = "editor"
            summary = "Open files in Jasper's Editor"

            [[subcommands]]
            name = "go-to"
            summary = "Go somewhere"
            """);
        assertThat(descriptor.subcommands()).containsExactly(
            new PluginDescriptor.Subcommand("editor", "Open files in Jasper's Editor"),
            new PluginDescriptor.Subcommand("go-to", "Go somewhere"));
        assertThat(DescriptorParser.parse(HEAD).subcommands()).isEmpty();
    }

    /** Browser plan 3b: {@code open-url} is no command of Jasper's, so a plugin may declare a subcommand of that name. */
    @Test void aPluginMayDeclareASubcommandNamedOpenUrl() throws Exception {
        PluginDescriptor descriptor = DescriptorParser.parse(HEAD + """

            [[subcommands]]
            name = "open-url"
            summary = "Open an address"
            """);
        assertThat(descriptor.subcommands()).containsExactly(new PluginDescriptor.Subcommand("open-url", "Open an address"));
        assertThat(DescriptorParser.RESERVED_SUBCOMMANDS).containsExactlyInAnyOrder("help", "version");
    }

    @Test void malformedSubcommandsNameTheirProblem() {
        Map<String, String> cases = Map.of(
            "subcommands = \"editor\"\n", "subcommands must be an array of tables",
            "[[subcommands]]\nname = \"editor\"\nsummary = \"s\"\nicon = \"x\"\n", "subcommands has an unknown key: icon",
            "[[subcommands]]\nname = \"Editor\"\nsummary = \"s\"\n", "subcommands.name is not a command name: Editor",
            "[[subcommands]]\nname = \"-x\"\nsummary = \"s\"\n", "subcommands.name is not a command name: -x",
            "[[subcommands]]\nname = \"help\"\nsummary = \"s\"\n", "subcommands.name is reserved: help",
            "[[subcommands]]\nname = \"a\"\nsummary = \"s\"\n[[subcommands]]\nname = \"a\"\nsummary = \"t\"\n", "subcommands names a command twice: a",
            "[[subcommands]]\nname = \"a\"\n", "Missing required key: summary",
            "[[subcommands]]\nname = \"a\"\nsummary = \"two\\nlines\"\n", "subcommands.summary must be one visible line: a",
            "[[subcommands]]\nname = \"a\"\nsummary = \"   \"\n", "subcommands.summary must be one visible line: a");
        cases.forEach((tail, message) -> assertThatThrownBy(() -> DescriptorParser.parse(HEAD + tail)).as(tail)
            .isInstanceOf(DescriptorParser.InvalidDescriptor.class).hasMessageContaining(message));
    }

    /** Browser plan 3: the web browser role's capability. */
    @Test void acceptsTheBrowserCapability() throws Exception {
        PluginDescriptor descriptor = DescriptorParser.parse(FULL.replace("\"session.provide\"", "\"browser.provide\""));
        assertThat(descriptor.capabilities()).contains("browser.provide");
    }

    @Test void acceptsTheEnvironmentAndEditorCapabilities() throws Exception {
        PluginDescriptor descriptor = DescriptorParser.parse(FULL.replace("\"session.provide\"", "\"terminal.environment\", \"editor.provide\""));
        assertThat(descriptor.capabilities()).contains("terminal.environment", "editor.provide");
    }
}
