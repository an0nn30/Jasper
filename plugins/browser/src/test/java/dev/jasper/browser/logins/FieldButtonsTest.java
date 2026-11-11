package dev.jasper.browser.logins;

import dev.jasper.browser.json.StrictJson;
import dev.jasper.browser.page.Activation;
import dev.jasper.browser.page.LastPress;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageContext;
import dev.jasper.browser.page.PageQuery;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.store.CapturedLog;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ContentArea;
import dev.jasper.sdk.ui.UiAnchor;
import dev.jasper.vault.api.CredentialPickResult;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 8 Task 4b: {@link FieldButtons}, which answers the {@code login.pick} queries the buttons of
 * {@code login-fields.js} send, over {@link FakeVault} and a recording query. A click opens the Vault's picker at the
 * field, its CSS rectangle scaled by the zoom, searched for the asking origin's host without one {@code www.}; a
 * malformed request, an origin that is no web page's, a second pick while one is open, a missing click and a field off
 * the page are refused, and only the missing click and what follows it spend the click (F3). A choice answers the
 * field's members (F19), nothing chosen answers {@code {}}, and a choice for a document that has gone fills nothing
 * and makes no reply text (F29). The query's cancel, the page's close (F15) and the item's own close (L6) close the
 * picker and its anchor. Run on the UI thread, where the Vault is called.
 */
class FieldButtonsTest {
    private static final String ORIGIN = "https://example.com";
    private static final String NAME = "https://example.com (Example)";
    private final FakeVault fake = new FakeVault();
    private final List<String> replies = new CopyOnWriteArrayList<>();
    // How often the writer was asked to write a reply, which is where a password's text is made (Minor 6): a test can so
    // see that no reply text, and no password text, was made.
    private final AtomicInteger written = new AtomicInteger();
    private final FieldButtons buttons = new FieldButtons(new VaultLogins(fake.api()), (username, password) -> {
        written.incrementAndGet();
        return FieldButtons.reply(username, password);
    });
    private final WindowHandle window = new Window();
    // The areas the anchor function was asked for, and the anchors it made for them, one each.
    private final List<ContentArea> areas = new ArrayList<>();
    private final List<Anchor> made = new ArrayList<>();
    // How often the queries' activation was asked.
    private final AtomicInteger activations = new AtomicInteger();
    // The origin the page's main frame shows now, as the bridge would read it.
    private final AtomicReference<Optional<String>> shown = new AtomicReference<>(Optional.of(ORIGIN));
    // The click that activates the next query, on the page's canvas: inside the field (8, 16, 200, 24) at 125%, whose
    // area is (10, 20, 250, 30).
    private final AtomicReference<Optional<LastPress>> press = new AtomicReference<>(Optional.of(click(12, 22)));
    // The page's canvas at 125%, as the item gives it, and the host's anchors on it.
    private final Function<FieldButtons.FieldRect, Optional<ContentArea>> canvas = css -> FieldButtons.area(css, 1.25, new Dimension(800, 600));
    private final Function<ContentArea, UiAnchor> anchors = area -> {
        areas.add(area);
        var anchor = new Anchor();
        made.add(anchor);
        return anchor;
    };

    @Test void aClickOpensThePickerAtTheFieldWithTheHost() throws Exception {
        onUi(() -> {
            fake.pickAnswer = new CompletableFuture<>();
            buttons.query(query(page(), ORIGIN, pick("password", 8, 16, 200, 24), true), window, canvas, anchors);
            assertThat(areas).as("(8, 16, 200, 24) at 125%").containsExactly(new ContentArea(10, 20, 250, 30));
            assertThat(fake.picks).singleElement().satisfies(request -> {
                assertThat(request.anchor()).isSameAs(made.getFirst());
                assertThat(request.search()).isEqualTo("example.com");
            });
            assertThat(fake.pickOwners).containsExactly(window);
            assertThat(activations).as("the click is spent").hasValue(1);
            assertThat(replies).as("answered as the user chooses").isEmpty();
        });
    }

