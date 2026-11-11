package dev.jasper.browser.item;

import dev.jasper.browser.page.NavigationHistory.History;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.permissions.AudioMode;
import dev.jasper.browser.permissions.Decision;
import dev.jasper.browser.permissions.Grant;
import dev.jasper.browser.permissions.Permission;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TabPlacement;
import dev.jasper.sdk.ui.UiEvent;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Spec section 6: the menu button's privacy group, a separator, a Reset item for each permission stored for the page's
 * site and a separator, then Unmute Current Page and a separator while the page is muted, between the bookmark group and
 * Settings; the menu drops the separators that would lead or follow one another. Spec section 12's audio, as the
 * group's item drives it: the first sound of a page with no stored choice mutes it and the audio bar asks; the Always
 * answers are stored in the item's own store, a /dev/null item's in its group's (ruling V20); Unmute Current Page acts
 * as Allow Once; and the next main-frame load drops the Once answers and the bar. Stand-in items over stores in memory on
 * the testkit's fake host; the page's sound is reported as the privacy router would, and its muting read from the mode
 * its sub-frames would get. No Chromium.
 */
class PrivacyMenuTest {
    private static final String ID = "dev.jasper.browser";
    private static final String SITE = "https://a.test";
    private static final BrowserSettings GOOGLE = SearchEngineTest.settings(SearchEngine.GOOGLE, "", "");
    private static final List<Grant> GRANTS = List.of(grant(Permission.CAMERA, Decision.ALLOWED), grant(Permission.CAMERA_AND_MICROPHONE, Decision.BLOCKED),
        grant(Permission.NOTIFICATIONS, Decision.BLOCKED), grant(Permission.AUDIO_PLAYBACK, Decision.ALLOWED));
    private final MemoryStores memory = new MemoryStores();
    private FakePluginHost host;

    @AfterEach void close() throws Exception {
        if (host != null) onUi(() -> {
            host.close();
            return null;
        });
        memory.close();
    }

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

    /** Spec section 6: Unmute Current Page, only while the page is muted, then a separator, after the Reset items. */
    @Test void unmuteCurrentPageAndASeparatorFollowTheResetsOnlyWhileThePageIsMuted() {
        List<MenuItem> muted = PrivacyMenu.items(GRANTS, true);
        assertThat(muted.subList(0, 6)).isEqualTo(PrivacyMenu.items(GRANTS, false));
        assertThat(muted.subList(6, muted.size())).extracting(MenuItem::id, MenuItem::label, MenuItem::action, MenuItem::separator, MenuItem::enabled)
            .containsExactly(tuple("browser.unmute", "Unmute Current Page", "browser.unmute", false, true), tuple("browser.unmute.end", "", "", true, true));
        assertThat(PrivacyMenu.items(List.of(), true)).extracting(MenuItem::label).containsExactly("", "", "Unmute Current Page", "");
        assertThat(PrivacyMenu.items(GRANTS, false)).extracting(MenuItem::action).doesNotContain("browser.unmute");

        assertThat(labels(Optional.of(false), GRANTS, true)).containsExactly("Add Bookmark", "", "Manage Bookmarks", "History", "",
            "Reset Camera Permission (Allowed)", "Reset Camera and Microphone Permission (Blocked)", "Reset Notifications Permission (Blocked)",
            "Reset Audio Playback Permission (Allowed)", "", "Unmute Current Page", "", "Settings");
        assertThat(labels(Optional.empty(), List.of(), true)).as("no bookmark group and no grant").containsExactly("Unmute Current Page", "", "Settings");
        assertThat(labels(Optional.of(true), List.of(), true)).containsExactly("Remove Bookmark", "", "Manage Bookmarks", "History", "",
            "Unmute Current Page", "", "Settings");
    }

