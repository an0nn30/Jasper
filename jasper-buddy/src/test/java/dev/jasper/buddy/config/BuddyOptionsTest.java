package dev.jasper.buddy.config;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class BuddyOptionsTest {
@Test void optionsCopyPreservesAllFields() {
    var font = new java.awt.Font("Dialog", java.awt.Font.PLAIN, 13);
    var positions = new java.util.ArrayList<BuddyPosition>();
    Runnable activate = () -> {};
    Runnable toggle = () -> {};
    var original = BuddyOptions.builder(font).dark(false)
        .initialPosition(new BuddyPosition(-100, 42)).positionChanged(positions::add)
        .activateHost(activate).toggleRequested(toggle).build();
    var changed = original.toBuilder().primaryFont(font.deriveFont(15f)).build();
    assertThat(changed.dark()).isFalse();
    assertThat(changed.initialPosition()).contains(new BuddyPosition(-100, 42));
    assertThat(changed.activateHost()).isSameAs(activate);
    assertThat(changed.toggleRequested()).isSameAs(toggle);
    changed.positionChanged().accept(new BuddyPosition(1, 2));
    assertThat(positions).containsExactly(new BuddyPosition(1, 2));
}
}
