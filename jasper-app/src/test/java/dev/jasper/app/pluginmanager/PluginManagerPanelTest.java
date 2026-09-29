package dev.jasper.app.pluginmanager;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EdtTestExtension.class)
class PluginManagerPanelTest {
    private final List<String> events = new ArrayList<>();
    private final List<String> opened = new ArrayList<>();
    private final List<URI> browsed = new ArrayList<>();
    private final PluginManagerPanel panel = new PluginManagerPanel(new PluginManagerPanel.Handlers(
        row -> events.add("toggle:" + row.id()), row -> events.add("review:" + row.id()), row -> events.add("remove:" + row.id()),
        row -> events.add("discard:" + row.id()), () -> events.add("install"),
        row -> opened.add("settings:" + row.id()), row -> opened.add("folder:" + row.id()), row -> opened.add("data:" + row.id()),
        browsed::add));

    private static PluginRuntime.Snapshot snapshot(PluginRuntime.Row... rows) { return new PluginRuntime.Snapshot(List.of(rows), true, false); }

    @Test void thePrimaryButtonAndTheMenuFollowTheSelectedPlugin() {
        panel.show(snapshot(
            TestRows.plugin("dev.example.active", "Active").errors(2).capabilities(List.of("terminal.inject", "made.up"))
                .requires(List.of("dev.example.base >=1.0.0")).exports(List.of("dev.example.active.api")).row(),
            TestRows.plugin("dev.example.bundled", "Bundled One").origin("Bundled")
                .state("SKIPPED", "requires dev.example.base, which is not available").row(),
            TestRows.plugin("dev.example.found", "Found").needsReview("terminal.inject").row(),
            TestRows.plugin("dev.example.doomed", "Doomed").removing().row(),
            TestRows.plugin("dev.example.staged", "Staged").installing().row()));
        panel.select("dev.example.active");
        assertThat(panel.primary().getText()).isEqualTo("Disable");
        assertThat(panel.menuLabels()).containsExactly("Open Settings", "Open Plugin Folder", "Open Data Folder", "-", "Remove…");
        assertThat(panel.details()).contains("Active", "1.0.0", "Example", "Does things", "Type into your terminals (terminal.inject)",
            "made.up", "dev.example.base >=1.0.0", "dev.example.active.api", "2 errors", java.nio.file.Path.of("/plugins/dev.example.active/data").toString());
        panel.select("dev.example.bundled");
        assertThat(panel.primary().getText()).isEqualTo("Disable");
        assertThat(panel.menuLabels()).containsExactly("Open Settings", "Open Plugin Folder", "Open Data Folder");
        assertThat(panel.details()).contains("requires dev.example.base, which is not available", "Bundled with Jasper");
        panel.select("dev.example.found");
        assertThat(panel.primary().getText()).isEqualTo("Review…");
        panel.select("dev.example.doomed");
        assertThat(panel.primary().isVisible()).isFalse();
        assertThat(panel.menuLabels()).endsWith("Keep");
        panel.select("dev.example.staged");
        assertThat(panel.menuLabels()).endsWith("Discard Install");

        panel.select("dev.example.found");
        panel.primary().doClick();
        panel.select("dev.example.active");
        panel.primary().doClick();
        panel.clickMenu("Remove…");
        panel.select("dev.example.staged");
        panel.clickMenu("Discard Install");
        panel.clickMenu("Open Settings");
        panel.clickMenu("Open Plugin Folder");
        panel.clickMenu("Open Data Folder");
        assertThat(panel.gearLabels()).containsExactly("Install from Zip…");
        panel.clickGear("Install from Zip…");
        assertThat(events).containsExactly("review:dev.example.found", "toggle:dev.example.active", "remove:dev.example.active",
            "discard:dev.example.staged", "install");
        assertThat(opened).containsExactly("settings:dev.example.staged", "folder:dev.example.staged", "data:dev.example.staged");
    }

    @Test void aCardsCheckboxReviewsOrTogglesAndTheListIsGroupedAndSearchable() {
        panel.show(snapshot(TestRows.plugin("dev.example.found", "Found").needsReview("terminal.inject").row(),
            TestRows.plugin("dev.example.on", "On").row(), TestRows.plugin("dev.jasper.remote", "Remote").origin("Bundled").row()));
        assertThat(panel.groupTitles()).containsExactly("User-installed (2 of 2 enabled)", "Bundled (1 of 1 enabled)");
        panel.card("dev.example.found").enabled.doClick();
        panel.card("dev.example.on").enabled.doClick();
        assertThat(events).containsExactly("review:dev.example.found", "toggle:dev.example.on");
        panel.search("remote");
        assertThat(panel.listed()).containsExactly("Remote  1.0.0");
        assertThat(panel.selected()).isEqualTo("dev.jasper.remote");
    }

