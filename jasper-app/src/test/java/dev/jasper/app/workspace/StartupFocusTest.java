package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelRegion;
import java.awt.FocusTraversalPolicy;
import java.util.ArrayDeque;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class StartupFocusTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    // Starts DesktopTestSupport's /bin/sh shell, which Windows lacks (as MockUiTest's shell-starting test).
    @Test @DisabledOnOs(OS.WINDOWS) void defaultFocusTracksTheActiveTerminalBeforeAndAfterShellStartup() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        var owners = new WindowContent[1];
        var policies = new FocusTraversalPolicy[1];
        edt(() -> {
            var owner = owners[0] = content(launcher(pending));
            var model = new Contributions();
            owner.connectContributions(model);
            model.addPanel("focus.panel", "Tool panel", new ImageIcon(), PanelRegion.LEFT, site -> new JTextField("Search hosts"));
            model.requestPanel(new Contributions.PanelRequest(owner.id(), "focus.panel", Contributions.PanelRequest.Op.SHOW));
            var policy = policies[0] = owner.getFocusTraversalPolicy();
            assertThat(policy).as("the window supplies a terminal-first focus policy").isNotNull();
            assertThat(owner.isFocusTraversalPolicyProvider()).isTrue();
            assertThat(policy.getDefaultComponent(owner)).isSameAs(owner.currentPane());
            assertThat(owner.currentPane().isFocusable()).as("the pending shell has a focus target").isTrue();
        });
        pending.remove().run();
        until(() -> owners[0].currentPane().view() != null);
        edt(() -> {
            var owner = owners[0];
            var first = owner.currentTab();
            var view = owner.currentPane().view();
            assertThat(policies[0].getDefaultComponent(owner)).isSameAs(view);
            assertThat(owner.currentPane().isFocusable()).as("the placeholder leaves the tab order after startup").isFalse();
            owner.newTab(HOME);
            assertThat(policies[0].getDefaultComponent(owner)).isSameAs(owner.currentPane());
            owner.selectTab(first);
            assertThat(policies[0].getDefaultComponent(owner)).isSameAs(view);
            owner.close();
            assertThat(policies[0].getDefaultComponent(owner)).isNull();
        });
    }
}
