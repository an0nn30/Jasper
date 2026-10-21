package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.about.ThirdPartyNotices;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.presentation.ChromeSpec;
import dev.jasper.app.presentation.FontFile;
import dev.jasper.app.testsupport.EnterpriseResources;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;

/** Spec 16.1.6: the shipped Enterprise plugin is what spec 4.1, 5.1, 7.6 and 14.1 say, from the sources and from its jar. */
class PresentationConformanceTest {
    /** Every key of spec 5.1's kit file. */
    static final Set<String> KEYS = Set.of("kit", "base",
        "chrome/title_bar", "chrome/menu_bar", "chrome/window_title", "chrome/tool_windows", "chrome/editor_tabs", "chrome/status_bar",
        "chrome/palette", "chrome/dialogs", "chrome/motion", "chrome/icons", "chrome/toolbar_search",
        "controls/toggle", "controls/exclusive", "controls/tabs", "controls/group", "controls/form_label", "controls/empty",
        "controls/table", "controls/tree", "controls/stepper", "controls/ordered_list", "controls/buttons", "controls/tag",
        "controls/route", "controls/prompt",
        "metrics/control_height", "metrics/row_height", "metrics/control_radius", "metrics/field_radius", "metrics/group_radius",
        "metrics/focus_ring", "metrics/tool_window_header", "metrics/group_padding", "metrics/dependent_indent",
        "metrics/navigation_width", "metrics/status_dot",
        "type/base", "type/ui/family", "type/ui/regular", "type/ui/medium", "type/ui/semibold",
        "type/data/family", "type/data/regular", "type/data/medium",
        "colors/dialog_surface", "colors/navigation_surface",
        "defaults/ui.theme.light", "defaults/ui.theme.dark", "defaults/palette.visible_rows", "defaults/window.toolbar_height",
        "defaults/window.toolbar", "defaults/dev.jasper.editor/font_family");
    /** The official IBM Plex 1.1.0 files (verified fact 11), by their name in the plugin. */
    static final Map<String, String> SUMS = Map.of(
        "presentation/fonts/ibm-plex-sans-regular.ttf", "975dcda37d80f038dcd143c22e33ca2d97a0cc5a929aace1c749153b0fe1afa5",
        "presentation/fonts/ibm-plex-sans-medium.ttf", "331c8639d7598b2cde62a911a71db195e30cb655cd6bdf2e324a7e984955f907",
        "presentation/fonts/ibm-plex-sans-semibold.ttf", "a20caf8286023a6a7a85e40b1d2a4ae9fc3e3b1f9eda8f4c542dd4986af67bb1",
        "presentation/fonts/ibm-plex-mono-regular.ttf", "fe11304a5fe956d5744e9b6a246cc83d90425245e75a62230044966ca96a7f50",
        "presentation/fonts/ibm-plex-mono-medium.ttf", "0bede3debdea8488bbb927f8f0650d915073209734a67fe8cd5a3320b572511c");

    @Test void theDescriptorIsSpecFourOnes() throws Exception {
        var descriptor = DescriptorParser.parse(Files.readString(EnterpriseResources.RESOURCES.resolve("plugin.toml")));
        assertThat(descriptor.id()).isEqualTo("dev.jasper.enterprise");
        assertThat(descriptor.name()).isEqualTo("Enterprise");
        assertThat(descriptor.version()).isEqualTo(Version.parse("0.3.0"));
        assertThat(descriptor.entry()).isEqualTo("dev.jasper.enterprise.EnterprisePlugin");
        assertThat(descriptor.capabilities()).containsExactly(DescriptorParser.PRESENTATION_PROVIDE);
        assertThat(descriptor.exports()).as("no API (plan 15a)").isEmpty();
        assertThat(descriptor.sdk()).as("the kit's plugin defaults need SDK 0.18.2 (Rulings 14)").isEqualTo(VersionRange.parse(">=0.18.2, <0.19"));
        assertThat(descriptor.requires()).as("no requirements (spec 14.1)").isEmpty();
        assertThat(descriptor.presentation()).isEqualTo("presentation/enterprise.kit.toml");
        assertThat(descriptor.themes()).containsExactly("themes/enterprise.json");
        assertThat(descriptor.themeIds()).containsExactly("enterprise");
        assertThat(descriptor.enabledByDefault()).isFalse();
        assertThat(descriptor.iconHue()).isEqualTo(dev.jasper.ui.model.Nodes.Hue.BLUE);
        assertThat(EnterpriseResources.RESOURCES.resolve(descriptor.icon())).isRegularFile();
    }

