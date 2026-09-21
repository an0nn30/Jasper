package dev.jasper.buddy.view;

import dev.jasper.buddy.notice.BuddyNoticeId;
import dev.jasper.buddy.config.BuddyOptions;
import dev.jasper.buddy.notice.*;
import dev.jasper.buddy.internal.model.BuddyDeck;
import java.awt.Font;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BuddyCompanionTest {
    private BuddyOptions options() { return BuddyOptions.builder(new Font("Dialog", Font.PLAIN, 13)).build(); }
    @Test void modelRemainsUsableWithoutNativePresentationAndCloseReleasesIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var deck = new BuddyDeck();
            var buddy = new BuddyCompanion(options(), deck);
            var id = new BuddyNoticeId("test", 1);
            var notice = new BuddyNotice(id, BuddyNotice.Kind.TASK, "work", BuddyNotice.State.RUNNING, () -> "busy", () -> {});
            buddy.post(notice); buddy.hide();
            assertThat(deck.notices()).containsExactly(notice);
            assertThat(buddy.show()).isFalse();
            buddy.clear(); assertThat(deck.notices()).isEmpty();
            buddy.post(notice); buddy.close(); buddy.close();
            buddy.post(notice); buddy.updateTitle(id,"later"); buddy.acknowledge(id); buddy.dismiss(id);
            buddy.orphan(id); buddy.orphan(id,"done"); buddy.clear(); buddy.setWorking(true);
            buddy.hide(); buddy.greet(); buddy.poke(); buddy.applyOptions(options());
            assertThat(buddy.show()).isFalse(); assertThat(deck.notices()).isEmpty();
            assertThatNullPointerException().isThrownBy(() -> new BuddyCompanion(null));
        });
    }
    @Test void everyOperationRequiresTheEdt() throws Exception {
        assertThatIllegalStateException().isThrownBy(() -> new BuddyCompanion(options()));
        var holder = new BuddyCompanion[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new BuddyCompanion(options()));
        var b = holder[0]; var id = new BuddyNoticeId("test",1);
        for (Runnable call : java.util.List.<Runnable>of(() -> b.post(null), () -> b.updateTitle(id,"title"),
            () -> b.acknowledge(id), () -> b.dismiss(id), () -> b.orphan(id), () -> b.orphan(id,"done"),
            b::clear, () -> b.setWorking(true), () -> b.show(), b::hide, b::greet, b::poke,
            () -> b.applyOptions(options()), b::close)) assertThatIllegalStateException().isThrownBy(call::run);
        SwingUtilities.invokeAndWait(b::close);
    }
}
