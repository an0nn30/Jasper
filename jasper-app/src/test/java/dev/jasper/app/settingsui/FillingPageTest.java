package dev.jasper.app.settingsui;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Dimension;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.junit.jupiter.api.Test;

class FillingPageTest {
    @Test void fillsTheVisibleAreaSoItsContentScrollsItself() throws Exception {
        edt(() -> {
            var content = new JPanel();
            content.setPreferredSize(new Dimension(800, 900));
            var page = new FillingPage(content);
            var scroll = new JScrollPane(page);
            scroll.setSize(400, 300);
            scroll.doLayout();
            scroll.getViewport().doLayout();
            page.doLayout();
            assertThat(page.getSize()).isEqualTo(scroll.getViewport().getExtentSize());
            assertThat(content.getSize()).isEqualTo(page.getSize());
        });
    }
}
