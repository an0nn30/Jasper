package dev.jasper.app.platform;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinuxTrayPopupTest {
    private static LinuxTrayPopup.Screen screen(int dx, int dy, int dw, int dh, int ux, int uy, int uw, int uh, double scale) {
        return new LinuxTrayPopup.Screen(new Rectangle(dx, dy, dw, dh), new Rectangle(ux, uy, uw, uh),
            new Insets(0, 0, 40, 0), AffineTransform.getScaleInstance(scale, scale));
    }
    @Test void mapsRelativeDeviceVectorAtSecondMonitorOrigin() {
        var s = screen(1920, 0, 2560, 1440, 1920, 0, 1280, 720, 2);
        assertThat(LinuxTrayPopup.toUser(new Rectangle(2120, 100, 40, 40), s)).isEqualTo(new Rectangle(2020, 50, 20, 20));
        assertThat(LinuxTrayPopup.clamp(new Point(3190, 710), new Dimension(240, 200), s)).isEqualTo(new Point(2960, 480));
    }
    @Test void negativeOriginAndFractionalScalesRoundOutward() {
        var s = screen(-1920, -1080, 1920, 1080, -1280, -720, 1280, 720, 1.5);
        assertThat(LinuxTrayPopup.toUser(new Rectangle(-1919, -1079, 4, 4), s)).isEqualTo(new Rectangle(-1280, -720, 4, 4));
        s = screen(-1600, 0, 1600, 1000, -1280, 0, 1280, 800, 1.25);
        assertThat(LinuxTrayPopup.toUser(new Rectangle(-1599, 1, 2, 2), s)).isEqualTo(new Rectangle(-1280, 0, 3, 3));
    }
    @Test void clampsAllEdgesAndOversizedMenuToInsetWorkArea() {
        var s = new LinuxTrayPopup.Screen(new Rectangle(0, 0, 800, 600), new Rectangle(0, 0, 800, 600),
            new Insets(30, 10, 40, 20), new AffineTransform());
        assertThat(LinuxTrayPopup.clamp(new Point(-100, -100), new Dimension(200, 100), s)).isEqualTo(new Point(10, 30));
        assertThat(LinuxTrayPopup.clamp(new Point(900, 900), new Dimension(200, 100), s)).isEqualTo(new Point(580, 460));
        assertThat(LinuxTrayPopup.clamp(new Point(500, 500), new Dimension(900, 900), s)).isEqualTo(new Point(10, 30));
    }
    @Test void movedOrMissingPointerUsesVerifiedIconBottomCenter() {
        var s = screen(0, 0, 1600, 1200, 0, 0, 800, 600, 2);
        var icon = new Rectangle(100, 100, 40, 40);
        assertThat(LinuxTrayPopup.anchor(icon, true, new Point(55, 55), s)).isEqualTo(new Point(55, 55));
        assertThat(LinuxTrayPopup.anchor(icon, true, new Point(200, 200), s)).isEqualTo(new Point(60, 70));
        assertThat(LinuxTrayPopup.anchor(icon, true, null, s)).isEqualTo(new Point(60, 70));
        assertThat(LinuxTrayPopup.anchor(icon, false, new Point(55, 55), s)).isEqualTo(new Point(60, 70));
    }
    @Test void nativeIdentityMustMatchUniquely() {
        var s = screen(0, 0, 1600, 1200, 0, 0, 800, 600, 2);
        assertThat(LinuxTrayPopup.match(new Rectangle(0, 0, 1600, 1200), List.of(s))).isEqualTo(s);
        assertThat(LinuxTrayPopup.match(new Rectangle(0, 0, 800, 600), List.of(s))).isNull();
        assertThat(LinuxTrayPopup.match(new Rectangle(0, 0, 1600, 1200), List.of(s, s))).isNull();
        assertThat(LinuxTrayPopup.match(new Rectangle(), List.of())).isNull();
    }
    @Test void productionScreenAdmissionDeclinesUnprovedMonitorIdentity() {
        var nativeBounds = new Rectangle(0, 0, 1600, 1200);
        var user = new Rectangle(0, 0, 800, 600); var insets = new Insets(0, 0, 0, 0);
        var scale = AffineTransform.getScaleInstance(2, 2);
        assertThat(LinuxTrayPopup.verifiedScreen(nativeBounds, user, insets, scale, 1)).isNotNull();
        assertThat(LinuxTrayPopup.verifiedScreen(nativeBounds, user, insets, scale, 2)).isNull();
        assertThat(LinuxTrayPopup.verifiedScreen(nativeBounds, user, insets, new AffineTransform(), 1)).isNull();
        assertThat(LinuxTrayPopup.verifiedScreen(nativeBounds, new Rectangle(20, 0, 800, 600), insets, scale, 1)).isNull();
    }
    @Test void tenThousandRequestsQueueOneEdtTask() {
        var f = new Fixture();
        for (int i = 0; i < 10_000; i++) f.popup.request(f.request());
        assertThat(f.edt).hasSize(1); f.drain();
        assertThat(f.created).hasSize(1);
    }
    @Test void invalidEpochBeforeEdtCreatesNoWindow() {
        var f = new Fixture(); f.popup.request(f.request()); f.valid.set(false); f.drain();
        assertThat(f.created).isEmpty();
    }
    @Test void hostLossCancelsAndDisposesOnce() {
        var f = new Fixture(); f.popup.request(f.request()); f.drain();
        var stale = f.created.getFirst(); f.valid.set(false); f.popup.cancel(); f.popup.cancel();
        assertThat(f.edt).hasSize(1); f.drain(); stale.action().accept(1);
        assertThat(f.actions).isEmpty(); assertThat(f.disposals).hasValue(1);
        f.valid.set(true); f.popup.request(f.request()); f.drain(); assertThat(f.created).hasSize(2);
    }
    @Test void closeDropsQueuedSelection() {
        var f = new Fixture(); f.popup.request(f.request()); f.drain();
        f.popup.close(); f.created.getFirst().action().accept(1); f.drain();
        f.popup.request(f.request()); f.drain(); assertThat(f.actions).isEmpty(); assertThat(f.created).hasSize(1);
    }
    @Test void cancelBeforeDrainAndReplaceInvalidateCapturedCallbacks() {
        var f = new Fixture(); f.popup.request(f.request()); f.popup.cancel(); f.drain(); assertThat(f.created).isEmpty();
        f.popup.request(f.request()); f.drain(); var stale = f.created.getFirst();
        f.popup.request(f.request()); stale.action().accept(1); f.drain(); assertThat(f.actions).isEmpty();
    }
    @Test void quitCanceledDoesNotCloseAdapterAndSelectionIsOnce() {
        var f = new Fixture(); f.popup.request(f.request()); f.drain();
        var menu = f.created.getFirst(); menu.action().accept(5); menu.action().accept(5); f.drain();
        assertThat(f.actions).containsExactly(5);
        f.popup.request(f.request()); f.drain(); f.created.getLast().action().accept(1);
        assertThat(f.actions).containsExactly(5, 1);
    }
    @Test void realCreatorDeclinesHeadlessWithoutCreatingWindow() {
        assertThat(java.awt.GraphicsEnvironment.isHeadless()).isTrue();
        var f = new Fixture(); var popup = new LinuxTrayPopup(Runnable::run);
        popup.request(f.request()); popup.close();
        assertThat(java.awt.Window.getWindows()).isEmpty();
    }
    @Test void canceledPeerIsRetainedUntilHintsFinishAndNewestRequestWaits() {
        var f = new Leased(); f.popup.request(f.request()); f.drain();
        assertThat(f.hints).hasSize(1); f.popup.cancel(); f.drain();
        for (int i = 0; i < 10_000; i++) f.popup.request(f.request());
        f.drain(); assertThat(f.hints).hasSize(1); assertThat(f.disposed).hasValue(0);
        f.hints.getFirst().complete().accept(true); f.drain();
        assertThat(f.disposed).hasValue(1); assertThat(f.shown).hasValue(0); assertThat(f.hints).hasSize(2);
        f.hints.getLast().complete().accept(true); f.drain(); assertThat(f.shown).hasValue(1);
        f.popup.close(); f.drain(); assertThat(f.disposed).hasValue(2);
    }
    @Test void closeDuringHintLeaseNeverShowsAndDisposesOnceAfterCompletion() {
        var f = new Leased(); f.popup.request(f.request()); f.drain();
        f.popup.close(); f.drain(); assertThat(f.disposed).hasValue(0);
        f.hints.getFirst().complete().accept(true); f.drain();
        f.hints.getFirst().complete().accept(false); f.drain();
        assertThat(f.shown).hasValue(0); assertThat(f.disposed).hasValue(1);
    }
    @Test void failedHintsDisposeWithoutShowAndAllowNextRequest() {
        var f = new Leased(); f.popup.request(f.request()); f.drain();
        f.hints.getFirst().complete().accept(false); f.drain();
        assertThat(f.shown).hasValue(0); assertThat(f.disposed).hasValue(1);
        f.popup.request(f.request()); f.drain(); assertThat(f.hints).hasSize(2);
    }
    @Test void activationIsOneLeasedOperationAndLateCancelCannotRunCompletion() {
        var f = new Leased(); f.popup.request(f.request()); f.drain();
        f.hints.getFirst().complete().accept(true); f.drain();
        var request = f.request(); f.popup.activate(request, 0x200, f.shown::incrementAndGet);
        assertThat(f.hints.getLast().activation()).isTrue();
        f.popup.close(); f.drain(); int shown = f.shown.get();
        f.hints.getLast().complete().accept(true); f.drain(); assertThat(f.shown).hasValue(shown);
    }
    @Test void deniedFocusDeadlineDisposesAndInvalidatesCapturedActions() {
        var edt = new ArrayDeque<Runnable>(); var expires = new ArrayList<Runnable>();
        var created = new ArrayList<LinuxLegacyTray.MenuRequest>(); var disposed = new java.util.concurrent.atomic.AtomicInteger();
        var actions = new java.util.concurrent.atomic.AtomicInteger();
        var popup = new LinuxTrayPopup(edt::add, created::add, disposed::incrementAndGet, task -> { expires.add(task); return () -> {}; });
        popup.request(new LinuxLegacyTray.MenuRequest(1, new Rectangle(), new Rectangle(), false, id -> actions.incrementAndGet(), () -> true));
        while (!edt.isEmpty()) edt.remove().run(); popup.awaitFocus(() -> false);
        assertThat(expires).hasSize(1); expires.getFirst().run();
        while (!edt.isEmpty()) edt.remove().run(); created.getFirst().action().accept(1);
        assertThat(disposed).hasValue(1); assertThat(actions).hasValue(0);
    }
    private static final class Leased {
        final ArrayDeque<Runnable> edt = new ArrayDeque<>();
        final ArrayList<LinuxLegacyTray.PopupHint> hints = new ArrayList<>();
        final java.util.concurrent.atomic.AtomicInteger disposed = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger shown = new java.util.concurrent.atomic.AtomicInteger();
        final LinuxTrayPopup popup = new LinuxTrayPopup(edt::add, this::create, disposed::incrementAndGet);
        void create(LinuxLegacyTray.MenuRequest r) { popup.lease(r, 0x200, shown::incrementAndGet); }
        LinuxLegacyTray.MenuRequest request() { return new LinuxLegacyTray.MenuRequest(1, new Rectangle(0, 0, 24, 24),
            new Rectangle(0, 0, 1280, 800), true, ignored -> {}, () -> true, hints::add); }
        void drain() { while (!edt.isEmpty()) edt.remove().run(); }
    }
    @Test void sharedRowsRetainIdsOrderLabelsAndSeparator() {
        assertThat(LinuxTrayMenu.rows()).extracting(LinuxTrayMenu.Row::id).containsExactly(1, 2, 3, 4, 5);
        assertThat(LinuxTrayMenu.rows()).extracting(LinuxTrayMenu.Row::label).containsExactly("Open Jasper", "New Window", "Settings\u2026", "", "Quit Jasper");
        assertThat(LinuxTrayMenu.rows()).extracting(LinuxTrayMenu.Row::separator).containsExactly(false, false, false, true, false);
    }
    private static final class Fixture {
        final ArrayDeque<Runnable> edt = new ArrayDeque<>();
        final ArrayList<LinuxLegacyTray.MenuRequest> created = new ArrayList<>();
        final ArrayList<Integer> actions = new ArrayList<>();
        final AtomicBoolean valid = new AtomicBoolean(true);
        final java.util.concurrent.atomic.AtomicInteger disposals = new java.util.concurrent.atomic.AtomicInteger();
        final LinuxTrayPopup popup = new LinuxTrayPopup(edt::add, created::add, disposals::incrementAndGet);
        LinuxLegacyTray.MenuRequest request() { return new LinuxLegacyTray.MenuRequest(1, new Rectangle(0, 0, 24, 24), new Rectangle(0, 0, 1280, 800), true, actions::add, valid::get); }
        void drain() { while (!edt.isEmpty()) edt.remove().run(); }
    }
}
