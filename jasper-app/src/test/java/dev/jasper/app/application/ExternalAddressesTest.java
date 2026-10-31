package dev.jasper.app.application;

import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.LogRecorder;
import dev.jasper.app.testsupport.MutableClock;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Browser plan 4c Task 3: web addresses from outside, the loop breaker and the "Web links can’t open in Jasper" notice. */
@ExtendWith(EdtTestExtension.class)
class ExternalAddressesTest {
    private final MutableClock clock = new MutableClock();
    private final NotificationCenter center = new NotificationCenter(Runnable::run, clock, () -> Delivery.Presence.FOCUSED);
    /** What the browser hook heard, {@code <address>|<window or ->}, and what it leaves to its fallback. */
    private final List<String> offered = new CopyOnWriteArrayList<>();
    private final List<URI> declined = new CopyOnWriteArrayList<>();
    private final List<String> system = new CopyOnWriteArrayList<>();
    private final List<String> copied = new CopyOnWriteArrayList<>();
    private boolean provides;
    private boolean quitting;
    private boolean restarts = true;
    private int restarted;
    private Optional<String> turnOnProblem = Optional.empty();
    private int turnedOn;

    private ExternalAddresses addresses() {
        center.register(NotificationsFeature.GENERAL);
        return new ExternalAddresses(center, clock, new ExternalAddresses.Hooks(
            (address, window, notTaken) -> {
                offered.add(address + "|" + window.map(UUID::toString).orElse("-"));
                if (declined.contains(address)) notTaken.accept(address);
            },
            address -> system.add(address.toString()), () -> provides,
            done -> { turnedOn++; done.accept(turnOnProblem); }, () -> { restarted++; return restarts; },
            text -> { if (text.contains("refused")) throw new IllegalStateException("The clipboard is busy"); copied.add(text); },
            () -> quitting));
    }

    private static URI uri(String text) { return URI.create(text); }

    @Test void addressesWaitUntilReadyInArrivalOrderAndTheThirtyThirdIsDropped() throws Exception {
        var addresses = addresses();
        UUID window = UUID.randomUUID();
        List<Optional<CompletableFuture<Void>>> waiting;
        try (var log = LogRecorder.of(ExternalAddresses.class.getName())) {
            waiting = IntStream.rangeClosed(1, 33)
                .mapToObj(n -> addresses.open(uri("https://example.com/" + n), n == 1 ? Optional.of(window) : Optional.empty())).toList();
            assertThat(log.warnings()).containsExactly("A web address from outside was dropped: 32 were already waiting for Jasper's browser");
        }
        assertThat(waiting.subList(0, 32)).allSatisfy(call -> assertThat(call).get().satisfies(future -> assertThat(future).isNotDone()));
        assertThat(waiting.get(32)).as("the 33rd").isEmpty();
        assertThat(offered).isEmpty();
        addresses.ready();
        assertThat(offered).hasSize(32).startsWith("https://example.com/1|" + window, "https://example.com/2|-").endsWith("https://example.com/32|-");
        assertThat(waiting.subList(0, 32)).allSatisfy(call -> assertThat(call.orElseThrow()).isCompleted());
        assertThat(addresses.open(uri("https://example.com/later"), Optional.empty())).get().satisfies(future -> assertThat(future).isCompleted());
        assertThat(offered).last().isEqualTo("https://example.com/later|-");
    }

    @Test void waitingAddressesOpenNothingOnceJasperQuitsAndTheirCallsFail() {
        var addresses = addresses();
        var first = addresses.open(uri("https://example.com/a"), Optional.empty()).orElseThrow();
        var second = addresses.open(uri("https://example.com/b"), Optional.empty()).orElseThrow();
        addresses.close();
        assertThatThrownBy(first::get).isInstanceOf(ExecutionException.class).cause()
            .isInstanceOf(IllegalStateException.class).hasMessage("Jasper is quitting; run the command again");
        assertThat(second).isCompletedExceptionally();
        var late = addresses.open(uri("https://example.com/c"), Optional.empty()).orElseThrow();
        quitting = true;
        addresses.ready();
        assertThat(late).as("ready while quitting").isCompletedExceptionally();
        assertThat(offered).isEmpty();
    }