    /** F26: the search is the origin's host, never the page's, without one leading {@code www.}. */
    @Test void aWwwHostSearchesWithoutWww() throws Exception {
        onUi(() -> {
            fake.pickAnswer = new CompletableFuture<>();
            Page page = page();
            shown.set(Optional.of("https://www.example.com"));
            buttons.query(query(page, "https://www.example.com", pick("password", 8, 16, 200, 24), true), window, canvas, anchors);
            fake.pickAnswer.complete(new CredentialPickResult.Cancelled());
            fake.pickAnswer = new CompletableFuture<>();
            shown.set(Optional.of("https://www.www.example.com:8443"));
            buttons.query(query(page, "https://www.www.example.com:8443", pick("password", 8, 16, 200, 24), true), window, canvas, anchors);
            assertThat(fake.picks).extracting(request -> request.search()).containsExactly("example.com", "www.example.com");
        });
    }

    /**
     * F3: a pick the user's click did not allow opens no picker, and the page is told why: no press within the
     * activation, or a press whose activation the page has spent; the anchor made for the latter is closed.
     */
    @Test void aPickWithoutTheUsersClickIsRefused() throws Exception {
        onUi(() -> {
            Page page = page();
            press.set(Optional.empty());
            buttons.query(query(page, ORIGIN, pick("password", 8, 16, 200, 24), true), window, canvas, anchors);
            assertThat(areas).as("no press: no anchor").isEmpty();
            assertThat(activations).as("and nothing spent").hasValue(0);
            press.set(Optional.of(click(12, 22)));
            buttons.query(query(page, ORIGIN, pick("password", 8, 16, 200, 24), false), window, canvas, anchors);
            assertThat(made).singleElement().satisfies(anchor -> assertThat(anchor.closed).as("closed unused").isTrue());
            assertThat(fake.picks).as("no picker").isEmpty();
            assertThat(replies).hasSize(2).allSatisfy(reply -> assertThat(reply)
                .isEqualTo(PageQuery.REFUSED + " The password manager opens only after a click in the page."));
        });
    }

    /**
     * L20, the double-click clickjack: a page's own request names a field just above where the user's second press
     * lands, so the picker would open under the pointer. A press outside the area the pick names, its right and bottom
     * edges included, opens nothing and makes no anchor, and spends its click (L21); one inside it opens the picker.
     */
    @Test void aPressOutsideTheFieldIsRefused() throws Exception {
        onUi(() -> {
            fake.pickAnswer = new CompletableFuture<>();
            Page page = page();
            for (LastPress outside : List.of(click(100, 52), click(100, 50), click(260, 30), click(9, 30), click(100, 19))) {
                press.set(Optional.of(outside));
                buttons.query(query(page, ORIGIN, pick("password", 8, 16, 200, 24), true), window, canvas, anchors);
            }
            assertThat(replies).hasSize(5).allSatisfy(reply -> assertThat(reply)
                .isEqualTo(PageQuery.REFUSED + " The password manager opens only after a click in the page."));
            assertThat(areas).as("no anchor").isEmpty();
            assertThat(activations).as("each press's click spent").hasValue(5);
            assertThat(fake.picks).isEmpty();
            press.set(Optional.of(click(259, 49)));
            buttons.query(query(page, ORIGIN, pick("password", 8, 16, 200, 24), true), window, canvas, anchors);
            assertThat(fake.picks).as("its last pixel is inside").hasSize(1);
        });
    }

