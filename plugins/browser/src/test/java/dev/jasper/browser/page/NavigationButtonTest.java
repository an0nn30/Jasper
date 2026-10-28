package dev.jasper.browser.page;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Which of AWT's mouse buttons are the mouse's back and forward buttons, on each platform. */
class NavigationButtonTest {
    static final String WINDOWS = "Windows 11", LINUX = "Linux", MAC = "Mac OS X";
    static final String W_TOOLKIT = "sun.awt.windows.WToolkit", X11 = "sun.awt.X11.XToolkit", WAYLAND = "sun.awt.wl.WLToolkit",
        MAC_TOOLKIT = "sun.lwawt.macosx.LWCToolkit";

    @Test void onWindowsTheyAreButtonsFourAndFive() {
        assertThat(NavigationButton.of(4, WINDOWS, W_TOOLKIT)).contains(NavigationButton.BACK);
        assertThat(NavigationButton.of(5, WINDOWS, W_TOOLKIT)).contains(NavigationButton.FORWARD);
        for (int button = 0; button <= 9; button++)
            if (button != 4 && button != 5) assertThat(NavigationButton.of(button, WINDOWS, W_TOOLKIT)).as("button " + button).isEmpty();
    }

    /** X11's side buttons are its 8 and 9, which AWT reports as 6 and 7; its 6 and 7 scroll sideways and arrive as 4 and 5. */
    @Test void onLinuxUnderX11TheyAreButtonsSixAndSevenAndFourAndFiveScrollSideways() {
        assertThat(NavigationButton.of(6, LINUX, X11)).contains(NavigationButton.BACK);
        assertThat(NavigationButton.of(7, LINUX, X11)).contains(NavigationButton.FORWARD);
        assertThat(NavigationButton.of(4, LINUX, X11)).isEmpty();
        assertThat(NavigationButton.of(5, LINUX, X11)).isEmpty();
    }

    /** JetBrains Runtime's Wayland toolkit reports the side buttons, BTN_SIDE and BTN_EXTRA, as 4 and 5. */
    @Test void onLinuxUnderWaylandTheyAreButtonsFourAndFive() {
        assertThat(NavigationButton.of(4, LINUX, WAYLAND)).contains(NavigationButton.BACK);
        assertThat(NavigationButton.of(5, LINUX, WAYLAND)).contains(NavigationButton.FORWARD);
        assertThat(NavigationButton.of(6, LINUX, WAYLAND)).isEmpty();
    }

    @Test void onMacOsNoButtonIsOne() {
        for (int button = 0; button <= 9; button++)
            assertThat(NavigationButton.of(button, MAC, MAC_TOOLKIT)).as("button " + button).isEmpty();
    }

    @Test void theOrdinaryButtonsAreNeverOne() {
        for (String toolkit : new String[] { W_TOOLKIT, X11, WAYLAND })
            for (int button = 0; button <= 3; button++)
                assertThat(NavigationButton.of(button, toolkit.equals(W_TOOLKIT) ? WINDOWS : LINUX, toolkit)).isEqualTo(Optional.empty());
    }
}
