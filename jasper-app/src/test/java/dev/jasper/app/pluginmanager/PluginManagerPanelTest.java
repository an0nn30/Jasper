package dev.jasper.app.pluginmanager;

import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class PluginManagerPanelTest {
    private final List<String> events = new ArrayList<>();
    private final PluginManagerPanel panel = new PluginManagerPanel(new PluginManagerPanel.Handlers(
        row -> events.add("toggle:" + row.id()), row -> events.add("review:" + row.id()), row -> events.add("remove:" + row.id()),
        row -> events.add("discard:" + row.id()), () -> events.add("install")));

    static PluginRuntime.Row row(String id, String state, boolean enabled, boolean needsConsent, boolean canToggle, boolean canRemove,
                                 boolean pendingRemoval, boolean pendingInstall, String pending) {
        return new PluginRuntime.Row(id, "<html><b>" + id, "1.0.0", "Does things", "Example", canRemove ? "Installed" : "Bundled", state,
            state.equals("SKIPPED") ? "requires dev.example.base, which is not available" : "", List.of("terminal.inject", "made.up"),
            needsConsent ? List.of("terminal.inject") : List.of(), List.of("dev.example.base >=1.0.0"), 2, enabled, needsConsent, canToggle,
            canRemove, pendingRemoval, pendingInstall, pending);
    }

    @Test void listsPluginsAndOffersOnlyWhatTheSelectedRowAllows() {
        panel.show(new PluginRuntime.Snapshot(List.of(
            row("dev.example.active", "ACTIVE", true, false, true, true, false, false, ""),
            row("dev.example.bundled", "SKIPPED", true, false, true, false, false, false, ""),
            row("dev.example.found", "NEEDS_CONSENT", true, true, false, true, false, false, ""),
            row("dev.example.doomed", "DISABLED", false, false, false, true, true, false, "Will be removed at restart"),
            row("dev.example.staged", "NOT_LOADED", true, false, true, true, false, true, "Version 1.0.0 will be installed at restart")), true, false));
        assertThat(panel.listed()).containsExactly(
            "<html><b>dev.example.active  1.0.0 — Active",
            "<html><b>dev.example.bundled  1.0.0 — Skipped",
            "<html><b>dev.example.found  1.0.0 — Needs review",
            "<html><b>dev.example.doomed  1.0.0 — Disabled (restart to apply)",
            "<html><b>dev.example.staged  1.0.0 — Not loaded yet (restart to apply)");
        assertThat(panel.selected()).as("the first row is selected").isEqualTo("dev.example.active");
        assertThat(visible()).containsExactly("Disable", "Remove");
        assertThat(panel.details()).contains("Example", "Installed", "Does things", "Type into your terminals (terminal.inject)",
            "made.up", "dev.example.base >=1.0.0", "2 errors");

        panel.select("dev.example.bundled");
        assertThat(visible()).containsExactly("Disable");
        assertThat(panel.details()).contains("requires dev.example.base, which is not available");
        panel.select("dev.example.found");
        assertThat(visible()).containsExactly("Review…", "Remove");
        panel.select("dev.example.doomed");
        assertThat(visible()).containsExactly("Keep");
        assertThat(panel.details()).contains("Will be removed at restart");
        panel.select("dev.example.staged");
        assertThat(visible()).containsExactly("Disable", "Discard Install");

        panel.select("dev.example.found");
        panel.review.doClick(); panel.remove.doClick(); panel.install.doClick();
        panel.select("dev.example.active");
        panel.toggle.doClick();
        panel.select("dev.example.staged");
        panel.discard.doClick();
        assertThat(events).containsExactly("review:dev.example.found", "remove:dev.example.found", "install",
            "toggle:dev.example.active", "discard:dev.example.staged");
    }

    private List<String> visible() {
        List<String> labels = new ArrayList<>();
        for (JButton button : List.of(panel.toggle, panel.review, panel.remove, panel.discard)) if (button.isVisible()) labels.add(button.getText());
        return labels;
    }

    @Test void keepsTheSelectionAcrossRefreshesAndDisablesEverythingWhileBusy() {
        var rows = List.of(row("dev.example.a", "ACTIVE", true, false, true, true, false, false, ""),
            row("dev.example.b", "ACTIVE", true, false, true, true, false, false, ""));
        panel.show(new PluginRuntime.Snapshot(rows, false, false));
        panel.select("dev.example.b");
        panel.show(new PluginRuntime.Snapshot(rows, false, false));
        assertThat(panel.selected()).isEqualTo("dev.example.b");
        panel.busy(true);
        assertThat(panel.toggle.isEnabled() || panel.remove.isEnabled() || panel.install.isEnabled()).isFalse();
        panel.busy(false);
        assertThat(panel.toggle.isEnabled() && panel.install.isEnabled()).isTrue();
        panel.show(new PluginRuntime.Snapshot(List.of(), false, false));
        assertThat(panel.selected()).isNull();
        assertThat(visible()).isEmpty();
        assertThat(panel.details()).contains("No plugins");
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
