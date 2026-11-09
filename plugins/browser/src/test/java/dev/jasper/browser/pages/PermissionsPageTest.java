package dev.jasper.browser.pages;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.json.StrictJson;
import dev.jasper.browser.permissions.Decision;
import dev.jasper.browser.permissions.Grant;
import dev.jasper.browser.permissions.Permission;
import dev.jasper.browser.permissions.PermissionStore;
import dev.jasper.browser.store.BrowserDatabase;
import java.awt.EventQueue;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code jasper-about:permissions} (spec section 12): the page as the plugin serves it, iTerm2's copy with no stored
 * permission in it, and what its script asks, answered from the site permissions of the asking browser's store, here
 * two stores in memory, browser 1's and browser 2's; a change reloads that store's items that show the sites it changed.
 */
class PermissionsPageTest {
    private static final String NOT_UNDERSTOOD = "Jasper does not understand this request.";
    private static final String A = "https://a.test", B = "https://b.test";
    private final List<BrowserDatabase> databases = new ArrayList<>();
    private final PermissionStore one = store(), two = store();
    // The sites each store's change reloaded, as the plugin hands them to that store's items.
    private final List<Set<String>> reloadedOne = new CopyOnWriteArrayList<>(), reloadedTwo = new CopyOnWriteArrayList<>();
    private PagesFixture fixture;

    @BeforeEach void register() throws Exception {
        fixture = new PagesFixture();
        fixture.about.register(PermissionsPage.NAME, () -> fixture.pages.render(PermissionsPage.NAME, Map.of()));
        fixture.about.registerQueries(PermissionsPage.NAME, (browser, request) -> browser == 1 ? PermissionsPage.answer(one, reloadedOne::add, request)
            : browser == 2 ? PermissionsPage.answer(two, reloadedTwo::add, request) : CompletableFuture.failedFuture(new IllegalStateException("no store")));
    }

    @AfterEach void close() throws Exception {
        fixture.close();
        for (BrowserDatabase database : databases) database.close();
        for (BrowserDatabase database : databases) database.closed().get(30, TimeUnit.SECONDS);
    }

    @Test void itsAddressIsThePermissionsPage() {
        assertThat(PermissionsPage.NAME).isEqualTo("permissions");
        assertThat(PermissionsPage.ADDRESS).isEqualTo(URI.create("jasper-about:permissions"));
        assertThat(AboutScheme.page(PermissionsPage.ADDRESS.toString())).contains(PermissionsPage.NAME);
    }

    /** The page holds iTerm2's copy and its own script after about.js, and none of the permissions stored: they come by its queries. */
    @Test void thePageHoldsItsCopyAndItsScriptAndNoStoredPermission() throws Exception {
        done(one.set("https://stored.example", Permission.CAMERA, Decision.ALLOWED));
        String html = fixture.html(PermissionsPage.NAME);
        assertThat(html).contains("<title>Site Permissions</title>", "<h1>Site Permissions</h1>", "Clear All Permissions",
            "placeholder=\"Search by website or permission type…\"", "<option value=\"\">All Types</option>",
            "<option value=\"\">All Statuses</option>", "<option value=\"allowed\">Allowed</option>", "<option value=\"blocked\">Blocked</option>",
            "No permissions found",
            "(" + StrictJson.quote(fixture.about.secret(PermissionsPage.NAME).orElseThrow()) + ");\n" + AboutPages.resource("permissions.js").strip());
        int at = html.indexOf("All Types");
        for (Permission permission : Permission.values()) {
            String option = "<option value=\"" + permission.token() + "\">" + permission.label() + "</option>";
            assertThat(html.indexOf(option, at)).as("each type, in order: " + permission).isGreaterThan(at);
            at = html.indexOf(option, at);
        }
        assertThat(html).doesNotContain("{{", "stored.example");
        assertThat(AboutPages.resource("permissions.js")).contains("'Revoke ' + grant.label + ' permission for ' + grant.origin + '?'",
            "'Clear'", "'list'", "'revoke'", "'clear-site'", "'clear-all'", "toLowerCase()", "jasper.el(");
    }

    /** Each browser's page lists its own store's permissions, by site, then in the permissions' order. */
    @Test void listAnswersTheAskingStoresGrantsOnly() throws Exception {
        done(one.set(B, Permission.NOTIFICATIONS, Decision.BLOCKED));
        done(one.set(A, Permission.MICROPHONE, Decision.ALLOWED));
        done(one.set(A, Permission.CAMERA, Decision.BLOCKED));
        done(two.set("https://other.test", Permission.AUDIO_PLAYBACK, Decision.ALLOWED));
        assertThat(grants(ask(1, "{\"op\":\"list\"}"))).containsExactly(
            Map.of("origin", A, "type", "camera", "label", "Camera", "decision", "blocked"),
            Map.of("origin", A, "type", "microphone", "label", "Microphone", "decision", "allowed"),
            Map.of("origin", B, "type", "notifications", "label", "Notifications", "decision", "blocked"));
        assertThat(grants(ask(2, "{\"op\":\"list\"}"))).containsExactly(
            Map.of("origin", "https://other.test", "type", "audio", "label", "Audio Playback", "decision", "allowed"));
        assertThat(StrictJson.object(done(PermissionsPage.answer(store(), sites -> { }, "{\"op\":\"list\"}")), 3).orElseThrow())
            .as("none stored").isEqualTo(Map.of("grants", List.of()));
    }

