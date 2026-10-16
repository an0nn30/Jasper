package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.model.Nodes.Stack;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SurfaceContrast;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 8.2's Notice: the severity as a glyph and its word before one sentence, on the severity's ground. */
@SuppressWarnings("try")
class NoticesTest {
    static final ControlState READY = ControlState.READY;
    static final String FTP = "Plain FTP sends the password unencrypted";

    @Test void aNoticeShowsItsSeverityAsAGlyphAndItsWordBeforeItsText() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var notices = new ArrayList<Node>();
            for (var severity : Severity.values()) notices.add(new Notice("notice-" + severity, READY, severity, FTP));
            try (var view = new KitView(context, new ViewSpec(new Stack("notices", READY, LayoutRole.VERTICAL, notices)), e -> {})) {
                KitFixtures.render(view, 420, 200, 1);
                for (var severity : Severity.values()) {
                    String id = "notice-" + severity;
                    var text = find(view, id + ".text", JLabel.class);
                    assertThat(text.getText()).isEqualTo(Notices.word(severity) + " · " + FTP);
                    assertThat(text.getForeground()).isEqualTo(SurfaceContrast.painted(context.theme(), Notices.text(severity)));
                    assertThat(text.getAccessibleContext().getAccessibleName()).isEqualTo(text.getText());
                    var glyph = find(view, id + ".glyph", JLabel.class);
                    assertThat(glyph.getIcon()).isNotNull();
                    assertThat(glyph.getAccessibleContext().getAccessibleName()).isEqualTo(Notices.word(severity));
                }
                assertThat(List.of(Notices.word(Severity.INFO), Notices.word(Severity.WARNING), Notices.word(Severity.DANGER)))
                    .containsExactly("Information", "Warning", "Error");
            } finally { release(context); }
        });
    }

    @Test void aLongNoticeWrapsBesideItsGlyphAndKeepsItsActionAtTheTrailingEdge() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var choose = new Nodes.Button("choose", READY, "Choose…", "", ButtonKind.SECONDARY, "choose", "");
            var wordy = new Notice("wordy", READY, Severity.DANGER,
                "The folder no longer exists, so the server cannot start until you choose another folder to share", List.of(choose));
            try (var view = new KitView(context, new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(wordy))), events::add)) {
                KitFixtures.render(view, 300, 160, 1);
                var surface = find(view, "wordy", JComponent.class);
                var text = find(view, "wordy.text", JLabel.class);
                var glyph = find(view, "wordy.glyph", JLabel.class);
                var button = find(view, "choose", JButton.class);
                assertThat(text.getHeight()).as("the text wraps").isGreaterThan(text.getFontMetrics(text.getFont()).getHeight());
                assertThat(glyph.getX() + glyph.getWidth()).isLessThanOrEqualTo(text.getX());
                assertThat(text.getX() + text.getWidth()).isLessThanOrEqualTo(button.getX());
                assertThat(button.getX() + button.getWidth()).isEqualTo(surface.getWidth() - surface.getInsets().right);
                button.doClick(0);
                assertThat(events).containsExactly(new UiEvent.Action("choose"));
            } finally { release(context); }
        });
    }
}