    @Test void aResetsActionNamesItsPermission() {
        for (Permission permission : Permission.values())
            assertThat(PrivacyMenu.reset("browser.permission.reset." + permission.token())).contains(permission);
        for (String other : List.of("browser.permission.reset.", "browser.permission.reset.location", "browser.permission.reset.Camera",
                "browser.settings", "browser.bookmark.add", "camera", "browser.unmute"))
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
            for (var site : List.of(GRANTS, List.<Grant>of()))
                for (boolean muted : new boolean[] {false, true}) {
                    List<MenuItem> menu = menu(bookmarked, site, muted);
                    assertThat(menu.getFirst().separator()).as("none leads").isFalse();
                    assertThat(menu.getLast().separator()).isFalse();
                    for (int at = 1; at < menu.size(); at++)
                        assertThat(menu.get(at).separator() && menu.get(at - 1).separator()).as("no two follow each other").isFalse();
                }
    }

    /**
     * Spec section 12: the first time a site with no stored choice plays sound, the page is muted at once and the bar
     * asks; a second sound asks nothing more. Allow Once plays the page's sound until its next main-frame load, which asks
     * again; Deny Once keeps it muted, and Unmute Current Page then plays it. Nothing is stored.
     */
    @Test void aSitesFirstSoundWithNoStoredChoiceMutesThePageAndTheBarAsks() throws Exception {
        BrowserItems items = items();
        BrowserItem item = onUi(() -> items.open(URI.create(SITE + "/room"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty()));
        Page page = ItemTestAccess.page(item);
        onUi(() -> {
            // As Chromium starts the document, its frames in the store's mode, then the UI thread hears of it.
            PageTestAccess.audioDocumentStarted(page, AudioMode.ASK);
            PageTestAccess.loadStarted(page, SITE + "/room");
            assertThat(bar(item).state().visible()).as("no sound yet").isFalse();
            assertThat(menuActions(item)).doesNotContain("browser.unmute");
            page.privacy().audioPlaying();
            assertThat(bar(item).state().visible()).isTrue();
            assertThat(((Nodes.Text) bar(item).children().getFirst()).text()).isEqualTo("Audio was muted. Allow playback by https://a.test?");
            assertThat(page.privacy().audioMode()).as("every frame muted").isEqualTo(AudioMode.BLOCK);
            assertThat(menuActions(item)).contains("browser.unmute");
            page.privacy().audioPlaying();
            assertThat(bar(item).state().visible()).as("a second sound").isTrue();

            view(item).emit(new UiEvent.Action("browser.audio.allow-once"));
            assertThat(bar(item).state().visible()).isFalse();
            assertThat(page.privacy().audioMode()).as("every frame plays").isEqualTo(AudioMode.ALLOW);
            assertThat(menuActions(item)).doesNotContain("browser.unmute");
            page.privacy().audioPlaying();
            assertThat(bar(item).state().visible()).as("allowed for this document").isFalse();

            PageTestAccess.audioDocumentStarted(page, AudioMode.ASK);
            PageTestAccess.loadStarted(page, SITE + "/room");
            page.privacy().audioPlaying();
            assertThat(bar(item).state().visible()).as("the next document asks again").isTrue();
            assertThat(page.privacy().audioMode()).as("every frame muted again").isEqualTo(AudioMode.BLOCK);
            view(item).emit(new UiEvent.Action("browser.audio.deny-once"));
            assertThat(bar(item).state().visible()).isFalse();
            assertThat(page.privacy().audioMode()).isEqualTo(AudioMode.BLOCK);
            assertThat(menuActions(item)).as("still muted").contains("browser.unmute");

            view(item).choose(BrowserToolbar.MENU, "browser.unmute");
            assertThat(page.privacy().audioMode()).isEqualTo(AudioMode.ALLOW);
            assertThat(menuActions(item)).doesNotContain("browser.unmute");
            assertThat(host.failures()).isEmpty();
            return null;
        });
        assertThat(item.store().permissions().all()).as("the Once answers store nothing").isEmpty();
    }

    /**
     * The Always answers are stored in the item's store, and decide the site's next documents: a stored Allow plays
     * without asking, and a stored Block mutes without the bar, offering Unmute Current Page once the page plays.
     */
    @Test void theAlwaysAnswersAreStoredAndDecideTheSitesNextDocuments() throws Exception {
        BrowserItems items = items();
        BrowserItem item = onUi(() -> items.open(URI.create(SITE + "/"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty()));
        Page page = ItemTestAccess.page(item);
        onUi(() -> {
            PageTestAccess.loadStarted(page, SITE + "/");
            page.privacy().audioPlaying();
            view(item).emit(new UiEvent.Action("browser.audio.allow-always"));
            assertThat(item.store().permissions().decision(SITE, Permission.AUDIO_PLAYBACK)).contains(Decision.ALLOWED);
            assertThat(page.privacy().audioMode()).isEqualTo(AudioMode.ALLOW);
            assertThat(bar(item).state().visible()).isFalse();
            return null;
        });
        onUi(() -> {
            assertThat(menuLabels(item)).as("the menu follows the store").contains("Reset Audio Playback Permission (Allowed)");
            PageTestAccess.loadStarted(page, SITE + "/next");
            page.privacy().audioPlaying();
            assertThat(bar(item).state().visible()).as("allowed always").isFalse();
            assertThat(menuActions(item)).doesNotContain("browser.unmute");

            PageTestAccess.loadStarted(page, "https://b.test/");
            page.privacy().audioPlaying();
            view(item).emit(new UiEvent.Action("browser.audio.deny-always"));
            assertThat(item.store().permissions().decision("https://b.test", Permission.AUDIO_PLAYBACK)).contains(Decision.BLOCKED);
            assertThat(page.privacy().audioMode()).isEqualTo(AudioMode.BLOCK);
            PageTestAccess.loadStarted(page, "https://b.test/again");
            assertThat(menuActions(item)).as("muted only once the page plays").doesNotContain("browser.unmute");
            page.privacy().audioPlaying();
            assertThat(bar(item).state().visible()).as("blocked always: no bar").isFalse();
            assertThat(menuActions(item)).contains("browser.unmute");
            view(item).choose(BrowserToolbar.MENU, "browser.unmute");
            assertThat(page.privacy().audioMode()).isEqualTo(AudioMode.ALLOW);
            assertThat(host.failures()).isEmpty();
            return null;
        });
        assertThat(item.store().permissions().decision("https://b.test", Permission.AUDIO_PLAYBACK)).as("Unmute stores nothing")
            .contains(Decision.BLOCKED);
    }

    /**
     * Review focus 3: the bar goes as the page loads another document, and an answer from the bar it showed before
     * stores nothing and changes nothing.
     */
    @Test void theBarGoesWithItsDocumentAndALateAnswerStoresNothing() throws Exception {
        BrowserItems items = items();
        BrowserItem item = onUi(() -> items.open(URI.create(SITE + "/"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty()));
        Page page = ItemTestAccess.page(item);
        onUi(() -> {
            PageTestAccess.loadStarted(page, SITE + "/");
            page.privacy().audioPlaying();
            assertThat(bar(item).state().visible()).isTrue();
            PageTestAccess.loadStarted(page, "https://b.test/");
            assertThat(bar(item).state().visible()).isFalse();
            assertThat(menuActions(item)).doesNotContain("browser.unmute");
            for (String late : List.of("browser.audio.allow-always", "browser.audio.deny-always", "browser.audio.allow-once"))
                view(item).emit(new UiEvent.Action(late));
            assertThat(page.privacy().audioMode()).as("no frame told anything").isEqualTo(AudioMode.BLOCK);
            assertThat(bar(item).state().visible()).isFalse();
            view(item).emit(new UiEvent.Action("browser.unmute"));
            assertThat(page.privacy().audioMode()).as("nothing to unmute").isEqualTo(AudioMode.BLOCK);
            assertThat(host.failures()).isEmpty();
            return null;
        });
        assertThat(item.store().permissions().all()).isEmpty();
    }

    /**
     * Review round 1, item 2: a stored Block's first sound mutes every frame of the page, whatever mode they started in,
     * so a sub-frame that started playing in another mode is muted too.
     */
    @Test void aStoredBlocksFirstSoundMutesEveryFrameWhateverModeTheyStartedIn() throws Exception {
        BrowserItems items = items();
        BrowserItem item = onUi(() -> items.open(URI.create(SITE + "/"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty()));
        item.store().permissions().set(SITE, Permission.AUDIO_PLAYBACK, Decision.BLOCKED).get(10, java.util.concurrent.TimeUnit.SECONDS);
        Page page = ItemTestAccess.page(item);
        onUi(() -> {
            PageTestAccess.audioDocumentStarted(page, AudioMode.ALLOW);
            PageTestAccess.loadStarted(page, SITE + "/");
            page.privacy().audioPlaying();
            assertThat(page.privacy().audioMode()).as("every frame muted").isEqualTo(AudioMode.BLOCK);
            assertThat(bar(item).state().visible()).isFalse();
            assertThat(menuActions(item)).contains("browser.unmute");
            assertThat(host.failures()).isEmpty();
            return null;
        });
    }

    /**
     * Review round 1, item 2: an answer meant for the document before, which arrives once Chromium has started the next,
     * leaves the next document's mode as Chromium set it, and tells its frames nothing.
     */
    @Test void aMuteMeantForTheDocumentBeforeLeavesTheNextDocumentsModeAlone() throws Exception {
        BrowserItems items = items();
        BrowserItem item = onUi(() -> items.open(URI.create(SITE + "/"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty()));
        Page page = ItemTestAccess.page(item);
        onUi(() -> {
            PageTestAccess.audioDocumentStarted(page, AudioMode.ASK);
            PageTestAccess.loadStarted(page, SITE + "/");
            page.privacy().audioPlaying();
            assertThat(page.privacy().audioMode()).isEqualTo(AudioMode.BLOCK);
            // Chromium starts the next document, in its site's stored mode; the UI thread has not heard of it yet.
            PageTestAccess.audioDocumentStarted(page, AudioMode.ASK);
            view(item).emit(new UiEvent.Action("browser.audio.allow-once"));
            assertThat(page.privacy().audioMode()).as("the next document's, as Chromium set it").isEqualTo(AudioMode.ASK);
            PageTestAccess.loadStarted(page, SITE + "/next");
            assertThat(bar(item).state().visible()).isFalse();
            page.privacy().audioPlaying();
            assertThat(page.privacy().audioMode()).as("the next document's own sound mutes it").isEqualTo(AudioMode.BLOCK);
            assertThat(host.failures()).isEmpty();
            return null;
        });
        assertThat(item.store().permissions().all()).isEmpty();
    }

    /** Review round 1, item 5: a group that has closed hears nothing more of its page, which may outlive it. */
    @Test void aClosedGroupHearsNothingMoreOfItsPage() throws Exception {
        BrowserItems items = items();
        BrowserItem item = onUi(() -> items.open(URI.create(SITE + "/"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty()));
        Page page = ItemTestAccess.page(item);
        onUi(() -> {
            PageTestAccess.audioDocumentStarted(page, AudioMode.ASK);
            PageTestAccess.loadStarted(page, SITE + "/");
            item.privacy().close();
            page.privacy().audioPlaying();
            assertThat(page.privacy().audioMode()).as("no frame muted").isEqualTo(AudioMode.ASK);
            assertThat(host.failures()).isEmpty();
            return null;
        });
    }

    /** Ruling V20: a /dev/null item's audio choices come from, and go to, its group's store in memory alone. */
    @Test void aDevNullItemsAudioChoicesStayInItsGroupsStore() throws Exception {
        BrowserItems items = items();
        DataStore persistent = onUi(() -> items.dataStores().persistent());
        persistent.permissions().set(SITE, Permission.AUDIO_PLAYBACK, Decision.ALLOWED).get(10, java.util.concurrent.TimeUnit.SECONDS);
        BrowserItem item = onUi(() -> items.open(URI.create(SITE + "/"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty(),
            items.dataStores().newDevNull()));
        Page page = ItemTestAccess.page(item);
        onUi(() -> {
            PageTestAccess.loadStarted(page, SITE + "/");
            page.privacy().audioPlaying();
            assertThat(bar(item).state().visible()).as("the persistent store's Allow is not its").isTrue();
            view(item).emit(new UiEvent.Action("browser.audio.deny-always"));
            return null;
        });
        assertThat(item.store().permissions().decision(SITE, Permission.AUDIO_PLAYBACK)).contains(Decision.BLOCKED);
        assertThat(persistent.permissions().all()).extracting(Grant::origin, Grant::permission, Grant::decision)
            .containsExactly(tuple(SITE, Permission.AUDIO_PLAYBACK, Decision.ALLOWED));
    }

    /** A document without a site has no choice to ask for or store: it is muted without the bar, and Unmute plays it. */
    @Test void aDocumentWithoutASiteIsMutedWithoutTheBar() throws Exception {
        BrowserItems items = items();
        BrowserItem item = onUi(() -> items.open(URI.create(SITE + "/"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty()));
        Page page = ItemTestAccess.page(item);
        onUi(() -> {
            PageTestAccess.loadStarted(page, "data:text/html,<audio autoplay src=a.mp3>");
            page.privacy().audioPlaying();
            assertThat(bar(item).state().visible()).isFalse();
            assertThat(menuActions(item)).contains("browser.unmute");
            view(item).choose(BrowserToolbar.MENU, "browser.unmute");
            assertThat(page.privacy().audioMode()).isEqualTo(AudioMode.ALLOW);
            assertThat(host.failures()).isEmpty();
            return null;
        });
        assertThat(item.store().permissions().all()).isEmpty();
    }

    private static List<String> labels(Optional<Boolean> bookmarked, List<Grant> site) {
        return labels(bookmarked, site, false);
    }

    private static List<String> labels(Optional<Boolean> bookmarked, List<Grant> site, boolean muted) {
        return menu(bookmarked, site, muted).stream().map(MenuItem::label).toList();
    }

    private static List<MenuItem> menu(Optional<Boolean> bookmarked, List<Grant> site, boolean muted) {
        Nodes.Stack bar = new BrowserToolbar(typed -> List.of()).node(List.of(), "https://a.test/page", false, false, false, Optional.empty(), History.EMPTY,
            GOOGLE, false, bookmarked.isEmpty(), bookmarked, PrivacyMenu.items(site, muted));
        return ((Nodes.MenuButton) Views.node(bar, BrowserToolbar.MENU)).items();
    }

    private static Grant grant(Permission permission, Decision decision) {
        return new Grant(SITE, permission, decision, Instant.EPOCH, Instant.EPOCH);
    }

    /** The audio bar of {@code item}'s view. UI thread. */
    private Nodes.Stack bar(BrowserItem item) {
        return (Nodes.Stack) Views.node(view(item).spec().root(), AudioBar.BAR);
    }

    /** The actions of {@code item}'s menu button. UI thread. */
    private List<String> menuActions(BrowserItem item) {
        return ((Nodes.MenuButton) Views.node(view(item).spec().root(), BrowserToolbar.MENU)).items().stream().map(MenuItem::action).toList();
    }

    private List<String> menuLabels(BrowserItem item) {
        return ((Nodes.MenuButton) Views.node(view(item).spec().root(), BrowserToolbar.MENU)).items().stream().map(MenuItem::label).toList();
    }

    /** The view of {@code item}'s tab, found by the title it shows now. UI thread. */
    private FakeKitView view(BrowserItem item) {
        return host.contentView(ID, host.contentPresentation(item.tab()).title()).orElseThrow();
    }

    /** The items of a started stand-in plugin on a fake host with a window, whose stores are in memory. */
    private BrowserItems items() throws Exception {
        return onUi(() -> {
            host = new FakePluginHost();
            var context = new AtomicReference<PluginContext>();
            var settings = new AtomicReference<BrowserSettings.Declared>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), plugin -> {
                context.set(plugin);
                settings.set(BrowserSettings.declare(plugin.settings()));
            });
            host.activateTerminalWindow(host.addTerminalWindow());
            var stores = new DataStores(memory.make(), memory::make, () -> PageTestAccess.recordingContext(new AtomicInteger()), () -> false);
            return new BrowserItems(context.get(), settings.get()::read, stores, page -> { });
        });
    }

    private static <T> T onUi(Callable<T> task) throws Exception {
        var result = new AtomicReference<T>();
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(task.call());
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        // What the task posted, such as a store's change told on the UI thread, runs before the next step.
        SwingUtilities.invokeAndWait(() -> { });
        if (failure.get() instanceof Error error) throw error;
        if (failure.get() instanceof Exception exception) throw exception;
        return result.get();
    }
}