    /**
     * L21, the press oracle: a page that asks again and again, with areas around where it guesses the last press was,
     * as one in a cross-origin frame, learns one answer per press. The first ask outside the press is refused and
     * spends the press's click, so a second ask on the same press is refused as no click, whatever its area, even one
     * that holds the press; the next press inside the field opens the picker.
     */
    @Test void aPressOutsideTheAreaSpendsItsClick() throws Exception {
        onUi(() -> {
            fake.pickAnswer = new CompletableFuture<>();
            Page page = page();
            var activation = new Activation();
            activation.activate(1_000);
            press.set(Optional.of(new LastPress(1, 0, 1_000, 100, 60)));
            buttons.query(query(page, ORIGIN, pick("password", 8, 16, 200, 24), () -> activation.consume(1_000)), window, canvas, anchors);
            assertThat(activation.active(1_000)).as("the first probe spent the press").isFalse();
            buttons.query(query(page, ORIGIN, pick("password", 8, 100, 200, 24), () -> activation.consume(1_000)), window, canvas, anchors);
            buttons.query(query(page, ORIGIN, pick("password", 72, 40, 40, 16), () -> activation.consume(1_000)), window, canvas, anchors);
            assertThat(replies).hasSize(3).allSatisfy(reply -> assertThat(reply)
                .isEqualTo(PageQuery.REFUSED + " The password manager opens only after a click in the page."));
            assertThat(fake.picks).as("not even an area that holds the spent press").isEmpty();
            assertThat(made).as("whose anchor was closed unused").allSatisfy(anchor -> assertThat(anchor.closed).isTrue());
            activation.activate(2_000);
            press.set(Optional.of(new LastPress(1, 0, 2_000, 12, 22)));
            buttons.query(query(page, ORIGIN, pick("password", 8, 16, 200, 24), () -> activation.consume(2_000)), window, canvas, anchors);
            assertThat(fake.picks).as("the next press, inside the field").hasSize(1);
            assertThat(activation.active(2_000)).isFalse();
        });
    }

    /** L20: the press is compared with the field's area after the zoom, in the canvas's coordinates. */
    @Test void theZoomIsAppliedToThePress() throws Exception {
        onUi(() -> {
            fake.pickAnswer = new CompletableFuture<>();
            Page page = page();
            press.set(Optional.of(click(9, 22)));
            buttons.query(query(page, ORIGIN, pick("password", 8, 16, 200, 24), true), window, canvas, anchors);
            assertThat(fake.picks).as("inside (8, 16, 200, 24), outside it at 125%").isEmpty();
            press.set(Optional.of(click(255, 45)));
            buttons.query(query(page, ORIGIN, pick("password", 8, 16, 200, 24), true), window, canvas, anchors);
            assertThat(fake.picks).as("outside (8, 16, 200, 24), inside it at 125%").hasSize(1);
            assertThat(replies).containsExactly(PageQuery.REFUSED + " The password manager opens only after a click in the page.");
        });
    }

    /** One picker per page: a second pick while one is open is told the page is busy, and spends no click. */
    @Test void aSecondPickWhileOneIsOpenIsRefused() throws Exception {
        onUi(() -> {
            fake.pickAnswer = new CompletableFuture<>();
            Page page = page();
            buttons.query(query(page, ORIGIN, pick("password", 8, 16, 200, 24), true), window, canvas, anchors);
            buttons.query(query(page, ORIGIN, pick("username", 8, 48, 200, 24), true), window, canvas, anchors);
            assertThat(fake.picks).hasSize(1);
            assertThat(replies).containsExactly(PageQuery.BUSY + " The password manager is open already.");
            assertThat(activations).as("only the first click is spent").hasValue(1);
            fake.pickAnswer.complete(new CredentialPickResult.Cancelled());
            fake.pickAnswer = new CompletableFuture<>();
            buttons.query(query(page, ORIGIN, pick("username", 8, 16, 200, 24), true), window, canvas, anchors);
            assertThat(fake.picks).as("once it closed, a pick opens").hasSize(2);
        });
    }

    @Test void aFileOrOpaqueOriginIsRefused() throws Exception {
        onUi(() -> {
            Page page = page();
            buttons.query(query(page, Optional.of("file://"), pick("password", 8, 16, 200, 24), true), window, canvas, anchors);
            buttons.query(query(page, Optional.empty(), pick("password", 8, 16, 200, 24), true), window, canvas, anchors);
            buttons.query(query(page, Optional.of("jasper-about://welcome"), pick("password", 8, 16, 200, 24), true), window, canvas, anchors);
            assertThat(fake.picks).isEmpty();
            assertThat(replies).hasSize(3).allSatisfy(reply -> assertThat(reply)
                .isEqualTo(PageQuery.REFUSED + " The password manager fills only web pages."));
        });
    }

