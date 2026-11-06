package dev.jasper.browser.item;

import dev.jasper.browser.item.AudioGate.AudioAnswer;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.permissions.AudioMode;
import dev.jasper.browser.permissions.Decision;
import dev.jasper.browser.permissions.Grant;
import dev.jasper.browser.permissions.Origins;
import dev.jasper.browser.permissions.Permission;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * One item's privacy group of the menu button (spec section 6): a separator, a Reset item for each permission its store
 * holds for the page's site, such as "Reset Camera Permission (Allowed)", and a separator; then, while the page is
 * muted, Unmute Current Page and a separator. The menu drops a separator that leads or follows another
 * ({@link BrowserToolbar}), so with no permission stored and the page unmuted the group shows nothing. From
 * {@link #follows}, the item draws again as its store's permissions change.
 *
 * <p>A Reset item chosen revokes its permission for the site the menu last offered that item for (ruling V17), even when
 * the page has gone to another site since, and the items of the store that show that site load again. The host updates
 * an open menu as the item draws, so the site is the one the row showed; where the new site offers the same Reset, the
 * row looks the same, and it counts as the new site's.
 *
 * <p>It also holds the item's {@link AudioGate} (spec section 12) and the {@link AudioBar} it shows. As the page's main
 * frame starts each document, the gate takes the mode the item's store holds for the document's top-level site, a
 * /dev/null item's store in memory included (ruling V20), and the bar goes. The first time a frame of the document
 * reports sound, through the page's privacy hooks, a site with no stored choice has every frame of the page muted and
 * the bar asks, and a stored Block offers Unmute Current Page. The bar's Allow answers have every frame play, its Deny
 * answers keep them muted, and its Always answers are stored in the item's store; Unmute Current Page acts as Allow
 * Once. UI thread.
 */
final class PrivacyMenu implements AutoCloseable {
    /** What a Reset item reports, before its permission's token. */
    static final String RESET = "browser.permission.reset.";
    /** What Unmute Current Page reports. */
    static final String UNMUTE = "browser.unmute";
    private static final MenuItem START = new MenuItem("browser.permission.separator", "", "", "", true, true),
        END = new MenuItem("browser.permission.end", "", "", "", true, true),
        UNMUTE_ITEM = new MenuItem(UNMUTE, "Unmute Current Page", "", UNMUTE, true, false),
        UNMUTE_END = new MenuItem("browser.unmute.end", "", "", "", true, true);
    private final DataStore store;
    private final BrowserItems items;
    private final Supplier<String> address;
    private final Runnable reload;
    private final Page page;
    private final AudioGate audio = new AudioGate();
    // The item's listening to its page's load starts and its store's permissions, from follows() until close(); and what
    // draws the item.
    private Subscription loadStarts = () -> { }, changed = () -> { };
    private Runnable render = () -> { };
    // For each permission, the site the menu last offered its Reset item for.
    private final Map<Permission, String> offered = new EnumMap<>(Permission.class);

    /**
     * The group of an item in {@code store}, whose page is {@code page}.
     *
     * @param store the item's data store, whose permissions the group offers to reset
     * @param items the plugin's items, of which those in {@code store} that show a site reset load again
     * @param address the address the item shows now
     * @param reload loads the item's page again, when it may
     * @param page the item's page, whose sound the group mutes and plays
     */
    PrivacyMenu(DataStore store, BrowserItems items, Supplier<String> address, Runnable reload, Page page) {
        this.store = Objects.requireNonNull(store, "store");
        this.items = Objects.requireNonNull(items, "items");
        this.address = Objects.requireNonNull(address, "address");
        this.reload = Objects.requireNonNull(reload, "reload");
        this.page = Objects.requireNonNull(page, "page");
    }

    /**
     * The item is made: from now on, until {@link #close()}, the group hears its page's load starts and sound, and
     * {@code render} draws the item again as its store's permissions change and as its page's sound is muted or played.
     * The group hears nothing before, as its page loads nothing before it is shown.
     *
     * @param render draws the item again
     */
    void follows(Runnable render) {
        this.render = Objects.requireNonNull(render, "render");
        loadStarts.close();
        changed.close();
        loadStarts = page.onLoadStart(this::documentStarted);
        changed = store.permissions().onChanged(render);
        page.privacy().onAudioPlaying(this::playing);
    }

    /**
     * The group as the menu shows it now, for the site of the page's address; each Reset item is then the one a choice
     * of its action revokes.
     *
     * @return the group's items, as {@link #items(List, boolean)} gives them, with Unmute Current Page while the page is
     *     muted
     */
    List<MenuItem> group() {
        Optional<String> site = Origins.of(address.get());
        List<Grant> grants = site.map(store.permissions()::of).orElse(List.of());
        for (Grant grant : grants) offered.put(grant.permission(), grant.origin());
        return items(grants, audio.muted());
    }

    /**
     * The audio bar as the item shows it now, between its toolbar and its page.
     *
     * @return the bar, which shows while it asks
     */
    Nodes.Stack audioBar() {
        return AudioBar.node(audio.origin(), audio.barShown());
    }

    /**
     * The menu or the audio bar reported {@code action}: a Reset item's revokes its permission for the site the menu
     * offered it for, and the store's items that show that site load again, this one among them; a bar's answer and
     * Unmute Current Page change what the page plays.
     *
     * @param action the action
     * @return whether it was a Reset item's, a bar's answer or Unmute Current Page's
     */
    boolean choose(String action) {
        if (action.equals(UNMUTE)) {
            unmute();
            return true;
        }
        Optional<AudioAnswer> answer = AudioBar.answer(action);
        if (answer.isPresent()) {
            answer(answer.get());
            return true;
        }
        Optional<Permission> permission = reset(action);
        if (permission.isEmpty()) return false;
        String site = offered.get(permission.get());
        if (site != null) items.reloadShowing(store, store.permissions().revoke(site, permission.get()));
        return true;
    }

    /**
     * The item's page loads again if it shows one of {@code sites}, whose permissions changed in its store.
     *
     * @param sites the sites, as {@link Origins#of} names them
     */
    void reloadShowing(Set<String> sites) {
        if (Origins.of(address.get()).filter(sites::contains).isPresent()) reload.run();
    }

    /** The item closes: the group hears its page no more, and draws the item no more. */
    @Override public void close() {
        loadStarts.close();
        changed.close();
        page.privacy().onAudioPlaying(() -> { });
        render = () -> { };
    }

    /**
     * The page's main frame starts loading the document at {@code url}: the gate takes the mode the item's store holds
     * for its top-level site, as the page's frames are given it ({@code AudioMode.of}), muted for a document without a
     * site, and the bar goes.
     */
    private void documentStarted(String url) {
        Optional<String> site = Origins.of(url);
        boolean shown = audio.barShown() || audio.muted();
        audio.document(site.orElse(""), site.map(store.permissions()::audioMode).orElse(AudioMode.BLOCK));
        if (shown) render.run();
    }

    /**
     * A frame of the page's document plays sound: a site with no stored choice has every frame muted and the bar ask,
     * and a stored Block has every frame muted too, whatever mode a frame started in.
     */
    private void playing() {
        switch (audio.playing()) {
            case SHOW_BAR, MUTED -> {
                page.privacy().mute(true);
                render.run();
            }
            case NONE -> { }
        }
    }

    /** The bar is answered: the page plays or stays muted, and an Always answer is stored for the site it named. */
    private void answer(AudioAnswer answer) {
        if (!audio.barShown()) return;
        Optional<Decision> decision = audio.answer(answer);
        page.privacy().mute(audio.muted());
        decision.ifPresent(stored -> store.permissions().set(audio.origin(), Permission.AUDIO_PLAYBACK, stored));
        render.run();
    }

    /** Unmute Current Page: the page's sound plays until its next main-frame load. Nothing while it is not muted. */
    private void unmute() {
        if (!audio.muted()) return;
        audio.unmute();
        page.privacy().mute(false);
        render.run();
    }

    /**
     * The group's items.
     *
     * @param site the permissions stored for the page's site, in the order the items list them
     * @param muted whether the page's sound is muted
     * @return a separator, a Reset item for each grant, its id and action {@code browser.permission.reset.<token>}, and
     *     a separator; then, while muted, Unmute Current Page, its id and action {@value #UNMUTE}, and a separator
     */
    static List<MenuItem> items(List<Grant> site, boolean muted) {
        var items = new ArrayList<MenuItem>(site.size() + 4);
        items.add(START);
        for (Grant grant : site) {
            String id = RESET + grant.permission().token();
            items.add(new MenuItem(id, "Reset " + grant.permission().label() + " Permission (" + grant.decision().label() + ")", "", id, true, false));
        }
        items.add(END);
        if (muted) {
            items.add(UNMUTE_ITEM);
            items.add(UNMUTE_END);
        }
        return List.copyOf(items);
    }

    /**
     * The permission a Reset item's action resets.
     *
     * @param action an action the menu reported
     * @return the permission; empty for any other action
     */
    static Optional<Permission> reset(String action) {
        return action.startsWith(RESET) ? Permission.ofToken(action.substring(RESET.length())) : Optional.empty();
    }
}
