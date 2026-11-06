package dev.jasper.browser.item;

import dev.jasper.browser.page.NavigationHistory.History;
import dev.jasper.browser.permissions.Decision;
import dev.jasper.browser.permissions.Grant;
import dev.jasper.browser.permissions.Permission;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Spec section 6: the menu button's privacy group, a separator, a Reset item for each permission stored for the page's
 * site and a separator, between the bookmark group and Settings; the menu drops the separators that would lead or follow
 * one another.
 */
class PrivacyMenuTest {
    private static final String SITE = "https://a.test";
    private static final BrowserSettings GOOGLE = SearchEngineTest.settings(SearchEngine.GOOGLE, "", "");
    private static final List<Grant> GRANTS = List.of(grant(Permission.CAMERA, Decision.ALLOWED), grant(Permission.CAMERA_AND_MICROPHONE, Decision.BLOCKED),
        grant(Permission.NOTIFICATIONS, Decision.BLOCKED), grant(Permission.AUDIO_PLAYBACK, Decision.ALLOWED));

    @Test void theGroupIsASeparatorAResetForEachGrantInTheSitesOrderAndASeparator() {
        List<MenuItem> items = PrivacyMenu.items(GRANTS, false);
        assertThat(items).extracting(MenuItem::label, MenuItem::action, MenuItem::separator).containsExactly(tuple("", "", true),
            tuple("Reset Camera Permission (Allowed)", "browser.permission.reset.camera", false),
            tuple("Reset Camera and Microphone Permission (Blocked)", "browser.permission.reset.camera-microphone", false),
            tuple("Reset Notifications Permission (Blocked)", "browser.permission.reset.notifications", false),
            tuple("Reset Audio Playback Permission (Allowed)", "browser.permission.reset.audio", false), tuple("", "", true));
        assertThat(items).allSatisfy(item -> {
            if (!item.separator()) assertThat(item.id()).isEqualTo(item.action());
            assertThat(item.enabled()).isTrue();
        });
        assertThat(PrivacyMenu.items(List.of(), false)).as("no grant: only the separators, which the menu drops").allMatch(MenuItem::separator);
    }

    @Test void aResetsActionNamesItsPermission() {
        for (Permission permission : Permission.values())
            assertThat(PrivacyMenu.reset("browser.permission.reset." + permission.token())).contains(permission);
        for (String other : List.of("browser.permission.reset.", "browser.permission.reset.location", "browser.permission.reset.Camera",
                "browser.settings", "browser.bookmark.add", "camera"))
            assertThat(PrivacyMenu.reset(other)).as(other).isEmpty();
    }

    /**
     * The menu: the bookmark group, then the privacy group, then Settings, joined by single separators; with no grant
     * the privacy group shows nothing, and without the bookmark group (as in /dev/null mode) no separator leads.
     */
    @Test void theMenuJoinsItsGroupsWithSingleSeparatorsAndNoneLeading() {
        assertThat(labels(Optional.of(false), GRANTS)).containsExactly("Add Bookmark", "", "Manage Bookmarks", "History", "",
            "Reset Camera Permission (Allowed)", "Reset Camera and Microphone Permission (Blocked)", "Reset Notifications Permission (Blocked)",
            "Reset Audio Playback Permission (Allowed)", "", "Settings");
        assertThat(labels(Optional.of(true), List.of())).as("no grant").containsExactly("Remove Bookmark", "", "Manage Bookmarks", "History", "", "Settings");
        assertThat(labels(Optional.empty(), List.of(grant(Permission.MICROPHONE, Decision.ALLOWED)))).as("no bookmark group")
            .containsExactly("Reset Microphone Permission (Allowed)", "", "Settings");
        assertThat(labels(Optional.empty(), List.of())).containsExactly("Settings");
        for (var bookmarked : List.of(Optional.of(true), Optional.<Boolean>empty()))
            for (var site : List.of(GRANTS, List.<Grant>of())) {
                List<MenuItem> menu = menu(bookmarked, site);
                assertThat(menu.getFirst().separator()).as("none leads").isFalse();
                assertThat(menu.getLast().separator()).isFalse();
                for (int at = 1; at < menu.size(); at++)
                    assertThat(menu.get(at).separator() && menu.get(at - 1).separator()).as("no two follow each other").isFalse();
            }
    }

    private static List<String> labels(Optional<Boolean> bookmarked, List<Grant> site) {
        return menu(bookmarked, site).stream().map(MenuItem::label).toList();
    }

    private static List<MenuItem> menu(Optional<Boolean> bookmarked, List<Grant> site) {
        Nodes.Stack bar = new BrowserToolbar(typed -> List.of()).node("https://a.test/page", false, false, false, Optional.empty(), History.EMPTY,
            GOOGLE, false, bookmarked.isEmpty(), bookmarked, PrivacyMenu.items(site, false));
        return ((Nodes.MenuButton) Views.node(bar, BrowserToolbar.MENU)).items();
    }

    private static Grant grant(Permission permission, Decision decision) {
        return new Grant(SITE, permission, decision, Instant.EPOCH, Instant.EPOCH);
    }
}