    /**
     * F3, L20: a malformed request, an origin that is no web page's, a field off the page, an anchor the host refuses
     * and a busy page leave the click to a later request; a press elsewhere does not (L21).
     */
    @Test void aRefusedRequestKeepsTheClick() throws Exception {
        onUi(() -> {
            fake.pickAnswer = new CompletableFuture<>();
            Page page = page();
            buttons.query(query(page, ORIGIN, pick("email", 8, 16, 200, 24), true), window, canvas, anchors);
            buttons.query(query(page, Optional.of("file://"), pick("password", 8, 16, 200, 24), true), window, canvas, anchors);
            buttons.query(query(page, ORIGIN, pick("password", 8, 700, 200, 24), true), window, canvas, anchors);
            buttons.query(query(page, ORIGIN, pick("password", 8, 16, 200, 24), true), window, canvas, area -> {
                throw new IllegalArgumentException("An area must lie inside its content");
            });
            assertThat(activations).as("malformed, not a web page, off the page, an anchor refused").hasValue(0);
            press.set(Optional.of(click(100, 60)));
            buttons.query(query(page, ORIGIN, pick("password", 8, 16, 200, 24), true), window, canvas, anchors);
            assertThat(activations).as("a press elsewhere spends it").hasValue(1);
            press.set(Optional.of(click(12, 22)));
            buttons.query(query(page, ORIGIN, pick("password", 8, 16, 200, 24), true), window, canvas, anchors);
            assertThat(activations).hasValue(2);
            buttons.query(query(page, ORIGIN, pick("password", 8, 16, 200, 24), true), window, canvas, anchors);
            assertThat(activations).as("busy").hasValue(2);
            assertThat(replies).extracting(reply -> reply.substring(0, 1)).containsExactly("" + PageQuery.MALFORMED,
                "" + PageQuery.REFUSED, "" + PageQuery.REFUSED, "" + PageQuery.REFUSED, "" + PageQuery.REFUSED, "" + PageQuery.BUSY);
        });
    }

    /** F2: only the flat request login-fields.js sends, with integer members in range, is understood. */
    @Test void malformedRequestsAreRefused() throws Exception {
        onUi(() -> {
            Page page = page();
            List<Map<String, Object>> malformed = new ArrayList<>();
            malformed.add(pick("email", 8, 16, 200, 24));
            malformed.add(with(pick("password", 8, 16, 200, 24), "op", "login.fill"));
            var noRect = pick("password", 8, 16, 200, 24);
            noRect.remove("x");
            malformed.add(noRect);
            malformed.add(with(pick("password", 8, 16, 200, 24), "y", StrictJson.NUMBER));
            malformed.add(with(pick("password", 8, 16, 200, 24), "y", 16.5));
            malformed.add(with(pick("password", 8, 16, 200, 24), "width", StrictJson.NULL));
            malformed.add(with(pick("password", 8, 16, 200, 24), "width", null));
            malformed.add(with(pick("password", 8, 16, 200, 24), "height", "24"));
            malformed.add(pick("password", 8, 16, -1, 24));
            malformed.add(pick("password", 1_000_001, 16, 200, 24));
            malformed.add(pick("password", 8, -1_000_001, 200, 24));
            malformed.add(with(pick("password", 8, 16, 200, 24), "rect", Map.of("x", 8L)));
            malformed.add(with(pick("password", 8, 16, 200, 24), "value", "pw"));
            for (Map<String, Object> request : malformed) buttons.query(query(page, ORIGIN, request, true), window, canvas, anchors);
            assertThat(replies).hasSize(malformed.size())
                .allSatisfy(reply -> assertThat(reply).isEqualTo(PageQuery.MALFORMED + " Jasper does not understand this request."));
            assertThat(fake.picks).isEmpty();
            assertThat(activations).hasValue(0);
            assertThat(FieldButtons.rect(pick("password", -1_000_000, 1_000_000, 0, 1_000_000)))
                .as("the bounds themselves").contains(new FieldButtons.FieldRect(-1_000_000, 1_000_000, 0, 1_000_000));
        });
    }

