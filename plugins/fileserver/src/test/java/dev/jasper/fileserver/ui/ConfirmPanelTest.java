package dev.jasper.fileserver.ui;

import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ConfirmPanelTest {
    @Test void runsOnlyTheChosenAnswersAndOwnsTheSheet() {
        var answers = new ArrayList<String>();
        var kit = new FakeKit("dev.jasper.fileserver");
        UiTestAccess.edt(() -> {
            var panel = new ConfirmPanel(kit, "2 file servers are running and will stop.", "Quit", "Cancel",
                () -> answers.add("quit"), () -> answers.add("cancel"));
            var view = (FakeKitView) panel.view();
            assertThat(view.spec().root()).isInstanceOf(Nodes.Form.class);
            assertThat(((Nodes.Text) UiTestAccess.node(view, "fileserver.confirm.message")).text()).isEqualTo("2 file servers are running and will stop.");
            view.emit(new UiEvent.Action("fileserver.confirm.cancel"));
            view.emit(new UiEvent.Action("fileserver.confirm.accept"));
            assertThat(answers).containsExactly("cancel", "quit");
            panel.close();
            assertThat(kit.views()).isEmpty();
        });
    }
    @Test void preservesHtmlLookingTextAsLiteralDescriptorContent() {
        UiTestAccess.edt(() -> {
            var panel = new ConfirmPanel(new FakeKit("dev.jasper.fileserver"), "<html><b>docs</b> will stop</html>", "Quit", "Cancel", () -> { }, () -> { });
            assertThat(((Nodes.Text) UiTestAccess.node((FakeKitView) panel.view(), "fileserver.confirm.message")).text())
                .isEqualTo("<html><b>docs</b> will stop</html>");
            panel.close();
        });
    }
}