    @Test void theHomepageLinkShowsOnlyForAnHttpOrHttpsAddress() {
        panel.show(snapshot(TestRows.plugin("a.web", "Web").homepage("https://example.dev/web").row(),
            TestRows.plugin("a.ftp", "Ftp").homepage("ftp://example.dev").row(), TestRows.plugin("a.none", "None").row()));
        panel.select("a.web");
        assertThat(panel.homepageShown()).isTrue();
        panel.clickHomepage();
        assertThat(browsed).containsExactly(URI.create("https://example.dev/web"));
        panel.select("a.ftp");
        assertThat(panel.homepageShown()).isFalse();
        panel.select("a.none");
        assertThat(panel.homepageShown()).isFalse();
    }

    @Test void theTabsHoldTheOverviewAndTheDetails() {
        panel.show(snapshot(TestRows.plugin("a.quiet", "Quiet").description("").row()));
        assertThat(panel.tabs().getTabCount()).isEqualTo(2);
        assertThat(panel.tabs().getTitleAt(0)).isEqualTo("Overview");
        assertThat(panel.tabs().getTitleAt(1)).isEqualTo("Details");
        assertThat(panel.details()).contains("No description.", "Capabilities", "None", "Requires", "Exports", "Locations",
            java.nio.file.Path.of("/plugins/a.quiet/a.quiet.toml").toString());
    }

    @Test void busyDisablesEveryControlAndTheSelectionSurvivesARefresh() {
        var rows = new PluginRuntime.Row[]{TestRows.plugin("dev.example.a", "A").row(), TestRows.plugin("dev.example.b", "B").row()};
        panel.show(snapshot(rows));
        panel.select("dev.example.b");
        panel.show(snapshot(rows));
        assertThat(panel.selected()).isEqualTo("dev.example.b");
        panel.busy(true);
        assertThat(panel.primary().isEnabled() || panel.card("dev.example.a").enabled.isEnabled() || panel.gear.isEnabled()).isFalse();
        panel.clickGear("Install from Zip…");
        assertThat(events).as("nothing runs while busy").isEmpty();
        panel.busy(false);
        assertThat(panel.primary().isEnabled() && panel.card("dev.example.a").enabled.isEnabled() && panel.gear.isEnabled()).isTrue();
        panel.show(snapshot());
        assertThat(panel.selected()).isNull();
        assertThat(panel.details()).isEqualTo("No plugins installed");
    }

    @Test void pluginTextIsNeverMarkup() {
        panel.show(snapshot(TestRows.plugin("a.h", "<html><b>Bold").vendor("<html><i>Acme").row()));
        assertThat(panel.card("a.h").name.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
        assertThat(panel.details()).contains("<html><b>Bold", "<html><i>Acme");
    }

    @Test void theBannerTheNoticeAndTheMessageAreIndependent() {
        List<String> clicks = new ArrayList<>();
        assertThat(panel.bannerText()).isEmpty();
        panel.banner("Restart Jasper to apply your changes.", List.of(new PluginManagerPanel.BannerAction("Restart Now", () -> clicks.add("restart"))));
        assertThat(panel.bannerText()).isEqualTo("Restart Jasper to apply your changes.");
        assertThat(panel.bannerButtons()).extracting(JButton::getText).containsExactly("Restart Now");
        panel.bannerButtons().get(0).doClick();
        assertThat(clicks).containsExactly("restart");
        panel.notice("Another Jasper process is still running.");
        panel.message("The zip holds no jar files", true);
        assertThat(panel.noticeText()).isEqualTo("Another Jasper process is still running.");
        assertThat(panel.messageText()).isEqualTo("The zip holds no jar files");
        panel.banner("", List.of());
        assertThat(panel.bannerText()).isEmpty();
        assertThat(panel.bannerButtons()).isEmpty();
        assertThat(panel.noticeText()).isNotEmpty();
    }

    @Test void theConsentViewSaysWhatIsAskedAndThatItIsNoSandbox() {
        List<String> decisions = new ArrayList<>();
        var view = new ConsentView("<html>Tool", "2.0.0", "Example", List.of("terminal.inject", "made.up"), true, "Install",
            () -> decisions.add("allow"), () -> decisions.add("cancel"));
        assertThat(view.text()).contains("<html>Tool 2.0.0", "Example", "replaces the installed version",
            "Type into your terminals (terminal.inject)", "made.up", "unrestricted code", "not a sandbox");
        assertThat(view.allow.getText()).isEqualTo("Install");
        view.allow.doClick(); view.cancel.doClick();
        assertThat(decisions).containsExactly("allow", "cancel");
        assertThat(new ConsentView("Quiet", "1.0.0", "", List.of(), false, "Allow and Enable", () -> { }, () -> { }).text())
            .contains("asks for no access to your terminals").doesNotContain("replaces");
    }
}