    /** The area is the CSS rectangle scaled by the zoom, clipped to the page's canvas; empty when nothing is left. */
    @Test void anAreaIsClippedToThePageAndEmptyWhenNothingIsLeft() {
        var view = new Dimension(800, 600);
        assertThat(FieldButtons.area(new FieldButtons.FieldRect(8, 16, 200, 24), 1.0, view)).contains(new ContentArea(8, 16, 200, 24));
        assertThat(FieldButtons.area(new FieldButtons.FieldRect(-50, 590, 200, 24), 1.0, view)).as("partly off the page")
            .contains(new ContentArea(0, 590, 150, 10));
        assertThat(FieldButtons.area(new FieldButtons.FieldRect(1, 1, 3, 3), 0.5, view)).as("rounded outwards")
            .contains(new ContentArea(0, 0, 2, 2));
        assertThat(FieldButtons.area(new FieldButtons.FieldRect(8, 700, 200, 24), 1.0, view)).as("below the page").isEmpty();
        assertThat(FieldButtons.area(new FieldButtons.FieldRect(-300, 16, 200, 24), 1.0, view)).as("left of the page").isEmpty();
        assertThat(FieldButtons.area(new FieldButtons.FieldRect(8, 16, 0, 24), 1.0, view)).as("no width").isEmpty();
        assertThat(FieldButtons.area(new FieldButtons.FieldRect(8, 16, 200, 24), 1.0, new Dimension(0, 0))).as("no canvas").isEmpty();
    }

    @Test void aPasswordFieldGetsThePasswordOnly() throws Exception {
        onUi(() -> {
            fake.login(NAME, "ada", "pw");
            buttons.query(query(page(), ORIGIN, pick("password", 8, 16, 200, 24), true), window, canvas, anchors);
            assertThat(replies).containsExactly("0 {\"password\":\"pw\"}");
            assertThat(fake.issued).singleElement().satisfies(credential -> assertThat(FakeVault.closed(credential)).isTrue());
            assertThat(made.getFirst().closed).as("the pick's anchor closes with it").isTrue();
        });
    }

    @Test void aUsernameFieldGetsBoth() throws Exception {
        onUi(() -> {
            fake.login(NAME, "ada", "pw");
            Page page = page();
            buttons.query(query(page, ORIGIN, pick("username", 8, 16, 200, 24), true), window, canvas, anchors);
            assertThat(replies).containsExactly("0 {\"username\":\"ada\",\"password\":\"pw\"}");
            UUID empty = fake.login("https://example.com (Empty)", "", "");
            fake.pickAnswer = CompletableFuture.completedFuture(new CredentialPickResult.Chosen(fake.descriptor(empty)));
            buttons.query(query(page, ORIGIN, pick("username", 8, 16, 200, 24), true), window, canvas, anchors);
            assertThat(replies).as("empty members left out").containsExactly("0 {\"username\":\"ada\",\"password\":\"pw\"}", "0 {}");
        });
    }

    @Test void aCancelledPickAnswersEmpty() throws Exception {
        onUi(() -> {
            fake.login(NAME, "ada", "pw");
            fake.pickAnswer = CompletableFuture.completedFuture(new CredentialPickResult.Cancelled());
            buttons.query(query(page(), ORIGIN, pick("username", 8, 16, 200, 24), true), window, canvas, anchors);
            assertThat(replies).containsExactly("0 {}");
            assertThat(fake.calls).as("nothing fetched").isEmpty();
            assertThat(made.getFirst().closed).isTrue();
        });
    }

    /** Review focus 2: a choice that comes once the page shows another origin fills nothing, and the login is closed. */
    @Test void aChoiceAfterTheDocumentChangedOriginFillsNothing() throws Exception {
        onUi(() -> {
            UUID login = fake.login(NAME, "ada", "pw");
            fake.pickAnswer = new CompletableFuture<>();
            buttons.query(query(page(), ORIGIN, pick("username", 8, 16, 200, 24), true), window, canvas, anchors);
            shown.set(Optional.of("https://evil.example"));
            fake.pickAnswer.complete(new CredentialPickResult.Chosen(fake.descriptor(login)));
            assertThat(fake.calls).as("fetched, so the one-time grant is spent (F15)").containsExactly("credential " + NAME);
            assertThat(fake.issued).singleElement().satisfies(credential -> assertThat(FakeVault.closed(credential)).isTrue());
            assertThat(replies).singleElement().asString().startsWith(PageQuery.GONE + " ").doesNotContain("pw", "ada");
        });
    }

