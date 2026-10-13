package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.UiFontConfig;
import java.util.ArrayDeque;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class PresentationOwnershipTest {
    @Test void installationDoesNotPublishLegacyPaletteOrDelegateAliases() throws Exception {
        edt(() -> {
            try (var themes = ThemeTestSupport.controller(true)) {
                themes.configure(false, dev.jasper.app.config.TerminalSchemeChoice.MATCH);
                for (String key : new String[]{"Jasper.workspace.text", "Jasper.workspace.editorBackground"})
                    assertThat(UIManager.get(key)).as(key).isNull();
                assertThat(UIManager.getString("ListUI")).doesNotContain("AppListUI");
                assertThat(UIManager.getInt("SplitPane.dividerSize")).isEqualTo(8);
                assertThat(UIManager.getBoolean("TitlePane.centerTitle")).isTrue();
                assertThat(UIManager.getBorder("SplitPaneDivider.border")).isInstanceOf(SplitDividerBorder.class);
                try { UIManager.setLookAndFeel(UIManager.getLookAndFeel()); }
                catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
                assertThat(UIManager.getInt("SplitPane.dividerSize")).isEqualTo(8);
                assertThat(UIManager.getBoolean("TitlePane.centerTitle")).isTrue();
                assertThat(UIManager.getBorder("SplitPaneDivider.border")).isInstanceOf(SplitDividerBorder.class);
            }
        });
    }

    @Test void hostedRoutesStayShallowAndReleaseTheirLifetimeOnRemoval() throws Exception {
        edt(() -> {
            try (var themes = ThemeTestSupport.controller(true); var owner = content(launcher(new ArrayDeque<>()), themes)) {
                themes.configure(false, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());
                var model = new dev.jasper.app.contributions.Contributions(); owner.connectContributions(model);
                var root = new JPanel(); var child = new JTextField("retained"); root.add(child);
                var document = child.getDocument(); child.select(1, 4);
                int rootListeners = root.getContainerListeners().length;
                int childListeners = child.getPropertyChangeListeners().length;
                var entry = model.content().open("test", "shallow", "Shallow", owner.id(), root);
                assertThat(root.getClientProperty("Jasper.workspaceTheme")).as("the variant matches: no workspace override").isNull();
                assertThat(root.getContainerListeners()).hasSize(rootListeners);
                assertThat(child.getPropertyChangeListeners()).hasSize(childListeners);
                assertThat(child.getClientProperty("FlatLaf.style")).isNull();
                var late = new JButton("late"); root.add(late);
                assertThat(late.getClientProperty("FlatLaf.style")).isNull();
                themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH);
                assertThat(child.getDocument()).isSameAs(document);
                assertThat(child.getSelectedText()).isEqualTo("eta");
                model.content().remove(entry);
                assertThat(root.getClientProperty("Jasper.zone")).isNull();
                assertThat(root.getClientProperty("Jasper.workspaceTheme")).isNull();
                assertThat(child.getPropertyChangeListeners()).hasSize(childListeners);
            }
        });
    }

    @Test void toolSurfaceDoesNotStyleOrObserveDescendants() throws Exception {
        edt(() -> {
            try (var themes = ThemeTestSupport.controller(true)) {
                themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH);
                var root = new JPanel(); var child = new JPanel(); root.add(child);
                var color = child.getBackground();
                int listeners = root.getContainerListeners().length;
                ToolWindowSurface.apply(root);
                assertThat(child.getBackground()).isSameAs(color);
                assertThat(root.getContainerListeners()).hasSize(listeners);
                var late = new JPanel(); var lateColor = late.getBackground(); root.add(late);
                assertThat(late.getBackground()).isSameAs(lateColor);
            }
        });
    }
}
