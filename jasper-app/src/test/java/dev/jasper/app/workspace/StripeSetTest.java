package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.kit.Kit;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Spec 10.7: a window docks through one stripe set; the default look's set is today's two rails. */
@ExtendWith(EdtTestExtension.class)
class StripeSetTest {
    @Test void theRailsShowABottomSlotOnItsSidesRailAndHaveNoStrip() {
        ThemeTestSupport.install(true);
        var rails = new StripeSet.IconRails();
        assertThat(rails.of(Slot.LEFT_TOP)).isSameAs(rails.left());
        assertThat(rails.of(Slot.LEFT_BOTTOM)).isSameAs(rails.left());
        assertThat(rails.of(Slot.BOTTOM_LEFT)).isSameAs(rails.left());
        assertThat(rails.of(Slot.RIGHT_TOP)).isSameAs(rails.right());
        assertThat(rails.of(Slot.BOTTOM_RIGHT)).isSameAs(rails.right());
        assertThat(rails.bottom()).isEmpty();
        assertThat(rails.all()).containsExactly(rails.left(), rails.right());
        assertThat(rails.left().thickness()).isEqualTo(rails.left().getPreferredSize().width);
        assertThat(DockChrome.STANDARD.stripes(Kit.DEFAULT)).isInstanceOf(StripeSet.IconRails.class);
    }

    @Test void aButtonCarriesTheDigitOfItsToggleAndKeepsItThroughABadge() {
        ThemeTestSupport.install(true);
        var button = new ToolStripe.Button("p", "SFTP", null, Slot.LEFT_TOP, true, Optional.empty(), OptionalInt.of(2));
        assertThat(button.number()).hasValue(2);
        assertThat(new ToolStripe.Button("p", "SFTP", null, Slot.LEFT_TOP, true).number()).isEmpty();
        var rail = new ToolStripe(dev.jasper.app.dock.Side.LEFT);
        rail.render(java.util.List.of(button), java.util.List.of());
        rail.updateBadge("p", Optional.of(new dev.jasper.app.contributions.PanelBadge(1, dev.jasper.app.contributions.PanelBadge.Tone.ACCENT, "1 unread")));
        assertThat(rail.buttons()).hasSize(1);
    }

    @Test void aWindowDocksThroughItsStripeSet() {
        var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()));
        try {
            assertThat(owner.stripes()).isInstanceOf(StripeSet.IconRails.class);
            assertThat(owner.leftStripe()).isSameAs(owner.stripes().left());
            assertThat(owner.rightStripe()).isSameAs(owner.stripes().right());
            assertThat(owner.bottomStrip()).isEmpty();
        } finally { owner.close(); }
    }
}