    @Test void theKitListsEveryKeyAndParsesWithoutAProblem() throws Exception {
        var toml = Toml.parse(Files.readString(EnterpriseResources.RESOURCES.resolve("presentation/enterprise.kit.toml")));
        assertThat(toml.errors()).isEmpty();
        var keys = new TreeSet<String>();
        toml.keyPathSet().forEach(path -> keys.add(String.join("/", path)));
        assertThat(keys).containsExactlyInAnyOrderElementsOf(KEYS);
        var facts = EnterpriseResources.facts();
        assertThat(facts.problem()).as("every token in range, the constraint held, every file present").isEmpty();
        var parsed = facts.parsed().orElseThrow();
        assertThat(parsed.classicBase()).isTrue();
        assertThat(parsed.chrome().icons()).isEqualTo(ChromeSpec.Icons.CLASSIC);
        assertThat(parsed.dropped()).as("the [defaults] themes resolve").isEmpty();
        assertThat(parsed.defaults().value(CoreSettings.UI_THEME_LIGHT)).isEqualTo("enterprise");
        assertThat(parsed.defaults().value(CoreSettings.UI_THEME_DARK)).isEqualTo("enterprise");
        assertThat(parsed.defaults().value(CoreSettings.PALETTE_VISIBLE_ROWS)).isEqualTo(8);
        // The user's 2026-10-19 direction: IntelliJ's 28 px toolbar.
        assertThat(parsed.defaults().value(CoreSettings.WINDOW_TOOLBAR_HEIGHT)).isEqualTo(28);
        assertThat(parsed.defaults().value(CoreSettings.WINDOW_TOOLBAR)).isEqualTo(dev.jasper.app.config.ToolbarMode.ICONS);
        assertThat(parsed.defaults().plugin("dev.jasper.editor")).as("the Editor in the data face (plan 15a)")
            .containsExactly(java.util.Map.entry("font_family", "IBM Plex Mono"));
    }