    /**
     * F29, L17: for a query that would answer {@code GONE}, no reply text, and so no password string, is made: the
     * writer, which is where the password's text is made, is never asked.
     */
    @Test void noPasswordStringIsMadeForAGoneQuery() throws Exception {
        onUi(() -> {
            UUID login = fake.login(NAME, "ada", "pw");
            fake.pickAnswer = new CompletableFuture<>();
            buttons.query(query(page(), ORIGIN, pick("password", 8, 16, 200, 24), true), window, canvas, anchors);
            shown.set(Optional.empty());
            fake.pickAnswer.complete(new CredentialPickResult.Chosen(fake.descriptor(login)));
            assertThat(written).as("no reply text").hasValue(0);
            assertThat(replies).singleElement().asString().startsWith(PageQuery.GONE + " ");
        });
    }

    /** L18: a query that is over by the time it is handed over opens no picker and spends no click. */
    @Test void aQueryThatIsOverOpensNoPicker() throws Exception {
        onUi(() -> {
            Page page = page();
            PageQuery cancelled = query(page, ORIGIN, pick("password", 8, 16, 200, 24), true);
            cancelled.cancelled();
            buttons.query(cancelled, window, canvas, anchors);
            assertThat(fake.picks).isEmpty();
            assertThat(activations).hasValue(0);
            assertThat(replies).as("nothing sent").isEmpty();
            PageQuery gone = query(page, ORIGIN, pick("password", 8, 16, 200, 24), true);
            shown.set(Optional.of("https://other.example"));
            buttons.query(gone, window, canvas, anchors);
            assertThat(fake.picks).isEmpty();
            assertThat(activations).hasValue(0);
            assertThat(replies).singleElement().asString().startsWith(PageQuery.GONE + " ");
        });
    }

    /** Review focus 2, L6: the query's cancel, as the page scrolls or navigates, closes the picker and its anchor. */
    @Test void aCancelledQueryClosesThePicker() throws Exception {
        onUi(() -> {
            fake.login(NAME, "ada", "pw");
            CompletableFuture<CredentialPickResult> picker = new CompletableFuture<>();
            fake.pickAnswer = picker;
            Page page = page();
            PageQuery query = query(page, ORIGIN, pick("password", 8, 16, 200, 24), true);
            buttons.query(query, window, canvas, anchors);
            query.cancelled();
            assertThat(picker).as("the pick withdrawn, which closes the picker").isCancelled();
            assertThat(made.getFirst().closed).isTrue();
            assertThat(replies).as("nothing sent").isEmpty();
            fake.pickAnswer = new CompletableFuture<>();
            buttons.query(query(page, ORIGIN, pick("password", 8, 16, 200, 24), true), window, canvas, anchors);
            assertThat(fake.picks).as("no longer busy").hasSize(2);
        });
    }

    /** F15: the page's close cancels its pick, even before Chromium cancels the query. */
    @Test void closingThePageCancelsThePick() throws Exception {
        onUi(() -> {
            CompletableFuture<CredentialPickResult> picker = new CompletableFuture<>();
            fake.pickAnswer = picker;
            Page page = page();
            buttons.query(query(page, ORIGIN, pick("password", 8, 16, 200, 24), true), window, canvas, anchors);
            page.close();
            assertThat(picker).isCancelled();
            assertThat(made.getFirst().closed).isTrue();
            assertThat(replies).as("nothing chosen").containsExactly("0 {}");
        });
    }

