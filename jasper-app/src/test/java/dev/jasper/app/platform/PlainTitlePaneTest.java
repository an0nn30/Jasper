package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.ui.FlatTitlePane;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import javax.swing.JLabel;
import javax.swing.JRootPane;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** FlatLaf's title pane shows a window's title, a program's or a plugin's, as typed. */
@ExtendWith(EdtTestExtension.class)
class PlainTitlePaneTest {
    private static final String MARKUP = "<html><b>x</b>";

    @Test void theTitlePaneShowsAWindowTitleAsTextAndSoDoesOneAThemeChangeBuilds() throws Exception {
        var original = UIManager.getLookAndFeel();
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
            var root = new JRootPane();
            root.setWindowDecorationStyle(JRootPane.FRAME);
            PlainTitlePane.install(root);
            // FlatLaf sets the title when the window shows and whenever it changes.
            var title = title(pane(root));
            title.setText(MARKUP);
            assertThat(title.getText()).isEqualTo(MARKUP);
            assertThat(title.getClientProperty(BasicHTML.propertyKey)).as("the title builds no HTML view").isNull();
            // A theme change adds a new pane, which a shown window fills with its title as it is added.
            var replaced = new FlatTitlePane(root);
            title(replaced).setText(MARKUP);
            root.getLayeredPane().add(replaced);
            assertThat(title(replaced).getText()).isEqualTo(MARKUP);
            assertThat(title(replaced).getClientProperty(BasicHTML.propertyKey)).as("the new pane's title builds no HTML view").isNull();
        } finally { UIManager.setLookAndFeel(original); }
    }

    private static FlatTitlePane pane(JRootPane root) {
        return Arrays.stream(root.getLayeredPane().getComponents()).filter(FlatTitlePane.class::isInstance)
            .map(FlatTitlePane.class::cast).findFirst().orElseThrow();
    }

    private static JLabel title(Container pane) {
        for (Component part : pane.getComponents()) if (part instanceof JLabel label) return label;
        throw new AssertionError("no title label in " + pane);
    }
}