    @Test void theFontsAreIbmsDecodeAndEveryWeightResolvesToItsFamily() throws Exception {
        var faces = EnterpriseResources.facts().parsed().orElseThrow().faces();
        var ui = faces.ui().orElseThrow();
        var data = faces.data().orElseThrow();
        assertThat(ui.family()).isEqualTo("IBM Plex Sans");
        assertThat(data.family()).isEqualTo("IBM Plex Mono");
        assertThat(faces.files()).extracting(FontFile::fullName)
            .containsExactly("IBM Plex Sans", "IBM Plex Sans Medm", "IBM Plex Sans SmBld", "IBM Plex Mono", "IBM Plex Mono Medm");
        var families = new ArrayList<String>();
        for (FontFile file : faces.files()) {
            // A face's path is relative to the kit file's folder, presentation/ in the jar.
            String jarPath = "presentation/" + file.path();
            Path path = EnterpriseResources.RESOURCES.resolve(jarPath);
            assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))))
                .as(jarPath + " is IBM's file").isEqualTo(SUMS.get(jarPath));
            assertThat(file.bytes().length).as(file.path()).isLessThanOrEqualTo(2 * 1024 * 1024);
            Font font = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(file.bytes()));
            assertThat(font.getFontName(Locale.ROOT)).as(file.path()).isEqualTo(file.fullName());
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            families.add(file.family());
        }
        assertThat(families).containsExactly("IBM Plex Sans", "IBM Plex Sans", "IBM Plex Sans", "IBM Plex Mono", "IBM Plex Mono");
        assertThat(SUMS.keySet()).containsExactlyInAnyOrderElementsOf(faces.files().stream().map(file -> "presentation/" + file.path()).toList());
        EnterpriseResources.assumePlexResolves();
        for (FontFile file : faces.files())
            assertThat(new Font(file.fullName(), Font.PLAIN, 12).getFontName(Locale.ROOT)).as(file.path() + " resolves").isEqualTo(file.fullName());
    }

    @Test void theThemeLoadsWithBothVariantsAndTheNoticeIsListed() {
        var theme = EnterpriseResources.theme();
        assertThat(theme.has(true)).isTrue();
        assertThat(theme.has(false)).isTrue();
        assertThat(theme.name()).isEqualTo("Enterprise");
        var plex = ThirdPartyNotices.thirdParty().stream().filter(component -> component.name().equals("IBM Plex")).toList();
        assertThat(plex).singleElement().satisfies(component -> {
            assertThat(component.license()).isEqualTo("OFL-1.1");
            assertThat(component.version()).isEqualTo("1.1.0");
            assertThat(ThirdPartyNotices.read(component.text())).contains("Reserved Font Name \"Plex\"", "SIL OPEN FONT LICENSE Version 1.1");
        });
    }

    /** Enterprise shares Jasper's icon; its historical reference assets remain intact. */
    @Test void enterpriseSharesJaspersIconAndPreservesItsReferenceArtwork() throws Exception {
        var branding = EnterpriseResources.facts().parsed().orElseThrow().branding();
        assertThat(branding).isSameAs(dev.jasper.app.presentation.Branding.NONE);
        assertThat(branding.iconFiles()).as("use the shared Shell Prompt platform icon").isEmpty();
        Path reference = EnterpriseResources.ROOT.resolve("docs/design/enterprise/reference");
        var sources = new java.util.LinkedHashMap<Integer, String>();
        sources.put(16, "09 App Icon/macOS/AppIcon.iconset/icon_16x16.png");
        sources.put(32, "09 App Icon/macOS/AppIcon.iconset/icon_32x32.png");
        sources.put(48, "09 App Icon/Linux/hicolor/48x48/apps/jasper.png");
        sources.put(64, "09 App Icon/macOS/AppIcon.iconset/icon_32x32-2x.png");
        sources.put(128, "09 App Icon/macOS/AppIcon.iconset/icon_128x128.png");
        sources.put(256, "09 App Icon/macOS/AppIcon.iconset/icon_256x256.png");
        sources.put(1024, "09 App Icon/macOS/AppIcon.iconset/icon_512x512-2x.png");
        assertThat(List.copyOf(sources.keySet())).isEqualTo(dev.jasper.app.presentation.Branding.SIZES);
        for (var source : sources.entrySet()) {
            int size = source.getKey();
            byte[] shipped = Files.readAllBytes(EnterpriseResources.RESOURCES.resolve("presentation/icons/icon-" + size + ".png"));
            assertThat(shipped).as("icon-%s.png is the reference file", size).isEqualTo(Files.readAllBytes(reference.resolve(source.getValue())));
            assertThat(shipped.length).isLessThanOrEqualTo(512 * 1024);
            var image = javax.imageio.ImageIO.read(new ByteArrayInputStream(shipped));
            assertThat(image.getWidth()).as("icon-%s.png width", size).isEqualTo(size);
            assertThat(image.getHeight()).as("icon-%s.png height", size).isEqualTo(size);
            assertThat(image.getColorModel().hasAlpha()).as("icon-%s.png alpha", size).isTrue();
        }
        byte[] art = Files.readAllBytes(EnterpriseResources.RESOURCES.resolve("presentation/splash/art.svg"));
        assertThat(art).as("the splash art is the reference file").isEqualTo(Files.readAllBytes(reference.resolve("10 Boot Splash/extracted/art.svg")));
        assertThat(art.length).isLessThanOrEqualTo(256 * 1024);
    }

    /** The staged jar carries the same kit: the host reads it with the same digest and no problem. */
    @Test void theStagedPluginIsTheSourcesAndOffByDefault() {
        var problems = new ArrayList<String>();
        var candidates = PluginDiscovery.scan(Path.of(System.getProperty("jasper.stagedPlugins")), PluginCandidate.Origin.BUNDLED, problems);
        assertThat(problems).isEmpty();
        var enterprise = candidates.stream().filter(candidate -> candidate.id().equals("dev.jasper.enterprise")).findFirst().orElseThrow();
        var facts = Presentations.read(List.of(enterprise), dev.jasper.app.presentation.AttemptStore.Record.EMPTY, "").of(enterprise).orElseThrow();
        assertThat(facts.problem()).isEmpty();
        assertThat(facts.digest()).isEqualTo(EnterpriseResources.facts().digest());
        assertThat(PluginStateStore.effective(null, enterprise)).as("off until the user turns it on").isFalse();
    }
}
