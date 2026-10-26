package dev.jasper.buddy.internal.presentation;

import java.awt.Rectangle;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BuddyDrawerLifetimeTest {
    @Test void onlyExplicitOpeningCanMakeHiddenDrawerVisibleOrRequestFocus() {
        var life = new BuddyDrawerLifetime();
        var anchor = new Rectangle(10, 20, 80, 90);
        life.move(anchor);
        assertThat(life.anchor()).isNull();
        assertThat(life.takeFocusRequest()).isFalse();
        life.open(anchor);
        assertThat(life.anchor()).isEqualTo(anchor);
        assertThat(life.takeFocusRequest()).isTrue();
        life.move(new Rectangle(20, 30, 80, 90));
        assertThat(life.takeFocusRequest()).isFalse();
        life.close();
        life.move(anchor);
        assertThat(life.anchor()).isNull();
        assertThat(life.takeFocusRequest()).isFalse();
        life.open(anchor); life.dispose(); life.open(anchor);
        assertThat(life.anchor()).isNull();
        assertThat(life.takeFocusRequest()).isFalse();
    }
    @Test void hiddenWindowRefreshAndAppearanceNeverAcquireANativeWindow() {
        var deck = new dev.jasper.buddy.internal.model.BuddyDeck();
        var window = new BuddyDeckWindow(BuddyTestAppearance.options(), deck, () -> {});
        try {
            deck.post(new dev.jasper.buddy.notice.BuddyNotice(new dev.jasper.buddy.notice.BuddyNoticeId("test", "one"),
                dev.jasper.buddy.notice.BuddyNotice.Kind.TASK, "Task", dev.jasper.buddy.notice.BuddyNotice.State.RUNNING,
                () -> "detail", () -> {}));
            window.refresh(); window.moveBeside(new Rectangle(10, 20, 80, 90));
            window.applyOptions(BuddyTestAppearance.options(false));
            assertThat(window.isShowing()).isFalse();
            window.hide(); window.refresh();
            assertThat(window.isShowing()).isFalse();
        } finally { window.dispose(); }
        window.refresh(); window.moveBeside(new Rectangle(10, 20, 80, 90));
        assertThat(window.isShowing()).isFalse();
    }
}
