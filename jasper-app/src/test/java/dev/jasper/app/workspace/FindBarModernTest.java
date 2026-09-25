package dev.jasper.app.workspace;

import com.formdev.flatlaf.FlatClientProperties;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.ThemeStyle;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.config.TerminalOptions;
import dev.jasper.terminal.session.SessionLaunchOptions;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.view.TerminalView;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(OS.WINDOWS)
class FindBarModernTest {
    @AfterEach void reset() throws Exception { edt(() -> new ThemeController()); }

    @Test void modernBarIsOneRowWithInFieldTogglesAndIconOnlyNavigation() throws Exception {
        try (TerminalSession session = shell(HOME)) {
            edt(() -> {
                new ThemeController(ThemeStyle.MODERN, Appearance.LIGHT);
                var bar = new FindBar(new TerminalView(session, TerminalOptions.defaults()));
                var query = bar.queryField();
                assertThat(query.getAccessibleContext().getAccessibleName()).isEqualTo("Find in terminal");
                var leading = (JComponent) query.getClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_COMPONENT);
                assertThat(leading.getName()).isEqualTo("findHistory");
                var trailing = (JComponent) query.getClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT);
                assertThat(names(trailing)).containsSubsequence("clearFind", "caseSensitive", "regex");
                assertThat(bar.caseButton().getText()).isNull();
                assertThat(bar.caseButton().getIcon().getIconWidth()).isEqualTo(16);
                assertThat(bar.caseButton().getAccessibleContext().getAccessibleName()).isEqualTo("Case sensitive");
                assertThat(bar.regexButton().getText()).isNull();
                assertThat(bar.regexButton().getAccessibleContext().getAccessibleName()).isEqualTo("Regular expression");
                String[][] buttons = {{"previousMatch", "Previous"}, {"nextMatch", "Next"}, {"closeFind", "Close"}};
                for (String[] expected : buttons) {
                    var button = (AbstractButton) WindowTabsTest.named(bar, expected[0]);
                    assertThat(button).as(expected[0]).isNotNull();
                    assertThat(button.getText()).as(expected[0]).isNull();
                    assertThat(button.getIcon().getIconWidth()).isEqualTo(16);
                    assertThat(button.getAccessibleContext().getAccessibleName()).isEqualTo(expected[1]);
                    assertThat(button.getToolTipText()).isNotBlank();
                }
                var clear = (AbstractButton) WindowTabsTest.named(bar, "clearFind");
                assertThat(clear.isVisible()).isFalse();
                query.setText("alpha");
                assertThat(clear.isVisible()).isTrue();
                clear.doClick();
                assertThat(query.getText()).isEmpty();
                assertThat(clear.isVisible()).isFalse();
                bar.dispose();
            });
        }
    }

    @Test void togglesDriveTheQueryMissesTintTheFieldAndRecentSearchesStayInMemory() throws Exception {
        try (TerminalSession session = TerminalSession.start(SessionLaunchOptions.builder().command(List.of("/bin/sh", "-c",
            "printf 'alpha alpha\\n\\033]2;ready\\007'; read answer")).environment(System.getenv()).workingDirectory(HOME)
            .grid(new GridSize(80, 24)).scrollback(100).build())) {
            until(() -> session.title().equals("ready"));
            FindBar[] bar = new FindBar[1];
            edt(() -> {
                new ThemeController(ThemeStyle.MODERN, Appearance.LIGHT);
                var view = new TerminalView(session, TerminalOptions.defaults());
                view.setSize(view.getPreferredSize());
                bar[0] = attached(view); bar[0].open();
                bar[0].queryField().setText("ALPHA");
                bar[0].caseButton().doClick();
            });
            until(() -> bar[0].missing());
            edt(() -> {
                var query = bar[0].queryField();
                assertThat(query.getClientProperty(FlatClientProperties.OUTLINE)).isEqualTo(FlatClientProperties.OUTLINE_ERROR);
                assertThat(query.getBackground()).isEqualTo(UIManager.getColor("Jasper.findErrorBackground"));
                assertThat(bar[0].countLabel().getText()).isEqualTo("0 results");
                SwingUtilities.updateComponentTreeUI(bar[0]);
                assertThat(query.getBackground()).as("a theme switch keeps the no-match tint")
                    .isEqualTo(UIManager.getColor("Jasper.findErrorBackground"));
                bar[0].caseButton().doClick();
            });
            until(() -> bar[0].result().count() == 2);
            edt(() -> {
                assertThat(bar[0].missing()).isFalse();
                assertThat(bar[0].queryField().getClientProperty(FlatClientProperties.OUTLINE)).isNull();
                SwingUtilities.updateComponentTreeUI(bar[0]);
                assertThat(bar[0].queryField().getBackground()).isEqualTo(UIManager.getColor("TextField.background"));
                assertThat(bar[0].countLabel().getText()).isEqualTo("2/2");
                bar[0].next();
                bar[0].queryField().setText("beta");
                bar[0].close();
                var menu = bar[0].recentMenu();
                assertThat(menu.getComponentCount()).isEqualTo(2);
                assertThat(((JMenuItem) menu.getComponent(0)).getText()).isEqualTo("beta");
                assertThat(((JMenuItem) menu.getComponent(1)).getText()).isEqualTo("ALPHA");
                assertThat(((JMenuItem) menu.getComponent(0)).getClientProperty("html.disable")).isEqualTo(true);
                ((JMenuItem) menu.getComponent(1)).doClick();
                assertThat(bar[0].queryField().getText()).isEqualTo("ALPHA");
                bar[0].dispose(); bar[0].removeNotify();
            });
        }
    }

    private static FindBar attached(TerminalView view) {
        var bar = new FindBar(view);
        // Search runs only while showing; keep the root lightweight and omit native caret location queries.
        bar.queryField().removeCaretListener((javax.swing.event.CaretListener) bar.queryField().getAccessibleContext());
        bar.addNotify();
        return bar;
    }

    private static List<String> names(Container parent) {
        List<String> names = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            if (child.getName() != null) names.add(child.getName());
            if (child instanceof Container nested) names.addAll(names(nested));
        }
        return names;
    }
}
