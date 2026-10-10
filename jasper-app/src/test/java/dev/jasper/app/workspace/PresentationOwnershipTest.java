package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.UiFontConfig;
import dev.jasper.app.config.WorkspaceThemeChoice;
import java.util.ArrayDeque;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class PresentationOwnershipTest {
    @Test void installationDoesNotPublishLegacyPaletteOrDelegateAliases() throws Exception {
        edt(() -> {
            try (var themes = new ThemeController()) {
                themes.configureWorkspace(new WorkspaceThemeChoice("Jasper Light"), Theme.JASPER_LIGHT);
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
            try (var themes = new ThemeController(); var owner = content(launcher(new ArrayDeque<>()), themes)) {
                themes.configure(Theme.DARK, new WorkspaceThemeChoice("Jasper Light"), Theme.JASPER_LIGHT, UiFontConfig.defaults());
                var model = new dev.jasper.app.contributions.Contributions(); owner.connectContributions(model);
                var root = new JPanel(); var child = new JTextField("retained"); root.add(child);
                var document = child.getDocument(); child.select(1, 4);
                int rootListeners = root.getContainerListeners().length;
                int childListeners = child.getPropertyChangeListeners().length;
                var entry = model.content().open("test", "shallow", "Shallow", owner.id(), root);
                assertThat(root.getClientProperty("Jasper.workspaceTheme")).isSameAs(themes.current().workspace());
                assertThat(root.getContainerListeners()).hasSize(rootListeners);
                assertThat(child.getPropertyChangeListeners()).hasSize(childListeners);
                assertThat(child.getClientProperty("FlatLaf.style")).isNull();
                var late = new JButton("late"); root.add(late);
                assertThat(late.getClientProperty("FlatLaf.style")).isNull();
                themes.configureWorkspace(new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK);
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
            try (var themes = new ThemeController()) {
                themes.configure(dev.jasper.app.appearance.Theme.DARK);
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
