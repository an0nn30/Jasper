package dev.jasper.fileserver.ui;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ConfirmPanelTest {
    @Test void runsTheChosenAnswer() {
        List<String> answers = new ArrayList<>();
        var panel = new ConfirmPanel("2 file servers are running and will stop.", "Quit", "Cancel", () -> answers.add("quit"), () -> answers.add("cancel"));
        assertThat(panel.message.getText()).isEqualTo("2 file servers are running and will stop.");
        assertThat(panel.confirm.getText()).isEqualTo("Quit");
        panel.cancel.doClick();
        panel.confirm.doClick();
        assertThat(answers).containsExactly("cancel", "quit");
    }

    @Test void showsHtmlLookingTextLiterally() {
        var panel = new ConfirmPanel("<html><b>docs</b> will stop</html>", "Quit", "Cancel", () -> { }, () -> { });
        assertThat(panel.message.getText()).isEqualTo("<html><b>docs</b> will stop</html>");
        assertThat(panel.message.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey)).isNull();
    }
}
