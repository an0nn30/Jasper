package dev.jasper.app.contributions;

import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.util.ArrayList;
import java.util.Optional;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class PanelBadgeTest {
    @Test void aBadgeChangeRerendersTheRailOnlyWhenItChanges() {
        var model = new Contributions();
        PanelEntry panel = model.addPanel("dev.x.notes", "Notes", new ImageIcon(), Slot.RIGHT_TOP, site -> new JLabel());
        var kinds = new ArrayList<Contributions.Kind>();
        model.onChanged(kinds::add);
        var badge = new PanelBadge(2, PanelBadge.Tone.ACCENT, "2 unread notifications");
        panel.setBadge(Optional.of(badge));
        panel.setBadge(Optional.of(new PanelBadge(2, PanelBadge.Tone.ACCENT, "2 unread notifications")));
        assertThat(kinds).containsExactly(Contributions.Kind.RAIL);
        assertThat(panel.badge()).contains(badge);
        panel.setBadge(Optional.empty());
        assertThat(kinds).containsExactly(Contributions.Kind.RAIL, Contributions.Kind.RAIL);
        panel.close();
        kinds.clear();
        panel.setBadge(Optional.of(badge));
        assertThat(kinds).as("a closed panel has no rail button to mark").isEmpty();
    }

    @Test void aBadgeCountsSomethingAndSaysWhat() {
        assertThatThrownBy(() -> new PanelBadge(0, PanelBadge.Tone.ACCENT, "none")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PanelBadge(1, PanelBadge.Tone.ERROR, " ")).isInstanceOf(IllegalArgumentException.class);
    }
}