    /** L6: the item closes the picker as its zoom changes or it navigates; the page is told nothing was chosen. */
    @Test void closingThePickerAnswersEmptyAndEndsTheBusyState() throws Exception {
        onUi(() -> {
            CompletableFuture<CredentialPickResult> picker = new CompletableFuture<>();
            fake.pickAnswer = picker;
            Page page = page();
            buttons.closePicker();
            buttons.query(query(page, ORIGIN, pick("password", 8, 16, 200, 24), true), window, canvas, anchors);
            buttons.closePicker();
            assertThat(picker).isCancelled();
            assertThat(made.getFirst().closed).isTrue();
            assertThat(replies).containsExactly("0 {}");
            buttons.closePicker();
            assertThat(replies).as("once").hasSize(1);
            fake.pickAnswer = new CompletableFuture<>();
            buttons.query(query(page, ORIGIN, pick("password", 8, 16, 200, 24), true), window, canvas, anchors);
            assertThat(fake.picks).as("no longer busy").hasSize(2);
        });
    }

    /** UiAnchor's contract: the anchor's own close, as the page's canvas is resized or hidden, closes the picker. */
    @Test void anAnchorThatClosesClosesThePicker() throws Exception {
        onUi(() -> {
            CompletableFuture<CredentialPickResult> picker = new CompletableFuture<>();
            fake.pickAnswer = picker;
            buttons.query(query(page(), ORIGIN, pick("password", 8, 16, 200, 24), true), window, canvas, anchors);
            made.getFirst().close();
            assertThat(picker).isCancelled();
            assertThat(replies).containsExactly("0 {}");
        });
    }

    /** The field off the page, or an anchor the host refuses, opens nothing: the page is told so. */
    @Test void anAnchorThatThrowsIsRefused() throws Exception {
        onUi(() -> {
            Page page = page();
            buttons.query(query(page, ORIGIN, pick("password", 8, 16, 200, 24), true), window, canvas, area -> {
                throw new IllegalArgumentException("An area must lie inside its content");
            });
            buttons.query(query(page, ORIGIN, pick("password", 8, 700, 200, 24), true), window, canvas, anchors);
            assertThat(fake.picks).isEmpty();
            assertThat(replies).hasSize(2).allSatisfy(reply -> assertThat(reply)
                .isEqualTo(PageQuery.REFUSED + " The field is not on the page."));
        });
    }

    /** F29: a fill that fails is logged with the exception alone, and the page is told nothing was chosen. */
    @Test void aFailedFillIsLoggedWithoutTheLogin() throws Exception {
        var failing = new FieldButtons(new VaultLogins(fake.api()), (username, password) -> {
            throw new IllegalStateException("The reply could not be written");
        });
        try (var log = new CapturedLog(FieldButtons.class.getName())) {
            onUi(() -> {
                fake.login(NAME, "ada", "pw");
                failing.query(query(page(), ORIGIN, pick("username", 8, 16, 200, 24), true), window, canvas, anchors);
                assertThat(replies).containsExactly("0 {}");
                assertThat(fake.issued).singleElement().satisfies(credential -> assertThat(FakeVault.closed(credential)).isTrue());
            });
            assertThat(log.records()).singleElement().satisfies(record -> {
                assertThat(record.getThrown()).hasMessage("The reply could not be written");
                assertThat(text(record)).doesNotContain("pw", "ada", "example.com");
            });
        }
    }

