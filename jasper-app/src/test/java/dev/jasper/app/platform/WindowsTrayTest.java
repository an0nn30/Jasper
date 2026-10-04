package dev.jasper.app.platform;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.TrayIcon;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WindowsTrayTest {
    private final List<String> events = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    private final List<String> clicks = new ArrayList<>();
    private int opened;
    private boolean refuse;

    private WindowsTray tray() {
        var tray = new WindowsTray((action, menu) -> {
            if (refuse) return null;
            int number = actions.size() + 1;
            actions.add(action);
            events.add("add " + number + (menu.isPresent() ? " with Open and Quit" : ""));
            return new WindowsTray.Icon(tip -> events.add("tooltip " + number + " " + tip),
                message -> events.add("balloon " + number + " " + message.level() + " " + message.title()),
                () -> events.add("remove " + number));
        });
        tray.onClick(clicks::add);
        return tray;
    }

    private static SystemNotifier.Message message(String id) {
        return new SystemNotifier.Message(id, SystemNotifier.Level.WARNING, "title " + id, "body");
    }

    /** A click on the newest icon's balloon or on the icon itself, which Windows reports alike. */
    private void click() { actions.getLast().run(); }

    @Test void onlyADesktopWindowsSessionGetsATrayIcon() {
        assertThat(WindowsTray.applies("Windows 11", false)).isTrue();
        assertThat(WindowsTray.applies("Windows 11", true)).as("headless").isFalse();
        assertThat(WindowsTray.applies("Mac OS X", false)).as("macOS keeps its Dock icon").isFalse();
        assertThat(WindowsTray.applies("Linux", false)).isFalse();
        assertThat(WindowsTray.applies(null, false)).isFalse();
    }

    @Test void picksTheSmallestIconThatFillsTheSlot() {
        var images = ApplicationIcon.images("windows");
        assertThat(WindowsTray.image(images, new Dimension(16, 16)).getWidth(null)).isEqualTo(16);
        assertThat(WindowsTray.image(images, new Dimension(20, 20)).getWidth(null)).as("150% scaling").isEqualTo(20);
        assertThat(WindowsTray.image(images, new Dimension(33, 33)).getWidth(null)).isEqualTo(36);
        assertThat(WindowsTray.image(images, new Dimension(512, 512)).getWidth(null)).as("largest available").isEqualTo(256);
    }

    @Test void aBalloonInTheBackgroundAddsAnIconThatGoesWhenJasperIsFocused() {
        var tray = tray();
        tray.balloon(message("a"));
        tray.focused();
        assertThat(events).containsExactly("add 1", "tooltip 1 Jasper", "balloon 1 WARNING title a", "remove 1");
    }

    @Test void aSecondBalloonReusesTheIcon() {
        var tray = tray();
        tray.balloon(message("a"));
        tray.balloon(message("b"));
        assertThat(events).containsExactly("add 1", "tooltip 1 Jasper", "balloon 1 WARNING title a",
            "balloon 1 WARNING title b");
    }

    @Test void aClickActivatesTheMostRecentNotificationOnce() {
        var tray = tray();
        tray.balloon(message("a"));
        tray.balloon(message("b"));
        click();
        click();
        assertThat(clicks).containsExactly("b");
    }

    @Test void aNotificationReadInJasperIsNotActivated() {
        var tray = tray();
        tray.balloon(message("a"));
        tray.withdraw("other");
        tray.withdraw("a");
        click();
        tray.balloon(message("b"));
        tray.withdrawAll();
        click();
        assertThat(clicks).isEmpty();
    }

    @Test void theResidentIconCarriesBalloonsAndStaysWhenJasperIsFocused() {
        var tray = tray();
        Runnable remove = tray.resident(() -> opened++, () -> { });
        tray.balloon(message("a"));
        tray.focused();
        tray.balloon(message("b"));
        click();
        click();
        remove.run();
        remove.run();
        assertThat(clicks).containsExactly("b");
        assertThat(opened).as("with no notification waiting, the click is Open").isEqualTo(1);
        assertThat(events).containsExactly("add 1 with Open and Quit", "tooltip 1 Jasper is running in the background",
            "balloon 1 WARNING title a", "balloon 1 WARNING title b", "remove 1");
    }

    @Test void aBalloonsIconGivesWayToTheResidentIconAndItsNotificationStillWaits() {
        var tray = tray();
        tray.balloon(message("a"));
        tray.resident(() -> opened++, () -> { });
        click();
        assertThat(events).containsExactly("add 1", "tooltip 1 Jasper", "balloon 1 WARNING title a", "remove 1",
            "add 2 with Open and Quit", "tooltip 2 Jasper is running in the background");
        assertThat(clicks).containsExactly("a");
        assertThat(opened).isZero();
    }

    @Test void whereTheTrayRefusesAnIconNothingShowsAndRemovalIsANoOp() {
        refuse = true;
        var tray = tray();
        tray.resident(() -> opened++, () -> { }).run();
        tray.balloon(message("a"));
        tray.focused();
        assertThat(events).isEmpty();
        assertThat(opened).isZero();
    }

    @Test void headlessTheRealTrayShowsNothing() {
        // Headless only: elsewhere on Windows this would add an icon to the real notification area.
        assumeTrue(GraphicsEnvironment.isHeadless());
        var ran = new ArrayList<String>();
        var tray = WindowsTray.forThisDesktop();
        tray.resident(() -> ran.add("open"), () -> ran.add("quit")).run();
        tray.balloon(message("a"));
        assertThat(ran).isEmpty();
    }

    @Test void eachLevelHasItsBalloonIcon() {
        assertThat(WindowsTray.type(SystemNotifier.Level.INFO)).isEqualTo(TrayIcon.MessageType.INFO);
        assertThat(WindowsTray.type(SystemNotifier.Level.WARNING)).isEqualTo(TrayIcon.MessageType.WARNING);
        assertThat(WindowsTray.type(SystemNotifier.Level.ERROR)).isEqualTo(TrayIcon.MessageType.ERROR);
    }

    @Test void theBackendHopsToTheEdtAndRoutesClicks() throws Exception {
        var tray = tray();
        var backend = WindowsTray.backend(tray, clicks::add);
        backend.show().accept(message("a"));
        SwingUtilities.invokeAndWait(() -> { });
        click();
        backend.focused().run();
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(clicks).containsExactly("a");
        assertThat(events).endsWith("remove 1");
    }
}
