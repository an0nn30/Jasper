package dev.jasper.app.platform;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ResidentTrayTest {
    private final List<String> events = new ArrayList<>();
    /** Whether each icon call in {@code events} ran on the EDT, in the same order. */
    private final List<Boolean> onEdt = new ArrayList<>();
    private final List<Runnable> clicksOnIcons = new ArrayList<>();
    private final List<String> clicks = new ArrayList<>();
    private int opened;
    private boolean refuse;

    /** A tray over a recording stand-in for the notification area (or, with {@code mac}, the menu bar). */
    private ResidentTray tray(boolean mac) {
        var tray = new ResidentTray(mac, (action, actions) -> {
            if (refuse) return null;
            int number = clicksOnIcons.size() + 1;
            clicksOnIcons.add(action);
            record("add " + number + (actions.isPresent() ? " with its menu" : ""));
            return new ResidentTray.Icon(tip -> record("tooltip " + number + " " + tip),
                message -> record("balloon " + number + " " + message.level() + " " + message.title()),
                () -> record("remove " + number));
        });
        tray.onClick(clicks::add);
        return tray;
    }

    private ResidentTray tray() { return tray(false); }

    private ResidentTray.Actions actions() {
        return new ResidentTray.Actions(() -> opened++, () -> { }, () -> { }, () -> { });
    }

    private void record(String event) {
        events.add(event);
        onEdt.add(SwingUtilities.isEventDispatchThread());
    }

    private static SystemNotifier.Message message(String id) {
        return new SystemNotifier.Message(id, SystemNotifier.Level.WARNING, "title " + id, "body");
    }

    /** A click on the newest icon's balloon or on the icon itself, which Windows reports alike. */
    private void click() { clicksOnIcons.getLast().run(); }

    @Test void macOsAndWindowsDesktopsGetAnIconAndLinuxDoesNot() {
        assertThat(ResidentTray.applies("Windows 11", false)).isTrue();
        assertThat(ResidentTray.applies("Windows 11", true)).as("headless").isFalse();
        assertThat(ResidentTray.applies("Mac OS X", false)).as("the menu bar item").isTrue();
        assertThat(ResidentTray.applies("Mac OS X", true)).as("headless").isFalse();
        assertThat(ResidentTray.applies("Linux", false)).isFalse();
        assertThat(ResidentTray.applies(null, false)).isFalse();
    }

    @Test void theMenuBarItemOffersANewTerminalWindowSettingsAndTheRealQuit() {
        assertThat(ResidentTray.entries(true)).extracting(entry -> entry.label)
            .containsExactly("New Terminal Window", "Settings…", null, "Quit Jasper Completely");
        assertThat(ResidentTray.entries(false)).extracting(entry -> entry.label)
            .as("the Windows tray menu is unchanged").containsExactly("Open Jasper", null, "Quit Jasper");
    }

    @Test void eachEntryRunsItsAction() {
        List<String> ran = new ArrayList<>();
        var actions = new ResidentTray.Actions(() -> ran.add("open"), () -> ran.add("new terminal window"),
            () -> ran.add("settings"), () -> ran.add("quit"));
        for (ResidentTray.Entry entry : ResidentTray.Entry.values()) entry.action(actions).run();
        assertThat(ran).containsExactly("open", "new terminal window", "settings", "quit", "quit");
    }

    @Test void theMenuBarGlyphIsAShapeAtOneAndTwoTimesScale() {
        Image glyph = ResidentTray.menuBarGlyph();
        assertThat(glyph).isInstanceOf(MultiResolutionImage.class);
        List<Image> variants = ((MultiResolutionImage) glyph).getResolutionVariants();
        assertThat(variants).extracting(image -> image.getWidth(null)).containsExactly(16, 32);
        BufferedImage large = (BufferedImage) variants.get(1);
        int opaque = 0, clear = 0;
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
            int alpha = large.getRGB(x, y) >>> 24;
            if (alpha == 0) clear++;
            else if (alpha > 200) opaque++;
        }
        assertThat(opaque).as("a template image draws with its alpha").isPositive();
        assertThat(clear).as("and lets the menu bar show through elsewhere").isGreaterThan(opaque);
    }

    @Test void picksTheSmallestIconThatFillsTheSlot() {
        var images = ApplicationIcon.images("windows");
        assertThat(ResidentTray.image(images, new Dimension(16, 16)).getWidth(null)).isEqualTo(16);
        assertThat(ResidentTray.image(images, new Dimension(20, 20)).getWidth(null)).as("150% scaling").isEqualTo(20);
        assertThat(ResidentTray.image(images, new Dimension(33, 33)).getWidth(null)).isEqualTo(36);
        assertThat(ResidentTray.image(images, new Dimension(512, 512)).getWidth(null)).as("largest available").isEqualTo(256);
    }

    @Test void whereNoIconAppliesInstallChangesNothingAndRemovalIsANoOp() {
        // Headless only: elsewhere this would add an icon to the real menu bar or notification area.
        assumeTrue(GraphicsEnvironment.isHeadless());
        var ran = new AtomicInteger();
        var tray = ResidentTray.forThisDesktop();
        Runnable remove = tray.install(new ResidentTray.Actions(ran::incrementAndGet, ran::incrementAndGet,
            ran::incrementAndGet, ran::incrementAndGet));
        remove.run();
        tray.balloon(message("a"));
        assertThat(ran).hasValue(0);
    }

    @Test void theMenuBarItemShowsWithItsMenuAndCarriesNoBalloons() {
        var tray = tray(true);
        Runnable remove = tray.install(actions());
        tray.balloon(message("a"));
        tray.focused();
        remove.run();
        assertThat(events).as("macOS banners come from their own bridge").containsExactly("add 1 with its menu",
            "tooltip 1 Jasper", "remove 1");
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
        Runnable remove = tray.install(actions());
        tray.balloon(message("a"));
        tray.focused();
        tray.balloon(message("b"));
        click();
        click();
        remove.run();
        remove.run();
        assertThat(clicks).containsExactly("b");
        assertThat(opened).as("with no notification waiting, the click is Open").isEqualTo(1);
        assertThat(events).containsExactly("add 1 with its menu", "tooltip 1 Jasper is running in the background",
            "balloon 1 WARNING title a", "balloon 1 WARNING title b", "remove 1");
    }

    @Test void aBalloonsIconGivesWayToTheResidentIconAndItsNotificationStillWaits() {
        var tray = tray();
        tray.balloon(message("a"));
        tray.install(actions());
        click();
        assertThat(events).containsExactly("add 1", "tooltip 1 Jasper", "balloon 1 WARNING title a", "remove 1",
            "add 2 with its menu", "tooltip 2 Jasper is running in the background");
        assertThat(clicks).containsExactly("a");
        assertThat(opened).isZero();
    }

    @Test void whereTheTrayRefusesAnIconNothingShowsAndRemovalIsANoOp() {
        refuse = true;
        var tray = tray();
        tray.install(actions()).run();
        tray.balloon(message("a"));
        tray.focused();
        assertThat(events).isEmpty();
        assertThat(opened).isZero();
    }

    @Test void eachLevelHasItsBalloonIcon() {
        assertThat(ResidentTray.type(SystemNotifier.Level.INFO)).isEqualTo(TrayIcon.MessageType.INFO);
        assertThat(ResidentTray.type(SystemNotifier.Level.WARNING)).isEqualTo(TrayIcon.MessageType.WARNING);
        assertThat(ResidentTray.type(SystemNotifier.Level.ERROR)).isEqualTo(TrayIcon.MessageType.ERROR);
    }

    @Test void theBackendHopsToTheEdtAndRoutesClicks() throws Exception {
        assertThat(SwingUtilities.isEventDispatchThread()).as("the test calls the backend from off the EDT").isFalse();
        var tray = tray();
        var backend = ResidentTray.backend(tray, clicks::add);
        backend.show().accept(message("a"));
        SwingUtilities.invokeAndWait(() -> { });
        click();
        backend.focused().run();
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(clicks).containsExactly("a");
        assertThat(events).containsExactly("add 1", "tooltip 1 Jasper", "balloon 1 WARNING title a", "remove 1");
        assertThat(onEdt).as("every tray call made through the backend ran on the EDT").hasSameSizeAs(events)
            .containsOnly(true);
    }
}