    /** Ruling Q10: an address that comes back within 10 s of Jasper handing it to the system browser never goes back. */
    @Test void anAddressThatComesBackWithinTenSecondsOfGoingToTheSystemIsNeverHandedToItAgain() {
        var addresses = addresses();
        addresses.ready();
        URI loop = uri("https://example.com/loop");
        URI later = uri("https://example.com/later");
        declined.addAll(List.of(loop, later, uri("https://example.com/other")));
        addresses.handedToSystem(loop);
        addresses.handedToSystem(later);
        clock.advance(Duration.ofSeconds(10));
        addresses.open(loop, Optional.empty());
        assertThat(system).as("within 10 s").isEmpty();
        assertThat(center.history()).singleElement().satisfies(notice -> assertThat(notice.request().title()).isEqualTo("Web links can’t open in Jasper"));
        addresses.open(uri("https://example.com/other"), Optional.empty());
        assertThat(system).as("another address").containsExactly("https://example.com/other");
        clock.advance(Duration.ofMillis(1));
        addresses.open(later, Optional.empty());
        assertThat(system).as("after 10 s").containsExactly("https://example.com/other", "https://example.com/later");
        assertThat(offered).as("the browser has each first").hasSize(3);
    }

    /** Rulings Q1 and Q2: the notice only when no browser is provided; a provided browser that declined says why itself. */
    @Test void theNoticeOffersTurnOnBrowserAndCopyLinkOnlyWhenNoBrowserIsProvided() {
        var addresses = addresses();
        provides = true;
        try (var log = LogRecorder.of(ExternalAddresses.class.getName())) {
            addresses.cannotOpen(uri("https://example.com/declined"));
            assertThat(log.records()).extracting(java.util.logging.LogRecord::getMessage)
                .containsExactly("Jasper's browser did not open a web address, and the system would hand it back, so nothing opens");
        }
        assertThat(center.history()).isEmpty();
        provides = false;
        addresses.cannotOpen(uri("https://example.com/sign-in"));
        Notification notice = center.history().getFirst();
        assertThat(notice.request().group()).isEqualTo(NotificationsFeature.GENERAL.id());
        assertThat(notice.request().severity()).isEqualTo(Severity.WARNING);
        assertThat(notice.request().title()).isEqualTo("Web links can’t open in Jasper");
        assertThat(notice.request().content()).isEqualTo("The Browser plugin is off. Turn it on to open web links in Jasper, "
            + "or choose another default browser in your system settings.");
        assertThat(notice.request().actions()).extracting(NotificationAction::label).containsExactly("Turn On Browser", "Copy Link");
        assertThat(notice.request().actions()).extracting(NotificationAction::expires).containsExactly(true, false);
        quitting = true;
        addresses.cannotOpen(uri("https://example.com/quitting"));
        assertThat(center.history()).singleElement().satisfies(entry -> assertThat(entry.count()).isEqualTo(1));
    }

    @Test void copyLinkCopiesTheAddressOrSaysItCouldNot() {
        var addresses = addresses();
        addresses.cannotOpen(uri("https://example.com/a?b=1"));
        action(center.history().getFirst(), "Copy Link").run();
        assertThat(copied).containsExactly("https://example.com/a?b=1");
        addresses.cannotOpen(uri("https://example.com/refused"));
        action(center.history().getFirst(), "Copy Link").run();
        assertThat(center.history().getFirst().request().title()).isEqualTo("Could not copy the link");
        assertThat(center.history().getFirst().request().content()).isEqualTo("The clipboard is busy");
    }

    @Test void turnOnBrowserOffersTheRestartOrSaysWhyItCouldNot() {
        var addresses = addresses();
        addresses.cannotOpen(uri("https://example.com/a"));
        action(center.history().getFirst(), "Turn On Browser").run();
        assertThat(turnedOn).isOne();
        var offer = center.history().getFirst().request();
        assertThat(offer.severity()).isEqualTo(Severity.INFO);
        assertThat(offer.title()).isEqualTo("Restart Jasper to turn on the Browser");
        assertThat(offer.content()).isEqualTo("The Browser plugin starts the next time Jasper starts.");
        assertThat(offer.actions()).extracting(NotificationAction::label).containsExactly("Restart Now");
        restarts = false;
        offer.actions().getFirst().run().run();
        assertThat(restarted).isOne();
        assertThat(center.history().getFirst().request().title()).isEqualTo("Jasper could not restart");
        assertThat(center.history().getFirst().request().content())
            .isEqualTo("Jasper could not work out how it was started. Quit and reopen it to turn on the Browser.");

        turnOnProblem = Optional.of("No such plugin: dev.jasper.browser");
        addresses.cannotOpen(uri("https://example.com/b"));
        action(center.history().stream().filter(entry -> entry.request().title().equals("Web links can’t open in Jasper"))
            .findFirst().orElseThrow(), "Turn On Browser").run();
        assertThat(center.history().getFirst().request().severity()).isEqualTo(Severity.ERROR);
        assertThat(center.history().getFirst().request().title()).isEqualTo("Could not turn on the Browser");
        assertThat(center.history().getFirst().request().content()).isEqualTo("No such plugin: dev.jasper.browser");
    }

    private static Runnable action(Notification notice, String label) {
        return notice.request().actions().stream().filter(action -> action.label().equals(label)).findFirst().orElseThrow().run();
    }
}