    /** Review Focus 1: a hostile origin reads back exactly, as the page puts it in only as text. */
    @Test void aHostileOriginReadsBackExactly() throws Exception {
        String origin = "https://a.test</script><img src=x onerror=alert(1)>" + (char) 0x2028 + "\"'\\" + (char) 0x2029;
        done(one.set(origin, Permission.CAMERA, Decision.ALLOWED));
        assertThat(grants(ask(1, "{\"op\":\"list\"}"))).singleElement().satisfies(grant -> assertThat(grant).containsEntry("origin", origin));
    }

    /** Revoke, Clear and Clear All Permissions change the asking store alone, and reload its items that show the sites changed. */
    @Test void revokeClearSiteAndClearAllChangeOnlyThatStoreAndReloadItsItemsShowingThoseSites() throws Exception {
        for (PermissionStore store : List.of(one, two)) {
            done(store.set(A, Permission.CAMERA, Decision.ALLOWED));
            done(store.set(A, Permission.NOTIFICATIONS, Decision.BLOCKED));
            done(store.set(B, Permission.MICROPHONE, Decision.ALLOWED));
        }
        List<Grant> before = two.all();
        assertThat(ask(1, "{\"op\":\"revoke\",\"origin\":\"" + A + "\",\"type\":\"camera\"}")).isEqualTo("{}");
        assertThat(one.decision(A, Permission.CAMERA)).isEmpty();
        assertThat(one.decision(A, Permission.NOTIFICATIONS)).contains(Decision.BLOCKED);
        assertThat(reloadedOne).containsExactly(Set.of(A));
        assertThat(ask(1, "{\"op\":\"revoke\",\"origin\":\"" + A + "\",\"type\":\"camera\"}")).as("revoked already").isEqualTo("{}");
        assertThat(reloadedOne).as("nothing changed, nothing reloads").hasSize(1);

        assertThat(ask(1, "{\"op\":\"clear-site\",\"origin\":\"" + A + "\"}")).isEqualTo("{}");
        assertThat(one.of(A)).isEmpty();
        assertThat(one.of(B)).hasSize(1);
        assertThat(reloadedOne.getLast()).isEqualTo(Set.of(A));

        done(one.set(A, Permission.CAMERA, Decision.ALLOWED));
        assertThat(ask(1, "{\"op\":\"clear-all\"}")).isEqualTo("{}");
        assertThat(one.all()).isEmpty();
        assertThat(reloadedOne.getLast()).isEqualTo(Set.of(A, B));
        assertThat(two.all()).as("the other store is untouched").isEqualTo(before);
        assertThat(reloadedTwo).isEmpty();
    }

    /** Anything but the page's four requests fails with one reason, and changes nothing. */
    @Test void malformedJsonAnUnknownOpAndAMissingOriginFailTheAnswer() throws Exception {
        done(one.set(A, Permission.CAMERA, Decision.ALLOWED));
        for (String request : new String[] {null, "", "not json", "[]", "{\"op\":\"list\"", "{\"op\":\"list\"} x", "{}", "{\"op\":\"open\"}",
            "{\"op\":\"LIST\"}", "{\"op\":7}", "{\"op\":null}", "{\"op\":\"list\",\"op\":\"clear-all\"}", "{\"op\":\"list\",\"extra\":[]}",
            "{\"op\":\"revoke\",\"type\":\"camera\"}", "{\"op\":\"revoke\",\"origin\":\"" + A + "\"}",
            "{\"op\":\"revoke\",\"origin\":\"" + A + "\",\"type\":\"location\"}", "{\"op\":\"revoke\",\"origin\":\"" + A + "\",\"type\":\"Camera\"}",
            "{\"op\":\"revoke\",\"origin\":7,\"type\":\"camera\"}", "{\"op\":\"revoke\",\"origin\":null,\"type\":\"camera\"}",
            "{\"op\":\"clear-site\"}", "{\"op\":\"clear-site\",\"origin\":7}", "{\"op\":\"clear-site\",\"origin\":[]}", "{\"op\":\"clear\"}"}) {
            CompletableFuture<String> answer = PermissionsPage.answer(one, reloadedOne::add, request);
            assertThatThrownBy(() -> answer.get(10, TimeUnit.SECONDS)).as(String.valueOf(request)).isInstanceOf(ExecutionException.class)
                .cause().isInstanceOf(IllegalArgumentException.class).hasMessage(NOT_UNDERSTOOD);
        }
        assertThat(one.decision(A, Permission.CAMERA)).as("nothing changed").contains(Decision.ALLOWED);
        assertThat(reloadedOne).isEmpty();
    }

    private String ask(int browser, String request) throws Exception {
        String secret = fixture.about.secret(PermissionsPage.NAME).orElseThrow();
        return done(fixture.about.query("jasper-about:" + PermissionsPage.NAME, true, browser, secret + request).orElseThrow());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> grants(String answer) {
        Map<String, Object> read = StrictJson.object(answer, 3).orElseThrow();
        assertThat(read).containsOnlyKeys("grants");
        return (List<Map<String, Object>>) read.get("grants");
    }

    private PermissionStore store() {
        BrowserDatabase database = BrowserDatabase.memory();
        databases.add(database);
        Consumer<Throwable> unsaved = failure -> { throw new AssertionError("unsaved", failure); };
        return new PermissionStore(database, EventQueue::invokeLater, unsaved);
    }

    private static <T> T done(CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }
}