    /** A query of another page is its caller's mistake: one {@code FieldButtons} answers one page's. */
    @Test void aQueryOfAnotherPageThrows() throws Exception {
        onUi(() -> {
            fake.pickAnswer = new CompletableFuture<>();
            buttons.query(query(page(), ORIGIN, pick("password", 8, 16, 200, 24), true), window, canvas, anchors);
            PageQuery other = query(page(), ORIGIN, pick("password", 8, 16, 200, 24), true);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> buttons.query(other, window, canvas, anchors))
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(fake.picks).hasSize(1);
        });
    }

    @Test void theScriptIsTheResource() {
        assertThat(FieldButtons.SCRIPT).startsWith("/*").contains("Symbol.for('dev.jasper.browser.logins')").endsWith("})();");
        assertThat(FieldButtons.PICK).isEqualTo("login.pick");
    }

    /** F19: the answer is the login's non-empty members, in order, written strictly. */
    @Test void theReplyIsTheLoginsNonEmptyMembers() {
        assertThat(FieldButtons.reply("ada", "pw".toCharArray())).isEqualTo("{\"username\":\"ada\",\"password\":\"pw\"}");
        assertThat(FieldButtons.reply("", "pw".toCharArray())).isEqualTo("{\"password\":\"pw\"}");
        assertThat(FieldButtons.reply("a\"b", new char[0])).isEqualTo("{\"username\":\"a\\\"b\"}");
        assertThat(FieldButtons.reply("", new char[0])).isEqualTo("{}");
    }

    /** A {@code login.pick} of {@code field} at the CSS rectangle given, as login-fields.js sends it. */
    private static Map<String, Object> pick(String field, long x, long y, long width, long height) {
        var request = new LinkedHashMap<String, Object>();
        request.put("op", "login.pick");
        request.put("field", field);
        request.put("x", x);
        request.put("y", y);
        request.put("width", width);
        request.put("height", height);
        return request;
    }

    private static Map<String, Object> with(Map<String, Object> request, String name, Object value) {
        request.put(name, value);
        return request;
    }

    private PageQuery query(Page page, String origin, Map<String, Object> request, boolean clicked) {
        return query(page, Optional.of(origin), request, clicked);
    }

    /** A query of {@code page}'s from {@code origin}, whose activation counts its asks and allows it when {@code clicked}. */
    private PageQuery query(Page page, Optional<String> origin, Map<String, Object> request, boolean clicked) {
        return query(page, origin, request, () -> clicked);
    }

    /** A query of {@code page}'s from {@link #ORIGIN}, whose activation is {@code activation}'s, its asks counted. */
    private PageQuery query(Page page, String origin, Map<String, Object> request, java.util.function.BooleanSupplier activation) {
        return query(page, Optional.of(origin), request, activation);
    }

    private PageQuery query(Page page, Optional<String> origin, Map<String, Object> request, java.util.function.BooleanSupplier activation) {
        return new PageQuery(page, origin, request, () -> {
            activations.incrementAndGet();
            return activation.getAsBoolean();
        }, press::get, shown::get, (code, text) -> replies.add(code + " " + text));
    }

    private static Page page() {
        return new Page(URI.create("https://example.com/sign-in"), () -> { }, PageTestAccess.noFavicons(), Runnable::run,
            PageContext.persistent());
    }

    /** A left click at {@code x}, {@code y} on the page's canvas. */
    private static LastPress click(int x, int y) {
        return new LastPress(1, 0, 0, x, y);
    }

    private static String text(LogRecord record) {
        Object[] parameters = record.getParameters();
        return record.getMessage() + " " + (parameters == null ? "" : List.of(parameters));
    }

    private static void onUi(Runnable check) throws Exception {
        var thrown = new AtomicReference<Throwable>();
        EventQueue.invokeAndWait(() -> {
            try {
                check.run();
            } catch (Throwable failure) {
                thrown.set(failure);
            }
        });
        if (thrown.get() instanceof Error error) throw error;
        if (thrown.get() instanceof Exception exception) throw exception;
    }

    private static final class Window implements WindowHandle {
        private final UUID id = UUID.randomUUID();

        @Override public UUID id() { return id; }
        @Override public List<TabHandle> tabs() { return List.of(); }
        @Override public Optional<TabHandle> activeTab() { return Optional.empty(); }
        @Override public boolean isActive() { return true; }
        @Override public boolean isOpen() { return true; }
        @Override public void toFront() { }
    }

    /** A live anchor the Vault's picker would attach to, which tells those who asked as it closes, once. */
    private static final class Anchor implements UiAnchor {
        private final List<Runnable> listeners = new ArrayList<>();
        boolean closed;

        @Override public boolean isOpen() { return !closed; }

        @Override public Subscription onClosed(Runnable handler) {
            if (closed) {
                handler.run();
                return () -> { };
            }
            listeners.add(handler);
            return () -> listeners.remove(handler);
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            List.copyOf(listeners).forEach(Runnable::run);
            listeners.clear();
        }
    }
}
